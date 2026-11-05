-- =====================================================================
-- V3 - Lavori, task e assegnazioni
-- =====================================================================

CREATE TABLE lavoro (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    codice              VARCHAR(30)  NOT NULL,
    nome                VARCHAR(200) NOT NULL,
    descrizione         TEXT         NULL,
    data_inizio         DATE         NOT NULL,
    data_fine_prevista  DATE         NOT NULL,
    stato               VARCHAR(20)  NOT NULL DEFAULT 'BOZZA',
    responsabile_id     BIGINT       NULL,               -- il PM; può mancare solo in BOZZA
    creato_da_id        BIGINT       NOT NULL,
    creato_il           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    versione            INT          NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uk_lavoro_codice       UNIQUE (codice),
    CONSTRAINT fk_lavoro_responsabile FOREIGN KEY (responsabile_id) REFERENCES utente (id),
    CONSTRAINT fk_lavoro_creato_da    FOREIGN KEY (creato_da_id)    REFERENCES utente (id),
    CONSTRAINT ck_lavoro_stato        CHECK (stato IN ('BOZZA', 'ATTIVO', 'CHIUSO')),
    CONSTRAINT ck_lavoro_responsabile CHECK (stato = 'BOZZA' OR responsabile_id IS NOT NULL),
    CONSTRAINT ck_lavoro_date         CHECK (data_fine_prevista >= data_inizio),
    KEY idx_lavoro_stato (stato)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE task (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    lavoro_id      BIGINT        NOT NULL,
    titolo         VARCHAR(200)  NOT NULL,
    descrizione    TEXT          NULL,
    data_inizio    DATE          NOT NULL,
    scadenza       DATE          NOT NULL,
    stima_ore      DECIMAL(7,2)  NULL,
    stato          VARCHAR(20)   NOT NULL DEFAULT 'DA_FARE',
    creato_il      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    versione       INT           NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT fk_task_lavoro FOREIGN KEY (lavoro_id) REFERENCES lavoro (id),
    CONSTRAINT ck_task_stato  CHECK (stato IN ('DA_FARE', 'IN_CORSO', 'COMPLETATA', 'ANNULLATA')),
    CONSTRAINT ck_task_date   CHECK (scadenza >= data_inizio),
    CONSTRAINT ck_task_stima  CHECK (stima_ore IS NULL OR stima_ore > 0),
    KEY idx_task_lavoro_stato (lavoro_id, stato)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- La rimozione non cancella la riga: valorizza rimossa_il e rimossa_da_id.
CREATE TABLE assegnazione_task (
    id               BIGINT   NOT NULL AUTO_INCREMENT,
    task_id          BIGINT   NOT NULL,
    utente_id        BIGINT   NOT NULL,
    assegnata_da_id  BIGINT   NOT NULL,
    assegnata_il     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rimossa_da_id    BIGINT   NULL,
    rimossa_il       DATETIME NULL,
    -- 1 se attiva, NULL se rimossa: calcolata da MySQL, serve al vincolo di
    -- unicità (una sola assegnazione attiva per utente e task). Non va mappata in JPA.
    attiva           TINYINT  GENERATED ALWAYS AS (IF(rimossa_il IS NULL, 1, NULL)) STORED,

    PRIMARY KEY (id),
    CONSTRAINT uk_assegnazione_attiva UNIQUE (task_id, utente_id, attiva),
    CONSTRAINT fk_ass_task            FOREIGN KEY (task_id)         REFERENCES task (id),
    CONSTRAINT fk_ass_utente          FOREIGN KEY (utente_id)       REFERENCES utente (id),
    CONSTRAINT fk_ass_assegnata_da    FOREIGN KEY (assegnata_da_id) REFERENCES utente (id),
    CONSTRAINT fk_ass_rimossa_da      FOREIGN KEY (rimossa_da_id)   REFERENCES utente (id),
    CONSTRAINT ck_ass_rimozione       CHECK ((rimossa_il IS NULL) = (rimossa_da_id IS NULL)),
    KEY idx_ass_utente (utente_id, attiva)                -- "le mie task"
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
