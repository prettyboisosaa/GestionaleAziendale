-- =====================================================================
-- V16 - Le dichiarazioni esistenti diventano sessioni
--
-- Le ore consumate di un lavoro sono sempre state SUM(voce_dichiarazione.ore).
-- Dalla V17 saranno SUM(sessione_task.minuti)/60. Se le vecchie voci non
-- venissero portate dentro, le ore consumate di tutti i lavori tornerebbero a
-- zero: il consuntivo perderebbe la sua controparte reale e lo storico del
-- monte ore resterebbe a confrontarsi con il nulla.
--
-- Questa migrazione non elimina niente: la conversione sta qui da sola proprio
-- perche' se fallisse si deve poter rieseguire, e con le tabelle di partenza
-- ancora al loro posto. Il DROP e' nella V17.
--
-- Su questo database sono due voci, otto ore in tutto, su due task - entrambe
-- di task gia' COMPLETATE, quindi `rilasciaResiduo` non girera' piu' su di
-- loro e non c'e' il rischio che legga uno zero e restituisca al pool
-- un'allocazione intera. Resta vero per le task future, ed e' il motivo per cui
-- la riscrittura delle query sta nello stesso cambiamento di questa conversione.
-- =====================================================================


-- ---------------------------------------------------------------------
-- Gli orari sono SINTETICI, e va saputo.
--
-- Una voce di dichiarazione diceva «il 6 novembre, 4 ore sulla task 3»: di
-- quella riga era vera la DURATA, non l'ora del giorno, perche' nessuno
-- registrava l'istante in cui aveva cominciato. Qui le voci di una stessa
-- persona nello stesso giorno si mettono in fila a partire dalle 09:00, una
-- dopo l'altra: la somma dei minuti resta esatta - ed e' quella che alimenta
-- le ore consumate - mentre la collocazione nella giornata e' una convenzione.
--
-- In fila e non tutte alle 09:00: due sessioni sovrapposte della stessa
-- persona direbbero che ha lavorato due volte nello stesso momento, e chi
-- guardera' il suo tempo in futuro leggerebbe un'impossibilita'.
--
-- La finestra scorrevole somma i minuti delle voci precedenti dello stesso
-- giorno (UNBOUNDED PRECEDING .. 1 PRECEDING, cioe' tutte tranne questa) e
-- li usa come scostamento dalle 09:00.
--
-- Il NOT EXISTS rende l'istruzione ripetibile: se la migrazione si fermasse e
-- venisse rieseguita dopo un flyway repair, non raddoppierebbe le sessioni.
-- ---------------------------------------------------------------------
INSERT INTO sessione_task (task_id, utente_id, avviata_il, chiusa_il, origine, confermata)
SELECT da_convertire.task_id,
       da_convertire.utente_id,
       TIMESTAMP(da_convertire.data, '09:00:00')
           + INTERVAL da_convertire.inizio_min MINUTE,
       TIMESTAMP(da_convertire.data, '09:00:00')
           + INTERVAL (da_convertire.inizio_min + da_convertire.durata_min) MINUTE,
       'IMPORTATA',
       TRUE
FROM (SELECT v.task_id,
             d.utente_id,
             d.data,
             ROUND(v.ore * 60) AS durata_min,
             COALESCE(SUM(ROUND(v.ore * 60)) OVER (
                 PARTITION BY d.utente_id, d.data
                 ORDER BY v.id
                 ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0) AS inizio_min
      FROM voce_dichiarazione v
      JOIN dichiarazione d ON d.id = v.dichiarazione_id) AS da_convertire
WHERE NOT EXISTS (SELECT 1 FROM sessione_task gia WHERE gia.origine = 'IMPORTATA');


-- ---------------------------------------------------------------------
-- La descrizione non si porta dietro, e non e' una dimenticanza.
--
-- `voce_dichiarazione.descrizione` era obbligatoria e serviva a una cosa che
-- non c'e' piu': spiegare a posteriori che cosa si era fatto in quelle ore,
-- perche' il numero da solo non lo diceva. Una sessione dice quando si e'
-- lavorato su che cosa, e il «che cosa» e' la task. Aggiungere una colonna
-- per conservare due frasi vorrebbe dire tenere in piedi un campo che nessuna
-- schermata nuova scrive ne' mostra.
--
-- Le due frasi di questo database - «impostato i dockerfile», «revisionato il
-- contratto» - ripetono il titolo della loro task.
-- ---------------------------------------------------------------------
