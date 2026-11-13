# Rilascio in produzione

Procedura per mettere il gestionale su un server Debian/Ubuntu con Docker già
installato. Vale per il primo rilascio e per gli aggiornamenti successivi.

Ogni comando va eseguito **sul server**, dopo l'SSH, se non è scritto
diversamente. I passi sono in ordine: il 4 non funziona senza il 3.

---

## 0. Prima di partire: cinque verifiche

Due minuti qui evitano di scoprire un problema a metà del primo avvio, quando
il database esiste già e tornare indietro costa.

```bash
docker --version                 # serve il demone attivo
docker compose version           # deve dire v2.x, non 1.x
systemctl is-enabled docker      # deve dire "enabled"
```

`systemctl is-enabled docker` è il passo che la gente salta. `restart:
unless-stopped` nei servizi riavvia i **container**, non Docker: se il demone
non parte col sistema, dopo il primo reboot del server il gestionale resta
giù e nessuno capisce perché. Se risponde `disabled`:

```bash
sudo systemctl enable --now docker
```

La porta 80 deve essere libera, e va chiesto a **due** interlocutori diversi,
perché nessuno dei due vede quello che vede l'altro:

```bash
docker ps --filter 'publish=80' --format '{{.Names}}\t{{.Ports}}'   # VUOTO
ss -lntp | grep ':80 ' || echo 'nessun processo locale sulla 80'    # VUOTO
```

`ss` interroga il kernel di questa macchina; `docker ps` interroga il demone
Docker, che può essere **altrove**. La differenza non è teorica: in WSL con
Docker Desktop il socket in ascolto sta sull'host Windows e non nel namespace
di rete della WSL, quindi `ss` non mostra niente, la verifica sembra passata,
e `docker compose up` si schianta comunque con

```
Bind for 0.0.0.0:80 failed: port is already allocated
```

Lo stesso vale su un server dove gira più di uno stack compose: il container
che tiene la porta lo trova solo `docker ps`.

Se qualcosa risponde, le scelte sono due. Se è un container, fermarlo
(`docker stop <nome>`). Se è un nginx o un Apache di sistema, spegnerlo
(`sudo systemctl disable --now nginx`) — oppure spostare il gestionale su
un'altra porta: in `compose.prod.yml`, servizio `frontend`, `"80:80"` diventa
`"8080:80"`, cambiando **solo** il numero di sinistra.

Un avvertimento se pensavi di lasciare la riga originale e aggiungerne una in
un file di override: compose **somma** le liste di `ports` invece di
sostituirle, quindi ti ritrovi con entrambe le porte e di nuovo il conflitto.
Per sostituire serve il tag `!override`:

```yaml
services:
  frontend:
    ports: !override
      - "8090:80"
```

```bash
free -m                          # almeno ~2 GB disponibili
df -h /var/lib/docker            # almeno ~5 GB liberi
```

Il server **compila** il progetto: Maven scarica le dipendenze e `javac` gira
in un container, e lo stesso fa `npm ci` per il frontend. Con meno di 2 GB di
RAM il build del backend viene ucciso dal kernel e l'errore che vedi parla di
Maven, non di memoria. Se il server è piccolo, il modo giusto non è aggiungere
swap: è costruire le immagini altrove e pubblicarle su un registry, così il
server scarica e non compila. Oggi quel percorso non esiste ancora.

---

## 1. Prendere il codice

Il repository è pubblico, quindi non servono credenziali.

```bash
cd /opt
sudo git clone https://github.com/prettyboisosaa/GestionaleAziendale.git gestionale
sudo chown -R "$USER":"$USER" /opt/gestionale
cd /opt/gestionale
```

`/opt` è una convenzione, non un obbligo: qualunque cartella va bene, purché
l'utente che lancia `docker compose` possa scriverci. Il `chown` serve perché
i passi successivi creano e modificano il `.env` senza `sudo`.

