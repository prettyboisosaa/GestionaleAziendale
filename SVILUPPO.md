# Sviluppo

Come si avvia il gestionale sulla propria macchina, con quali utenti si entra,
cosa fare quando non parte e come si scrive una migrazione.

Per il *perché* delle scelte: [ARCHITETTURA.md](ARCHITETTURA.md). Per il
server: [RILASCIO.md](resources/RILASCIO.md) e [CONSEGNA.md](resources/CONSEGNA.md).

---

## 1. Avvio

Servono **Docker Desktop** e un **JDK 25 o superiore**; Node 22+ per il
frontend (Vite 8 pretende 20.19+ o 22+).

```bash
# 1. Il backend
cd backend && ./mvnw spring-boot:run

# 2. Il frontend, in un altro terminale
cd frontend && npm install && npm run dev
```

**Il database non va avviato a mano.** All'avvio il backend tira su da sé i
servizi di `docker-compose.yml` e **aspetta che MySQL sia sano** prima di
connettersi (`spring.docker.compose.*` in `application.properties`). Basta che
Docker Desktop sia in esecuzione.

Su Windows, se `./mvnw` si lamenta di `JAVA_HOME`:

```powershell
cd backend; .\avvia.ps1
```

Cerca un JDK, lo imposta per la durata del comando e avvia il backend.

| servizio | indirizzo |
|---|---|
| **l'applicazione** | <http://localhost:5173> |
| backend | <http://localhost:8080> |
| Swagger | <http://localhost:8080/swagger-ui.html> |
| phpMyAdmin | <http://localhost:8081> |
| MySQL | `localhost:3306` |

Vite inoltra `/api` al backend sulla 8080, quindi non serve configurare CORS —
e il browser vede una sola origine, che è ciò che il cookie `SameSite=Strict`
pretende (vedi ARCHITETTURA §2.1).

### Tutto in Docker

`docker-compose.yml` definisce anche `backend` e `frontend`, sotto il profilo
`app`. Un semplice `docker compose up -d` avvia **solo** `db` e `phpmyadmin`,
cioè l'infrastruttura che serve al backend lanciato dall'IDE: così nessun
container litiga con l'applicazione locale sulla porta 8080.

```bash
docker compose --profile app up -d --build     # lo stack intero
```

In quel caso il frontend è sulla porta 80. Per lo sviluppo quotidiano conviene
il percorso sopra, che dà ricompilazione automatica su entrambi i lati.

> ⚠️ **Questo percorso non è quello di produzione**, nemmeno con
> `--profile app`: non attiva `SPRING_PROFILES_ACTIVE=prod`, quindi carica i
> dati di prova e lascia a Hibernate la riscrittura dello schema. Per il server
> c'è `compose.prod.yml` — vedi ARCHITETTURA §4.1.

---

## 2. Utenti di sviluppo

I dati di prova stanno in `backend/src/main/resources/db/dev/` e Flyway li
carica come migrazione ripetibile. **Password di tutti: `Password1!`**

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
> I segreti veri vivono nel `.env` del server, che non è versionato — vedi
> `.env.example`.

---

## 3. Quando il backend non parte

- **`Unable to obtain connection from database: Communications link failure`**
  — il database non risponde. Flyway è la prima cosa che tocca il database, e
  se non ottiene la connessione il contesto Spring fallisce. Da quando il
  backend avvia il compose da sé non dovrebbe più capitare: se capita, Docker
  Desktop non è partito. Per controllare a mano:

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
  strada — schema nuovo e codice che se lo aspetta diverso. In sviluppo la via
  più pulita è ricostruirlo (§5); in alternativa si corregge la migrazione e si
  cancella la riga fallita dallo storico, così Flyway la rifà.

- **500 su operazioni che prima funzionavano, con `Check constraint ... is
  violated` nei log** — il database è a uno schema più avanzato del codice che
  risponde. Il caso tipico è un container `gestionale-backend` costruito prima
  dell'ultima migrazione: `docker compose --profile app up -d --build backend`,
  dove il `--build` non è opzionale. Oppure lo si spegne e si lavora dall'IDE.

- **`Port 8080 was already in use`** — c'è già un'istanza del backend, spesso
  avviata da IntelliJ o rimasta appesa. Su Windows:
  `netstat -ano | findstr :8080` per trovare il PID, poi
  `taskkill /PID <pid> /F`. Attenzione anche al container
  `gestionale-backend`: se è su occupa la 8080, e si spegne con
  `docker compose --profile app stop backend`.

