# Note di consegna — `fisStati1.md`

Cosa è stato fatto, cosa è stato adattato e cosa non era applicabile.

---

## 1. Adattamenti generali

Il documento è scritto in SQL PostgreSQL e con nomi di tabella al plurale. Lo
schema reale è MySQL 8.4 con nomi al singolare. La traduzione applicata:

| Documento | Reale |
|---|---|
| `dipendenti` | `utente` |
| `lavori` | `lavoro` |
| `richieste_assenza` | `richiesta_assenza` (singolare, come `richiesta_ore`) |
| `BIGSERIAL` | `BIGINT AUTO_INCREMENT` |
| `TIMESTAMPTZ` | `DATETIME` |
| `JSONB` | `JSON` |
| `now()` | `CURRENT_TIMESTAMP` |
| valori di stato minuscoli (`attivo`) | maiuscoli (`ATTIVO`), come il resto dello schema |
| `esito_da`, `esito_il`, `esito_nota` | `gestita_da_id`, `gestita_il`, `nota_esito`, come `richiesta_ore` |

In MySQL un `CHECK` non si modifica: va eliminato e ricreato. Tutte le
migrazioni nuove lo fanno, e **prima** delle `UPDATE`, altrimenti il vincolo
vecchio rifiuta i valori nuovi.

`V3` e `V5` non sono state toccate: sono già applicate, e cambiarle
invaliderebbe il checksum di Flyway. Le supera la `V10`.

---

## 2. Gli stati (sezione 1)

I quattro stati comuni sono `ATTIVO`, `IN_CORSO`, `COMPLETATO`, `ANNULLATO`,
per lavori e task.

**Scelta fatta: il lavoro conserva un quinto stato, `BOZZA`.** Il documento
prevede quattro stati, ma `BOZZA` nello schema reale regge due regole che non
hanno equivalente fra i quattro:

- `ck_lavoro_responsabile` — solo in bozza il responsabile può mancare;
- `ServizioLavori.elimina` — solo una bozza senza task si cancella davvero,
  invece di essere annullata.

Togliendolo si perdevano entrambe. Una task non ha questo problema: nasce
dentro un lavoro, quindi già completa.

Rinomini applicati: task `DA_FARE` → `ATTIVO`, `COMPLETATA` → `COMPLETATO`,
`ANNULLATA` → `ANNULLATO`; lavoro `CHIUSO` → `COMPLETATO`, più `IN_CORSO` che
prima non esisteva.

I predicati (`aperta()`, `finale()`, `selezionabile()`, `chiuso()`) stanno sugli
enum e non ripetuti nei servizi: la stessa condizione era scritta in otto punti.

---

## 3. Annullamento di un lavoro (sezione 2)

`POST /api/lavori/{id}/annulla` esisteva già. Aggiunto:

- `lavoro.annullato_il`, con il vincolo che lo tiene d'accordo con lo stato;
- **tutte le task del lavoro passano ad `ANNULLATO`**, comprese le completate,
  con un `UPDATE` in blocco (`TaskRepository.annullaTutteDelLavoro`);
- `GET /api/lavori` esclude gli annullati, salvo `?includi_annullati=true`;
- l'ordinamento mette gli annullati in fondo, lato server;
- interruttore «Mostra annullati» nell'elenco, righe attenuate e badge rosso;
- le task annullate restano visibili nel dettaglio del lavoro, sotto quelle
  aperte.

**Scelta fatta: le ore allocate e non consumate non tornano nel pool.** Il
documento non lo chiede, e il monte ore libero di un lavoro che non si farà è
un numero senza significato: restituirlo lo farebbe sembrare disponibile per
altro. Le ore già registrate restano nello storico.

### Non applicabile

- `DELETE FROM allocazioni` / `UPDATE allocazioni SET data_fine` — **la tabella
  `allocazioni` non esiste.** Le ore sono `task.ore_allocate` più la tabella
  `richiesta_ore`, e nessuna delle due ha le colonne `data_inizio`/`data_fine`
  su cui il documento opera.
- `/me/calendario` e `/me/lavori` — **non esistono**: vengono da
  `sezione-dipendente.md`, che non è implementato. L'elenco dei lavori è uno
  solo e si filtra per permesso, quindi copre anche «I miei lavori».
  `ServizioTask.segniDelPeriodo`, che è la cosa più vicina a un calendario,
  esclude già le task non aperte, quindi dopo l'annullamento di un lavoro le sue
  task spariscono da sé.

---

## 4. Colori (sezione 3)

I quattro colori del documento sono in `index.css` come variabili, più
l'arancione «in attesa» e il viola delle assenze. Entrano solo in un badge a
pillola (fondo al 13%, testo pieno) e in un punto, tramite i componenti
`componenti/Stato.tsx`. L'etichetta di testo c'è sempre.

**Scelta fatta: i valori per il tema scuro non sono stati inseriti.** Il
gestionale dichiara `color-scheme: light` e non ha un tema scuro: metterli
darebbe badge in colori notturni su una pagina chiara. Vanno aggiunti quando
arriverà il tema scuro, insieme al resto della tavolozza.