Tutti i comandi che seguono partono da `/opt/gestionale`: `docker compose`
cerca il `.env` **nella cartella corrente**, non accanto al file che gli passi
con `-f`. Lanciare la procedura da `/home/tuonome` dà un errore su
`DB_PASSWORD` mancante con il `.env` che esiste benissimo due cartelle più in
là.

---

## 2. Il file `.env`

```bash
cp .env.example .env
chmod 600 .env
```

Il `chmod` non è cerimoniale: di default il file nasce leggibile da tutti gli
utenti del server, e dentro ci va la password del database.

Genera i due segreti del database:

```bash
openssl rand -base64 24 | tr -d '/+=' ; openssl rand -base64 24 | tr -d '/+='
```

Il `tr` toglie `/`, `+` e `=` e **non è un vezzo**: `docker compose` interpreta
il contenuto del `.env` prima di passarlo ai container, quindi un `$` nella
password diventa un riferimento a una variabile e la password arriva troncata
o vuota. L'errore che ne esce è `Access denied for user 'gestionale'`, che
manda a cercare nel posto sbagliato per un'ora. Limitati a lettere, cifre e
`_ - . ! %`.

Poi apri il file e riempi i quattro valori:

```bash
nano .env
```

```ini
DB_PASSWORD=<il primo valore generato>
MYSQL_ROOT_PASSWORD=<il secondo>
ADMIN_EMAIL=nome.cognome@azienda.it
ADMIN_PASSWORD=<password del primo accesso>
```

`ADMIN_PASSWORD` deve rispettare le stesse regole di tutte le altre del
gestionale: **almeno 8 caratteri, almeno una cifra, almeno un carattere non
alfanumerico**. Se non le rispetta, l'utente **non viene creato** e il
container parte comunque, sano, con la pagina di login che si apre
normalmente: sembra tutto a posto e non entra nessuno. È una password usa e
getta, la cambi fra cinque minuti al passo 5 — `Primo-Accesso-2026!` va
benissimo.

---

## 3. Il primo avvio

```bash
docker compose -f compose.prod.yml up -d --build
```

La prima volta sono diversi minuti: scarica MySQL, nginx, l'immagine di Maven
e quella di Node, poi compila. Se una variabile manca, compose si ferma
**prima** di avviare qualunque cosa e dice quale.

Guarda il log del backend mentre sale:

```bash
docker compose -f compose.prod.yml logs -f backend
```

Tre righe ti dicono che è andata bene. Esci con `Ctrl+C` quando le hai viste.

1. **Le migrazioni.** `Successfully applied 23 migrations to schema
   'gestionale', now at version v23`. Il numero cresce coi rilasci futuri.
2. **Nessun dato di prova.** Non deve comparire mai la parola
   `dati_sviluppo`. Se compare, il profilo di produzione non è attivo e nel
   database stanno entrando sette utenti con una password nota: fermati,
   `down -v`, e capisci perché `SPRING_PROFILES_ACTIVE: prod` non è arrivato.
