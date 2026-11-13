# Gestionale Anitia

[![ci](https://github.com/prettyboisosaa/GestionaleAziendale/actions/workflows/ci.yml/badge.svg)](https://github.com/prettyboisosaa/GestionaleAziendale/actions/workflows/ci.yml)

Gestionale interno per l'assegnazione di lavori e task ai dipendenti, con
gestione del **monte ore**: ogni progetto ha un budget di ore che si
distribuisce fra le sue task, i dipendenti registrano le ore lavorate giorno
per giorno, e quando le ore non bastano c'è un percorso tracciato per ottenerne
altre.

| | |
|---|---|
| **Backend** | Spring Boot 4.1 su Java 25, MySQL 8.4, Flyway, Spring Security con autenticazione a sessione |
| **Frontend** | React 19 + TypeScript + Vite, senza librerie di UI |
| **Esecuzione** | Docker Compose su un host solo; i manifest Kubernetes esistono ma non sono mai stati eseguiti |

---

## Avvio rapido

Servono **Docker Desktop** e un **JDK 25+**; Node 22+ per il frontend.

```bash
cd backend && ./mvnw spring-boot:run      # il database lo avvia da sé
cd frontend && npm install && npm run dev # in un altro terminale
```

L'applicazione è su **<http://localhost:5173>**. Su Windows, se `./mvnw` si
lamenta di `JAVA_HOME`, c'è `backend\avvia.ps1`.

Utenti di prova, indirizzi dei servizi e diagnostica: **[SVILUPPO.md](SVILUPPO.md)**.

---

## La documentazione

| documento | risponde a |
|---|---|
| **[SVILUPPO.md](SVILUPPO.md)** | come lo avvio, con che utenti entro, perché non parte, come scrivo una migrazione |
| **[ARCHITETTURA.md](ARCHITETTURA.md)** | perché è fatto così, quali rotte espone e con quali permessi, come è composta l'infrastruttura |
| **[RILASCIO.md](resources/RILASCIO.md)** | perché il rilascio è fatto così, e la diagnostica del server |
| **[CONSEGNA.md](resources/CONSEGNA.md)** | la sequenza del giorno della consegna, dall'SSH al gestionale in uso |
| [`MD files/`](resources/MD%20files/) · [`design system/`](resources/design%20system/) · [calendario.md](resources/calendario.md) | i documenti di progetto per singola funzionalità |

---

## Le due idee che tengono insieme tutto

**I permessi, non i ruoli, decidono.** Cosa può fare un utente dipende solo dai
permessi effettivi — la vista `permesso_effettivo` combina ruolo, scostamenti
per singola persona e stato dell'utente, e un utente disattivato non ha nessun
permesso. I permessi sono riletti a ogni richiesta, quindi una revoca ha effetto
immediato. Dettaglio in [ARCHITETTURA §2.2](ARCHITETTURA.md#22-i-permessi-non-i-ruoli-decidono).

**I totali non si salvano mai.**

```
ore libere del lavoro     = monte_ore − SUM(task.ore_allocate)
ore consumate di una task = SUM(sessione_task.minuti) / 60
task in sforamento        = ore consumate > ore allocate
```

Si calcolano al volo, quindi lo sforamento di una task compare da sé appena il
tempo misurato supera il budget: nessun codice deve ricordarsi di marcarla. Ciò
che si salva è la **storia** — `movimento_ore` registra ogni variazione con
autore, data e motivo, e nessuna riga si cancella.

Il percorso completo: l'amministratore crea il lavoro e ne fissa il monte ore →
il responsabile crea le task e alloca le ore → il dipendente avvia, mette in
pausa e completa, e il tempo si registra da sé → se il pool non basta il
responsabile apre una **richiesta di ore**, che l'amministratore valuta con
tutti i numeri davanti. Il resoconto è in
[`MD files/monte_ore_report.md`](resources/MD%20files/done/monte_ore_report.md).

---

## Le rotte

La fonte autorevole è **Swagger**, su <http://localhost:8080/swagger-ui.html>
(in produzione è spento di proposito). Il riassunto navigabile di tutte le
rotte, **con il permesso che ciascuna pretende**, è in
[ARCHITETTURA §3](ARCHITETTURA.md#3-le-rotte): autenticazione, giornata di
lavoro, lavori, task, monte ore e richieste, assenze, consuntivi, utenti e
ruoli.

---

## In produzione

Un host, tre container, **una sola porta pubblicata**:

```bash
cp .env.example .env            # poi si riempiono i cinque valori
docker compose -f compose.prod.yml up -d --build
```

**Non si usa `docker-compose.yml`**, nemmeno con `--profile app`: quel percorso
non attiva il profilo `prod`, quindi carica i dati di prova e lascia a Hibernate
la riscrittura dello schema. Le differenze fra i due file, riga per riga, sono
in [ARCHITETTURA §4](ARCHITETTURA.md#4-linfrastruttura); la procedura vera è in
[CONSEGNA.md](resources/CONSEGNA.md).

Tre cose da sapere prima di toccare il server:

- le **due utenze di database** (`gestionale` solo DML, `gestionale_flyway`
  anche DDL) nascono da `db-init/10-utenze.sh` **solo alla creazione del
  volume**: su un database che esiste già si creano a mano, RILASCIO.md §10;
- il primo rilascio è in **HTTP**, quindi `COOKIE_SECURE: "false"`. Il cookie di
  sessione viaggia in chiaro: è una decisione, ed è scritta;
- **`down -v` cancella il database.** È a due caratteri da un comando che si usa
  davvero.

---

## Struttura

```
GestionaleAziendale/
├── backend/              Spring Boot — un pacchetto per dominio
│   └── src/main/
│       ├── java/com/anitia/backend/
│       │   ├── assenza/ autenticazione/ dashboard/ impostazione/
│       │   ├── lavoro/ ore/ permesso/ report/ ruolo/
│       │   ├── sicurezza/ task/ tempo/ utente/
│       └── resources/db/
│           ├── migration/        lo schema, versionato
│           └── dev/              dati di prova, esclusi dal jar
├── frontend/             React + Vite — stessi domini del backend
│   └── src/
│       ├── api/          il wrapper di fetch e la traduzione degli errori
│       ├── componenti/   barra superiore e rotte protette
│       └── stato/        lo stato condiviso
├── db-init/              le utenze del database di produzione
├── k8s/                  manifest per un cluster — scheletro, mai eseguito
├── resources/            documentazione e materiale di progetto
│   ├── RILASCIO.md  CONSEGNA.md  calendario.md
│   ├── MD files/         i documenti per singola funzionalità
│   ├── design system/    le tavole del disegno
│   └── postman/          collezioni per provare le API a mano
├── compose.prod.yml      il server
└── docker-compose.yml    lo sviluppo
```

Nomi di classi, campi, tabelle e classi CSS sono **in italiano**, e i commenti
spiegano il *perché* delle scelte, non il *cosa*. È una convenzione del
progetto: conviene seguirla.
