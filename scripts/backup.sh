#!/usr/bin/env bash
#
# Il backup del database di produzione: un dump compresso, verificato, e la
# rotazione delle copie vecchie.
#
# Risolve tre modi concreti di perdere i dati, in ordine di quanto sono
# probabili:
#
#   1. `docker compose -f compose.prod.yml down -v`. Il -v cancella il volume
#      `dati-mysql`, ed e' a due caratteri da un comando che si usa davvero.
#   2. Una migrazione che si porta via una colonna. `git checkout` del commit
#      precedente rimette il codice di ieri, non lo schema di ieri: per
#      tornare indietro serve un dump preso PRIMA. Per questo lo script si
#      lancia anche a mano, ed e' la cosa da fare prima di ogni aggiornamento.
#   3. Una cancellazione per sbaglio dall'interfaccia.
#
# NON risolve il guasto del disco ne' la perdita del server: i dump stanno
# sullo stesso filesystem dei dati. E' un primo passo dichiarato, non una
# svista. Il passo dopo e' una copia su un'altra macchina.
#
# Uso:
#   ./scripts/backup.sh                    # con i valori predefiniti
#   PROGETTO=/opt/gestionale DESTINAZIONE=/var/backups/gestionale ./scripts/backup.sh
#
# Esce 0 se e' andato tutto bene, diverso da zero altrimenti - cosi' l'unita'
# systemd va in stato `failed` e `systemctl status` lo racconta.

set -euo pipefail

# --- Configurazione ---------------------------------------------------------
# Sovrascrivibili da ambiente, cosi' lo script si prova in locale senza
# toccare queste righe.

PROGETTO="${PROGETTO:-/opt/gestionale}"
DESTINAZIONE="${DESTINAZIONE:-/var/backups/gestionale}"
COMPOSE="${COMPOSE:-compose.prod.yml}"

# Il nome del progetto compose. Vuoto = quello che compose deduce dal nome
# della cartella, che sul server e' l'unico stack presente ed e' giusto.
#
# Esiste perche' il nome dedotto NON identifica il file: `docker compose -f
# compose.prod.yml ps db` trova il container con le etichette
# (progetto=<cartella>, servizio=db) anche se quel container l'ha avviato un
# ALTRO file compose. Su una macchina dove convivono lo stack di sviluppo e
# quello di produzione nella stessa cartella, il backup dumpa in silenzio il
# database sbagliato e riesce: il dump e' valido, contiene
# flyway_schema_history, passa ogni verifica, e sono i dati di qualcun altro.
# E' successo durante il collaudo di questo script.
NOME_PROGETTO="${NOME_PROGETTO:-}"

# Un solo punto che sa come si chiama docker compose qui dentro.
compose() {
    if [ -n "$NOME_PROGETTO" ]; then
        docker compose -p "$NOME_PROGETTO" -f "$COMPOSE" "$@"
    else
        docker compose -f "$COMPOSE" "$@"
    fi
}

# Quanti ne teniamo. I giornalieri coprono l'incidente che ci si accorge
# subito, i settimanali quello che si scopre dopo (un dato sbagliato copiato
# avanti per giorni).
GIORNI_GIORNALIERI="${GIORNI_GIORNALIERI:-7}"
GIORNI_SETTIMANALI="${GIORNI_SETTIMANALI:-28}"

# In che giorno si prende la copia settimanale, nella numerazione di
# `date +%u`: 1 lunedi', 7 domenica. E' una variabile e non un 7 scritto nel
# codice anche per una ragione pratica: cosi' la rotazione settimanale si
# prova senza aspettare domenica.
GIORNO_SETTIMANALE="${GIORNO_SETTIMANALE:-7}"