3. **L'amministratore.** `Creato l'amministratore iniziale <email> con il
   ruolo «Amministratore»`. Se invece leggi un `WARN` che dice che nessuno
   può accedere, la password non ha passato le regole del passo 2: correggi
   il `.env`, poi `docker compose -f compose.prod.yml up -d --force-recreate
   backend`. Funziona perché la tabella utente è ancora vuota.

Infine, che tutto sia in piedi:

```bash
docker compose -f compose.prod.yml ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost/
```

Tutti e tre i servizi `healthy`. Il backend può metterci un paio di minuti: il
suo healthcheck, dichiarato in `backend/Dockerfile`, concede
`--start-period=180s` prima di considerare un fallimento un vero fallimento.

---

## 4. Il firewall (probabilmente non c'è niente da fare)

Questo passo è più corto di quanto sembri, e la ragione va capita invece che
saltata.

```bash
command -v ufw nft iptables firewall-cmd || echo 'nessun firewall installato'
```

Su una Debian installata da netinst spesso **`ufw` non c'è affatto** — e nella
WSL di solito non c'è nemmeno `iptables`. `command not found` non è un errore:
vuol dire che su questa macchina non c'è un firewall locale, e quindi niente
da aprire. Se invece `ufw` c'è ed è `active`, la regola si aggiunge con
`sudo ufw allow 80/tcp`.

**Il punto importante, che vale anche se ufw c'è: non è ufw a decidere se la
porta 80 è raggiungibile.** Docker pubblica le porte con una regola di DNAT e
infila le proprie catene in testa a `FORWARD`: il traffico verso una porta
pubblicata non passa da `INPUT`, dove stanno le regole di ufw, e arriva a
destinazione **anche con `ufw default deny incoming`**. Chi si fida di
`ufw status` per sapere cosa è esposto legge la risposta a una domanda
diversa.

La domanda giusta è quali porte Docker sta pubblicando:

```bash
docker ps --format '{{.Names}}\t{{.Ports}}'
```

Deve comparire **solo** il frontend, con `0.0.0.0:80->80/tcp`. Il database e
il backend non hanno `ports` in `compose.prod.yml`, quindi non sono
raggiungibili da fuori: non per una regola di firewall, ma perché non sono
pubblicati. È quella la protezione, ed è più solida di una regola che Docker
può scavalcare. Se un domani qualcuno aggiunge `"3306:3306"` al servizio `db`
«tanto c'è il firewall», il database finisce sulla rete aziendale e ufw non lo
fermerà.

Da qui il gestionale è raggiungibile su `http://<indirizzo-del-server>`.

---

## 5. Le utenze: dal primo accesso all'assetto definitivo

Questi passi si fanno **dal browser**, non in SSH. Sono la parte che non si
può automatizzare e quella in cui si sbaglia l'ordine.

1. **Entra** con `ADMIN_EMAIL` e `ADMIN_PASSWORD`.
2. **Cambia subito la password** da «Profilo». Quella del `.env` è passata per
   un file di testo e per il log di compose: va considerata bruciata.
3. **Crea le utenze vere**, incluse almeno **due** amministratori — una è la
   tua. Due e non una: nessuno può disattivare se stesso, quindi con un solo
   amministratore non c'è nessuno che possa riaprire un'utenza chiusa per
   sbaglio. Serve password di **almeno 10** caratteri: dall'interfaccia la
   regola è più severa che per l'admin iniziale.
4. **Esci e rientra con la tua utenza.** Da qui in avanti l'admin iniziale non
   ti serve più.
5. **Disattiva l'admin iniziale** dall'elenco utenti. La riga non viene
   cancellata — resta col suo badge grigio, così lo storico di chi ha creato
   cosa continua a leggersi — e la sua sessione, se ancora aperta, cade alla
   richiesta successiva.

Poi, tornando in SSH, togli i segreti dall'ambiente del processo:

```bash
nano .env          # svuota ADMIN_EMAIL= e ADMIN_PASSWORD=, lasciando le righe
docker compose -f compose.prod.yml up -d
```

Svuotarle è previsto: a differenza di `DB_PASSWORD`, compose non le pretende.
Dal secondo avvio sono inerti comunque, perché la tabella utente non è più
vuota, ma lasciarle significa tenere una password valida dentro
`docker inspect` e dentro `/proc/<pid>/environ` per sempre.

Verifica che siano andate via:

```bash
docker compose -f compose.prod.yml exec backend env | grep AMMINISTRATORE
```

Devono comparire le due righe con il valore vuoto.

---

## 6. Gli aggiornamenti

```bash
cd /opt/gestionale
git pull
docker compose -f compose.prod.yml up -d --build
docker compose -f compose.prod.yml logs -f backend
```

Il `.env` non viene toccato da `git pull`: non è versionato.

Due cose da sapere prima di premere invio.

**Lo schema viene validato, non riscritto.** In produzione
`ddl-auto=validate`: se le entità Java e le tabelle divergono, il backend
**non parte** e lo dice. È il comportamento voluto — meglio un'applicazione
che non sale di una che riscrive lo schema sotto i dati — ma vuol dire che un
aggiornamento che si è dimenticato una migrazione si manifesta come backend
giù. Il frontend intanto continua a servire la pagina, che dirà che il server
non risponde.

