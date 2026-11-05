-- =====================================================================
-- V2 - Team: quali dipendenti fanno capo a ciascun responsabile (PM).
-- Un dipendente può far parte del team di più responsabili.
-- =====================================================================

CREATE TABLE membro_team (
    responsabile_id  BIGINT   NOT NULL,                  -- il PM
    dipendente_id    BIGINT   NOT NULL,
    aggiunto_il      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiunto_da_id   BIGINT   NULL,

    PRIMARY KEY (responsabile_id, dipendente_id),
    CONSTRAINT fk_team_responsabile FOREIGN KEY (responsabile_id) REFERENCES utente (id),
    CONSTRAINT fk_team_dipendente   FOREIGN KEY (dipendente_id)   REFERENCES utente (id),
    CONSTRAINT fk_team_aggiunto_da  FOREIGN KEY (aggiunto_da_id)  REFERENCES utente (id),
    CONSTRAINT ck_team_diversi      CHECK (responsabile_id <> dipendente_id),
    KEY idx_team_dipendente (dipendente_id)               -- "di quali team fa parte questo dipendente"
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
