# Gestionale Anitia

[![ci](https://github.com/prettyboisosaa/GestionaleAziendale/actions/workflows/ci.yml/badge.svg)](https://github.com/prettyboisosaa/GestionaleAziendale/actions/workflows/ci.yml)

Gestionale interno per l'assegnazione di lavori e task ai dipendenti, con
gestione del **monte ore**: ogni progetto ha un budget di ore che si distribuisce
fra le sue task, i dipendenti registrano le ore lavorate giorno per giorno, e
quando le ore non bastano c'è un percorso tracciato per ottenerne altre.

- **Backend** — Spring Boot 4.1 su Java 25, MySQL 8.4, Flyway, Spring Security
  con autenticazione a sessione.
- **Frontend** — React 19 + TypeScript + Vite, senza librerie di UI.
- **Database** — MySQL in Docker, con phpMyAdmin per guardarci dentro.

---

## Avvio rapido

Servono **Docker Desktop** e un **JDK 25 o superiore**. Node 20+ per il
frontend.

```bash
# 1. Il backend (dalla radice del progetto)
cd backend && ./mvnw spring-boot:run

# 2. Il frontend, in un altro terminale
cd frontend && npm install && npm run dev
```

Il database non va avviato a mano: all'avvio il backend tira su da solo i
servizi del `docker-compose.yml` e **aspetta che MySQL sia sano** prima di
connettersi (`spring.docker.compose.*` in `application.properties`). Basta che
Docker Desktop sia in esecuzione.

Su Windows, se `./mvnw` si lamenta di `JAVA_HOME`, c'è `backend\avvia.ps1`: cerca
un JDK, lo imposta per la durata del comando e avvia il backend.

```powershell
cd backend; .\avvia.ps1
```

L'applicazione è su **http://localhost:5173**. Vite inoltra `/api` al backend
sulla 8080, quindi non serve configurare CORS.

| servizio | indirizzo |
|---|---|
| frontend (dev) | http://localhost:5173 |
| backend | http://localhost:8080 |
| phpMyAdmin | http://localhost:8081 |
| MySQL | localhost:3306 |

### Se il backend non parte

- **`Unable to obtain connection from database: Communications link failure`**
  — il database non risponde. Flyway è la prima cosa che tocca il database, e
  se non ottiene la connessione il contesto Spring fallisce. Da quando il
  backend avvia il compose da sé questo non dovrebbe più capitare: se capita,
  Docker Desktop non è partito. Per controllare il database a mano:

  ```bash
  docker compose up -d db
  docker inspect -f '{{.State.Health.Status}}' gestionale-db
  ```

- **`Detected failed migration to version N`** — una migrazione si è rotta a
  metà, e Flyway blocca l'avvio finché la cosa non è sistemata. Il motivo vero
  sta nel log del tentativo precedente, non in questo messaggio. Lo stato si
  legge così:

  ```bash
  docker exec gestionale-db mysql -uroot -proot_dev -D gestionale \
    -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
  ```

  La riga con `success = 0` è quella fallita. Attenzione: le migrazioni che
  l'hanno preceduta **sono già applicate**, quindi il database resta a metà
  strada — schema nuovo e codice che se lo aspetta diverso. Su un database di
  sviluppo la via più pulita è ricostruirlo (vedi «ripartire da un database
  pulito» più sotto); in alternativa si corregge la migrazione e si cancella
  la riga fallita dallo storico, così Flyway la rifà.

- **500 su operazioni che prima funzionavano, con `Check constraint ... is
  violated` nei log** — il database è a uno schema più avanzato del codice che
  risponde. Il caso tipico è un container `gestionale-backend` costruito prima
  dell'ultima migrazione: `docker compose --profile app up -d --build backend`,
  dove il `--build` non è opzionale, oppure lo si spegne e si lavora dall'IDE.

- **`Port 8080 was already in use`** — c'è già un'istanza del backend in
  esecuzione, spesso avviata da IntelliJ o rimasta appesa. Su Windows:
  `netstat -ano | findstr :8080` per trovare il PID, poi `taskkill /PID <pid> /F`.
  Attenzione anche al container `gestionale-backend`: se è su occupa la 8080.
  Si spegne con `docker compose --profile app stop backend`.
- **`JAVA_HOME is not defined correctly`** — succede solo da riga di comando;
  IntelliJ usa l'SDK del progetto e non ne ha bisogno. Si risolve con
  `backend\avvia.ps1`, oppure impostando la variabile sul percorso del JDK, per
  esempio `C:\Users\<utente>\.jdks\openjdk-27.0.1.0.3`.
- **Container di un progetto compose diverso** — se i container esistono ma
  appartengono a un altro progetto (tipico dopo aver rinominato la cartella),
  `docker compose up` va in conflitto sul nome: `Conflict. The container name
  "/gestionale-db" is already in use`. Si controlla a quale progetto
  appartengono, e si rimuovono:

  ```bash
  docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' gestionale-db
  docker rm gestionale-db gestionale-phpmyadmin
  ```

  I dati di sviluppo vivono nel bind mount `.data/mysql` della cartella da cui
  il container è stato creato: rimuovere il container non li cancella, ma un
  container ricreato da un'altra cartella parte da un database vuoto (che
  Flyway ripopola).

### Tutto in Docker

`docker-compose.yml` definisce anche `backend` e `frontend`, sotto il profilo
`app`. Un semplice `docker compose up -d` avvia **solo** `db` e `phpmyadmin`,
cioè l'infrastruttura che serve al backend lanciato in locale: così nessun
container litiga con l'applicazione locale sulla porta 8080. Per lo stack
intero, senza strumenti locali:

```bash
docker compose --profile app up -d --build
```

In quel caso il frontend è sulla porta 80. Per lo sviluppo quotidiano conviene
il percorso sopra, perché dà ricompilazione automatica su entrambi i lati.

---

## Utenti di sviluppo

I dati di prova stanno in `backend/src/main/resources/db/dev/` e vengono
caricati da Flyway come migrazione ripetibile. **Password di tutti:
`Password1!`**

Il seed contiene **solo le persone**: utenti, ruoli e team. Nessun lavoro,
nessuna task, nessuna ora. I lavori fittizi sono stati rimossi il 5 novembre
2026, quando il gestionale ha iniziato a raccogliere dati veri: siccome la
migrazione è ripetibile, lasciarli avrebbe significato vederli ricomparire a
ogni modifica del file, mescolati a quelli reali.

| email | ruolo | a cosa serve |
|---|---|---|
| `admin@gestionale.local` | Amministratore | tutti i permessi: crea lavori, fissa il monte ore, decide le richieste di ore |
| `laura.bianchi@gestionale.local` | Project Manager | nel suo team: Verdi e Russo |
| `marco.rossi@gestionale.local` | Project Manager | nel suo team: Russo e Colombo |
| `giulia.verdi@gestionale.local` | Dipendente | registra le ore sulle task che le vengono assegnate |
| `luca.russo@gestionale.local` | Dipendente | nel team di entrambi i responsabili |
| `sara.colombo@gestionale.local` | Dipendente | — |
| `paolo.ferrari@gestionale.local` | Dipendente | **disattivato**, per provare che un utente non attivo non ha nessun permesso |

I team contano: `TASK_GESTISCI_PROPRI` lascia assegnare le task solo ai
dipendenti del proprio team, quindi un responsabile non vede tutti gli utenti
nel menu di assegnazione.

> ⚠️ **Le credenziali nel repository sono solo per lo sviluppo.**
> `docker-compose.yml` e `application.properties` contengono in chiaro
> `gestionale_dev` e `root_dev`: sono le password del container MySQL locale, e
> stanno nel repository perché `docker compose up` funzioni appena clonato.
>
> Per il server vero **non si usa `docker-compose.yml`**, nemmeno con
> `--profile app`: quel percorso non attiva il profilo `prod`, quindi carica i
> dati di prova e lascia a Hibernate la riscrittura dello schema. Si usa
> `compose.prod.yml`, con un `.env` accanto — vedi `.env.example`.

---

## In produzione

**La procedura completa è in [RILASCIO.md](RILASCIO.md)**: l'SSH sul server,
il `.env`, il primo avvio, il giro delle utenze, gli aggiornamenti e cosa
guardare quando qualcosa non va. Quello che segue è il riassunto.

```bash
cp .env.example .env            # poi si riempiono i valori
docker compose -f compose.prod.yml up -d --build
```

L'applicazione è sulla porta 80 del server. Le differenze rispetto allo
sviluppo le dichiara `application-prod.properties`, riga per riga con il
motivo; quelle che contano di più:

| | sviluppo | produzione |
|---|---|---|
| dati di prova (`db/dev`) | caricati | **esclusi**, e nemmeno presenti nel jar |
| schema | `ddl-auto=update` | `validate`: non parte se le entità divergono |
| primo utente | i sette del seed | creato da `GESTIONALE_AMMINISTRATOREINIZIALE_*`, solo su database vuoto |
| Swagger | aperto | spento |
| cookie di sessione | normale | `${COOKIE_SECURE:true}`: protetto se non si dice altro |
| utenze del database | una, con tutti i privilegi sullo schema | **due**: l'applicazione solo `SELECT/INSERT/UPDATE/DELETE`, le migrazioni anche il DDL |

Le due utenze di produzione nascono con il volume, da `db-init/10-utenze.sh`,
e Flyway usa la sua tramite `spring.flyway.user`. Il motivo è che
`ddl-auto=validate` è una promessa mentre i privilegi sono un vincolo: una
variabile d'ambiente sbagliata rimette Hibernate a `update` — è già successo,
il 9 novembre 2026 — e senza i privilegi DDL quella distrazione diventa un
errore all'avvio invece di uno schema riscritto sotto i dati. In sviluppo
l'utenza resta una sola, perché lì `ddl-auto=update` è il modo di lavorare.

Non protegge i *dati*: con `SELECT` e `UPDATE` si leggono e si falsificano
tutte le ore e le assenze. Protegge lo *schema*.

Il seed di sviluppo non è escluso soltanto dalla configurazione: `pom.xml` lo
toglie dall'artefatto (`maven-jar-plugin`, `db/dev/**`). Prima, l'unica difesa
era la riga `spring.flyway.locations` del profilo prod, e dimenticare
`SPRING_PROFILES_ACTIVE=prod` su un database vuoto seminava sette utenti con
una password nota, uno dei quali amministratore. Una difesa che si disarma per
distrazione non è una difesa.

Il cookie di sessione è una variabile perché il primo rilascio è in **HTTP**:
`compose.prod.yml` imposta `COOKIE_SECURE: "false"`. Con `true` e senza TLS il
login risponde 200 e ogni richiesta successiva è anonima, senza nessun errore
che lo spieghi. Il valore predefinito resta quello protetto, quindi il giorno
che arriva un certificato non si tocca il jar: si toglie quella riga.

Tre cose che `compose.prod.yml` **non** fa, e che restano da decidere:

- **HTTPS.** Finché non c'è, il cookie di sessione viaggia in chiaro: su una
  rete dove qualcuno può ascoltare, rubarlo vale quanto rubare la password.
  Con il certificato vanno insieme `COOKIE_SECURE` a `"true"` e
  `Strict-Transport-Security` in `frontend/nginx.conf`.
- **I backup fuori dal server.** Quelli locali ci sono: `scripts/backup.sh`,
  un timer systemd, 7 giornalieri più 4 settimanali in `/var/backups`, con
  `scripts/ripristina.sh` per rimetterli dentro — installazione e prova di
  ripristino in [RILASCIO.md §7](RILASCIO.md). Vivono però sullo stesso disco
  dei dati: coprono la cancellazione per sbaglio e la migrazione andata male,
  non il guasto hardware. Una copia su un'altra macchina resta da fare.
- **L'avvio automatico.** `restart: unless-stopped` riavvia i container, non
  Docker. Su Linux basta `systemctl enable docker` (da verificare: è il passo
  che si salta); su Windows con Docker Desktop serve una sessione utente
  aperta, quindi dopo un riavvio del server l'applicazione resta giù finché
  qualcuno non fa il login.

---

## Struttura

```
Gestionale/
├── backend/              Spring Boot
│   └── src/main/
│       ├── java/com/anitia/backend/
│       │   ├── autenticazione/   login, logout, "chi sono"
│       │   ├── impostazione/     parametri di sistema e calendario lavorativo
│       │   ├── lavoro/           i progetti, con il loro monte ore
│       │   ├── ore/              budget, allocazioni, movimenti, richieste
│       │   ├── permesso/ ruolo/  catalogo dei permessi e ruoli
│       │   ├── sicurezza/        configurazione di Spring Security
│       │   ├── task/             le task e le assegnazioni
│       │   ├── tempo/            le sessioni di lavoro: avvia, pausa, completa
│       │   └── utente/           utenti e scostamenti di permesso
│       └── resources/db/
│           ├── migration/        V1…V17, lo schema
│           └── dev/              dati di prova, solo per lo sviluppo
├── frontend/             React + Vite
│   └── src/
│       ├── api/          il wrapper di fetch e la traduzione degli errori
│       ├── tempo/ ore/ lavoro/ task/ utente/   un modulo per dominio
│       └── componenti/   barra superiore e rotte protette
├── MD files/             documenti di progetto
├── postman/              collezioni per provare le API a mano
└── docker-compose.yml
```

Nomi di classi, campi, tabelle e classi CSS sono **in italiano**, e i commenti
spiegano il *perché* delle scelte, non il *cosa*. È una convenzione del
progetto: conviene seguirla.

---

## Il modello dei permessi

Cosa può fare un utente dipende **solo dai permessi**, non dal nome del ruolo.

- Il **catalogo dei permessi** è fisso e si estende solo con una migrazione.
- I **ruoli** si creano e si modificano dall'applicazione. Il ruolo *protetto*
  ha sempre tutti i permessi: ogni migrazione che ne aggiunge uno deve
  concederlo anche a lui.
- La tabella `utente_permesso` tiene gli **scostamenti per singola persona**,
  in due direzioni: un permesso aggiunto a uno solo, o tolto a uno solo
  nonostante il suo ruolo glielo dia.
- La vista `permesso_effettivo` risolve tutto. Un utente disattivato non ha
  nessun permesso.

I permessi vengono **riletti dal database a ogni richiesta**: toglierne uno ha
effetto immediato, senza aspettare che la sessione scada.

Nel codice il controllo è a due livelli: `@PreAuthorize` sulla rotta fa il
primo filtro, e il servizio rifinisce la parte che dipende dai dati — «i
*propri* lavori», «le task a cui sei assegnato» — che in un'annotazione non si
esprime.

---

## Il monte ore

Il principio che tiene insieme la funzionalità: **i totali non si salvano
mai.**

```
ore libere del lavoro   = monte_ore − SUM(task.ore_allocate)
ore consumate di una task = SUM(sessione_task.minuti) / 60
task in sforamento        = ore consumate > ore allocate
```

Si calcolano al volo. La conseguenza utile è che lo sforamento di una task
**compare da sé** appena il tempo misurato supera il budget — mentre qualcuno
ci sta lavorando, non la sera in cui si ricorda di dichiarare: nessun codice
deve ricordarsi di marcarla.

Ciò che invece si salva è la **storia**: `movimento_ore` registra ogni
variazione con autore, data e motivo, e nessuna riga si cancella. È per questo
che modificare il monte ore non distrugge la stima iniziale, e a fine progetto
si può confrontare preventivo e consuntivo.

Il percorso, in breve:

1. l'amministratore crea il lavoro e ne fissa il monte ore;
2. il responsabile crea le task e alloca le ore dal pool;
3. il dipendente avvia la task a cui è assegnato, la mette in pausa, la
   completa: il tempo si registra da sé, senza niente da compilare;
4. se una task sfora, il responsabile lo vede subito;
5. se il pool non basta, il responsabile apre una **richiesta di ore**
   all'amministratore;
6. l'amministratore la valuta con tutti i numeri del lavoro davanti, e approva,
   approva in parte o rifiuta — l'approvazione alza il monte ore in automatico;
7. chiudendo una task, le ore non consumate tornano nel pool.

Il resoconto completo — decisioni di disegno, rotte, permessi, verifiche — è in
**[`MD files/monte_ore_report.md`](MD%20files/monte_ore_report.md)**; il piano
da cui è nato è in
[`MD files/piano_monte_ore.md`](MD%20files/piano_monte_ore.md).

---

## Test

```bash
cd backend && ./mvnw test        # 147 test
```

Due stili, di proposito:

- `@WebMvcTest` con il servizio simulato, per verificare il **contratto delle
  rotte**: che i permessi dichiarati funzionino e che gli errori del servizio
  arrivino al client con il codice giusto;
- test con il contesto completo contro il database reale, per le regole che
  dipendono dai dati.

Frontend:

```bash
cd frontend && npm run build     # compila anche i tipi (tsc -b)
cd frontend && npm run lint      # oxlint
```

---

## Migrazioni del database

Flyway applica le migrazioni all'avvio del backend.

- `db/migration/` — **versionate**, applicate una volta sola e in ordine. Una
  migrazione già applicata **non si modifica**: il checksum non combacerebbe
  più e Flyway rifiuterebbe di partire. Si aggiunge la successiva. Quante
  sono lo dice la cartella, e non questa riga: c'era scritto «`V1`…`V8`»
  mentre di migrazioni ce n'erano già il doppio.
- `db/dev/` — **ripetibile** (`R__dati_sviluppo.sql`), rieseguita ogni volta che
  il file cambia.

Due trappole di MySQL che il progetto ha già incontrato, e che vale la pena
sapere prima di scrivere una migrazione:

- **un vincolo CHECK non si modifica**: va eliminato e ricreato (vedi `V5` e
  `V8`);
- **una colonna `NOT NULL` senza default** su una tabella che ha già righe
  viene rifiutata: si aggiunge nullable, si riempie, poi si rende obbligatoria;
- **DDL e dati vanno in migrazioni separate**: MySQL non ha DDL transazionale,
  quindi Flyway non puo' annullare un `CREATE TABLE`. Se un errore sui dati
  lasciasse la migrazione a meta', al riavvio il `CREATE` si ripeterebbe e
  fallirebbe, e non ci sarebbe modo di ripartire senza intervenire a mano
  (vedi `V13`/`V14` e `V15`/`V16`/`V17`).

> ⚠️ La cartella `db/dev/` contiene i dati di prova e viene caricata perché
> `application.properties` la elenca in `spring.flyway.locations`. **In
> produzione va lasciata fuori**, e il profilo `prod` la lascia fuori: riscrive
> `spring.flyway.locations` con le sole migrazioni versionate.
>
> La conseguenza da conoscere è che un database di produzione nasce **senza
> nessun utente** — le migrazioni seminano ruoli, permessi e impostazioni, non
> persone — e il gestionale non ha registrazione pubblica. Il primo
> amministratore lo crea `AmministratoreIniziale` leggendo
> `GESTIONALE_AMMINISTRATOREINIZIALE_EMAIL` e `…_PASSWORD`, e solo se la
> tabella `utente` è vuota. Vedi `.env.example`.

Per ripartire da un database pulito:

```bash
docker compose down
rm -rf .data/mysql        # cancella il database di sviluppo
docker compose up -d db
```

---

## Scelte di progetto da conoscere

- **Niente si cancella.** Un lavoro si *annulla*, una task si *annulla*,
  un'assegnazione si rimuove in modo morbido valorizzando `rimossa_il`. Una
  cancellazione fisica porterebbe via le ore registrate, cioè la
  registrazione del lavoro svolto dalle persone. **Nessuna eccezione**: una
  sessione sbagliata si annulla valorizzando `annullata_il` ed esce dai conti,
  ma la riga resta. (L'eccezione era la voce di dichiarazione, che si
  cancellava davvero; è uscita con la giornata da compilare.)
- **Le ore sono `DECIMAL` / `BigDecimal`**, mai virgola mobile: sono numeri che
  vanno sommati, e `double` accumulerebbe errori.
- **L'autenticazione sta nel cookie di sessione** `JSESSIONID`, `HttpOnly` e
  `SameSite=Strict`. Nessun token, nessun segreto di firma da gestire. I token
  CSRF sono disattivati *di proposito*: la difesa è `SameSite=Strict`.
  Il profilo `prod` aggiunge `server.servlet.session.cookie.secure=true`, che
  **pretende HTTPS**: servendo l'applicazione in chiaro, il login risponde 200
  e ogni richiesta successiva è anonima, senza nessun errore che lo spieghi.
- **Il fuso orario è `Europe/Rome`, dichiarato nelle immagini.** Non è una
  cortesia: senza, la JVM in container parte in UTC mentre MySQL sta su
  `Europe/Rome`, e nelle stesse tabelle finiscono due orologi a un'ora di
  distanza — `creato_il` lo scrive il database, `avviata_il` lo scrive
  l'applicazione. In un gestionale di ore lavorate la differenza si somma nei
  consuntivi.
- **La sessione scade dopo 30 minuti di inattività**, e il frontend se ne
  accorge: ogni 401 che non venga da `/login` o da `/io` riporta alla
  schermata di accesso con la spiegazione (`api/http.ts`).
- **Chi dimentica la password** non ha un «password dimenticata» — non c'è
  infrastruttura email. Gliene assegna una nuova chi ha
  `UTENTI_RESET_PASSWORD`, dall'elenco utenti, e gliela comunica a voce.
- **`open-in-view` è disattivato.** Le relazioni lazy non si possono leggere
  fuori dalla transazione: i repository usano `JOIN FETCH` espliciti, e gli
  elenchi caricano i totali con query raggruppate invece di una per riga.
- **Nessuna notifica.** Il gestionale non ha infrastruttura di notifica o
  email. Al suo posto, ciò che una notifica annuncerebbe è messo in evidenza
  nelle schermate.
- **Tema scuro rinviato**: `color-scheme: light`, ma i token CSS sono già
  strutturati per accoglierlo. La palette è fissa — si aggiungono solo
  derivati, mai tinte nuove.
