-- =====================================================================
-- Quando un utente e' stato disattivato.
--
-- La colonna utente.attivo esiste dalla V1 e regge gia' tutto: Spring
-- Security la legge al login e FiltroUtenteAggiornato la rilegge a ogni
-- richiesta. Qui si aggiunge solo la data, che serve all'interfaccia per
-- dire "disattivato il 3 marzo" invece del solo badge grigio.
--
-- aggiornato_il non risponde alla domanda: cambia a ogni modifica
-- successiva dell'utente, quindi dopo una riattivazione racconterebbe
-- quella.
-- =====================================================================

ALTER TABLE utente
    ADD COLUMN disattivato_il DATETIME NULL AFTER attivo;

-- Gli utenti gia' disattivati prendono aggiornato_il, la cosa piu' vicina
-- al momento in cui lo sono diventati.
UPDATE utente SET disattivato_il = aggiornato_il WHERE attivo = FALSE;

ALTER TABLE utente ADD CONSTRAINT ck_utente_disattivato
    CHECK (attivo = TRUE OR disattivato_il IS NOT NULL);
