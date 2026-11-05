-- =====================================================================
-- V8 - Monte ore: budget del lavoro, allocazione alle task, ore
--      registrate, richieste di ore e movimenti.
--
-- Il principio che tiene insieme tutto: i totali NON si salvano.
--   ore libere del lavoro = monte_ore - SUM(task.ore_allocate)
--   ore consumate di una task = SUM(voce_dichiarazione.ore)
--   task in sforamento = ore consumate > ore allocate
-- Si calcolano al volo. Un campo salvato sarebbe una seconda verita' da
-- tenere in sincronia, e prima o poi divergerebbe da quella vera.
--
-- Cio' che invece si salva e' la STORIA: movimento_ore registra ogni
-- variazione con autore, data e motivo, cosi' il monte ore iniziale non si
-- perde quando l'admin lo modifica e a fine lavoro si puo' confrontare la
-- stima con il consuntivo.
-- =====================================================================


-- ---------------------------------------------------------------------
-- Il budget del lavoro
--
-- NULL = non ancora fissato. Serve per i lavori che esistono gia' e per le
-- bozze che si creano prima di sapere quante ore costeranno: un DEFAULT 0
-- direbbe "budget esaurito", che e' un'affermazione diversa e sbagliata.
-- ---------------------------------------------------------------------
ALTER TABLE lavoro
    ADD COLUMN monte_ore DECIMAL(9,2) NULL AFTER stato;

ALTER TABLE lavoro ADD CONSTRAINT ck_lavoro_monte_ore
    CHECK (monte_ore IS NULL OR monte_ore >= 0);


-- ---------------------------------------------------------------------
-- Le ore allocate alla task
--
-- Sono una cosa diversa da stima_ore, che c'e' gia' dalla V3: la stima dice
-- quante ore servono secondo il PM, l'allocazione quante sono coperte dal
-- budget. Confonderle in un numero solo e' l'errore che questa colonna
-- evita, ed e' anche cio' che permette di precompilare una richiesta di ore
-- con la differenza fra le due.
--
-- Qui NOT NULL DEFAULT 0 e' giusto: una task senza allocazione ha davvero
-- zero ore coperte dal budget.
-- ---------------------------------------------------------------------
ALTER TABLE task
    ADD COLUMN ore_allocate DECIMAL(7,2) NOT NULL DEFAULT 0 AFTER stima_ore;

ALTER TABLE task ADD CONSTRAINT ck_task_allocate
    CHECK (ore_allocate >= 0);


-- ---------------------------------------------------------------------
-- Le ore registrate dal dipendente
--
-- Non serve una tabella nuova: voce_dichiarazione, dalla V4, e' GIA' il
-- registro delle ore. Ha la task, la descrizione del lavoro svolto, e
-- attraverso dichiarazione(utente_id, data) ha la persona e il giorno, con
-- uk_dich_utente_data che garantisce una sola dichiarazione per persona e
-- giorno. Mancava solo quante ore.
--
-- Il tetto giornaliero diventa percio' la somma delle voci di UNA
-- dichiarazione, e il vincolo di unicita' fa in modo che non ce ne sia una
-- seconda di nascosto per lo stesso giorno.
--
-- In tre passaggi perche' la tabella puo' avere righe (i dati di sviluppo ne
-- hanno tre): una colonna NOT NULL senza default le farebbe rifiutare, e un
-- DEFAULT 0 violerebbe subito il CHECK ore > 0.
-- ---------------------------------------------------------------------
ALTER TABLE voce_dichiarazione
    ADD COLUMN ore DECIMAL(4,2) NULL AFTER task_id;

-- Valore di comodo per le righe preesistenti. In produzione non ne tocca
-- nessuna; i dati di sviluppo sovrascrivono con valori realistici.
UPDATE voce_dichiarazione SET ore = 1.00 WHERE ore IS NULL;

ALTER TABLE voce_dichiarazione
    MODIFY COLUMN ore DECIMAL(4,2) NOT NULL;

ALTER TABLE voce_dichiarazione ADD CONSTRAINT ck_voce_ore
    CHECK (ore > 0);


