# Consegna: dall'accesso SSH al gestionale in uso

Questo file è la sequenza del **giorno della consegna**, da seguire dall'alto
verso il basso senza salti. Dice *cosa* fare e in *che ordine*.

[RILASCIO.md](RILASCIO.md) dice *perché*, e contiene la diagnostica quando
qualcosa non va. Quando qui c'è un rimando a «§8» o «§9», è a quel file.

**Si presume**: un server Debian o Ubuntu, accesso SSH con `sudo`, Docker e
il plugin compose già installati, e una ventina di utenti. Se il server è
Windows, i comandi di questo file non valgono: cambiano l'installazione, i
percorsi e l'avvio automatico.

Tempo realistico: **due ore**, di cui la metà di attesa. Non farlo di venerdì
pomeriggio.

---

## Fase 0 — Prima di collegarti al server

Sul **tuo PC**. Sono i passi che, se salti, ti bloccano a metà strada con la
sessione SSH già aperta.

### 0.1 Portare il codice sul ramo che il server clonerà

Il server clona da GitHub, quindi quello che c'è su GitHub è quello che va in
produzione. Oggi `origin/deploy` **non contiene** le ultime modifiche
(separazione delle utenze del database): si fermerebbe al
primo avvio, perché `compose.prod.yml` chiederebbe variabili che la tua
versione di `deploy` non conosce — o peggio, partirebbe senza la separazione.

- [ ] Committa la modifica in sospeso a `.github/workflows/ci.yml` (i trigger
      per `develop` e `deploy`). Senza, la CI non gira su questi rami e il
      verde che guarderai prima di unire non esiste.
- [ ] Apri la PR da `develop` verso `deploy` e **aspetta che la CI sia verde**.
      Il job che conta è `profilo-produzione`: avvia il jar con il profilo
      `prod`, crea le due utenze del database e pretende un login riuscito. È
      l'unica prova automatica che la configurazione di produzione sta in
      piedi.
- [ ] Unisci la PR.
- [ ] Verifica che ci sia davvero tutto:

```bash
git fetch origin
git log --oneline -3 origin/deploy
git ls-tree --name-only origin/deploy -- db-init CONSEGNA.md
```

Devono comparire `db-init/` e questo file. Se manca `db-init/`, il
server non creerà le due utenze.

### 0.2 Decidere i cinque segreti, e dove li conservi

Il `.env` del server è l'unico posto dove vivranno. **Se lo perdi, perdi
l'accesso al database.** Mettili in un gestore di password aziendale prima di
digitarli, non dopo.

```bash
for i in 1 2 3; do openssl rand -base64 24 | tr -d '/+='; done
```

Il `tr` non è un vezzo: `docker compose` interpreta il `.env`, e un `$` nella
password diventa un riferimento a una variabile. La password arriva troncata e
l'errore che vedi è `Access denied for user 'gestionale'`, che ti manda a
cercare in MySQL per un'ora. Usa solo lettere, cifre e `_ - . ! %`.

- [ ] `DB_PASSWORD` — il backend
- [ ] `MYSQL_ROOT_PASSWORD` — healthcheck e manutenzione del database
- [ ] `FLYWAY_PASSWORD` — le migrazioni
- [ ] `ADMIN_EMAIL` — **un indirizzo vero**, tuo. Finisce nel database al
      primo avvio e dal secondo non si cambia più: correggerla dopo vuol dire
      creare un'altra utenza e disattivare questa.
- [ ] `ADMIN_PASSWORD` — usa e getta, la cambi dopo dieci minuti. Deve avere
      **almeno 8 caratteri, almeno una cifra, almeno un carattere non
      alfanumerico**. Se non li ha, l'utente **non viene creato** e il
      container parte comunque sano: la pagina di login si apre e dentro non
      entra nessuno.

### 0.3 Cosa chiedere a chi gestisce la rete

Non sono porte. Mandalo per iscritto, così non lo ridici tre volte:

> Per il gestionale sul server `<nome>` mi servono:
> 1. un **IP fisso** o una riserva DHCP per quel server;
> 2. un **record DNS** `gestionale.intranet.<azienda>.it` che punti a quell'IP;
> 3. se esiste una **CA interna**, un certificato per quel nome (serve dopo,
>    non oggi).
>
> Non serve aprire porte né NAT: l'applicazione ascolta sulla 80 del server e
> i client sono sulla stessa rete. Il database **non** va esposto.

Senza il punto 2 si parte comunque, con l'indirizzo numerico. Ma un gestionale
che la gente raggiunge con un IP è un gestionale che smetterà di usare quando
l'IP cambia.

---

## Fase 1 — Primo accesso: ricognizione

```bash
ssh <utente>@<server>
```