`BOZZA` usa il grigio di `ATTIVO`: il documento prevede quattro colori, e una
bozza è la cosa più vicina a «creato, non ancora iniziato».

---

## 5. Registrazione delle ore (sezione 4)

- La tendina delle task filtrava già sullo stato della task; ora filtra **anche
  sullo stato del lavoro** (`TaskRepository.trovaAperteAssegnateA`).
- Il server risponde `422` con i codici del documento:
  `TASK_NON_SELEZIONABILE` e `LAVORO_CHIUSO` (prima erano `409`).

**Adattamento:** il documento cita `GET /me/task?stato=attivo,in_corso`. Le task
selezionabili arrivano già dentro il payload della giornata
(`Giornata.taskDisponibili`), che è una chiamata invece di due: la regola è la
stessa, scritta nella query che riempie quel campo.

I codici viaggiano in testa al messaggio (`CODICE: frase in italiano`); il
frontend li riconosce con `codice()` e mostra la frase con `testo()`.

---

## 6. Utenti (sezione 5)

`utente.attivo` esisteva già dalla `V1`. Aggiunto `disattivato_il`.

- `PATCH /api/utenti/{id}` con `{"attivo": false|true}`, permesso
  `UTENTI_DISATTIVA` (anche questo esisteva già nel catalogo).
- Nessuno può cambiare lo stato della propria utenza: `409`.
- **Login:** le credenziali si verificano **prima**, e solo se sono giuste
  arriva `403 UTENTE_DISATTIVATO` con il messaggio del documento.
- Sessioni aperte: cadono alla richiesta successiva
  (`FiltroUtenteAggiornato`).
- Non compare nei selettori di assegnazione; le assegnazioni esistenti restano.
- Resta visibile in elenco con badge «Disattivato» e la data, e un interruttore
  per riattivarlo.

**Scelta tecnica importante.** `ServizioDettagliUtente` non marca più l'utente
come `disabled`, perché `DaoAuthenticationProvider` controlla quel flag
**prima** della password: con quello, chi tira a indovinare un indirizzo
scoprirebbe dall'errore quali account esistono — l'opposto di ciò che il
documento chiede. Il flag viaggia dentro un `UserDetails` nostro,
`UtenteCollegato`, che resta sempre `enabled` ed espone `attivo()`. Login e
filtro lo leggono da lì, quindi **senza nessuna query in più**.

---

## 7. Ferie e permessi (sezione 6)

Tabella `richiesta_assenza`, con i vincoli del documento più tre che il
documento implica senza scriverli: un permesso orario è sempre una fascia, le
ferie sono sempre giornate intere, e un rifiuto ha sempre una nota.

Package `assenza`: entità, tre enum (`TipoAssenza`, `StatoAssenza`,
`MotivoPermesso`), `DettagliAssenza` nella colonna `JSON`, repository, servizio
e controller.

| Metodo | Percorso |
|---|---|
| `GET` | `/api/mie-assenze` |
| `POST` | `/api/mie-assenze` |
| `PUT` | `/api/mie-assenze/{id}` |
| `POST` | `/api/mie-assenze/{id}/ritira` |
| `GET` | `/api/assenze?stato=&utente_id=` |
| `POST` | `/api/assenze/{id}/approva` |
| `POST` | `/api/assenze/{id}/rifiuta` |

**Adattamento dei percorsi:** `/api/mie-assenze` invece di `/me/richieste`,
per seguire `/api/mie-task`, che è già la convenzione del gestionale per «le
mie cose».

Permessi nuovi: `ASSENZE_RICHIEDI` (concesso a Project Manager e Dipendente) e
`ASSENZE_GESTISCI` (solo ai ruoli protetti). La categoria `ASSENZE` è stata
aggiunta a `ck_permesso_categoria`.

Tutte le regole della tabella del documento sono implementate con i loro
codici: `CAMPI_MANCANTI`, `DATA_PASSATA`, `RICHIESTA_SOVRAPPOSTA`,
`RICHIESTA_CHIUSA`, `NOTA_OBBLIGATORIA`.

**Scelta fatta: la sovrapposizione si valuta sulle sole date, non sugli
orari.** Due permessi orari nello stesso giorno in fasce diverse risultano
quindi sovrapposti. È la soluzione più semplice e la più prudente — chi ha due
permessi lo stesso giorno li chiede come uno — e il documento non dice
altrimenti.

**Scelta fatta: i giorni lavorativi li conta il server**
(`ServizioCalendario`), che conosce festività e giorni lavorativi configurati, e
arrivano già scritti nel campo `riepilogo`. Il form mostra un conto
approssimato mentre si compila (solo sabato e domenica esclusi), e lo dichiara.

