#!/usr/bin/env bash
#
# Il ripristino del database di produzione da un dump di backup.sh.
#
# Esiste perche' provare un backup sia un comando e non un pomeriggio. Un
# backup mai ripristinato e' una speranza, non un backup: il momento per
# scoprire che il dump era troncato, o che la vista non si ricrea, non e' il
# giorno in cui il database non c'e' piu'.
#
# ATTENZIONE: SOVRASCRIVE TUTTI I DATI ATTUALI. Quello che c'e' adesso nel
# database sparisce e prende il posto il contenuto del dump.
#
# Uso:
#   ./scripts/ripristina.sh /var/backups/gestionale/giornalieri/gestionale-2026-11-13-0230.sql.gz
#   ./scripts/ripristina.sh --si <file>      # senza chiedere conferma
#
# L'ordine dei passi non e' casuale: prima si verifica il dump, POI si tocca
# il sistema. Rifiutare un dump rotto dopo aver gia' cancellato il database
# sarebbe il peggiore dei risultati possibili.

set -euo pipefail

PROGETTO="${PROGETTO:-/opt/gestionale}"
COMPOSE="${COMPOSE:-compose.prod.yml}"

# Vedi la nota in backup.sh: il nome progetto dedotto dalla cartella non
# identifica il file compose, e su una macchina con piu' stack si finisce a
# lavorare sul database sbagliato. Qui il danno sarebbe peggiore che in un
# backup, perche' questo script CANCELLA il database prima di riempirlo.
NOME_PROGETTO="${NOME_PROGETTO:-}"

compose() {
    if [ -n "$NOME_PROGETTO" ]; then
        docker compose -p "$NOME_PROGETTO" -f "$COMPOSE" "$@"
    else
        docker compose -f "$COMPOSE" "$@"
    fi
}

if [ -t 1 ]; then
    C_PASSO=$'\033[36m'; C_OK=$'\033[32m'; C_ATTENZIONE=$'\033[33m'
    C_ERRORE=$'\033[31m'; C_FINE=$'\033[0m'
else
    C_PASSO=''; C_OK=''; C_ATTENZIONE=''; C_ERRORE=''; C_FINE=''
fi

passo()      { printf '%s%s%s\n' "$C_PASSO" "$*" "$C_FINE"; }
riuscito()   { printf '%s%s%s\n' "$C_OK" "$*" "$C_FINE"; }
attenzione() { printf '%s%s%s\n' "$C_ATTENZIONE" "$*" "$C_FINE"; }
fallito()    { printf '%s%s%s\n' "$C_ERRORE" "$*" "$C_FINE" >&2; }

# --- Argomenti --------------------------------------------------------------

conferma_richiesta=1
dump=''

while [ $# -gt 0 ]; do
    case "$1" in
        --si|-s) conferma_richiesta=0; shift ;;
        -h|--help)
            sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        -*) fallito "Opzione sconosciuta: $1"; exit 2 ;;
        *)  dump="$1"; shift ;;
    esac
done