Cinque verifiche. Due minuti qui evitano di scoprire un problema a metà del
primo avvio, quando il database esiste già e tornare indietro costa.

```bash
docker --version
docker compose version          # deve dire v2.x, non 1.x
systemctl is-enabled docker     # deve dire "enabled"
```

- [ ] Se `is-enabled` risponde `disabled`: `sudo systemctl enable --now docker`.
      È il passo che si salta sempre. `restart: unless-stopped` riavvia i
      **container**, non il demone: senza questo, dopo il primo reboot del
      server il gestionale resta giù e nessuno capisce perché.

```bash
docker ps --filter 'publish=80' --format '{{.Names}}\t{{.Ports}}'
ss -lntp | grep ':80 ' || echo 'nessun processo locale sulla 80'
```

- [ ] Entrambi **vuoti**. Vanno chiesti a entrambi perché `ss` interroga il
      kernel locale e `docker ps` il demone: su un server con più di uno stack
      compose, il container che tiene la porta lo vede solo il secondo.
- [ ] Se risponde un container: `docker stop <nome>`. Se è un nginx o un
      Apache di sistema: `sudo systemctl disable --now nginx`. Se invece
      quel servizio deve restare, cambia la porta del gestionale — in
      `compose.prod.yml`, servizio `frontend`, `"80:80"` diventa `"8080:80"`,
      cambiando **solo** il numero di sinistra.

```bash
free -m                         # almeno ~2 GB disponibili
df -h /var/lib/docker           # almeno ~5 GB liberi
```

- [ ] Il server **compila** il progetto: Maven e `npm ci` girano in container.
      Sotto i 2 GB di RAM il build del backend viene ucciso dal kernel e
      l'errore parla di Maven, non di memoria.

```bash
timedatectl | grep -i 'time zone'
```

- [ ] Non è bloccante — i container impostano `TZ=Europe/Rome` da sé — ma un
      server su UTC rende illeggibili i log e gli orari che ci leggerai dentro.

---

## Fase 2 — Prendere il codice

```bash
cd /opt
sudo git clone -b deploy https://github.com/prettyboisosaa/GestionaleAziendale.git gestionale
sudo chown -R "$USER":"$USER" /opt/gestionale
cd /opt/gestionale
```

- [ ] `-b deploy`: il ramo protetto, quello in cui si entra solo per PR con la
      CI verde. **Non** `main`, che nel flusso attuale non è più il
      riferimento.
- [ ] Il repository è pubblico, quindi non servono credenziali.
- [ ] Il `chown` serve perché i passi dopo scrivono il `.env` senza `sudo`.

**Tutti i comandi che seguono partono da `/opt/gestionale`.** `docker compose`
cerca il `.env` nella **cartella corrente**, non accanto al file passato con
`-f`: lanciarlo dalla home dà un errore su `DB_PASSWORD` mancante con il
`.env` che esiste benissimo due cartelle più in là.

---

## Fase 3 — Il file `.env`

```bash
cp .env.example .env
chmod 600 .env
nano .env
```

- [ ] Il `chmod` non è cerimoniale: il file nasce leggibile da tutti gli
      utenti del server, e dentro ci sono tre password del database.
- [ ] Riempi i **cinque** valori della Fase 0.2. Nessuna riga deve restare
      vuota a questo giro.
- [ ] Rileggi il file prima di chiuderlo. Nessun `$`, nessuno spazio dopo il
      `=`, nessuna virgoletta attorno ai valori.

```bash
grep -c '=' .env                # deve dire 5
grep -E '^[A-Z_]+=$' .env       # deve essere VUOTO: nessuna chiave senza valore
```

---

## Fase 4 — Il primo avvio

```bash
docker compose -f compose.prod.yml up -d --build
```

La prima volta sono diversi minuti: scarica MySQL, nginx, Maven e Node, poi
compila. Se una variabile manca, compose si ferma **prima** di avviare
qualunque cosa e dice quale.

Poi, in quest'ordine:

```bash
docker compose -f compose.prod.yml logs db | grep 10-utenze
```

- [ ] Deve dire `[10-utenze] Fatto: 'gestionale' solo DML, 'gestionale_flyway'
      anche DDL.` Se non c'è, le due utenze non esistono e il backend non
      partirà. Gli script di init girano **solo** su datadir vuoto: se stai
      rifacendo un avvio su un volume già esistente, vedi §9.

```bash
docker compose -f compose.prod.yml logs -f backend
```

Tre righe, in quest'ordine. Esci con `Ctrl+C` quando le hai viste.

- [ ] `Successfully applied 24 migrations to schema 'gestionale', now at
      version v24`.
