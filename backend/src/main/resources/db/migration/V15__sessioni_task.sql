-- =====================================================================
-- V15 - Le sessioni di lavoro su una task
--
-- Fino a qui le ore lavorate si registravano a mano, a fine giornata, su una
-- schermata dedicata: il dipendente sceglieva la task da un menu e scriveva
-- quante ore. Era un adempimento da ricordarsi ogni sera, e quello che ne
-- usciva era una ricostruzione a memoria.
--
-- Da qui il tempo si misura: una riga per ogni periodo di lavoro continuativo
-- di UNA persona su UNA task, aperta quando avvia e chiusa quando mette in
-- pausa o completa.
--
-- Questa migrazione fa SOLO la DDL. MySQL non ha DDL transazionale, quindi
-- Flyway non puo' annullare un CREATE TABLE: se dati e struttura stessero
-- insieme, un errore a meta' lascerebbe la tabella creata e la migrazione
-- segnata come fallita, e al riavvio successivo il CREATE si ripeterebbe
-- andando in errore. Da sola, questa riesce o non fa niente. La conversione
-- dei dati sta nella V16, l'eliminazione delle dichiarazioni nella V17.
-- =====================================================================

CREATE TABLE sessione_task (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    task_id       BIGINT      NOT NULL,
    utente_id     BIGINT      NOT NULL,
    avviata_il    DATETIME    NOT NULL,
    chiusa_il     DATETIME    NULL,                 -- NULL = sta scorrendo adesso

    -- Da dove viene la riga:
    --   TIMER       il dipendente ha premuto avvia e poi pausa o completa
    --   AUTOMATICA  chiusa d'ufficio perche' aperta oltre il tetto
    --   IMPORTATA   convertita da una vecchia voce di dichiarazione (V16)
    origine       VARCHAR(20) NOT NULL,

    -- Falso solo per una chiusura d'ufficio che il dipendente non ha ancora
    -- guardato. Una sessione non confermata CONTA comunque nei totali: non
    -- contarla farebbe sparire ore di lavoro vero dal consuntivo di un
    -- lavoro. Si conta e si dice che c'e'.
    confermata    BOOLEAN     NOT NULL DEFAULT TRUE,

    -- Niente si cancella, come nel resto del progetto: una sessione
    -- sbagliata si annulla e la riga resta.
    annullata_il  DATETIME    NULL,

    creato_il     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    versione      INT         NOT NULL DEFAULT 0,

    -- La durata non si scrive: la calcola il database dai due istanti, come
    -- fa `attiva` su assegnazione_task. Una colonna scritta a mano sarebbe
    -- una seconda verita' da tenere in sincronia con gli istanti, e prima o
    -- poi divergerebbe - lo stesso motivo per cui i totali non si salvano.
    -- NULL finche' la sessione e' aperta: la durata di una cosa che non e'
    -- finita non e' uno zero.
    minuti        INT     GENERATED ALWAYS AS
                  (TIMESTAMPDIFF(MINUTE, avviata_il, chiusa_il)) STORED,

    -- L'id di chi la sta lavorando se e' aperta, NULL altrimenti. E' il
    -- trucco di `assegnazione_task.attiva`: MySQL non considera due NULL
    -- come duplicati, quindi l'unicita' qui sotto permette infinite sessioni
    -- chiuse e ne lascia aperta UNA SOLA per persona.
    --
    -- E' il vincolo a garantire la regola, non un controllo applicativo: fra
    -- un SELECT che non trova sessioni aperte e l'INSERT che ne apre una ci
    -- sta un secondo clic da un'altra finestra.
    in_corso      BIGINT  GENERATED ALWAYS AS
                  (IF(chiusa_il IS NULL AND annullata_il IS NULL, utente_id, NULL)) STORED,

    PRIMARY KEY (id),
    CONSTRAINT uk_sessione_in_corso UNIQUE (in_corso),
    CONSTRAINT fk_sessione_task     FOREIGN KEY (task_id)   REFERENCES task (id),
    CONSTRAINT fk_sessione_utente   FOREIGN KEY (utente_id) REFERENCES utente (id),

    -- Una sessione chiusa dura almeno un istante. Senza questo, un orologio
    -- che torna indietro scriverebbe minuti negativi, e quelli si
    -- sommerebbero alle ore di un lavoro.
    CONSTRAINT ck_sessione_durata   CHECK (chiusa_il IS NULL OR chiusa_il > avviata_il),

    CONSTRAINT ck_sessione_origine  CHECK (origine IN ('TIMER', 'AUTOMATICA', 'IMPORTATA')),

    -- Solo una chiusura d'ufficio puo' restare da confermare: una sessione
    -- che il dipendente ha chiuso lui non ha niente da far confermare, e una
    -- importata e' storia gia' accaduta.
    CONSTRAINT ck_sessione_conferma CHECK (confermata = TRUE OR origine = 'AUTOMATICA'),

    KEY idx_sessione_task   (task_id),                 -- le ore di una task
    KEY idx_sessione_utente (utente_id, avviata_il)    -- il tempo di una persona
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
