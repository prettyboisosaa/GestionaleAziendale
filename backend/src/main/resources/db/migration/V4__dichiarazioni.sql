-- =====================================================================
-- V4 - Dichiarazione giornaliera, parametri e festività
-- =====================================================================

CREATE TABLE impostazione (
    chiave         VARCHAR(100) NOT NULL,
    valore         VARCHAR(500) NOT NULL,
    descrizione    VARCHAR(255) NOT NULL,
    aggiornato_il  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (chiave)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO impostazione (chiave, valore, descrizione) VALUES
    ('sessione.timeout_minuti',       '30',        'Minuti di inattività dopo cui la sessione scade'),
    ('calendario.giorni_lavorativi',  '1,2,3,4,5', 'Giorni lavorativi della settimana: 1 = lunedì … 7 = domenica'),
    ('dichiarazione.giorni_modifica', '1',         'Giorni lavorativi successivi entro cui il dipendente può modificare la dichiarazione'),
    ('report.orario_invio',           '18:00',     'Orario di invio automatico del report giornaliero');


CREATE TABLE festivita (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    data         DATE         NOT NULL,
    descrizione  VARCHAR(100) NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_festivita_data UNIQUE (data)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- Una dichiarazione per dipendente e per giorno
CREATE TABLE dichiarazione (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    utente_id      BIGINT      NOT NULL,
    data           DATE        NOT NULL,
    tipo_giornata  VARCHAR(20) NOT NULL DEFAULT 'LAVORATA',
    stato          VARCHAR(20) NOT NULL DEFAULT 'BOZZA',
    inviata_il     DATETIME    NULL,
    creato_il      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    versione       INT         NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uk_dich_utente_data  UNIQUE (utente_id, data),
    CONSTRAINT fk_dich_utente       FOREIGN KEY (utente_id) REFERENCES utente (id),
    CONSTRAINT ck_dich_tipo         CHECK (tipo_giornata IN ('LAVORATA', 'FERIE', 'PERMESSO', 'MALATTIA')),
    CONSTRAINT ck_dich_stato        CHECK (stato IN ('BOZZA', 'INVIATA')),
    CONSTRAINT ck_dich_invio        CHECK (stato = 'BOZZA' OR inviata_il IS NOT NULL),
    KEY idx_dich_data (data, stato)                       -- report giornaliero
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- Le voci: su quale task ha lavorato e che cosa ha fatto
CREATE TABLE voce_dichiarazione (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    dichiarazione_id  BIGINT        NOT NULL,
    task_id           BIGINT        NOT NULL,
    descrizione       VARCHAR(1000) NOT NULL,
    creato_il         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_voce_dichiarazione FOREIGN KEY (dichiarazione_id) REFERENCES dichiarazione (id) ON DELETE CASCADE,
    CONSTRAINT fk_voce_task          FOREIGN KEY (task_id)          REFERENCES task (id),
    KEY idx_voce_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
