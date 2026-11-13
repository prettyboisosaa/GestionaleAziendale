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

```bash
ss -lntp | grep ':80 '           # deve essere VUOTO
```

Se qualcosa risponde, c'è già un nginx o un Apache sulla 80 e il frontend non
partirà. Le due scelte sono spegnerlo (`sudo systemctl disable --now nginx`)
oppure spostare il gestionale su un'altra porta: in `compose.prod.yml`,
servizio `frontend`, `"80:80"` diventa `"8080:80"` — si cambia **solo** il
numero di sinistra.

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

## 4. Aprire la porta

```bash
sudo ufw status
```

Se è `active`, il mondo non vede ancora il gestionale:

```bash
sudo ufw allow 80/tcp
```

La 3306 del database e la 8080 del backend **non si aprono**: non sono
pubblicate sull'host di proposito, e si parlano fra container sulla rete
interna di compose. Se qualcuno te le chiede, la risposta è no.

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
preso **prima** dell'aggiornamento. Che è il motivo per cui il punto qui sotto
non è rimandabile a lungo.

---

## 7. Quello che questa procedura non copre

Non sono dettagli: sono decisioni ancora aperte.

- **I backup.** Il volume `dati-mysql` vive su questo server e basta. Un disco
  che si rompe, o un `docker compose down -v` battuto nella cartella
  sbagliata, e i dati non esistono più. Un `mysqldump` schedulato **su un
  disco diverso** va messo prima del primo dato vero, e il ripristino va
  provato una volta: un backup mai ripristinato è una speranza, non un backup.
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

## 8. Se qualcosa non va

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