# Le due soglie della verifica.
#
# BYTE_MINIMI prende solo il caso grossolano: un file vuoto o quasi. E'
# volutamente basso. La prima stesura di questo script lo teneva a 10 KB
# ragionando sulla taglia di un dump "normale", e al primo collaudo ha
# rifiutato un backup perfettamente valido: un gestionale appena installato,
# con due utenti e un lavoro, sta in 7,3 KB compressi. Una soglia tarata sui
# dati rifiuta i database piccoli, cioe' proprio quelli del primo giorno, che
# sono anche quelli in cui un errore passa inosservato piu' a lungo.
BYTE_MINIMI="${BYTE_MINIMI:-2048}"

# TABELLE_MINIME e' la soglia che conta, perche' guarda la STRUTTURA e non la
# quantita' di dati: lo schema di questo gestionale ha 16 tabelle dopo tutte
# le migrazioni, e ne avra' di piu', mai di meno. Un dump con meno di dieci
# CREATE TABLE non e' un dump di questo database, qualunque cosa pesi.
TABELLE_MINIME="${TABELLE_MINIME:-10}"

# Quanto aspettare che il database sia sano. Serve davvero: il timer ha
# Persistent=true, quindi un backup saltato perche' il server era spento parte
# subito dopo il boot, quando Docker c'e' ma MySQL sta ancora salendo.
SECONDI_ATTESA_DB="${SECONDI_ATTESA_DB:-120}"

# --- Messaggi ---------------------------------------------------------------
# Colorati solo se si sta scrivendo a un terminale: sotto systemd l'output
# finisce nel journal, dove i codici ANSI sono rumore.

if [ -t 1 ]; then
    C_PASSO=$'\033[36m'; C_OK=$'\033[32m'; C_ATTENZIONE=$'\033[33m'
    C_ERRORE=$'\033[31m'; C_FINE=$'\033[0m'
else
    C_PASSO=''; C_OK=''; C_ATTENZIONE=''; C_ERRORE=''; C_FINE=''
fi

passo()      { printf '%s%s%s\n' "$C_PASSO" "$*" "$C_FINE"; }
riuscito()   { printf '%s%s%s\n' "$C_OK" "$*" "$C_FINE"; }
attenzione() { printf '%s%s%s\n' "$C_ATTENZIONE" "$*" "$C_FINE" >&2; }
fallito()    { printf '%s%s%s\n' "$C_ERRORE" "$*" "$C_FINE" >&2; }

inizio_secondi=$(date +%s)

# Scrive l'esito dove lo si va a cercare quando ci si chiede «ma sta
# funzionando?». Viene chiamata sia in caso di successo sia di fallimento.
scrivi_esito() {
    local risultato="$1" dettaglio="$2"
    local durata=$(( $(date +%s) - inizio_secondi ))
    {
        echo "data:       $(date '+%Y-%m-%d %H:%M:%S %Z')"
        echo "risultato:  ${risultato}"
        echo "dettaglio:  ${dettaglio}"
        echo "durata:     ${durata}s"
    } > "${DESTINAZIONE}/ultimo-esito.txt" 2>/dev/null || true
}

# --- Controlli preliminari --------------------------------------------------

if [ ! -d "$PROGETTO" ]; then
    fallito "La cartella del progetto non esiste: ${PROGETTO}"
    fallito "Impostala con PROGETTO=... oppure correggi il valore in testa a questo script."
    exit 1
fi

cd "$PROGETTO"

if [ ! -f "$COMPOSE" ]; then
    fallito "Non trovo ${COMPOSE} dentro ${PROGETTO}."
    exit 1
fi

# Il .env serve a compose per risolvere ${DB_PASSWORD:?...} e compagnia: senza,
# si ferma con «required variable DB_PASSWORD is missing a value» e l'errore
# sembra parlare del database invece che di un file assente.
if [ ! -f .env ]; then
    fallito "Manca il file .env in ${PROGETTO}: compose non puo' risolvere le variabili."
    exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
    fallito "docker non e' nel PATH."
    exit 1
fi

mkdir -p "${DESTINAZIONE}/giornalieri" "${DESTINAZIONE}/settimanali"