**Le migrazioni non tornano indietro.** `git checkout` del commit precedente
rimette il codice di ieri, non lo schema di ieri: una migrazione che ha
cancellato una colonna l'ha cancellata. Per un rollback vero serve il dump
preso **prima** dell'aggiornamento, e quel dump è un comando:

```bash
sudo /opt/gestionale/scripts/backup.sh
```

Battilo **prima** del `git pull`, non dopo. Costa pochi secondi e si ritrova
nella cartella dei giornalieri, pronto per `scripts/ripristina.sh` se
l'aggiornamento va storto.

---

## 7. I backup

Il backup notturno del database: `scripts/backup.sh` lanciato da un timer
systemd, con i dump in `/var/backups/gestionale`.

**Il limite, dichiarato subito**: i dump stanno sullo **stesso filesystem dei
dati**. Ti salvano da un `down -v` battuto nella cartella sbagliata, da una
migrazione andata male e da una cancellazione per sbaglio — cioè dalle tre
cose che succedono davvero. **Non** ti salvano dal guasto del disco né dalla
perdita del server: lì se ne vanno insieme ai dati. È un primo passo, e il
secondo è portarne una copia su un'altra macchina.

### Installazione

```bash
cd /opt/gestionale
sudo cp scripts/systemd/gestionale-backup.* /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now gestionale-backup.timer
```

Se il progetto non sta in `/opt/gestionale`, va cambiato `Environment=` dentro
`gestionale-backup.service`, non lo script.

Poi **la prima esecuzione a mano**, senza aspettare la notte, perché un timer
che fallirà alle 02:30 è meglio scoprirlo adesso:

```bash
sudo /opt/gestionale/scripts/backup.sh
ls -lh /var/backups/gestionale/giornalieri/
cat /var/backups/gestionale/ultimo-esito.txt
```

### La prova di ripristino, che non è facoltativa

Un backup mai ripristinato è una speranza, non un backup. Fallo **ora**, che
nel database ci sono solo le utenze appena create e sbagliare non costa
niente — non il giorno in cui serve davvero.

```bash
sudo /opt/gestionale/scripts/ripristina.sh \
  /var/backups/gestionale/giornalieri/<il file di prima>
```

Lo script chiede conferma (si scrive `RIPRISTINA`), ferma il backend, ricrea
il database, carica il dump e riavvia. Alla fine mostra le righe di Flyway: se
dicono **«Successfully validated 23 migrations»** e non «Migrating schema», lo
schema ripristinato e il codice sono d'accordo. Poi rientra dal browser e
controlla di vedere i tuoi utenti.

### Com'è fatto

| | |
|---|---|
| Quando | ogni notte alle 02:30, con `Persistent=true`: se il server era spento, parte al boot successivo |
| Dove | `/var/backups/gestionale/giornalieri/` e `/settimanali/`, cartella `700` di root |
| Quante copie | gli ultimi 7 giorni, più una copia a settimana per 4 settimane |
| Formato | `mysqldump --single-transaction` compresso con gzip; il gestionale resta su durante il dump |
| Verifica | ogni dump è controllato prima di ruotare: non vuoto, gzip valido, finisce con `-- Dump completed`, almeno 10 tabelle, contiene `flyway_schema_history`. Se un controllo fallisce il file viene cancellato, **nessun backup vecchio viene toccato** e il servizio va in `failed` |

### Controllare che funzioni

È il punto debole di questo assetto: nessuno ti avvisa. Lo stato c'è, ma va
guardato — **mettilo in agenda una volta al mese**.

```bash
systemctl list-timers gestionale-backup.timer      # quando tocchera' di nuovo
systemctl status gestionale-backup.service         # com'e' andata l'ultima volta
cat /var/backups/gestionale/ultimo-esito.txt       # la risposta breve
journalctl -u gestionale-backup.service --since '7 days ago'
du -sh /var/backups/gestionale                     # quanto spazio occupa
```