- [ ] **Nessuna** riga contenente `dati_sviluppo`. Se compare, il profilo di
      produzione non è attivo e nel database stanno entrando sette utenti con
      una password nota: fermati, `down -v`, e capisci perché
      `SPRING_PROFILES_ACTIVE: prod` non è arrivato.
- [ ] `Creato l'amministratore iniziale <la tua email>`. Se invece leggi un
      `WARN` che dice che nessuno può accedere, la password del `.env` non ha
      passato le regole: correggila e
      `docker compose -f compose.prod.yml up -d --force-recreate backend`.
      Funziona perché la tabella utente è ancora vuota.

---

## Fase 5 — Verifica tecnica, prima di dirlo a qualcuno

```bash
docker compose -f compose.prod.yml ps
```

- [ ] Tutti e tre `healthy` (il backend può metterci due minuti: ha
      `--start-period=180s`).
- [ ] **Guarda la colonna delle porte, non lo stato.** Il frontend deve dire
      `0.0.0.0:80->80/tcp`. Se dice soltanto `80/tcp`, è un container creato a
      metà da un `up` fallito: `80/tcp` è la `EXPOSE` del Dockerfile, una
      dichiarazione d'intenti, non una porta pubblicata. Rimedio:
      `up -d --force-recreate frontend` — non `restart`, e non `--build`, che
      con la cache non ricrea niente.
- [ ] Il database e il backend **non** devono avere porte pubblicate. È quella
      la loro protezione.

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost/
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  http://localhost/api/autenticazione/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"nessuno@esempio.it","password":"sbagliata"}'
```

- [ ] `200` e `401`. **Il 401 vale più del 200**: dice che nginx ha inoltrato
      al backend, che il backend ha interrogato il database e che ha risposto
      di no. È la catena intera, provata con una password sbagliata di
      proposito.

### Il firewall: probabilmente non c'è niente da fare

```bash
command -v ufw nft iptables firewall-cmd || echo 'nessun firewall installato'
```

- [ ] `command not found` **non è un errore**: su Debian da netinst `ufw`
      spesso non c'è, e quindi non c'è niente da aprire.
- [ ] Se `ufw` c'è ed è `active`: `sudo ufw allow 80/tcp`.
- [ ] Sappi però che **non è ufw a decidere** se la 80 è raggiungibile: Docker
      pubblica le porte con una regola di DNAT e il traffico non passa da
      `INPUT`, dove stanno le regole di ufw. Arriva a destinazione anche con
      `ufw default deny incoming`. Non fidarti di `ufw status` per sapere cosa
      è esposto: la domanda giusta è `docker ps --format '{{.Ports}}'`.

### Da un'altra macchina

- [ ] Apri `http://<ip-del-server>` (o il nome DNS, se è già attivo) da un
      altro PC della rete. Se non risponde ma `curl` in locale sì, il problema
      è fra le due macchine: firewall di rete, isolamento dei client, VLAN.

---

## Fase 6 — Le utenze: dieci minuti al browser

Dal **browser**, non in SSH. È la parte che non si può automatizzare e quella
in cui si sbaglia l'ordine.

- [ ] **Entra** con `ADMIN_EMAIL` e `ADMIN_PASSWORD`.
- [ ] **Cambia subito la password** da «Profilo». Quella del `.env` è passata
      per un file di testo e per il log di compose: considerala bruciata.
- [ ] **Crea le utenze vere.** Almeno **due** amministratori, una è la tua.
      Due e non una: nessuno può disattivare se stesso, quindi con un solo
      amministratore non c'è nessuno che possa riaprire un'utenza chiusa per
      sbaglio, e perdere quella password significa rientrare dal database.
      Serve password di **almeno 10** caratteri: dall'interfaccia la regola è
      più severa che per l'admin iniziale.
- [ ] **Esci e rientra** con la tua utenza.
- [ ] **Disattiva l'admin iniziale** dall'elenco utenti. La riga non viene
      cancellata — resta col badge grigio, così lo storico di chi ha creato
      cosa continua a leggersi — e la sua sessione cade alla richiesta
      successiva.

Poi torna in SSH e togli i segreti dall'ambiente del processo:

```bash
nano .env      # svuota ADMIN_EMAIL= e ADMIN_PASSWORD=, lasciando le righe
docker compose -f compose.prod.yml up -d
docker compose -f compose.prod.yml exec backend env | grep AMMINISTRATORE
```

- [ ] Le due righe devono comparire **con il valore vuoto**.
- [ ] `FLYWAY_PASSWORD`, `DB_PASSWORD` e `MYSQL_ROOT_PASSWORD` **restano**:
      quelle servono a ogni avvio.
