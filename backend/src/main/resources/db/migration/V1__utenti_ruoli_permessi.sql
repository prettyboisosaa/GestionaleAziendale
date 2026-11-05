-- =====================================================================
-- V1 - Utenti, ruoli e permessi
-- Cosa può fare un utente dipende SOLO dai permessi dei suoi ruoli.
-- I ruoli si creano e si modificano dall'applicazione; i permessi sono
-- un catalogo fisso, aggiornato solo con nuove migrazioni.
-- =====================================================================

CREATE TABLE utente (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    nome           VARCHAR(100) NOT NULL,
    cognome        VARCHAR(100) NOT NULL,
    email          VARCHAR(255) NOT NULL,                -- nome utente per il login
    password_hash  VARCHAR(255) NOT NULL,                -- hash BCrypt, mai la password in chiaro
    attivo         BOOLEAN      NOT NULL DEFAULT TRUE,   -- disattivato = niente accesso, niente permessi
    creato_il      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    versione       INT          NOT NULL DEFAULT 0,      -- blocco ottimistico (@Version)

    PRIMARY KEY (id),
    CONSTRAINT uk_utente_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE ruolo (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    nome           VARCHAR(100) NOT NULL,
    descrizione    VARCHAR(255) NULL,
    protetto       BOOLEAN      NOT NULL DEFAULT FALSE,  -- TRUE = non eliminabile, sempre con tutti i permessi
    creato_il      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    versione       INT          NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uk_ruolo_nome UNIQUE (nome)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE permesso (
    codice       VARCHAR(50)  NOT NULL,                  -- il valore controllato dal codice Java
    categoria    VARCHAR(30)  NOT NULL,
    descrizione  VARCHAR(255) NOT NULL,
    ordine       INT          NOT NULL,

    PRIMARY KEY (codice),
    CONSTRAINT ck_permesso_categoria
        CHECK (categoria IN ('UTENTI', 'RUOLI', 'TEAM', 'LAVORI', 'TASK', 'DICHIARAZIONI', 'SISTEMA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE ruolo_permesso (
    ruolo_id         BIGINT      NOT NULL,
    permesso_codice  VARCHAR(50) NOT NULL,

    PRIMARY KEY (ruolo_id, permesso_codice),
    CONSTRAINT fk_rp_ruolo    FOREIGN KEY (ruolo_id)        REFERENCES ruolo (id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_permesso FOREIGN KEY (permesso_codice) REFERENCES permesso (codice)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE utente_ruolo (
    utente_id        BIGINT   NOT NULL,
    ruolo_id         BIGINT   NOT NULL,
    assegnato_il     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assegnato_da_id  BIGINT   NULL,                       -- NULL per le assegnazioni iniziali

    PRIMARY KEY (utente_id, ruolo_id),
    CONSTRAINT fk_ur_utente       FOREIGN KEY (utente_id)       REFERENCES utente (id),
    CONSTRAINT fk_ur_ruolo        FOREIGN KEY (ruolo_id)        REFERENCES ruolo (id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_assegnato_da FOREIGN KEY (assegnato_da_id) REFERENCES utente (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- Permessi effettivi: unione dei permessi di tutti i ruoli dell'utente.
-- Un utente disattivato non ha nessun permesso.
CREATE VIEW permesso_effettivo AS
SELECT DISTINCT ur.utente_id, rp.permesso_codice
FROM utente u
JOIN utente_ruolo   ur ON ur.utente_id = u.id
JOIN ruolo_permesso rp ON rp.ruolo_id  = ur.ruolo_id
WHERE u.attivo = TRUE;


-- ---------------------------------------------------------------------
-- Catalogo dei permessi
-- ---------------------------------------------------------------------
INSERT INTO permesso (codice, categoria, descrizione, ordine) VALUES
    ('UTENTI_VISUALIZZA',           'UTENTI',        'Vedere l''elenco degli utenti e i loro dati',                                      10),
    ('UTENTI_CREA',                 'UTENTI',        'Creare nuovi utenti',                                                               11),
    ('UTENTI_MODIFICA',             'UTENTI',        'Modificare i dati degli utenti',                                                    12),
    ('UTENTI_DISATTIVA',            'UTENTI',        'Disattivare e riattivare utenti',                                                   13),
    ('RUOLI_GESTISCI',              'RUOLI',         'Creare, rinominare ed eliminare ruoli e sceglierne i permessi',                     20),
    ('RUOLI_ASSEGNA',               'RUOLI',         'Assegnare e togliere ruoli agli utenti',                                            21),
    ('TEAM_GESTISCI',               'TEAM',          'Decidere quali dipendenti fanno parte del team di ciascun responsabile',           30),
    ('TEAM_VISUALIZZA_PROPRIO',     'TEAM',          'Vedere i dipendenti del proprio team',                                              31),
    ('LAVORI_VISUALIZZA_TUTTI',     'LAVORI',        'Vedere tutti i lavori',                                                             40),
    ('LAVORI_VISUALIZZA_PROPRI',    'LAVORI',        'Vedere i lavori di cui è responsabile o in cui ha task assegnate',                  41),
    ('LAVORI_CREA',                 'LAVORI',        'Creare nuovi lavori',                                                               42),
    ('LAVORI_MODIFICA_TUTTI',       'LAVORI',        'Modificare dati e stato di qualunque lavoro',                                      43),
    ('LAVORI_MODIFICA_PROPRI',      'LAVORI',        'Modificare dati e stato dei lavori di cui è responsabile',                         44),
    ('LAVORI_ASSEGNA_RESPONSABILE', 'LAVORI',        'Scegliere o cambiare il responsabile di un lavoro',                                45),
    ('TASK_GESTISCI_TUTTI',         'TASK',          'Creare, modificare e annullare task e assegnarle a qualunque utente, in ogni lavoro', 50),
    ('TASK_GESTISCI_PROPRI',        'TASK',          'Creare, modificare e annullare task nei propri lavori e assegnarle al proprio team', 51),
    ('TASK_VISUALIZZA_ASSEGNATE',   'TASK',          'Vedere le task a cui è assegnato',                                                  52),
    ('TASK_AGGIORNA_STATO',         'TASK',          'Aggiornare lo stato delle task a cui è assegnato',                                  53),
    ('DICHIARAZIONI_COMPILA',       'DICHIARAZIONI', 'Compilare e inviare la propria dichiarazione giornaliera',                          60),
    ('DICHIARAZIONI_VISUALIZZA_TEAM','DICHIARAZIONI','Vedere le dichiarazioni dei dipendenti del proprio team',                           61),
    ('DICHIARAZIONI_VISUALIZZA_TUTTE','DICHIARAZIONI','Vedere tutte le dichiarazioni e il report giornaliero aziendale',                   62),
    ('IMPOSTAZIONI_GESTISCI',       'SISTEMA',       'Modificare i parametri del sistema e il calendario delle festività',               70);


-- ---------------------------------------------------------------------
-- Ruoli di partenza: rinominabili e modificabili dall'applicazione.
-- Il ruolo protetto riceve tutti i permessi. ATTENZIONE: ogni migrazione
-- futura che aggiunge un permesso deve concederlo anche ai ruoli protetti.
-- ---------------------------------------------------------------------
INSERT INTO ruolo (id, nome, descrizione, protetto) VALUES
    (1, 'Amministratore',  'Accesso completo al gestionale',                                  TRUE),
    (2, 'Project Manager', 'Gestisce i propri lavori e il proprio team di dipendenti',        FALSE),
    (3, 'Dipendente',      'Vede le task assegnate e compila la dichiarazione giornaliera',   FALSE);

INSERT INTO ruolo_permesso (ruolo_id, permesso_codice)
SELECT r.id, p.codice
FROM ruolo r CROSS JOIN permesso p
WHERE r.protetto = TRUE;

INSERT INTO ruolo_permesso (ruolo_id, permesso_codice) VALUES
    (2, 'TEAM_VISUALIZZA_PROPRIO'),
    (2, 'LAVORI_VISUALIZZA_PROPRI'),
    (2, 'LAVORI_MODIFICA_PROPRI'),
    (2, 'TASK_GESTISCI_PROPRI'),
    (2, 'DICHIARAZIONI_VISUALIZZA_TEAM'),
    (3, 'LAVORI_VISUALIZZA_PROPRI'),
    (3, 'TASK_VISUALIZZA_ASSEGNATE'),
    (3, 'TASK_AGGIORNA_STATO'),
    (3, 'DICHIARAZIONI_COMPILA');
