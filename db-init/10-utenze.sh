#!/bin/sh
#
# Le due utenze del database di produzione, create alla nascita del volume.
#
# L'applicazione e le migrazioni hanno bisogni diversi e rischi diversi:
#
#   gestionale          SELECT, INSERT, UPDATE, DELETE. E' l'utenza del
#                       processo raggiungibile dalla rete, quello che esegue
#                       SQL costruito a partire da cio' che scrivono gli
#                       utenti. Non puo' toccare lo schema.
#
#   gestionale_flyway   Anche CREATE, DROP, ALTER, INDEX, REFERENCES,
#                       CREATE VIEW, SHOW VIEW. La usa solo Flyway, una volta
#                       per avvio, sotto il controllo di chi rilascia.
#
# Perche' non lasciare un'utenza sola con tutto, come prima: `ddl-auto=validate`
# vieta a Hibernate di riscrivere lo schema, ma e' una riga di properties, e
# una variabile d'ambiente sbagliata la rimette a `update`. E' gia' successo -
# il 9 novembre 2026 Hibernate ha ricreato le tabelle che la V17 aveva
# eliminato. Con i privilegi tolti, la stessa distrazione produce un errore
# all'avvio invece di un danno silenzioso.
#
# Non protegge i dati: con SELECT e UPDATE si leggono e si falsificano tutte le
# ore e le assenze di tutti. Protegge lo SCHEMA. E' contenimento del danno, non
# prevenzione.
#
# -----------------------------------------------------------------------------
# QUANDO GIRA
# -----------------------------------------------------------------------------
# L'entrypoint di MySQL esegue /docker-entrypoint-initdb.d/ SOLO quando il
# datadir e' vuoto, cioe' alla primissima creazione del volume. Su un database
# che esiste gia' questo file non viene letto e le utenze vanno create a mano:
# la procedura e' in RILASCIO.md, sezione 10.
#
# E' uno .sh e non uno .sql perche' un .sql non puo' leggere le variabili
# d'ambiente, e la password dell'utenza migrazioni arriva da FLYWAY_PASSWORD.

set -eu

if [ -z "${FLYWAY_PASSWORD:-}" ]; then
    echo "[10-utenze] ERRORE: manca FLYWAY_PASSWORD nell'ambiente del servizio db." >&2
    echo "[10-utenze] Senza, l'utenza delle migrazioni nascerebbe senza password." >&2
    echo "[10-utenze] Aggiungila al .env accanto a compose.prod.yml e ricrea il volume." >&2
    exit 1
fi

echo "[10-utenze] Separo l'utenza applicativa da quella delle migrazioni..."

# Il client si collega sul socket locale: durante l'inizializzazione il server
# temporaneo non ascolta ancora sulla rete.
mysql --protocol=socket -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
-- L'entrypoint ha gia' creato 'gestionale' da MYSQL_USER, con ALL PRIVILEGES
-- sullo schema: diciotto privilegi, di cui l'applicazione usa quattro. Qui si
-- revoca tutto e si riconcede solo quello che serve. REVOKE prima, GRANT
-- dopo: al contrario si cancellerebbe anche il grant appena dato.
REVOKE ALL PRIVILEGES ON \`gestionale\`.* FROM 'gestionale'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`gestionale\`.* TO 'gestionale'@'%';

-- L'utenza delle migrazioni. I privilegi sono l'elenco esatto di cio' che
-- fanno le migrazioni (18 CREATE TABLE, 40 ALTER TABLE, 3 DROP TABLE,
-- 1 CREATE INDEX, 1 CREATE VIEW, 2 CREATE OR REPLACE VIEW) e non ALL:
--   REFERENCES   serve per le chiavi esterne
--   SHOW VIEW    serve al CREATE OR REPLACE della vista permesso_effettivo
--   DROP         serve ai DROP TABLE e al REPLACE della vista
-- Restano fuori TRIGGER, EVENT, CREATE ROUTINE, ALTER ROUTINE, EXECUTE,
-- CREATE TEMPORARY TABLES e LOCK TABLES: nello schema non esiste niente su
-- cui potrebbero agire.
CREATE USER IF NOT EXISTS 'gestionale_flyway'@'%'
    IDENTIFIED BY '${FLYWAY_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE,
      CREATE, DROP, ALTER, INDEX, REFERENCES,
      CREATE VIEW, SHOW VIEW
    ON \`gestionale\`.* TO 'gestionale_flyway'@'%';

FLUSH PRIVILEGES;
SQL

echo "[10-utenze] Fatto: 'gestionale' solo DML, 'gestionale_flyway' anche DDL."
