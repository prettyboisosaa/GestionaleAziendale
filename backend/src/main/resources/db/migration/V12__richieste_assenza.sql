-- =====================================================================
-- Ferie e permessi: una richiesta, tre tipologie.
--
-- Una tabella sola e non tre. Le tre tipologie condividono tutto cio' su
-- cui si interroga il database - chi, quando, in che stato - e si
-- distinguono solo per i campi che l'utente compila nel form. Tre tabelle
-- vorrebbero dire tre UNION per rispondere a "le assenze di marzo", che e'
-- la domanda che fa il calendario.
--
-- I campi comuni (date e orari) sono colonne, perche' ci si filtra sopra.
-- Quelli propri di ciascuna tipologia stanno in dettagli, che il server
-- valida in base a tipo: metterli come colonne vorrebbe dire una colonna
-- NULL su due righe su tre.
--
-- Il nome della tabella e' al singolare e i valori sono in maiuscolo, come
-- richiesta_ore della V8: sono le convenzioni del resto dello schema.
-- =====================================================================


-- --- il catalogo dei permessi -----------------------------------------
--
-- La categoria ASSENZE non esisteva: il CHECK la rifiuterebbe. MySQL non
-- permette di modificare un vincolo, quindi si elimina e si ricrea.
--
-- ATTENZIONE: l'elenco qui sotto parte da quello della V8, non da quello
-- della V1. MySQL valida un CHECK nuovo contro le righe che ci sono gia', e
-- la V8 aveva aggiunto la categoria ORE con cinque permessi: ricopiare
-- l'elenco della V1 - la migrazione che il vincolo l'ha creato - fa fallire
-- questa, perche' quelle cinque righe lo violerebbero. Chi aggiungera' la
-- prossima categoria deve ripartire da qui, non dalla V1.
ALTER TABLE permesso DROP CHECK ck_permesso_categoria;

ALTER TABLE permesso ADD CONSTRAINT ck_permesso_categoria
    CHECK (categoria IN ('UTENTI', 'RUOLI', 'TEAM', 'LAVORI', 'TASK',
                         'DICHIARAZIONI', 'ORE', 'ASSENZE', 'SISTEMA'));

-- ordine 68 e 69: le categorie seguono le decine in ordine di comparsa, ORE
-- si e' presa 63-67 e SISTEMA resta per ultima a 70.
INSERT INTO permesso (codice, categoria, descrizione, ordine) VALUES
    ('ASSENZE_RICHIEDI', 'ASSENZE', 'Chiedere ferie e permessi per se stessi',          68),
    ('ASSENZE_GESTISCI', 'ASSENZE', 'Vedere tutte le richieste di assenza e decidere',  69);

-- I ruoli protetti hanno tutti i permessi: ogni migrazione che ne aggiunge
-- uno deve concederlo anche a loro, come avverte la V1.
INSERT INTO ruolo_permesso (ruolo_id, permesso_codice)
SELECT r.id, c.codice
FROM ruolo r
CROSS JOIN (SELECT 'ASSENZE_RICHIEDI' AS codice
            UNION ALL SELECT 'ASSENZE_GESTISCI') c
WHERE r.protetto = TRUE
  AND NOT EXISTS (SELECT 1 FROM ruolo_permesso gia
                  WHERE gia.ruolo_id = r.id AND gia.permesso_codice = c.codice);

-- Chiedere ferie non e' un privilegio: lo fanno tutti. Il NOT EXISTS
-- protegge i database dove un amministratore l'avesse gia' concesso a mano.
INSERT INTO ruolo_permesso (ruolo_id, permesso_codice)
SELECT r.id, 'ASSENZE_RICHIEDI'
FROM ruolo r
WHERE r.id IN (2, 3)
  AND r.protetto = FALSE
  AND NOT EXISTS (SELECT 1 FROM ruolo_permesso gia
                  WHERE gia.ruolo_id = r.id AND gia.permesso_codice = 'ASSENZE_RICHIEDI');


