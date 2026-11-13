-- =====================================================================
-- La vista permesso_effettivo passa a SQL SECURITY INVOKER.
--
-- Il corpo della vista NON cambia: e' identico, riga per riga, a quello
-- della V14. Cambia soltanto con quali privilegi viene eseguita.
--
-- ---------------------------------------------------------------------
-- IL PROBLEMA
-- ---------------------------------------------------------------------
-- La V1, la V6 e la V14 hanno creato la vista senza clausola
-- `SQL SECURITY`, e il valore predefinito di MySQL e' DEFINER: la vista
-- viene eseguita con i privilegi di CHI L'HA CREATA, e si porta quel
-- nome scritto dentro, visibile in `SHOW CREATE VIEW` come
-- `DEFINER=`utente`@`%``.
--
-- Finora non era un problema perche' esisteva una sola utenza. Da ora
-- le migrazioni girano come `gestionale_flyway`, che e' percio' il
-- definer, e due cose diventano fragili:
--
-- 1. IL RIPRISTINO DI UN BACKUP SI LEGA A UN'UTENZA. mysqldump esporta
--    la vista con la sua clausola DEFINER=. Ripristinare quel dump su
--    un database dove `gestionale_flyway` non esiste - un altro server,
--    un collaudo, un ambiente ricostruito in fretta - produce una vista
--    che si crea senza errori e poi fallisce a OGNI select con
--    «ERROR 1449: The user specified as a definer does not exist».
--    E permesso_effettivo e' la vista su cui si reggono tutti i
--    permessi del gestionale: il sintomo sarebbe «nessuno riesce a fare
--    niente», dopo un ripristino apparentemente riuscito.
--
-- 2. RINOMINARE O ELIMINARE L'UTENZA DELLE MIGRAZIONI romperebbe il
--    login di tutti, e niente nel sistema collega le due cose.
--
-- ---------------------------------------------------------------------
-- PERCHE' INVOKER VA BENE QUI
-- ---------------------------------------------------------------------
-- Con INVOKER la vista si esegue con i privilegi di chi la interroga,
-- che deve quindi poter leggere le tabelle sottostanti: utente,
-- utente_ruolo, ruolo_permesso, utente_permesso, permesso. L'utenza
-- applicativa ha SELECT su tutto lo schema, quindi le ha tutte.
--
-- In generale DEFINER serve quando si vuole dare a qualcuno una
-- finestra su dati che altrimenti non potrebbe leggere. Qui non e' il
-- caso: chi legge la vista puo' gia' leggere le tabelle. Il DEFINER non
-- stava proteggendo niente, stava solo creando una dipendenza.
--
-- Le migrazioni vecchie non si toccano, Flyway ne verifica i checksum:
-- per questo la vista si ridefinisce qui invece di correggere la V14.
-- =====================================================================

CREATE OR REPLACE SQL SECURITY INVOKER VIEW permesso_effettivo AS
-- dai ruoli, salvo revoca per questa persona
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
-- aggiunti a questa persona
SELECT up.utente_id, up.permesso_codice
FROM utente u
JOIN utente_permesso up ON up.utente_id = u.id
WHERE u.attivo = TRUE
  AND up.concesso = TRUE
UNION
-- il pavimento: non passa dai ruoli e non ha deroghe
SELECT u.id AS utente_id, p.codice AS permesso_codice
FROM utente u
CROSS JOIN permesso p
WHERE u.attivo = TRUE
  AND p.base = TRUE;