Frontend: `PaginaMieAssenze` (scelta della tipologia, poi il form di quella
tipologia, con riepilogo e modifica/ritiro) e `PaginaAssenze` per l'admin
(approva, rifiuta con nota obbligatoria, filtro per stato). Due voci nella
barra.

L'avviso per le ore registrate in un giorno di assenza approvata è il campo
`Giornata.assenzaApprovata`, mostrato nella pagina della giornata: **avviso,
salvataggio consentito**, come chiede il documento.

### Non applicabile

- **Calendario delle assenze** (eventi viola, giorni coperti non segnalati):
  non c'è un calendario. `AssenzaRepository.trovaApprovateDelPeriodo` è già
  pronta per quando ci sarà.
- **Notifiche**: il documento le chiede «se il sistema di notifiche è già
  presente». Non c'è.

---

## 8. Stato della build e test

Come chiesto al punto 7, **non sono stati scritti né eseguiti test, né fatte
verifiche manuali**. Sono state fatte solo le compilazioni, che sono parte del
costruire il codice:

- backend: `mvnw clean test-compile` — pulito;
- frontend: `tsc --noEmit`, `oxlint`, `npm run build` — puliti.

**Da sapere: tre test esistenti asseriscono il comportamento che il documento
cambia, e falliranno.** Non li ho toccati oltre a ciò che serviva a far
compilare i sorgenti, perché aggiornarli è scrivere test. Sono in
`AutenticazioneControllerTest`:

| Test | Perché ora fallisce |
|---|---|
| `conEmailInesistenteRispondeComeConLaPasswordSbagliata` | assertisce il messaggio vecchio, «…oppure utenza non attiva.» |
| `utenteDisattivatoNonEntraNemmenoConLaPasswordGiusta` | assertisce `401`; la sezione 5 richiede `403 UTENTE_DISATTIVATO` |
| `utenteDisattivatoMentreEraCollegatoVieneRifiutatoAllaRichiestaDopo` | costruisce uno `UserDetails` con `.disabled(…)`; ora il flag è `UtenteCollegato.attivo()` |

Per far compilare i sorgenti di test ho dovuto adeguare le chiamate ai record
che hanno un campo in più (`RigaLavoro.annullatoIl`, `Giornata.assenzaApprovata`,
`RigaUtente.disattivatoIl`), la firma di `ServizioLavori.elenco` e la costante
`StatoTask.DA_FARE`. Senza, `mvn package` non costruiva — e quindi non si
costruiva nemmeno l'immagine Docker, che compila i test anche con `-DskipTests`.

## 9. Aggiornamento: le migrazioni sono state eseguite

Alla consegna le `V10`–`V12` non erano mai state eseguite, perché avviare
l'applicazione sarebbe stata una verifica manuale, vietata dal punto 7. **È
così che tre errori sono arrivati fino all'uso reale.** Sono stati corretti, e
adesso le migrazioni girano da V1 a V12 su un database ricostruito da zero.

| Difetto | Causa | Correzione |
|---|---|---|
| `V12` falliva | l'elenco di `ck_permesso_categoria` era copiato dalla `V1`, ma la `V8` aveva già aggiunto `'ORE'`: il CHECK contraddiceva 5 righe esistenti | elenco ripreso dalla `V8` più `ASSENZE`; `ordine` spostato a 68–69 perché 70 è di `SISTEMA` |
| MySQL non partiva da solo | la riga `spring.docker.compose.file` era andata persa da `application.properties`: Spring Boot auto-rilevava `backend/compose.yaml`, lo stub vuoto, e non avviava niente **senza lamentarsi** | riga ripristinata e stub eliminato, così una futura perdita della proprietà dà un errore invece di un silenzio |
| `R__dati_sviluppo.sql` falliva | Paolo Ferrari è `attivo = FALSE` senza data, e il vincolo `ck_utente_disattivato` della `V11` la pretende | la riga porta la sua `disattivato_il` |

Verificato sul database vero, non più solo compilato:

- 13 migrazioni su 13 riuscite, nessuna violazione di vincolo nei log;
- creazione di lavoro e task — il sintomo segnalato — risponde `201`, stato `ATTIVO`;
- annullamento di un lavoro: `annullato_il` valorizzato, la task passa ad
  `ANNULLATO`, il lavoro sparisce dall'elenco e torna con `?includi_annullati=true`;
- **assenze**: ferie e permesso retribuito creati, colonna `JSON` `dettagli`
  letta e riscritta correttamente, riepiloghi calcolati («5 giorni lavorativi»,
  «2 h 30 min»), approvazione, e le regole `NOTA_OBBLIGATORIA`,
  `RICHIESTA_SOVRAPPOSTA`, `DATA_PASSATA`;
- le query con `IS NULL OR` di `AssenzaRepository` funzionano con zero, uno e
  due filtri.

I due punti che la sezione 8 segnalava come non verificati — il mapping `JSON`
e le query con `IS NULL OR` — sono quindi confermati funzionanti.

I tre test di `AutenticazioneControllerTest` elencati sopra restano da
aggiornare: non li ho toccati.
