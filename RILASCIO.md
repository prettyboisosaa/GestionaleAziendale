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

Genera i tre segreti del database:

```bash
for i in 1 2 3; do openssl rand -base64 24 | tr -d '/+='; done
```

Il `tr` toglie `/`, `+` e `=` e **non è un vezzo**: `docker compose` interpreta
il contenuto del `.env` prima di passarlo ai container, quindi un `$` nella
password diventa un riferimento a una variabile e la password arriva troncata
o vuota. L'errore che ne esce è `Access denied for user 'gestionale'`, che
manda a cercare nel posto sbagliato per un'ora. Limitati a lettere, cifre e
`_ - . ! %`.

Poi apri il file e riempi i cinque valori:

```bash
nano .env
```

```ini
DB_PASSWORD=<il primo valore generato>
MYSQL_ROOT_PASSWORD=<il secondo>
FLYWAY_PASSWORD=<il terzo>
ADMIN_EMAIL=nome.cognome@azienda.it
ADMIN_PASSWORD=<password del primo accesso>
```

`FLYWAY_PASSWORD` è la password di una **seconda utenza del database**. Il
gestionale ne usa due, con poteri diversi: `gestionale` può solo leggere e
scrivere righe, `gestionale_flyway` può anche cambiare lo schema ed è usata
solo da Flyway, una volta per avvio. Così il processo raggiungibile dalla rete
— quello che esegue SQL costruito a partire da ciò che scrivono gli utenti —
non ha il privilegio di cancellare le tabelle. Nascono entrambe al primo
avvio, da `db-init/10-utenze.sh`. Nessuno la digita mai: è solo un segreto che
compose passa ai container.

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

**Ma sono inerti solo finché quel database resta in piedi.** Il giorno in cui
ricrei il volume — per rifare il collaudo, o per passare dal collaudo al
rilascio vero — la tabella `utente` torna vuota e queste due righe tornano a
essere l'unica porta d'ingresso: vanno rimesse **prima** di riavviare, o il
gestionale riparte senza nessun utente e non te lo dice (§9).

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
cancellato una colonna l'ha cancellata. Un ritorno indietro vero non è una
cosa che questo file sappia fare: la copia dei dati da cui ripartire è in
mano all'amministratore di sistema, e un aggiornamento che porta migrazioni
distruttive si concorda con lui **prima** del `git pull`, non dopo.

---

## 7. Quello che questa procedura non copre

Non sono dettagli: sono decisioni ancora aperte.

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
| `Access denied for user 'gestionale_flyway'`, e il backend in crash loop | Manca l'utenza delle migrazioni, o la sua password non è più quella del `.env`. Flyway apre quella connessione a **ogni** avvio, anche solo per validare i checksum, quindi il backend non parte affatto. Succede se il volume è stato creato prima dell'introduzione delle due utenze, o se il database è stato rimpiazzato con uno che non le aveva: i comandi per crearla sono nel §9. |
| `CREATE command denied to user 'gestionale'` nel log del backend | Hibernate sta provando a modificare lo schema, cioè `ddl-auto` non è più `validate`. L'utenza applicativa non ha i privilegi DDL **di proposito**: l'errore è la difesa che funziona, non il problema. Controlla di non avere una `SPRING_JPA_HIBERNATE_DDL_AUTO` nell'ambiente. |
| Il login risponde 200 ma ogni pagina richiede di nuovo l'accesso | `COOKIE_SECURE` non è `"false"` e non c'è HTTPS. Controlla con `docker compose -f compose.prod.yml exec backend env \| grep COOKIE`. |
| Il backend resta `unhealthy` | `docker compose -f compose.prod.yml logs backend`. Se parla di `Validate failed` è lo schema divergente (passo 6); se muore senza un errore chiaro durante il build, è la RAM (passo 0). |
| `Bind for 0.0.0.0:80 failed: port is already allocated` | Qualcosa occupa la 80 (passo 0). **Risolvila subito**: se la lasci lì, il sintomo si trasforma in quello della riga qui sotto e non assomiglia più a un problema di porte. |
| Frontend in `Restarting` con `nginx: [emerg] host not found in upstream "backend"`, **mentre il backend è `healthy`** | Non è il backend. Vedi «Il frontend che accusa il backend» qui sotto. |
| `Public Key Retrieval is not allowed` | Qualcuno ha aggiunto `useSSL=false` al `DB_URL`. Va tolto: MySQL 8.4 autentica con `caching_sha2_password` e senza TLS pretenderebbe anche `allowPublicKeyRetrieval=true`. Lasciando decidere al driver si ottiene `sslMode=PREFERRED`, che funziona. |
| Dopo un reboot del server non risponde niente | `systemctl is-enabled docker` (passo 0). |
| Tutto `healthy`, la pagina di login si apre, ma **nessuna credenziale funziona** | Database vuoto e nessun utente creato. Su un volume nuovo `ADMIN_EMAIL` e `ADMIN_PASSWORD` nel `.env` sono l'unica porta d'ingresso: se le hai svuotate al passo 5 e poi hai ricreato il volume, vanno rimesse (§9). Lo conferma il log: `docker compose -f compose.prod.yml logs backend \| grep AmministratoreIniziale`. |