-- ---------------------------------------------------------------------
-- Le richieste di ore
--
-- Il percorso che prima non c'era: quando il pool del lavoro non basta, il
-- PM non contratta a voce con l'admin, apre una richiesta. L'admin la vede
-- con tutto il contesto del lavoro, e approva, approva in parte o rifiuta.
--
-- task_id e' facoltativo: se la richiesta nasce da una task specifica, le
-- ore concesse finiscono direttamente la', e non possono per distrazione
-- essere allocate a un'altra. Se e' nullo, le ore concesse restano nel pool.
-- ---------------------------------------------------------------------
CREATE TABLE richiesta_ore (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    lavoro_id       BIGINT       NOT NULL,
    task_id         BIGINT       NULL,                  -- NULL = le ore restano nel pool
    richiedente_id  BIGINT       NOT NULL,              -- il PM
    ore_richieste   DECIMAL(7,2) NOT NULL,
    ore_concesse    DECIMAL(7,2) NULL,                  -- NULL finche' e' in attesa; 0 se rifiutata
    motivo          VARCHAR(500) NOT NULL,
    stato           VARCHAR(20)  NOT NULL DEFAULT 'IN_ATTESA',
    gestita_da_id   BIGINT       NULL,                  -- l'admin che ha deciso
    gestita_il      DATETIME     NULL,
    creata_il       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_richiesta_lavoro      FOREIGN KEY (lavoro_id)      REFERENCES lavoro (id),
    CONSTRAINT fk_richiesta_task        FOREIGN KEY (task_id)        REFERENCES task (id),
    CONSTRAINT fk_richiesta_richiedente FOREIGN KEY (richiedente_id) REFERENCES utente (id),
    CONSTRAINT fk_richiesta_gestita_da  FOREIGN KEY (gestita_da_id)  REFERENCES utente (id),

    CONSTRAINT ck_richiesta_stato
        CHECK (stato IN ('IN_ATTESA', 'APPROVATA', 'APPROVATA_PARZIALE', 'RIFIUTATA')),
    CONSTRAINT ck_richiesta_ore
        CHECK (ore_richieste > 0 AND (ore_concesse IS NULL OR ore_concesse >= 0)),
    -- Chi ha deciso e quando: valorizzati o nulli insieme, come
    -- ck_ass_rimozione in V3.
    CONSTRAINT ck_richiesta_gestione
        CHECK ((gestita_il IS NULL) = (gestita_da_id IS NULL)),
    -- Una richiesta e' in attesa esattamente quando non ha ancora un esito.
    -- I tre vincoli insieme rendono impossibile una riga a meta': gestita ma
    -- senza ore decise, o decisa ma ancora in attesa.
    CONSTRAINT ck_richiesta_esito
        CHECK ((stato = 'IN_ATTESA') = (ore_concesse IS NULL)),
    CONSTRAINT ck_richiesta_chiusa
        CHECK ((stato = 'IN_ATTESA') = (gestita_il IS NULL)),
    -- Le etichette dell'esito devono dire la verita' sui numeri: rifiutata
    -- vuol dire zero ore, approvata vuol dire tutte quelle chieste. Tutto il
    -- resto e' APPROVATA_PARZIALE.
    CONSTRAINT ck_richiesta_rifiuto
        CHECK (stato <> 'RIFIUTATA' OR ore_concesse = 0),
    CONSTRAINT ck_richiesta_totale
        CHECK (stato <> 'APPROVATA' OR ore_concesse = ore_richieste),

    KEY idx_richiesta_stato  (stato, creata_il),         -- "le richieste da gestire"
    KEY idx_richiesta_lavoro (lavoro_id, creata_il)       -- lo storico di un lavoro
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ---------------------------------------------------------------------
-- I movimenti: la storia delle ore
--
-- Ogni variazione lascia una riga, e nessuna riga si cancella mai. E' per
-- questo che sovrascrivere monte_ore non distrugge la stima iniziale: quella
-- resta leggibile dal movimento MONTE_INIZIALE.
--
-- ore e' FIRMATA. Positiva quando le ore entrano (monte iniziale,
-- allocazione a una task, aumento del monte), negativa quando escono
-- (rilascio del residuo di una task chiusa, riduzione del monte). Cosi' il
-- saldo di un lavoro e' una somma, non una lettura caso per caso del tipo.
--
-- richiesta_id collega il movimento alla richiesta che lo ha causato: senza
-- quel riferimento, un aumento del monte ore approvato dall'admin sarebbe
-- indistinguibile da uno deciso di sua iniziativa.
-- ---------------------------------------------------------------------
CREATE TABLE movimento_ore (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    lavoro_id     BIGINT       NOT NULL,
    task_id       BIGINT       NULL,                    -- valorizzato per allocazioni e rilasci
    richiesta_id  BIGINT       NULL,                    -- se nasce da una richiesta approvata
    ore           DECIMAL(9,2) NOT NULL,                -- firmata: + entrano, - escono
    tipo          VARCHAR(20)  NOT NULL,
    motivo        VARCHAR(500) NULL,
    autore_id     BIGINT       NOT NULL,
    creato_il     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_movimento_lavoro    FOREIGN KEY (lavoro_id)    REFERENCES lavoro (id),
    CONSTRAINT fk_movimento_task      FOREIGN KEY (task_id)      REFERENCES task (id),
    CONSTRAINT fk_movimento_richiesta FOREIGN KEY (richiesta_id) REFERENCES richiesta_ore (id),
    CONSTRAINT fk_movimento_autore    FOREIGN KEY (autore_id)    REFERENCES utente (id),

    CONSTRAINT ck_movimento_tipo
        CHECK (tipo IN ('MONTE_INIZIALE', 'MODIFICA_MONTE', 'ALLOCAZIONE', 'RILASCIO')),
    -- Un'allocazione o un rilascio senza task non vogliono dire niente: le
    -- ore vanno a una task o tornano da una task.
    CONSTRAINT ck_movimento_task
        CHECK (tipo IN ('MONTE_INIZIALE', 'MODIFICA_MONTE') OR task_id IS NOT NULL),
    -- Il segno e' parte del significato del tipo, non un dettaglio: un
    -- rilascio che risultasse positivo gonfierebbe il saldo del lavoro.
    CONSTRAINT ck_movimento_segno
        CHECK ((tipo = 'RILASCIO'       AND ore < 0)
            OR (tipo = 'MONTE_INIZIALE' AND ore > 0)
            OR (tipo = 'ALLOCAZIONE'    AND ore > 0)
            OR (tipo = 'MODIFICA_MONTE' AND ore <> 0)),

    KEY idx_movimento_lavoro (lavoro_id, creato_il),
    KEY idx_movimento_task   (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ---------------------------------------------------------------------
-- Il tetto giornaliero come parametro, non come costante
--
-- Sta in impostazione come sessione.timeout_minuti e gli altri: un domani
-- cambiarlo e' una riga da modificare con IMPOSTAZIONI_GESTISCI, non una
-- ricompilazione. Se servissero i part-time o gli straordinari, da qui si
-- passa a un limite per persona senza rifare la regola.
-- ---------------------------------------------------------------------
INSERT INTO impostazione (chiave, valore, descrizione) VALUES
    ('dichiarazione.ore_massime_giorno', '8',
     'Ore massime che un dipendente puo'' registrare in un giorno');


-- ---------------------------------------------------------------------
-- Catalogo dei permessi: la categoria ORE
--
-- MySQL non permette di modificare un CHECK: va eliminato e ricreato, come
-- fa la V5 per ck_lavoro_stato.
-- ---------------------------------------------------------------------
ALTER TABLE permesso DROP CHECK ck_permesso_categoria;

ALTER TABLE permesso ADD CONSTRAINT ck_permesso_categoria
    CHECK (categoria IN ('UTENTI', 'RUOLI', 'TEAM', 'LAVORI', 'TASK',
                         'DICHIARAZIONI', 'ORE', 'SISTEMA'));


-- Nessun permesso di sola lettura sulle ore, di proposito: chi vede il
-- lavoro vede il suo budget. Un permesso in piu' andrebbe concesso a tutti
-- quelli che vedono i lavori, cioe' non distinguerebbe niente.
INSERT INTO permesso (codice, categoria, descrizione, ordine) VALUES
    ('ORE_MONTE_GESTISCI',     'ORE', 'Fissare e modificare il monte ore dei lavori',                      63),
    ('ORE_ALLOCA_TUTTI',       'ORE', 'Allocare ore alle task di qualunque lavoro',                        64),
    ('ORE_ALLOCA_PROPRI',      'ORE', 'Allocare ore alle task dei lavori di cui è responsabile',           65),
    ('ORE_RICHIEDI',           'ORE', 'Aprire richieste di ore aggiuntive per i propri lavori',            66),
    ('ORE_RICHIESTE_GESTISCI', 'ORE', 'Approvare, approvare in parte o rifiutare le richieste di ore',     67);


-- Il commento della V1 avverte, e la V7 ha fatto lo stesso: ogni migrazione
-- che aggiunge un permesso deve concederlo anche ai ruoli protetti, che per
-- definizione hanno tutto. Dimenticarlo lascerebbe l'amministratore senza.
INSERT INTO ruolo_permesso (ruolo_id, permesso_codice)
SELECT r.id, p.codice
FROM ruolo r CROSS JOIN permesso p
WHERE r.protetto = TRUE
  AND p.categoria = 'ORE';


-- Il Project Manager distribuisce il budget e chiede le ore che mancano, ma
-- non decide il monte ore ne' approva le proprie richieste: quelli sono i
-- due permessi che restano all'amministratore.
--
-- Il ruolo 2 e' quello creato dalla V1. Se intanto e' stato rinominato o
-- cancellato dall'applicazione la INSERT non deve far fallire la
-- migrazione, e la SELECT con il filtro sull'id lo garantisce: senza riga
-- corrispondente non inserisce niente.
INSERT INTO ruolo_permesso (ruolo_id, permesso_codice)
SELECT r.id, c.codice
FROM ruolo r
CROSS JOIN (SELECT 'ORE_ALLOCA_PROPRI' AS codice
            UNION ALL SELECT 'ORE_RICHIEDI') c
WHERE r.id = 2
  AND r.protetto = FALSE;