-- --- le richieste -----------------------------------------------------

CREATE TABLE richiesta_assenza (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    utente_id      BIGINT       NOT NULL,
    tipo           VARCHAR(30)  NOT NULL,
    data_inizio    DATE         NOT NULL,
    data_fine      DATE         NOT NULL,
    ora_inizio     TIME         NULL,                   -- NULL = giornate intere
    ora_fine       TIME         NULL,
    dettagli       JSON         NOT NULL,               -- campi propri della tipologia
    stato          VARCHAR(20)  NOT NULL DEFAULT 'IN_ATTESA',
    gestita_da_id  BIGINT       NULL,                   -- l'admin che ha deciso
    gestita_il     DATETIME     NULL,
    nota_esito     VARCHAR(500) NULL,
    creata_il      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornata_il  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    versione       INT          NOT NULL DEFAULT 0,     -- blocco ottimistico (@Version)

    PRIMARY KEY (id),
    CONSTRAINT fk_assenza_utente     FOREIGN KEY (utente_id)     REFERENCES utente (id),
    CONSTRAINT fk_assenza_gestita_da FOREIGN KEY (gestita_da_id) REFERENCES utente (id),

    CONSTRAINT ck_assenza_tipo
        CHECK (tipo IN ('FERIE', 'PERMESSO_ORARIO', 'PERMESSO_RETRIBUITO')),
    CONSTRAINT ck_assenza_stato
        CHECK (stato IN ('IN_ATTESA', 'APPROVATA', 'RIFIUTATA', 'RITIRATA')),
    CONSTRAINT ck_assenza_date
        CHECK (data_fine >= data_inizio),

    -- Le due ore vanno insieme: una fascia oraria senza fine non e' una
    -- fascia, e una fine senza inizio non dice niente.
    CONSTRAINT ck_assenza_ore_coppia
        CHECK ((ora_inizio IS NULL) = (ora_fine IS NULL)),

    -- Una fascia oraria sta dentro un giorno solo, e finisce dopo essere
    -- cominciata. Un permesso a cavallo di due giorni sarebbe due permessi.
    CONSTRAINT ck_assenza_fascia
        CHECK (ora_inizio IS NULL
               OR (data_inizio = data_fine AND ora_fine > ora_inizio)),

    -- Un permesso orario e' sempre una fascia; le ferie sono sempre
    -- giornate intere. Il permesso retribuito puo' essere l'uno o l'altro,
    -- e percio' non compare qui.
    CONSTRAINT ck_assenza_tipo_fascia
        CHECK ((tipo <> 'PERMESSO_ORARIO' OR ora_inizio IS NOT NULL)
               AND (tipo <> 'FERIE' OR ora_inizio IS NULL)),

    -- Chi ha deciso e quando: o entrambi, o nessuno dei due. Le stesse due
    -- righe di ck_richiesta_gestione nella V8.
    CONSTRAINT ck_assenza_gestione
        CHECK ((gestita_il IS NULL) = (gestita_da_id IS NULL)),

    -- In attesa vuol dire che nessuno ha ancora deciso; approvata e
    -- rifiutata vogliono dire il contrario. Ritirata la decide il
    -- dipendente, quindi non ha un gestore.
    CONSTRAINT ck_assenza_decisa
        CHECK ((stato IN ('APPROVATA', 'RIFIUTATA')) = (gestita_il IS NOT NULL)),

    -- Un rifiuto senza motivazione e' il modo piu' sicuro di far riaprire
    -- la stessa richiesta il giorno dopo.
    CONSTRAINT ck_assenza_nota_rifiuto
        CHECK (stato <> 'RIFIUTATA' OR nota_esito IS NOT NULL)
) ENGINE = InnoDB;

-- La domanda del calendario e del controllo di sovrapposizione: le
-- richieste di una persona che toccano un intervallo di date.
CREATE INDEX ix_assenza_periodo
    ON richiesta_assenza (utente_id, data_inizio, data_fine);