### Il frontend che accusa il backend

Vale una sezione a sé perché è costato un'ora, e perché nessuno dei tre
indizi punta nella direzione giusta.

Il sintomo: il frontend cicla in `Restarting` e il log dice
`host not found in upstream "backend"`, ma `docker compose ps` mostra il
backend `healthy`. Sembra un problema di rete fra i container, o di nginx.

Non è nessuno dei due. **Il container del frontend non ha nessuna rete**, e
quindi non può risolvere il nome `backend`. È il resto di un `up` fallito
prima: quando `docker compose up` si schianta su
`Bind for 0.0.0.0:80 failed`, il container è **già stato creato** e la
procedura si interrompe al passo «set up container networking». Rimane un
guscio senza rete, e `restart: unless-stopped` lo riavvia all'infinito invece
di ricrearlo. L'errore vero — il conflitto di porta — è passato una volta
sola, molto prima.

Come riconoscerlo in trenta secondi:

```bash
docker inspect gestionale-frontend-1 \
  --format 'creato: {{.Created}}  riavvii: {{.RestartCount}}'
docker inspect gestionale-frontend-1 \
  --format 'reti: {{range $k,$v := .NetworkSettings.Networks}}{{$k}} {{end}}'
```

Se `reti:` è **vuoto**, è questo. Lo conferma anche la data di creazione, che
risalirà al tentativo fallito e non all'ultimo `up`.

Il rimedio, e qui sta la seconda trappola: **`up -d --build` non basta.** Con
il build in cache l'immagine non cambia, compose non ha ragione di rifare il
container, scrive `Started` e sembra aver funzionato — mentre ha solo
riavviato lo stesso guscio. Serve dirglielo:

```bash
docker compose -f compose.prod.yml up -d --force-recreate frontend
```

Oppure ripartire pulito da tutti e tre, che il volume non lo tocca:

```bash
docker compose -f compose.prod.yml down
docker compose -f compose.prod.yml up -d
```

**E la terza: non guardare lo stato, guarda la porta.**

```bash
docker compose -f compose.prod.yml ps
```

Il frontend deve dire `0.0.0.0:80->80/tcp`. Se dice soltanto `80/tcp` è di
nuovo il container rotto: `80/tcp` è la `EXPOSE` del Dockerfile, una
dichiarazione d'intenti, non una porta pubblicata sull'host. Un container può
stare `Up` e perfino `healthy` per qualche secondo senza essere raggiungibile
da nessuno.