- [ ] ⚠️ Se un domani ricrei il volume, `ADMIN_EMAIL` e `ADMIN_PASSWORD`
      **vanno rimesse**: su tabella utente vuota tornano a essere l'unica
      porta d'ingresso. Vedi §9.

---

## Fase 7 — La consegna alle persone

- [ ] Manda l'indirizzo: `http://gestionale.intranet.<azienda>.it` (o l'IP, se
      il DNS non c'è ancora).
- [ ] Manda le credenziali **a ciascuno separatamente**, non in un messaggio
      di gruppo. Chiedi di cambiare la password al primo accesso.
- [ ] Di' che un riavvio del backend **fa ricadere il login a tutti**: le
      sessioni vivono nella memoria del processo, non in un archivio
      condiviso. Con venti persone è accettabile, ma se non lo dici viene
      segnalato come guasto.
- [ ] Di' a chi gestisce la rete che il server ora è in servizio, così non
      finisce in una manutenzione a sorpresa.

### Cosa non promettere

Due limiti reali di questa installazione. Scriverli adesso è più onesto che
spiegarli dopo un incidente.

- **Il traffico è in HTTP**, non cifrato. Chi può ascoltare la rete può
  prendere il cookie di sessione, e con quello diventare l'utente: non gli
  serve nemmeno la password. Il passo successivo è un certificato e
  `COOKIE_SECURE: "true"`.
- **La copia dei dati non la fa questa installazione.** Il gestionale tiene i
  suoi dati nel volume `dati-mysql` e basta: metterli al sicuro, e provare che
  si rimettano dentro, è lavoro dell'amministratore di sistema. Va chiesto a
  lui prima che nel database ci sia il lavoro di qualcuno, non dopo.

---

## Dopo, ogni volta che aggiorni

```bash
cd /opt/gestionale
git pull
docker compose -f compose.prod.yml up -d --build
docker compose -f compose.prod.yml logs -f backend
```

- [ ] `git checkout` del commit precedente rimette il codice di ieri, non lo
      schema di ieri: una migrazione che ha cancellato una colonna l'ha
      cancellata. Un aggiornamento con migrazioni distruttive si concorda con
      l'amministratore di sistema prima di lanciarlo.
- [ ] Il `.env` non viene toccato da `git pull`: non è versionato.
- [ ] In produzione `ddl-auto=validate`: se le entità e le tabelle divergono il
      backend **non parte** e lo dice. È il comportamento voluto, ma vuol dire
      che un aggiornamento a cui manca una migrazione si manifesta come
      backend giù, con il frontend che continua a servire la pagina.

---

## Cose da non fare, mai

| | Perché |
|---|---|
| `docker compose -f compose.prod.yml down -v` | Il `-v` **cancella il database**. È a due caratteri da un comando che si usa davvero. |
| Pubblicare la 3306 o la 8080 | Non sono pubblicate di proposito. È la protezione del database, e nessuna regola di firewall la sostituisce — Docker le scavalca. |
| Aggiungere phpMyAdmin | In sviluppo serve a guardare dentro il database; qui è una porta aperta sui dati di tutti. Per un'occhiata: `docker compose -f compose.prod.yml exec db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot gestionale'`. |
| Committare il `.env` | È in `.gitignore` e deve restarci. Contiene le tre password del database. |
| Mettere `ddl-auto=update` «per fare prima» | L'utenza applicativa non ha i privilegi DDL: otterresti un errore all'avvio. L'errore è la difesa che funziona. |
| Toccare una migrazione già applicata | Flyway ne verifica i checksum e il backend non parte più. Le correzioni si fanno con una migrazione nuova. |

---

## Se qualcosa va storto

La tabella dei sintomi è in **§8 di RILASCIO.md**, con la causa quasi sempre
giusta per ciascuno. Le tre trappole che costano un'ora e non hanno nessun
indizio che punti nella direzione corretta:

1. **`required variable DB_PASSWORD is missing a value`** con il `.env` che
   esiste — non sei in `/opt/gestionale`.
2. **Frontend in `Restarting` con `host not found in upstream "backend"`**
   mentre il backend è `healthy` — non è il backend: è un container senza rete,
   avanzo di un `up` fallito. Serve `--force-recreate`.
3. **Tutto `healthy`, la pagina di login si apre, nessuna credenziale
   funziona** — database vuoto e nessun utente: `ADMIN_EMAIL` e
   `ADMIN_PASSWORD` svuotate su un volume nuovo.

### Tornare indietro

```bash
docker compose -f compose.prod.yml down          # ferma tutto, i dati restano
```

Senza `-v`. Riparti con `up -d` quando hai capito. Se devi tornare a una
versione precedente del codice, ricorda che lo schema **non** torna indietro
con lui: a quel punto serve l'amministratore di sistema.