if [ -z "$dump" ]; then
    fallito "Manca il file da ripristinare."
    echo "Uso: $0 [--si] <file.sql.gz>" >&2
    echo "" >&2
    echo "I backup disponibili, se la cartella predefinita e' quella:" >&2
    ls -1t /var/backups/gestionale/giornalieri/*.sql.gz 2>/dev/null | head -10 >&2 || \
        echo "  (nessuno in /var/backups/gestionale/giornalieri)" >&2
    exit 2
fi

[ -f "$dump" ] || { fallito "Il file non esiste: ${dump}"; exit 1; }

cd "$PROGETTO" 2>/dev/null || { fallito "La cartella del progetto non esiste: ${PROGETTO}"; exit 1; }
[ -f "$COMPOSE" ] || { fallito "Non trovo ${COMPOSE} dentro ${PROGETTO}."; exit 1; }
[ -f .env ] || { fallito "Manca il file .env in ${PROGETTO}."; exit 1; }

# --- 1. Verificare il dump, PRIMA di toccare qualunque cosa -----------------

passo "Verifico il dump prima di toccare il database..."

rifiuta() {
    fallito "$1"
    fallito "Non ho toccato niente: il database e il backend sono come li hai lasciati."
    exit 1
}

# Gli stessi controlli di backup.sh, con le stesse soglie e per le stesse
# ragioni. Sono ripetuti e non estratti in un file comune di proposito: questi
# due script si copiano su un server e si lanciano a mano, anche da soli,
# anche il giorno in cui qualcosa e' gia' rotto. Una dipendenza in piu' fra
# loro e' una cosa in piu' che puo' mancare nel momento sbagliato.

byte=$(wc -c < "$dump")
[ "$byte" -ge "${BYTE_MINIMI:-2048}" ] || \
    rifiuta "Il dump e' praticamente vuoto (${byte} byte)."

gzip -t "$dump" 2>/dev/null || rifiuta "Il file compresso e' corrotto (gzip -t fallito)."

gzip -dc "$dump" | tail -5 | grep -q 'Dump completed' || \
    rifiuta "Il dump non finisce con '-- Dump completed': e' troncato."

tabelle=$(gzip -dc "$dump" | grep -c '^CREATE TABLE' || true)
[ "$tabelle" -ge "${TABELLE_MINIME:-10}" ] || \
    rifiuta "Il dump ha solo ${tabelle} tabelle: non e' lo schema del gestionale."

gzip -dc "$dump" | grep -q 'flyway_schema_history' || \
    rifiuta "Il dump non contiene flyway_schema_history: non e' un dump di questo gestionale."

riuscito "Dump integro."

# --- 2. Conferma ------------------------------------------------------------

if [ "$conferma_richiesta" -eq 1 ]; then
    echo ""
    attenzione "  Stai per SOVRASCRIVERE il database di ${PROGETTO}."
    attenzione "  Tutti i dati attuali spariscono e prende il posto:"
    attenzione "      $(basename "$dump")  ($(du -h "$dump" | cut -f1), del $(date -r "$dump" '+%d/%m/%Y %H:%M'))"
    echo ""
    printf 'Scrivi RIPRISTINA per procedere: '
    read -r risposta
    if [ "$risposta" != 'RIPRISTINA' ]; then
        echo "Annullato. Non ho toccato niente."
        exit 1
    fi
fi

# --- 3. Fermare il backend --------------------------------------------------
#
# Cosi' nessuno scrive a meta' ripristino. Il frontend resta su e dira' che il
# server non risponde, che in questo momento e' la verita'.

passo "Fermo il backend..."
compose stop backend >/dev/null
riuscito "Backend fermo."

# --- 4. Database pulito -----------------------------------------------------
#
# DROP + CREATE e non il solo ripristino sopra l'esistente: il dump contiene
# DROP TABLE per le tabelle che conosce, ma non saprebbe niente di una tabella
# aggiunta da una migrazione successiva al backup. Un ripristino parziale e'
# peggio di nessun ripristino, perche' sembra riuscito.
#
# I GRANT sono ridondanti su alcune versioni di MySQL e necessari su altre, a
# seconda di come DROP DATABASE tratta i privilegi a livello di database.
# Battendoli sempre, il dubbio sparisce.
#
# Sono DUE perche' le utenze sono due, e qui sta la trappola: la prima
# stesura di questo script faceva un unico
# `GRANT ALL PRIVILEGES ON gestionale.* TO 'gestionale'@'%'`, e dopo
# l'introduzione delle utenze separate quella riga avrebbe RIMESSO i privilegi
# DDL all'utenza applicativa a ogni ripristino - annullando la separazione in
# silenzio, un pezzo alla volta, senza che niente lo segnalasse. Un ripristino
# deve ricostruire l'assetto, non appiattirlo.
#
# L'elenco dei privilegi e' lo stesso di db-init/10-utenze.sh, dove c'e' anche
# la motivazione di ciascuno. Se cambia uno, cambiano entrambi.

passo "Ricreo il database vuoto..."

# `REVOKE IF EXISTS` e non `REVOKE`: a seconda della versione, DROP DATABASE
# porta via con se' i privilegi concessi su quello schema, e un REVOKE su un
# grant che non c'e' piu' e' un errore che fermerebbe il ripristino a metà -
# col database appena cancellato e il backend fermo. L'IF EXISTS c'e' da
# MySQL 8.0.16 e qui gira la 8.4.
compose exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot' <<'SQL'
DROP DATABASE IF EXISTS gestionale;
CREATE DATABASE gestionale CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- L'applicazione: solo righe, non schema.
REVOKE IF EXISTS ALL PRIVILEGES ON `gestionale`.* FROM 'gestionale'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `gestionale`.* TO 'gestionale'@'%';

FLUSH PRIVILEGES;
SQL

# Le migrazioni, a parte: se quell'utenza non esiste, un GRANT fallirebbe e
# porterebbe giu' tutto il ripristino. E crearla qui non si puo', perche'
# vorrebbe dire inventarle una password che poi non corrisponderebbe a quella
# del .env. Quindi si avverte e si va avanti: il ripristino in se' non ha
# bisogno di quell'utenza, ne avra' bisogno il PROSSIMO aggiornamento, quando
# Flyway dovra' applicare una migrazione nuova.
if [ "$(compose exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -e "SELECT COUNT(*) FROM mysql.user WHERE user = \"gestionale_flyway\""' 2>/dev/null | tr -d '[:space:]')" = "1" ]; then
    compose exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot' <<'SQL'
GRANT SELECT, INSERT, UPDATE, DELETE,
      CREATE, DROP, ALTER, INDEX, REFERENCES,
      CREATE VIEW, SHOW VIEW
    ON `gestionale`.* TO 'gestionale_flyway'@'%';
FLUSH PRIVILEGES;
SQL
    riuscito "Database vuoto, privilegi delle due utenze a posto."
else
    attenzione "L'utenza 'gestionale_flyway' non esiste su questo database."
    attenzione ""
    attenzione "Il ripristino dei DATI va avanti e riuscira': non le serve."
    attenzione "Ma il backend NON RIPARTIRA', e non al prossimo aggiornamento:"
    attenzione "subito, a questo riavvio. Flyway apre quella connessione a ogni"
    attenzione "avvio, anche solo per validare i checksum, e senza l'utenza muore"
    attenzione "con «Access denied for user 'gestionale_flyway'»."
    attenzione ""
    attenzione "Creala con la password che hai nel .env, poi riprova:"
    attenzione "  CREATE USER 'gestionale_flyway'@'%' IDENTIFIED BY '<FLYWAY_PASSWORD>';"
    attenzione "  piu' i GRANT elencati in db-init/10-utenze.sh"
    riuscito "Database vuoto, privilegi dell'utenza applicativa a posto."
fi

# --- 5. Caricare il dump ----------------------------------------------------
#
# Come root e non come `gestionale`: il dump contiene la vista
# permesso_effettivo con la sua clausola DEFINER=, e ricrearla richiede
# privilegi che l'utente applicativo non ha. E' la stessa ragione per cui
# backup.sh dumpa come root.
#
# Il dump non contiene CREATE DATABASE (mysqldump senza --databases), per
# questo il database l'ha creato il passo 4 e qui si indica su quale entrare.

passo "Carico il dump..."

set +e
gzip -dc "$dump" | compose exec -T db sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot gestionale'
esiti=("${PIPESTATUS[@]}")
set -e

if [ "${esiti[0]}" -ne 0 ] || [ "${esiti[1]}" -ne 0 ]; then
    fallito "Il caricamento e' fallito (stati: ${esiti[0]} ${esiti[1]})."
    fallito "Il database e' in uno stato incompleto e il backend e' ancora fermo."
    fallito "Riprova con un altro dump, oppure guarda: docker compose -f ${COMPOSE} logs db"
    exit 1
fi

riuscito "Dump caricato."

# --- 6. Riavviare il backend ------------------------------------------------
#
# Flyway all'avvio valida i checksum contro flyway_schema_history, che il dump
# ha riportato indietro insieme ai dati. Se il log dice «Successfully
# validated N migrations» senza applicarne nessuna, schema e codice sono
# d'accordo: e' la prova che il ripristino e' completo.

# `compose logs` restituisce TUTTO lo storico del container, compreso l'avvio
# di prima del ripristino. Senza --since, l'attesa qui sotto trova subito il
# «Started BackendApplication» della volta scorsa e prosegue senza aver
# aspettato niente, e il riepilogo finale mostra le righe di Flyway sbagliate:
# quelle del primo avvio, che dicono «Migrating schema» proprio dove lo script
# chiede di leggere «Successfully validated». Succedeva davvero: e' uscito al
# primo collaudo di questo script.
da_adesso=$(date -u '+%Y-%m-%dT%H:%M:%SZ')

passo "Riavvio il backend..."
compose start backend >/dev/null

# L'attesa guarda lo STATO DEL CONTAINER, non il log.
#
# La prima stesura cercava «Started BackendApplication» e, per il fallimento,
# «APPLICATION FAILED TO START». Quel banner pero' Spring Boot non lo stampa
# sempre: se e' Flyway a non riuscire a connettersi, l'eccezione arriva
# durante l'inizializzazione dei bean e nel log c'e' solo uno stack trace.
# Risultato, visto al collaudo: lo script ha scritto «Ripristino completato» e
# ha chiuso con stato 0 mentre il backend era in crash loop. Per uno strumento
# di ripristino e' il difetto peggiore possibile - dice che e' tutto a posto
# proprio quando non lo e'.
#
# Lo stato del container non si puo' fraintendere, e la salute la calcola
# Docker con l'healthcheck del Dockerfile, che interroga
# /actuator/health/readiness: `healthy` vuol dire che l'applicazione risponde
# davvero, non solo che il processo e' vivo.
contenitore=$(compose ps -q backend)
riavvii_prima=$(docker inspect "$contenitore" --format '{{.RestartCount}}' 2>/dev/null || echo 0)

scaduto=$(( $(date +%s) + 240 ))
salito=0
while [ "$(date +%s)" -lt "$scaduto" ]; do
    stato=$(docker inspect "$contenitore" --format '{{.State.Status}}' 2>/dev/null || echo assente)
    salute=$(docker inspect "$contenitore" \
        --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}assente{{end}}' 2>/dev/null || echo assente)
    riavvii=$(docker inspect "$contenitore" --format '{{.RestartCount}}' 2>/dev/null || echo 0)

    if [ "$salute" = healthy ]; then
        salito=1
        break
    fi
    # `restart: unless-stopped` fa ripartire il container all'infinito, quindi
    # un fallimento non si vede come "exited" ma come un contatore che cresce.
    if [ "$stato" = restarting ] || [ "$riavvii" -gt "$riavvii_prima" ]; then
        fallito "Il backend non riparte: e' in crash loop (riavvii: ${riavvii})."
        break
    fi
    sleep 3
done

echo ""
passo "Le righe di Flyway dopo il ripristino:"
compose logs --since "$da_adesso" backend 2>&1 | grep -iE 'flyway|migrat' | tail -5 | sed 's/^/    /'
echo ""

if [ "$salito" -ne 1 ]; then
    fallito "I DATI SONO STATI RIPRISTINATI, ma l'applicazione non e' ripartita."
    fallito "Il database contiene il dump; il problema sta fra l'applicazione e il"
    fallito "database, non nel backup. Le ultime righe del log:"
    compose logs --since "$da_adesso" --tail 30 backend 2>&1 | sed 's/^/    /' >&2
    echo "" >&2
    fallito "Se parla di «Access denied for user 'gestionale_flyway'», manca"
    fallito "l'utenza delle migrazioni: vedi l'avviso qui sopra e RILASCIO.md §10."
    exit 1
fi

riuscito "Ripristino completato, e il backend risponde."
echo ""
echo "Controlla a mano, prima di considerarlo finito:"
echo "  * che il log qui sopra dica «Successfully validated», non «Migrating schema»;"
echo "  * che il login funzioni dal browser;"
echo "  * che i dati siano quelli che ti aspetti dalla data del dump."