La prova che il giro funziona davvero non è nessuna di queste colonne, sono
due richieste:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost/
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  http://localhost/api/autenticazione/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"nessuno@esempio.it","password":"sbagliata"}'
```

`200` e `401`. Il `401` vale più del `200`: dice che nginx ha inoltrato al
backend, che il backend ha interrogato il database e che ha risposto di no.
Tutta la catena, con una password sbagliata di proposito.

### Due comandi da conoscere prima di averne bisogno

```bash
docker compose -f compose.prod.yml down          # ferma tutto, i dati restano
```

E uno da non battere mai sul server, perché **cancella il database**:

```bash
docker compose -f compose.prod.yml down -v       # il -v distrugge il volume
```

---

## 9. Ripartire da zero (cancella il database)

Serve in collaudo, per rifare l'intera procedura su un database vergine, e
serve al passaggio dal collaudo al rilascio vero: un volume usato per le
prove contiene utenze finte con password di prova, e riusarlo significa
portarsele in produzione senza accorgersene — perché
`AmministratoreIniziale` agisce **solo** su tabella `utente` vuota, quindi su
un volume già popolato non fa niente e non lo dice.

**Questo cancella tutti i dati.** È una procedura da collaudo. Su un server
di produzione, con dentro il lavoro di qualcuno, non si batte senza averne
prima parlato con l'amministratore di sistema: la copia da cui si ripartirebbe
è sua.

### Prima: rimettere le credenziali nel `.env`

È il passo che si dimentica, e il motivo per cui ha una sezione sua.

Al passo 5 hai **svuotato** `ADMIN_EMAIL` e `ADMIN_PASSWORD`, giustamente: su
un database già popolato sono inerti, e tenerle piene vuol dire lasciare una
password valida dentro `docker inspect` e in `/proc/<pid>/environ`.

Ma su un volume nuovo la tabella `utente` torna vuota, e quelle due righe
tornano a essere **l'unica porta d'ingresso**. Se le lasci vuote, il
gestionale riparte senza nessun utente — e il guasto non somiglia a un
guasto: `docker compose up` riesce, tutti e tre i container diventano
`healthy`, la pagina di login si apre normalmente, e dentro non entra
nessuno.

```bash
cd /opt/gestionale
grep -E '^(ADMIN_EMAIL|ADMIN_PASSWORD|FLYWAY_PASSWORD)=' .env   # NON devono finire con '='
nano .env                                                        # rimettile, se sono vuote
```

La password deve rispettare le regole di sempre: almeno 8 caratteri, almeno
una cifra, almeno un carattere non alfanumerico; e niente `$`, che compose
interpreta come riferimento a una variabile.

### Poi: la sequenza

```bash
cd /opt/gestionale
docker compose -f compose.prod.yml down            # senza -v: rimuove i container
docker volume rm gestionale_dati-mysql             # QUI spariscono i dati
docker compose -f compose.prod.yml up -d
docker compose -f compose.prod.yml logs -f backend
```

Il nome del volume è `<nome-progetto>_dati-mysql`, e il nome del progetto lo
deduce compose dalla cartella: da `/opt/gestionale` è `gestionale`. Se non ne
sei certo, chiedilo invece di indovinare:

```bash
docker volume ls | grep dati-mysql
```

Cancellando il volume si cancellano anche **le due utenze del database**, che
vivono nel datadir insieme ai dati. Non è un problema: nascono di nuovo da
`db-init/10-utenze.sh`, che l'entrypoint esegue proprio perché il datadir è
vuoto. È il motivo per cui ricreare il volume è la strada semplice e
aggiungere le utenze a un database esistente è quella scomoda — su un datadir
già popolato quello script **non viene letto**, e le utenze vanno create a
mano:

```bash
docker compose -f compose.prod.yml exec db \
  sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot' <<'SQL'
REVOKE IF EXISTS ALL PRIVILEGES ON `gestionale`.* FROM 'gestionale'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `gestionale`.* TO 'gestionale'@'%';
CREATE USER IF NOT EXISTS 'gestionale_flyway'@'%' IDENTIFIED BY '<FLYWAY_PASSWORD del .env>';
GRANT SELECT, INSERT, UPDATE, DELETE,
      CREATE, DROP, ALTER, INDEX, REFERENCES,
      CREATE VIEW, SHOW VIEW
    ON `gestionale`.* TO 'gestionale_flyway'@'%';
FLUSH PRIVILEGES;
SQL
```

### Infine: che sia ripartito davvero

Nel log del **database** cerca la riga dello script di init:

```
[10-utenze] Fatto: 'gestionale' solo DML, 'gestionale_flyway' anche DDL.
```

Nel log del **backend** cerca **tre** cose, nell'ordine:

1. `Successfully applied 24 migrations` — Flyway ha ricostruito lo schema;
2. nessuna riga con `dati_sviluppo` — i dati di prova non sono entrati;
3. `Creato l'amministratore iniziale <email>`.

Se al posto della terza leggi un `WARN` che dice che nessuno può accedere, è
il `.env`: o le due righe sono ancora vuote, o la password non ha passato le
regole. Si corregge e si ricrea il container, e funziona perché la tabella è
ancora vuota:

```bash
docker compose -f compose.prod.yml up -d --force-recreate backend
```

Da qui si riprende dal **passo 5**: primo accesso, cambio password, creazione
delle utenze vere. Questa volta creane **due** amministratori attivi prima di
disattivare quello iniziale: nessuno può disattivare se stesso, quindi con un
solo amministratore perdere la password significa rientrare dal database.