# 700 e non 755, e non e' burocrazia: da un dump si ricostruiscono nome,
# cognome ed email di ogni dipendente, lo storico di cosa ha fatto e quando, le
# assenze con le motivazioni testuali dei rifiuti, e gli hash delle password.
chmod 700 "$DESTINAZIONE" 2>/dev/null || \
    attenzione "Non sono riuscito a mettere i permessi 700 su ${DESTINAZIONE}: controllalo a mano."

# --- Attendere che il database sia sano -------------------------------------

passo "Aspetto che il database sia pronto..."

scaduto=$(( $(date +%s) + SECONDI_ATTESA_DB ))
stato=''
while [ "$(date +%s)" -lt "$scaduto" ]; do
    stato=$(compose ps --format '{{.Health}}' db 2>/dev/null | head -1 || true)
    [ "$stato" = 'healthy' ] && break
    sleep 3
done

if [ "$stato" != 'healthy' ]; then
    fallito "Il database non e' sano dopo ${SECONDI_ATTESA_DB}s (stato: '${stato:-assente}')."
    fallito "Controlla: docker compose -f ${COMPOSE} logs db"
    scrivi_esito "FALLITO" "database non sano (stato: ${stato:-assente})"
    exit 1
fi

riuscito "Database pronto."

# --- Il dump ----------------------------------------------------------------

marca=$(date '+%Y-%m-%d-%H%M')
dump="${DESTINAZIONE}/giornalieri/gestionale-${marca}.sql.gz"

passo "Dump in corso -> ${dump}"

# Le opzioni, una per una, perche' nessuna e' qui per abitudine:
#
#   exec -T          Sotto systemd non c'e' un TTY. Senza -T, fallisce.
#
#   sh -c '...'      Apici SINGOLI: $MYSQL_ROOT_PASSWORD si espande dentro il
#                    container, dove gia' esiste. Non passa mai dalla riga di
#                    comando dell'host, quindi non finisce nella lista dei
#                    processi ne' nel journal.
#
#   MYSQL_PWD        Stessa ragione dentro il container: -p"..." mostrerebbe
#                    la password nel `ps` del container.
#
#   -uroot           NON -ugestionale. Lo schema ha la vista
#                    `permesso_effettivo`, che mysqldump esporta con una
#                    clausola DEFINER=; ricreare una vista con un definer
#                    diverso da se' richiede privilegi che l'utente
#                    applicativo non ha. Dump come root e ripristino come root
#                    vanno in coppia: vedi ripristina.sh.
#
#   --single-transaction
#                    Tutte le tabelle sono InnoDB, quindi il dump e' coerente
#                    senza bloccare nessuno: il gestionale continua a
#                    funzionare mentre gira.
#
#   --hex-blob       richiesta_assenza.dettagli e' una colonna JSON NOT NULL.
#
#   --quick          Non carica in memoria una tabella intera per volta.
#
# Niente --routines ne' --events: non esistono trigger, procedure, funzioni o
# event in tutto lo schema, e metterle darebbe l'impressione che ci sia
# qualcosa da proteggere li'.
#
# flyway_schema_history NON si esclude. In produzione ddl-auto=validate e
# Flyway controlla i checksum all'avvio: ripristinare senza quella tabella fa
# ripartire Flyway dalla V1 su un database gia' pieno, e il backend non sale.

set +e
compose exec -T db sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot \
        --single-transaction --quick --hex-blob \
        --default-character-set=utf8mb4 gestionale' \
    2> "${dump}.errori" | gzip -9 > "$dump"
esiti=("${PIPESTATUS[@]}")
set -e

if [ "${esiti[0]}" -ne 0 ] || [ "${esiti[1]}" -ne 0 ]; then
    fallito "mysqldump o gzip hanno fallito (stati: ${esiti[0]} ${esiti[1]})."
    [ -s "${dump}.errori" ] && sed 's/^/    /' "${dump}.errori" >&2
    rm -f "$dump" "${dump}.errori"
    scrivi_esito "FALLITO" "mysqldump/gzip stati ${esiti[0]} ${esiti[1]}"
    exit 1
