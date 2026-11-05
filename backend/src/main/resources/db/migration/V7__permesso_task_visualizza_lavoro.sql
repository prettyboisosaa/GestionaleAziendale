-- =====================================================================
-- V7 - Un permesso per vedere tutte le task di un lavoro
--
-- Il catalogo distingueva il gestire dal vedere solo per le proprie task:
--   TASK_VISUALIZZA_ASSEGNATE  le task a cui si e' assegnati
--   TASK_GESTISCI_TUTTI        crea, modifica, annulla, in ogni lavoro
-- Mancava la via di mezzo: vedere il quadro completo di un lavoro senza
-- poterlo modificare. Senza questo permesso, dare a un dipendente la visione
-- d'insieme avrebbe richiesto TASK_GESTISCI_TUTTI, cioe' anche la modifica.
-- =====================================================================

INSERT INTO permesso (codice, categoria, descrizione, ordine) VALUES
    ('TASK_VISUALIZZA_LAVORO', 'TASK',
     'Vedere tutte le task dei lavori in cui è coinvolto', 54);


-- Il commento della V1 avverte: ogni migrazione che aggiunge un permesso deve
-- concederlo anche ai ruoli protetti, che per definizione hanno tutto.
-- Dimenticarlo lascerebbe l'amministratore con un permesso in meno.
INSERT INTO ruolo_permesso (ruolo_id, permesso_codice)
SELECT r.id, 'TASK_VISUALIZZA_LAVORO'
FROM ruolo r
WHERE r.protetto = TRUE;