Se `ultimo-esito.txt` ha una data di una settimana fa, il backup è morto una
settimana fa. Il passo successivo, quando vorrai toglierti il controllo
manuale, è un *dead man's switch*: lo script chiama un URL a fine corsa e un
servizio esterno ti manda una mail quando le chiamate smettono.

---

## 8. Quello che questa procedura non copre

Non sono dettagli: sono decisioni ancora aperte.

- **I backup fuori dal server.** Quelli locali ci sono (§7), ma vivono sullo
  stesso disco dei dati: un guasto hardware se li porta via insieme a quello
  che dovevano proteggere. Una copia su un'altra macchina — `rsync` su SSH, o
  un bucket — è il prossimo passo, e il momento giusto per farlo è prima che
  il database diventi importante.
- **HTTPS.** Oggi il gestionale è in HTTP e `compose.prod.yml` imposta
  `COOKIE_SECURE: "false"`, perché con `true` e senza TLS il login risponde
  200 e ogni richiesta successiva è anonima, senza nessun errore che lo
  spieghi. Vuol dire che il cookie di sessione viaggia in chiaro: su una rete
  dove qualcuno può ascoltare, rubarlo vale quanto rubare la password. Quando
  arriva un certificato, `COOKIE_SECURE` va a `"true"` (o si cancella: il
  valore predefinito nelle properties è già quello sicuro) e in
  `frontend/nginx.conf` si aggiunge `Strict-Transport-Security`.
- **Un riavvio del backend butta fuori tutti.** Le sessioni vivono nella
  memoria del processo, non in un archivio condiviso: ogni aggiornamento
  richiede di rifare il login. Con una ventina di persone è accettabile; va
  detto a chi lo usa, così non viene segnalato come guasto.
- **phpMyAdmin non c'è**, di proposito: in sviluppo serve a guardare dentro il
  database, in produzione è una porta aperta sui dati di tutti. Per
  un'occhiata al volo c'è `docker compose -f compose.prod.yml exec db mysql
  -ugestionale -p`.

---

## 9. Se qualcosa non va

| Sintomo | Causa quasi sempre |
|---|---|
| `required variable DB_PASSWORD is missing a value` | Non sei in `/opt/gestionale`, oppure il `.env` non c'è. Compose cerca il `.env` nella cartella corrente. |
| `Access denied for user 'gestionale'` | Un `$` nella password dentro il `.env` (vedi passo 2). Oppure hai cambiato `DB_PASSWORD` dopo il primo avvio: MySQL la fissa quando inizializza il volume e non la rilegge più. |
| Il login risponde 200 ma ogni pagina richiede di nuovo l'accesso | `COOKIE_SECURE` non è `"false"` e non c'è HTTPS. Controlla con `docker compose -f compose.prod.yml exec backend env \| grep COOKIE`. |
| Il backend resta `unhealthy` | `docker compose -f compose.prod.yml logs backend`. Se parla di `Validate failed` è lo schema divergente (passo 6); se muore senza un errore chiaro durante il build, è la RAM (passo 0). |
| `Bind for 0.0.0.0:80 failed: port is already allocated` | Qualcosa occupa la 80 (passo 0). |
| `Public Key Retrieval is not allowed` | Qualcuno ha aggiunto `useSSL=false` al `DB_URL`. Va tolto: MySQL 8.4 autentica con `caching_sha2_password` e senza TLS pretenderebbe anche `allowPublicKeyRetrieval=true`. Lasciando decidere al driver si ottiene `sslMode=PREFERRED`, che funziona. |
| Dopo un reboot del server non risponde niente | `systemctl is-enabled docker` (passo 0). |

Un comando che vale la pena conoscere prima di averne bisogno:

```bash
docker compose -f compose.prod.yml down          # ferma tutto, i dati restano
```

E uno da non battere mai sul server, perché **cancella il database**:

```bash
docker compose -f compose.prod.yml down -v       # il -v distrugge il volume
```