fi

rm -f "${dump}.errori"
chmod 600 "$dump"

# --- La verifica ------------------------------------------------------------
#
# E' la parte che distingue un backup da un file. Un disco pieno produce un
# .gz troncato, e il dump finisce a meta' senza che niente protesti: quello che
# si scopre e' che il "backup" di tre mesi non si ripristina, il giorno in cui
# serve.
#
# Nessuna rotazione prima di qui. Cancellare i dump vecchi dopo averne
# prodotto uno rotto e' il modo esatto in cui si perde tutto.

passo "Verifico il dump..."

scarta() {
    fallito "$1"
    rm -f "$dump"
    scrivi_esito "FALLITO" "$2"
    exit 1
}

byte=$(wc -c < "$dump")
[ "$byte" -ge "$BYTE_MINIMI" ] || \
    scarta "Il dump e' praticamente vuoto (${byte} byte, minimo ${BYTE_MINIMI})." \
           "dump vuoto (${byte} byte)"

gzip -t "$dump" 2>/dev/null || \
    scarta "Il file compresso e' corrotto (gzip -t fallito)." "gzip -t fallito"

# mysqldump scrive questa riga SOLO se e' arrivato in fondo. E' il controllo
# che smaschera il disco pieno: il dump si interrompe a meta' e nessuno
# protesta, finche' non serve.
gzip -dc "$dump" | tail -5 | grep -q 'Dump completed' || \
    scarta "Il dump non finisce con '-- Dump completed': e' troncato." "dump troncato"

tabelle=$(gzip -dc "$dump" | grep -c '^CREATE TABLE' || true)
[ "$tabelle" -ge "$TABELLE_MINIME" ] || \
    scarta "Il dump ha solo ${tabelle} tabelle (minimo ${TABELLE_MINIME}): non e' lo schema del gestionale." \
           "solo ${tabelle} tabelle"

# Se manca, stiamo guardando il database sbagliato.
gzip -dc "$dump" | grep -q 'flyway_schema_history' || \
    scarta "Il dump non contiene flyway_schema_history: database sbagliato?" \
           "flyway_schema_history assente"

leggibile=$(du -h "$dump" | cut -f1)
riuscito "Dump verificato: ${leggibile}"

# --- Rotazione --------------------------------------------------------------

giorno_settimana=$(date '+%u')
if [ "$giorno_settimana" = "$GIORNO_SETTIMANALE" ]; then
    settimanale="${DESTINAZIONE}/settimanali/gestionale-${marca}.sql.gz"
    cp -p "$dump" "$settimanale"
    riuscito "Copia settimanale: $(basename "$settimanale")"
fi

# -mtime +N cancella cio' che ha piu' di N giorni. I due numeri sono separati
# di proposito: i giornalieri coprono la settimana, i settimanali il mese.
find "${DESTINAZIONE}/giornalieri" -name 'gestionale-*.sql.gz' \
     -type f -mtime "+${GIORNI_GIORNALIERI}" -delete
find "${DESTINAZIONE}/settimanali" -name 'gestionale-*.sql.gz' \
     -type f -mtime "+${GIORNI_SETTIMANALI}" -delete

quanti_g=$(find "${DESTINAZIONE}/giornalieri" -name 'gestionale-*.sql.gz' -type f | wc -l)
quanti_s=$(find "${DESTINAZIONE}/settimanali" -name 'gestionale-*.sql.gz' -type f | wc -l)

riuscito "Fatto. In archivio: ${quanti_g} giornalieri, ${quanti_s} settimanali."
scrivi_esito "OK" "${leggibile}, ${quanti_g} giornalieri, ${quanti_s} settimanali"
