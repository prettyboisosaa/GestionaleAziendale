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
# Il GRANT e' ridondante su alcune versioni di MySQL e necessario su altre, a
# seconda di come DROP DATABASE tratta i privilegi a livello di database.
# Battendolo sempre, il dubbio sparisce.

passo "Ricreo il database vuoto..."

compose exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot' <<'SQL'
DROP DATABASE IF EXISTS gestionale;
CREATE DATABASE gestionale CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON gestionale.* TO 'gestionale'@'%';
FLUSH PRIVILEGES;
SQL

riuscito "Database vuoto, privilegi a posto."

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

scaduto=$(( $(date +%s) + 180 ))
while [ "$(date +%s)" -lt "$scaduto" ]; do
    recente=$(compose logs --since "$da_adesso" backend 2>&1 || true)
    if printf '%s' "$recente" | grep -q 'Started BackendApplication'; then
        break
    fi
    if printf '%s' "$recente" | grep -q 'APPLICATION FAILED TO START'; then
        fallito "Il backend non e' ripartito. Il log dice:"
        compose logs --since "$da_adesso" --tail 40 backend >&2
        exit 1
    fi
    sleep 3
done

echo ""
passo "Le righe di Flyway dopo il ripristino:"
compose logs --since "$da_adesso" backend 2>&1 | grep -iE 'flyway|migrat' | tail -5 | sed 's/^/    /'
echo ""

riuscito "Ripristino completato."
echo ""
echo "Controlla a mano, prima di considerarlo finito:"
echo "  * che il log qui sopra dica «Successfully validated», non «Migrating schema»;"
echo "  * che il login funzioni dal browser;"
echo "  * che i dati siano quelli che ti aspetti dalla data del dump."
