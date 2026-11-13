# Architettura

Le scelte di struttura del gestionale, le rotte che espone e l'infrastruttura
che lo esegue. Dice **perché** il sistema è fatto così; il *come si avvia* sta
in [SVILUPPO.md](SVILUPPO.md) e in [RILASCIO.md](resources/RILASCIO.md).

Dove una scelta ha una conseguenza scomoda, è scritta qui accanto alla scelta.
Una decisione di cui si ricorda solo il beneficio è una decisione che prima o
poi qualcuno disfa per sbaglio.

---

## 1. La forma del sistema

Tre livelli, e **un solo ingresso pubblico**. Il backend e il database non
pubblicano porte: ci si arriva solo dalla rete interna del compose.

```
browser ──80──▶ frontend            nginx + la SPA compilata
                   │                 proxy_pass /api ──▶ backend
                   ▼
                backend             Spring Boot, porta 8080, non pubblicata
                   │                 JDBC
                   ▼
                db                  MySQL 8.4, nessuna porta pubblicata
```

Il browser vede **una sola origine**, e non è una comodità: il cookie di
sessione è `SameSite=Strict`, quindi da un'origine diversa il browser non lo
allegherebbe e nessuno riuscirebbe ad autenticarsi. È la ragione per cui in
sviluppo le chiamate passano dal proxy di Vite e in produzione da quello di
nginx, invece di parlare al backend direttamente.

In sviluppo lo schema cambia solo nel primo anello: Vite serve la SPA sulla
5173 e inoltra `/api` alla 8080. Il resto è identico.

---

## 2. Le scelte architetturali

### 2.1 L'autenticazione sta in un cookie di sessione, non in un token

`JSESSIONID`, `HttpOnly`, `SameSite=Strict`, scadenza a 30 minuti di
inattività. Nessun JWT, nessun segreto di firma da custodire o ruotare, nessuna
revoca da inventare: il logout è la sessione che muore.

**I token CSRF sono disattivati di proposito** (`ConfigurazioneSicurezza`), e
la difesa è `SameSite=Strict`: un sito terzo non può far partire una richiesta
che porti con sé il cookie. Le due cose vanno insieme — chi un giorno
allentasse il `SameSite` deve riaccendere il CSRF nello stesso commit.

**La conseguenza:** le sessioni vivono nella memoria del processo. Il backend
gira in **una sola istanza**, e un suo riavvio fa ricadere il login a tutti.
Con venti utenti è accettabile, ed è scritto in `CONSEGNA.md` fra le cose da
dire alle persone; scalare a due istanze richiede prima un archivio condiviso
delle sessioni, non una modifica al `replicas`.

### 2.2 I permessi, non i ruoli, decidono

Cosa può fare un utente dipende **solo dai permessi effettivi**. Il nome del
ruolo non compare in nessun controllo.

- Il **catalogo dei permessi** è fisso: si estende solo con una migrazione.
- I **ruoli** si creano e si modificano dall'applicazione. Il ruolo *protetto*
  ha sempre tutti i permessi, quindi ogni migrazione che aggiunge un permesso
  deve concederlo anche a lui.
- `utente_permesso` tiene gli **scostamenti per singola persona**, nelle due
  direzioni: un permesso dato a uno solo, o tolto a uno solo nonostante il
  ruolo glielo dia.
- La vista `permesso_effettivo` risolve ruolo + scostamenti + stato
  dell'utente. **Un utente disattivato non ha nessun permesso**, e questo è
  provato da un test (`UtenteRepositoryTest`).

I permessi sono **riletti dal database a ogni richiesta**: togliere un permesso
ha effetto immediato, senza aspettare la scadenza della sessione. Si paga una
query per richiesta e si guadagna che una revoca sia davvero una revoca.

Il controllo è a **due livelli**, e servono entrambi:

| livello | cosa decide | esempio |
|---|---|---|
| `@PreAuthorize` sulla rotta | hai il permesso in assoluto? | `TASK_GESTISCI_PROPRI` |
| il servizio | su *questo* dato? | «*propri* lavori», «le task a cui sei assegnato» |

Il secondo non si esprime in un'annotazione, quindi non ci si prova: le rotte
dichiarano il requisito minimo e il servizio rifinisce.

