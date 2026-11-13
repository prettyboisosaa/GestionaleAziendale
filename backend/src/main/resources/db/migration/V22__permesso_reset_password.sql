-- =====================================================================
-- V22 - Un amministratore puo' azzerare la password di un altro utente
--
-- Fino a qui l'unico modo di cambiare una password era
-- POST /api/autenticazione/cambio-password, che pretende quella attuale.
-- Chi la dimenticava non aveva nessuna strada: il gestionale non manda
-- email, non ha domande di sicurezza e non ha un «password dimenticata».
-- L'unico rimedio era una UPDATE a mano sul database di produzione, cioe'
-- la cosa che non si vuole fare mai e che prima o poi si fa di fretta.
--
-- Serve un permesso SUO e non uno di quelli che ci sono gia':
--
--   UTENTI_MODIFICA   e' nel catalogo dalla V1 ma non lo controlla nessun
--                     endpoint (non esiste una modifica dei dati utente).
--                     Appoggiarsi a un permesso inerte vorrebbe dire dare
--                     un significato nuovo a una riga che qualcuno ha gia'
--                     concesso pensando ad altro.
--   UTENTI_CREA       crea un'utenza nuova, che nessuno ha ancora usato.
--                     Azzerare la password di un'utenza viva e' entrare
--                     nell'identita' di una persona che esiste: con quella
--                     password si leggono le sue ore e si agisce come lei.
--
-- Tenerlo separato significa poterlo NON dare: un capo ufficio che crea le
-- utenze dei nuovi assunti non deve per forza poter entrare nei panni di
-- chiunque.
-- =====================================================================

INSERT INTO permesso (codice, categoria, descrizione, ordine) VALUES
    ('UTENTI_RESET_PASSWORD', 'UTENTI',
     'Azzerare la password di un altro utente, senza conoscere quella attuale', 14);


-- I ruoli protetti hanno sempre tutti i permessi: e' la regola che la V1
-- scrive a chiare lettere e che ogni migrazione che aggiunge un permesso deve
-- rispettare.
--
-- NOT EXISTS e non una INSERT secca, come nella V9: il permesso e' nuovo di
-- zecca e nessuno puo' averlo gia', ma la forma e' quella giusta da copiare e
-- non costa niente.
INSERT INTO ruolo_permesso (ruolo_id, permesso_codice)
SELECT r.id, 'UTENTI_RESET_PASSWORD'
FROM ruolo r
WHERE r.protetto = TRUE
  AND NOT EXISTS (SELECT 1 FROM ruolo_permesso gia
                  WHERE gia.ruolo_id = r.id
                    AND gia.permesso_codice = 'UTENTI_RESET_PASSWORD');
