-- =====================================================================
-- Un solo vocabolario di stati per lavori e task.
--
-- Prima di questa migrazione le due tabelle usavano nomi diversi per dire
-- la stessa cosa: una task era DA_FARE e COMPLETATA, un lavoro era CHIUSO.
-- Chi legge una schermata con entrambi deve tradurre a mente, e chi scrive
-- codice deve ricordarsi quale dei due insiemi sta toccando.
--
-- I quattro stati comuni sono:
--
--   ATTIVO      creato, non ancora iniziato
--   IN_CORSO    iniziato
--   COMPLETATO  concluso
--   ANNULLATO   interrotto; stato finale, in sola lettura
--
-- Il lavoro ne conserva un quinto, BOZZA, che la task non ha: e' lo stato
-- in cui il responsabile puo' ancora mancare (ck_lavoro_responsabile) ed e'
-- l'unico in cui il lavoro si cancella davvero invece di essere annullato.
-- Togliendolo si perderebbero entrambe le regole, che non hanno un
-- equivalente fra i quattro.
--
-- MySQL non permette di modificare un CHECK: va eliminato e ricreato. E va
-- eliminato PRIMA delle UPDATE, altrimenti il vincolo vecchio rifiuta i
-- valori nuovi.
-- =====================================================================


-- --- task -------------------------------------------------------------

ALTER TABLE task DROP CHECK ck_task_stato;

UPDATE task SET stato = 'ATTIVO'     WHERE stato = 'DA_FARE';
UPDATE task SET stato = 'COMPLETATO' WHERE stato = 'COMPLETATA';
UPDATE task SET stato = 'ANNULLATO'  WHERE stato = 'ANNULLATA';

ALTER TABLE task ALTER COLUMN stato SET DEFAULT 'ATTIVO';

ALTER TABLE task ADD CONSTRAINT ck_task_stato
    CHECK (stato IN ('ATTIVO', 'IN_CORSO', 'COMPLETATO', 'ANNULLATO'));


-- --- lavoro -----------------------------------------------------------

ALTER TABLE lavoro DROP CHECK ck_lavoro_stato;

UPDATE lavoro SET stato = 'COMPLETATO' WHERE stato = 'CHIUSO';

ALTER TABLE lavoro ADD CONSTRAINT ck_lavoro_stato
    CHECK (stato IN ('BOZZA', 'ATTIVO', 'IN_CORSO', 'COMPLETATO', 'ANNULLATO'));

-- ck_lavoro_responsabile non va toccato: nomina BOZZA e ANNULLATO, e
-- nessuno dei due cambia nome qui.


-- --- quando e' stato annullato ----------------------------------------
--
-- Serve a distinguere un lavoro annullato ieri da uno annullato un anno fa
-- quando li si consulta con l'interruttore "Mostra annullati", e a dare un
-- riferimento allo storico. aggiornato_il non basta: cambia a ogni
-- modifica successiva.
ALTER TABLE lavoro
    ADD COLUMN annullato_il DATETIME NULL AFTER stato;

-- I lavori gia' annullati prima di questa migrazione non hanno una data da
-- recuperare: aggiornato_il e' la cosa piu' vicina al momento in cui lo
-- sono diventati, perche' annullare e' stata l'ultima scrittura su quelle
-- righe.
UPDATE lavoro SET annullato_il = aggiornato_il WHERE stato = 'ANNULLATO';

ALTER TABLE lavoro ADD CONSTRAINT ck_lavoro_annullato
    CHECK ((stato = 'ANNULLATO') = (annullato_il IS NOT NULL));
