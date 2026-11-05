-- =====================================================================
-- V6 - Scostamenti di permesso per singolo utente
--
-- Fino a qui i permessi arrivavano solo dai ruoli. Dare un permesso in piu' a
-- una persona sola non era esprimibile: o le si dava un ruolo intero, o si
-- modificava il ruolo cambiandolo a tutti quelli che lo hanno.
--
-- Questa tabella tiene gli scostamenti rispetto ai ruoli, in due direzioni:
-- un permesso aggiunto a una persona, o un permesso tolto a una persona
-- nonostante il suo ruolo glielo dia.
-- =====================================================================

CREATE TABLE utente_permesso (
    utente_id        BIGINT      NOT NULL,
    permesso_codice  VARCHAR(50) NOT NULL,
    -- TRUE  = aggiunto a questa persona
    -- FALSE = tolto a questa persona, anche se un suo ruolo lo concede
    concesso         BOOLEAN     NOT NULL,
    impostato_il     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    impostato_da_id  BIGINT      NULL,

    -- Una sola riga per persona e permesso: una concessione e una negazione
    -- dello stesso permesso non possono coesistere, quindi non c'e' nessuna
    -- regola di precedenza da inventare e da ricordare.
    PRIMARY KEY (utente_id, permesso_codice),
    CONSTRAINT fk_up_utente       FOREIGN KEY (utente_id)       REFERENCES utente (id),
    CONSTRAINT fk_up_permesso     FOREIGN KEY (permesso_codice) REFERENCES permesso (codice),
    CONSTRAINT fk_up_impostato_da FOREIGN KEY (impostato_da_id) REFERENCES utente (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ---------------------------------------------------------------------
-- La vista dei permessi effettivi tiene conto degli scostamenti.
--
-- Tutto il resto del sistema legge questa vista e continua a funzionare senza
-- modifiche: il filtro che rilegge l'utente a ogni richiesta, i @PreAuthorize
-- e la ricerca degli utenti per permesso.
--
-- Due vincoli di forma, non estetici:
--   * la UNION sta al livello piu' alto e le sottointerrogazioni stanno nella
--     WHERE, non nella FROM: MySQL non accetta tabelle derivate in una vista;
--   * il filtro u.attivo = TRUE e' ripetuto in entrambi i rami, perche' e' la
--     proprieta' su cui si appoggia tutto il resto: un utente disattivato non
--     ha nessun permesso.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW permesso_effettivo AS
SELECT DISTINCT ur.utente_id, rp.permesso_codice
FROM utente u
JOIN utente_ruolo   ur ON ur.utente_id = u.id
JOIN ruolo_permesso rp ON rp.ruolo_id  = ur.ruolo_id
WHERE u.attivo = TRUE
  AND NOT EXISTS (SELECT 1
                  FROM utente_permesso n
                  WHERE n.utente_id = u.id
                    AND n.permesso_codice = rp.permesso_codice
                    AND n.concesso = FALSE)
UNION
SELECT up.utente_id, up.permesso_codice
FROM utente u
JOIN utente_permesso up ON up.utente_id = u.id
WHERE u.attivo = TRUE
  AND up.concesso = TRUE;
