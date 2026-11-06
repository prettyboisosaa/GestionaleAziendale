-- =====================================================================
-- Dati di prova SOLO per lo sviluppo (cartella db/dev).
-- Password di tutti gli utenti: Password1!
-- Migrazione ripetibile: rieseguita quando il file cambia; ogni INSERT
-- ignora le righe già presenti.
--
-- Qui ci sono soltanto le PERSONE: utenti, ruoli e team. Niente lavori,
-- task, dichiarazioni o movimenti di ore.
--
-- È una scelta, non una dimenticanza. I lavori di prova sono stati
-- rimossi il 5 novembre 2026, quando il gestionale ha iniziato a
-- raccogliere dati veri: siccome questa migrazione è ripetibile, lasciarli
-- qui avrebbe significato vederli ricomparire a ogni modifica del file, e
-- mescolati ai lavori reali. Le persone invece servono: senza almeno un
-- utente non si entra nemmeno nell'applicazione.
--
-- Per provare il monte ore su dati finti, conviene crearli dall'interfaccia
-- e cancellarli, invece di rimetterli qui.
-- =====================================================================

-- Paolo Ferrari e' il disattivato di prova, e porta la sua data: dalla V11 il
-- vincolo ck_utente_disattivato pretende che un'utenza chiusa dica quando lo
-- e' stata. Negli altri la data e' NULL, come per chiunque sia attivo.
INSERT INTO utente (id, nome, cognome, email, password_hash, attivo, disattivato_il) VALUES
    (1, 'Anna',   'Amministratore', 'admin@gestionale.local',         '{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq', TRUE,  NULL),
    (2, 'Laura',  'Bianchi',        'laura.bianchi@gestionale.local', '{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq', TRUE,  NULL),
    (3, 'Marco',  'Rossi',          'marco.rossi@gestionale.local',   '{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq', TRUE,  NULL),
    (4, 'Giulia', 'Verdi',          'giulia.verdi@gestionale.local',  '{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq', TRUE,  NULL),
    (5, 'Luca',   'Russo',          'luca.russo@gestionale.local',    '{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq', TRUE,  NULL),
    (6, 'Sara',   'Colombo',        'sara.colombo@gestionale.local',  '{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq', TRUE,  NULL),
    (7, 'Paolo',  'Ferrari',        'paolo.ferrari@gestionale.local', '{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq', FALSE, '2026-11-05 09:00:00')
ON DUPLICATE KEY UPDATE id = id;

-- Ruoli: 1 = Amministratore, 2 = Project Manager, 3 = Dipendente
INSERT INTO utente_ruolo (utente_id, ruolo_id) VALUES
    (1, 1), (2, 2), (3, 2), (4, 3), (5, 3), (6, 3), (7, 3)
ON DUPLICATE KEY UPDATE utente_id = utente_id;

-- Team: Laura gestisce Giulia e Luca; Marco gestisce Luca e Sara.
-- Serve al permesso TASK_GESTISCI_PROPRI, che lascia assegnare le task solo
-- ai dipendenti del proprio team.
INSERT INTO membro_team (responsabile_id, dipendente_id, aggiunto_da_id) VALUES
    (2, 4, 1), (2, 5, 1), (3, 5, 1), (3, 6, 1)
ON DUPLICATE KEY UPDATE responsabile_id = responsabile_id;