- **`JAVA_HOME is not defined correctly`** — succede solo da riga di comando;
  IntelliJ usa l'SDK del progetto e non ne ha bisogno. Si risolve con
  `backend\avvia.ps1`, oppure impostando la variabile sul percorso del JDK.

- **`Conflict. The container name "/gestionale-db" is already in use`** — i
  container esistono ma appartengono a un altro progetto compose, tipico dopo
  aver rinominato la cartella. Si controlla a quale progetto appartengono, e si
  rimuovono:

  ```bash
  docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' gestionale-db
  docker rm gestionale-db gestionale-phpmyadmin
  ```

  I dati di sviluppo vivono nel bind mount `.data/mysql` della cartella da cui
  il container è stato creato: rimuovere il container non li cancella, ma un
  container ricreato da un'altra cartella parte da un database vuoto (che
  Flyway ripopola).

---

## 4. Migrazioni del database

Flyway le applica all'avvio del backend.

- **`db/migration/`** — versionate, applicate una volta sola e in ordine. Una
  migrazione già applicata **non si modifica**: il checksum non combacerebbe
  più e Flyway rifiuterebbe di partire. Si aggiunge la successiva. Quante sono
  lo dice la cartella, e non questa riga: c'era scritto «`V1`…`V8`» mentre di
  migrazioni ce n'erano già il doppio.
- **`db/dev/`** — ripetibile (`R__dati_sviluppo.sql`), rieseguita ogni volta
  che il file cambia. **In produzione è esclusa** dal profilo `prod` *e* dal
  jar (vedi ARCHITETTURA §2.8).

### Tre trappole di MySQL che questo progetto ha già incontrato

- **un vincolo CHECK non si modifica**: va eliminato e ricreato (vedi `V5` e
  `V8`);
- **una colonna `NOT NULL` senza default** su una tabella che ha già righe
  viene rifiutata: si aggiunge nullable, si riempie, poi si rende obbligatoria;
- **DDL e dati vanno in migrazioni separate**: MySQL non ha DDL transazionale,
  quindi Flyway non può annullare un `CREATE TABLE`. Se un errore sui dati
  lasciasse la migrazione a metà, al riavvio il `CREATE` si ripeterebbe e
  fallirebbe, senza modo di ripartire senza intervenire a mano (vedi
  `V13`/`V14` e `V15`/`V16`/`V17`).

Una migrazione che aggiunge un **permesso** deve concederlo anche al ruolo
protetto, che per definizione ha tutti i permessi (ARCHITETTURA §2.2).

---

## 5. Ripartire da un database pulito

```bash
docker compose down
rm -rf .data/mysql        # cancella il database di sviluppo
docker compose up -d db
```

Flyway ricostruisce schema e dati di prova al prossimo avvio del backend.

> Un test può passare in CI e fallire in locale proprio per questo:
> `UtenteRepositoryTest` verifica che l'utenza chiusa di prova non abbia
> permessi, e il seed la crea chiusa. Su un database di sviluppo in cui
> qualcuno l'ha riattivata a mano, la migrazione ripetibile **non** la richiude
> — Flyway la rilancia solo quando cambia il file, non quando cambiano i dati.

---

## 6. Test

```bash
cd backend && ./mvnw test
```

Due stili, di proposito:

- **`@WebMvcTest` con il servizio simulato**, per il *contratto delle rotte*:
  che i permessi dichiarati funzionino e che gli errori del servizio arrivino
  al client con il codice giusto;
- **contesto completo contro il database reale**, per le regole che dipendono
  dai dati — le colonne generate di `sessione_task`, la vista
  `permesso_effettivo` e le query native sono scritte in SQL di MySQL, quindi
  un database finto non le proverebbe.

Frontend:

```bash
cd frontend && npm run lint      # oxlint
cd frontend && npm run build     # tsc -b && vite build: compila anche i tipi
```

Non ci sono test automatici sul frontend: `lint` e `build` dimostrano che
compila e che i tipi tornano, non che funzioni.

---

## 7. Convenzioni

- **I nomi sono in italiano** — classi, campi, tabelle, pacchetti, classi CSS.
- **I commenti spiegano il *perché*, non il *cosa*.** Dove una scelta ha una
  conseguenza scomoda, si scrive accanto alla scelta.
- **Niente numeri che invecchiano** nella documentazione: quante migrazioni ci
  sono lo dice la cartella, quanti test ci sono lo dice Maven. Un commento che
  diceva «`V1..V17`» quando si era alla V21 è stato tolto per questo.
- **Non si cancella niente dal database**: si annulla. Vedi ARCHITETTURA §2.3.