Dalla **V24** la vista è `SQL SECURITY INVOKER` e non `DEFINER`: con due utenze
di database una vista legata al proprio creatore si rompe al ripristino di un
dump su un'altra installazione (`ERROR 1449: The user specified as a definer
does not exist`).

### 2.3 Niente si cancella

Un lavoro si *annulla*, una task si *annulla*, un'assegnazione si rimuove
valorizzando `rimossa_il`, una sessione sbagliata esce dai conti con
`annullata_il` ma **la riga resta**. Nessuna eccezione.

**Perché:** una cancellazione fisica porterebbe via le ore registrate, cioè la
traccia del lavoro svolto da una persona. In un gestionale di ore quella
traccia è il prodotto, non un effetto collaterale.

### 2.4 I totali non si salvano mai

```
ore libere del lavoro     = monte_ore − SUM(task.ore_allocate)
ore consumate di una task = SUM(sessione_task.minuti) / 60
task in sforamento        = ore consumate > ore allocate
```

Tutto calcolato al volo. **La conseguenza utile:** lo sforamento compare da sé
appena il tempo misurato supera il budget — mentre qualcuno ci sta lavorando,
non la sera in cui se ne ricorda. Nessun codice deve marcare niente, quindi
nessun codice può dimenticarsene.

Ciò che si salva è la **storia**: `movimento_ore` registra ogni variazione con
autore, data e motivo, e nessuna riga si cancella. È per questo che cambiare il
monte ore non distrugge la stima iniziale e a fine progetto preventivo e
consuntivo si confrontano.

**Il costo:** gli elenchi non possono fare una query per riga. I repository
usano `JOIN FETCH` e aggregazioni raggruppate — vedi 2.7.

### 2.5 Le ore sono `DECIMAL` / `BigDecimal`

Mai virgola mobile. Sono numeri destinati a essere sommati molte volte, e
`double` accumulerebbe errori che in un consuntivo si vedono.

### 2.6 Il fuso orario è `Europe/Rome`, dichiarato nelle immagini

Non nel solo compose: `ENV TZ=Europe/Rome` sta nel `Dockerfile` del backend, e
vale comunque lo si avvii.

**Perché:** senza, la JVM in container parte in **UTC** mentre il container di
MySQL sta su `Europe/Rome`, e nelle stesse tabelle finiscono due orologi a
un'ora di distanza (due in estate). `creato_il` e `aggiornato_il` li scrive
MySQL con `DEFAULT CURRENT_TIMESTAMP`; `avviata_il`, `chiusa_il` e
`stato_aggiornato_il` li scrive la JVM. Con UTC, fra mezzanotte e l'una
italiana «oggi» è ieri, e una sessione avviata alle 00:30 finisce nel giorno
sbagliato.

Chi installasse fuori dall'Italia lo sovrascrive con `-e TZ=...`.

### 2.7 `open-in-view` è disattivato

Le relazioni lazy non si possono leggere fuori dalla transazione. È una scelta
che costa lavoro in scrittura — `JOIN FETCH` espliciti nei repository — e lo
restituisce togliendo di mezzo la classe di guasti in cui una pagina esegue
cinquanta query senza che si veda nel codice.

### 2.8 Flyway è l'unica autorità sullo schema

In produzione `spring.jpa.hibernate.ddl-auto=validate`: entità e tabelle
devono concordare, altrimenti **il backend non parte**. In sviluppo resta
`update`, perché lì è il modo di lavorare.

Ma una property è una promessa, e una variabile d'ambiente sbagliata la
rimette a `update`: **è già successo il 9 novembre 2026**, con Hibernate che ha
ricreato le tabelle eliminate dalla V17. Per questo in produzione ci sono **due
utenze di database**, create da `db-init/10-utenze.sh` alla nascita del volume:

| utenza | privilegi | chi la usa |
|---|---|---|
| `gestionale` | `SELECT, INSERT, UPDATE, DELETE` | il processo raggiungibile dalla rete |
| `gestionale_flyway` | anche `CREATE, DROP, ALTER, INDEX, REFERENCES, CREATE VIEW, SHOW VIEW` | solo Flyway, una volta per avvio |

**I privilegi sono un vincolo, una property è una promessa**: con il DDL tolto
all'utenza applicativa, la stessa distrazione produce un errore all'avvio
invece di uno schema riscritto sotto i dati.

Non protegge i *dati* — con `SELECT` e `UPDATE` si leggono e si falsificano
tutte le ore e le assenze di tutti. Protegge lo **schema**. È contenimento del
danno, non prevenzione.

Il seed di sviluppo è escluso dal profilo `prod` (`spring.flyway.locations`) e
**anche dall'artefatto** (`maven-jar-plugin`, `db/dev/**`): prima l'unica
difesa era la riga delle properties, e dimenticare `SPRING_PROFILES_ACTIVE=prod`
su un database vuoto seminava sette utenti con una password nota, uno dei quali
amministratore. Una difesa che si disarma per distrazione non è una difesa.

### 2.9 Nessuna email, nessuna notifica

Il gestionale non ha infrastruttura di posta. **Le conseguenze, tutte
deliberate:**

- non c'è «password dimenticata»: una nuova password la assegna chi ha
  `UTENTI_RESET_PASSWORD`, dall'elenco utenti, e la comunica a voce;
- non c'è registrazione pubblica, quindi un database di produzione vuoto non ha
  **nessun** utente — il primo amministratore nasce da
  `GESTIONALE_AMMINISTRATOREINIZIALE_*` e solo se la tabella `utente` è vuota;
- ciò che una notifica annuncerebbe è messo in evidenza nelle schermate.

### 2.10 Gli errori sono `ProblemDetail`

`spring.mvc.problemdetails.enabled=true`: gli errori escono nel formato
RFC 7807, con `status`, `title` e `detail`. Il frontend li traduce in un punto
solo (`frontend/src/api/http.ts`), e lì sta anche la regola sulla sessione
scaduta: ogni **401** che non arrivi da `/login` o da `/io` riporta alla
schermata di accesso con la spiegazione, invece di lasciare una pagina che non
si aggiorna più.

### 2.11 Il tema scuro è rinviato, non escluso

`color-scheme: light`, ma i token CSS sono già strutturati per accoglierlo. La
**palette è fissa**: si aggiungono solo derivati, mai tinte nuove. Rinviare una
funzionalità lasciando la strada aperta costa poco; riaprirla dopo che
cinquanta componenti hanno scritto colori a mano costa molto.

### 2.12 I nomi sono in italiano

Classi, campi, tabelle, pacchetti, classi CSS. I commenti spiegano il
**perché**, non il *cosa*. È una convenzione del progetto e conviene seguirla:
mezzo codice in italiano e mezzo in inglese è peggio di entrambe le scelte.

---

## 3. Le rotte

La fonte autorevole è **Swagger**, generato da springdoc dalle annotazioni:

| | |
|---|---|
| pagina | <http://localhost:8080/swagger-ui.html> |
| documento OpenAPI | <http://localhost:8080/v3/api-docs> |

In **produzione sono spenti** (`springdoc.*.enabled=false` nel profilo `prod`):
l'elenco completo delle rotte, dei corpi accettati e dei codici d'errore è la
mappa di come attaccare il gestionale. Il job `stack-di-produzione` della CI
verifica che `/v3/api-docs` risponda 404 *dall'interno* del container, perché
da fuori nginx risponderebbe 200 con la pagina della SPA a qualunque percorso
sconosciuto — e quel 200 somiglierebbe a un api-docs acceso.

La tabella qui sotto è il riassunto navigabile, con il **permesso** che ogni
rotta pretende. Dove ci sono più permessi vale «almeno uno»
(`hasAnyAuthority`); il servizio rifinisce poi sul dato, come in 2.2.

### Autenticazione — `AutenticazioneController`

| | rotta | permesso |
|---|---|---|
| `POST` | `/api/autenticazione/login` | — (`permitAll`) |
| `GET` | `/api/autenticazione/io` | sessione valida |
| `POST` | `/api/autenticazione/cambio-password` | sessione valida |

Le uniche rotte raggiungibili senza autenticazione in tutto il sistema sono il
login, `/error`, il gruppo `/actuator/health` e — **solo in sviluppo** — le tre
di Swagger. Tutto il resto è `authenticated()`.

### La giornata di chi lavora — `DashboardController`, `MieTaskController`, `TempoController`

| | rotta | permesso |
|---|---|---|
| `GET` | `/api/mia-dashboard` | `TASK_VISUALIZZA_ASSEGNATE` |
| `GET` | `/api/mio-calendario` | `TASK_VISUALIZZA_ASSEGNATE` |
| `GET` | `/api/mie-task` | `TASK_VISUALIZZA_ASSEGNATE` |
| `GET` | `/api/mio-tempo` | `TASK_AGGIORNA_STATO` |
| `GET` | `/api/mio-tempo/settimana` | `TASK_AGGIORNA_STATO` |
| `POST` | `/api/lavori/{l}/task/{t}/avvia` | `TASK_AGGIORNA_STATO` |
| `POST` | `/api/mio-tempo/pausa` | `TASK_AGGIORNA_STATO` |
| `POST` | `/api/lavori/{l}/task/{t}/completa` | `TASK_AGGIORNA_STATO` |
| `POST` | `/api/lavori/{l}/task/{t}/sessioni` | `TASK_AGGIORNA_STATO` |
| `POST` | `/api/mio-tempo/sessioni` | `TASK_AGGIORNA_STATO` |
| `PUT` | `/api/mio-tempo/sessioni/{id}` | `TASK_AGGIORNA_STATO` |
| `POST` | `/api/mio-tempo/sessioni/{id}/conferma` | `TASK_AGGIORNA_STATO` |
| `POST` | `/api/mio-tempo/sessioni/{id}/annulla` | `TASK_AGGIORNA_STATO` |

### Lavori — `LavoriController`

| | rotta | permesso |
|---|---|---|
| `GET` | `/api/lavori` | `LAVORI_VISUALIZZA_TUTTI` / `_PROPRI` |
| `GET` | `/api/lavori/{id}` | `LAVORI_VISUALIZZA_TUTTI` / `_PROPRI` |
| `GET` | `/api/lavori/{id}/kpi` | `LAVORI_VISUALIZZA_TUTTI` / `_PROPRI` |
| `GET` | `/api/lavori/{id}/andamento` | `LAVORI_VISUALIZZA_TUTTI` / `_PROPRI` |
| `GET` | `/api/lavori/{id}/movimenti` | `LAVORI_VISUALIZZA_TUTTI` / `_PROPRI` |
| `GET` | `/api/lavori/responsabili` | `LAVORI_ASSEGNA_RESPONSABILE` |
| `POST` | `/api/lavori` | `LAVORI_CREA` |
| `POST` | `/api/lavori/{id}/monte-ore` | `ORE_MONTE_GESTISCI` |
| `POST` | `/api/lavori/{id}/annulla` | `LAVORI_MODIFICA_TUTTI` / `_PROPRI` |
| `DELETE` | `/api/lavori/{id}` | `LAVORI_MODIFICA_TUTTI` |

### Task — `TaskController`

| | rotta | permesso |
|---|---|---|
| `GET` | `/api/lavori/{l}/task` | `LAVORI_VISUALIZZA_TUTTI` / `_PROPRI` |
| `GET` | `/api/lavori/{l}/task/assegnabili` | `TASK_GESTISCI_TUTTI` / `_PROPRI` |
| `POST` | `/api/lavori/{l}/task` | `TASK_GESTISCI_TUTTI` / `_PROPRI` |
| `POST` | `/api/lavori/{l}/task/{t}/stato` | `TASK_AGGIORNA_STATO`, `TASK_GESTISCI_TUTTI` / `_PROPRI` |
| `POST` | `/api/lavori/{l}/task/{t}/assegnazioni` | `TASK_GESTISCI_TUTTI` / `_PROPRI` |
| `DELETE` | `/api/lavori/{l}/task/{t}/assegnazioni/{utenteId}` | `TASK_GESTISCI_TUTTI` / `_PROPRI` |

`assegnabili` esiste per via dei team: `TASK_GESTISCI_PROPRI` lascia assegnare
solo ai dipendenti del proprio team, quindi il menu di assegnazione non mostra
tutti gli utenti. È un esempio del secondo livello di 2.2.

### Monte ore e richieste — `OreController`, `RichiesteOreController`

| | rotta | permesso |
|---|---|---|
| `GET` | `/api/lavori/{l}/ore` | `LAVORI_VISUALIZZA_TUTTI` / `_PROPRI` |
| `POST` | `/api/lavori/{l}/task/{t}/ore` | `ORE_ALLOCA_TUTTI` / `_PROPRI` |
| `GET` | `/api/lavori/{l}/richieste-ore` | `LAVORI_VISUALIZZA_TUTTI` / `_PROPRI` |
| `POST` | `/api/lavori/{l}/richieste-ore` | `ORE_RICHIEDI`, `ORE_ALLOCA_TUTTI`, `ORE_RICHIESTE_GESTISCI` |
| `GET` | `/api/richieste-ore` | `ORE_RICHIEDI`, `ORE_RICHIESTE_GESTISCI`, `LAVORI_VISUALIZZA_TUTTI` |
| `GET` | `/api/richieste-ore/in-attesa` | `ORE_RICHIESTE_GESTISCI` |
| `GET` | `/api/richieste-ore/{id}/contesto` | `ORE_RICHIESTE_GESTISCI` |
| `POST` | `/api/richieste-ore/{id}/gestisci` | `ORE_RICHIESTE_GESTISCI` |

`/contesto` serve a decidere: restituisce i numeri del lavoro attorno alla
richiesta, così chi approva non deve aprire tre schermate per capire se ha
senso. `gestisci` approva, approva in parte o rifiuta, e l'approvazione alza il
monte ore scrivendo un `movimento_ore` (2.4).

### Assenze — `AssenzeController`

| | rotta | permesso |
|---|---|---|
| `GET` | `/api/mie-assenze` | `ASSENZE_RICHIEDI` |
| `POST` | `/api/mie-assenze` | `ASSENZE_RICHIEDI` |
| `PUT` | `/api/mie-assenze/{id}` | `ASSENZE_RICHIEDI` |
| `POST` | `/api/mie-assenze/{id}/ritira` | `ASSENZE_RICHIEDI` |
| `GET` | `/api/assenze` | `ASSENZE_GESTISCI` |
| `GET` | `/api/assenze/calendario` | `ASSENZE_GESTISCI` |
| `POST` | `/api/assenze/{id}/approva` | `ASSENZE_GESTISCI` |
| `POST` | `/api/assenze/{id}/rifiuta` | `ASSENZE_GESTISCI` |

### Consuntivi — `ReportController`, `TempoController`

| | rotta | permesso |
|---|---|---|
| `GET` | `/api/report/persone` | `TEMPO_VISUALIZZA_TUTTI` |
| `GET` | `/api/report/persone/{utenteId}` | `TEMPO_VISUALIZZA_TUTTI` |
| `GET` | `/api/tempo/utente/{utenteId}` | `TEMPO_VISUALIZZA_TUTTI`, `TEMPO_VISUALIZZA_LAVORO` |
| `GET` | `/api/tempo/da-confermare` | `TEMPO_VISUALIZZA_TUTTI` |

### Utenti, ruoli, permessi — `UtentiController`, `RuoliController`

| | rotta | permesso |
|---|---|---|
| `GET` | `/api/utenti` | `UTENTI_VISUALIZZA` |
| `POST` | `/api/utenti` | `UTENTI_CREA` **e** `RUOLI_ASSEGNA` |
| `PATCH` | `/api/utenti/{id}` | `UTENTI_DISATTIVA` |
| `PUT` | `/api/utenti/{id}/password` | `UTENTI_RESET_PASSWORD` |
| `GET` | `/api/utenti/{id}/permessi` | `UTENTI_VISUALIZZA` **e** `RUOLI_ASSEGNA` |
| `PUT` | `/api/utenti/{id}/permessi/{codice}` | `RUOLI_ASSEGNA` |
| `DELETE` | `/api/utenti/{id}/permessi/{codice}` | `RUOLI_ASSEGNA` |
| `GET` | `/api/ruoli` | `RUOLI_ASSEGNA`, `RUOLI_GESTISCI` |

Le due rotte con **`and`** e non `hasAnyAuthority` sono volute: creare un utente
significa assegnargli un ruolo, e vedere i permessi di qualcuno serve solo a chi
li può cambiare. `PUT` e `DELETE` su `/permessi/{codice}` sono i due versi degli
scostamenti di 2.2.

---

## 4. L'infrastruttura

Tre percorsi, e **uno solo è quello di produzione**.

| file | a cosa serve | stato |
|---|---|---|
| `docker-compose.yml` | sviluppo: `db` + `phpmyadmin`, più `backend`/`frontend` sotto il profilo `app` | in uso |
| `compose.prod.yml` | **il server**: un host, tre servizi, una porta pubblicata | in uso |
| `k8s/` | manifest per un cluster | **scheletro mai eseguito** |
| `db-init/10-utenze.sh` | le due utenze del database, alla nascita del volume | in uso (solo prod) |
| `frontend/nginx.conf` | proxy `/api`, ripiego sull'indice, intestazioni di sicurezza | dentro l'immagine |
| `.github/workflows/ci.yml` | test, tipi, profilo prod, e lo stack di produzione vero | in uso |

### 4.1 Perché due file di compose e non due profili

`docker-compose.yml` **non** attiva `SPRING_PROFILES_ACTIVE=prod`. Quindi
Flyway esegue anche `db/dev` e `ddl-auto` resta `update`: due comportamenti così
diversi non devono stare a tre righe di distanza, dove si sceglie quello
sbagliato per distrazione. Il percorso di sviluppo non diventa quello di
produzione aggiungendo un flag — è un altro file.

Le differenze che contano:

| | sviluppo | produzione |
|---|---|---|
| dati di prova (`db/dev`) | caricati | esclusi, e **assenti dal jar** |
| schema | `ddl-auto=update` | `validate`: non parte se le entità divergono |
| primo utente | i sette del seed | `GESTIONALE_AMMINISTRATOREINIZIALE_*`, solo su tabella vuota |
| Swagger | aperto | spento |
| utenze del database | una | **due**, DML e DDL separati |
| porta del database | `3306` pubblicata | nessuna |
| phpMyAdmin | c'è | **non c'è** |
| cookie | normale | `${COOKIE_SECURE:true}` |
| volume dei dati | bind mount `./.data/mysql` | volume nominato `dati-mysql` |

Il bind mount in sviluppo è comodo (si guarda, si cancella); in produzione un
volume nominato evita i problemi di lock del datadir su filesystem montato e
non se ne va con un `rm -rf` della cartella del progetto.

### 4.2 Quello che `compose.prod.yml` garantisce

- **`${VAR:?messaggio}`** sui segreti: `docker compose up` si ferma *prima* di
  avviare qualunque cosa se una chiave del `.env` manca, e dice quale. Senza il
  `:?`, Spring partirebbe con il segnaposto non risolto e l'errore incolperebbe
  il database — «Access denied for user '${DB_USERNAME}'».
- **`depends_on: condition: service_healthy`** sul database, e
  `service_started` fra frontend e backend: nginx deve poter servire la pagina
  mentre il backend applica le migrazioni, altrimenti durante un riavvio lungo
  il browser non trova nessuno invece di trovare l'applicazione che dice che il
  server non risponde.
- **Nessun `container_name`**, al contrario dello sviluppo: un nome fisso è
  unico per tutto il motore Docker, e i due mondi non potrebbero convivere
  sulla stessa macchina.
- **Healthcheck veri**: quello del backend interroga
  `/actuator/health/readiness`, che comprende il datasource — quindi si accorge
  di un backend vivo che ha perso il database, cosa che una prova sulla porta
  TCP non vedeva. `curl` è installato nell'immagine apposta: `eclipse-temurin`
  non lo ha.
- **Immagini non-root dove si può**: il backend gira come UID 10001, dichiarato
  nel `Dockerfile` e non solo nel `securityContext` di k8s, così vale comunque
  lo si avvii. Il frontend resta root perché `nginx:alpine` scrive il pid e poi
  scende a `nginx` da sé; per averlo non-root serve `nginx-unprivileged`, che è
  un cambio di immagine e di porta.

### 4.3 Quello che non fa, e resta da decidere

- **HTTPS.** Il frontend ascolta in HTTP, quindi `COOKIE_SECURE: "false"`. Con
  `true` e senza TLS il login risponde 200 e ogni richiesta successiva è
  anonima, senza nessun errore che lo spieghi. `Strict-Transport-Security` è
  deliberatamente **assente** da `nginx.conf`: su un server interno in HTTP si
  renderebbe irraggiungibili da ogni postazione che abbia aperto la pagina una
  volta, e non si disfa cancellando la riga. Entrambe si sistemano il giorno
  del certificato, insieme.
- **La copia dei dati.** Vivono nel volume `dati-mysql` e lì restano: copiarli
  altrove, **e provare che si rimettano dentro**, è lavoro
  dell'amministratore di sistema.
- **I tetti di memoria.** Nessun `deploy.resources.limits`, su un server che
  compila da sé: durante un `up -d --build` convivono Maven, Node, il backend
  vecchio e MySQL, e se la RAM finisce è il kernel a scegliere la vittima — che
  può essere `mysqld` nel mezzo di una migrazione.
- **La rotazione dei log** dei container, e `docker image prune` dopo gli
  aggiornamenti.
- **L'avvio automatico dopo un riavvio del server.** `restart: unless-stopped`
  riavvia i **container**, non il demone Docker. Su Linux serve
  `systemctl enable docker` — è il passo che si salta, e lo verifica la Fase 1
  di CONSEGNA.md. Su **Windows con Docker Desktop** serve una sessione utente
  aperta, quindi dopo un riavvio il gestionale resta giù finché qualcuno non
  fa il login: è una ragione in più per installare su Linux.

### 4.4 Kubernetes: cosa c'è e cosa manca

I manifest in `k8s/` descrivono namespace, Deployment e Service per backend e
frontend, un Ingress con cert-manager, probe, `securityContext` e limiti di
risorse. **Non sono mai stati eseguiti**, e prima di provarci va saputo che:

- **non contengono il database**: `DB_URL` punta a un host esterno
  (`CAMBIAMI-host-del-database`), quindi presuppongono un MySQL gestito fuori
  dal cluster — e lì `db-init/10-utenze.sh` non gira, perciò le due utenze
  vanno create a mano (la procedura è in RILASCIO.md §10);
- **il Secret è in chiaro** in un file versionato, con valori `CAMBIAMI`. Per
  un uso vero serve SealedSecrets, SOPS o External Secrets: così com'è, invita
  a committare la password del server;
- **`frontend: replicas: 2`** va bene, ma `backend: replicas: 1` **non si può
  alzare**: le sessioni stanno nella memoria del processo (2.1);
- **non c'è NetworkPolicy**, quindi qualunque pod del cluster potrebbe parlare
  al backend sulla 8080 scavalcando nginx — e con lui le intestazioni di
  sicurezza. In compose lo impedisce la rete interna, in k8s no.

La `nginx.conf` sta **dentro l'immagine** e la ConfigMap la monta sullo stesso
percorso: chi deve cambiarla senza ricostruire l'immagine continua a poterlo
fare, e chi avvia l'immagine da sola ha un frontend che funziona comunque.

### 4.5 La CI è l'unica prova che il rilascio stia in piedi

`ci.yml` ha quattro lavori, e **non** provano la stessa cosa:

| lavoro | prova | quando |
|---|---|---|
| `build` | compilazione e test contro un MySQL vero | sempre |
| `frontend` | `oxlint` e `tsc -b` | sempre |
| `profilo-produzione` | il jar avviato **con il profilo prod** su schema migrato senza `db/dev`, utenze separate, login dell'amministratore iniziale | sempre |
| `stack-di-produzione` | i due Dockerfile, `compose.prod.yml`, `db-init`, nginx e il proxy: il rilascio vero | solo su `deploy` o a mano |

`profilo-produzione` esiste perché `ddl-auto=validate` è il controllo più
importante del profilo prod, e il lavoro `build` gira **senza** profilo, cioè
con `update`, che una divergenza la sistema da sé in silenzio: senza quel
lavoro, la prima esecuzione di `validate` sarebbe stata in produzione, su un
container che non parte.

`stack-di-produzione` controlla tre invarianti che un avvio riuscito **non**
garantisce: un solo utente nella tabella `utente` (`db/dev` non è entrato),
`/v3/api-docs` a 404 interrogato dall'interno, e nessuna porta pubblicata dal
servizio `db`. Verifica anche che il cookie **regga una seconda richiesta**,
perché il login risponde 200 anche quando il cookie non torna indietro.

> ⚠️ Il verde della CI non impedisce da sé di unire una PR rossa: serve che il
> controllo sia dichiarato obbligatorio in *Settings → Branches* su `deploy`.
> Senza quel passo la pipeline informa, non protegge.

---

## 5. Dove continuare

| | |
|---|---|
| avviare in locale, dati di prova, diagnostica | [SVILUPPO.md](SVILUPPO.md) |
| il *perché* del rilascio e la diagnostica del server | [RILASCIO.md](resources/RILASCIO.md) |
| la sequenza del giorno della consegna | [CONSEGNA.md](resources/CONSEGNA.md) |
| il monte ore in dettaglio | [`MD files/monte_ore_report.md`](resources/MD%20files/done/monte_ore_report.md) |
| il calendario lavorativo | [calendario.md](resources/calendario.md) |
