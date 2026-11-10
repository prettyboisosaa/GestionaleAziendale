# Sezione dipendente — piano di ridefinizione

Ridisegno della parte del gestionale che un **dipendente** usa ogni giorno: le
task che gli sono state affidate, il calendario di ciò che deve fare, e la
registrazione di ciò che ha fatto davvero.

Data: 5 novembre 2026. Scritto dopo la lettura dello stato attuale del codice —
backend, schema, frontend — e non da zero: buona parte di ciò che serve esiste
già e va collegata, non costruita.

---

## 1. Contesto

Oggi il gestionale è costruito **intorno al lavoro**. Si entra da *Lavori*, si
apre un lavoro, e dentro il lavoro si vedono le sue task. È il punto di vista
giusto per chi distribuisce il lavoro — amministratore e responsabile — ed è
quello sbagliato per chi lo esegue.

Un dipendente ha il ruolo `Dipendente`, cioè quattro permessi:

| permesso | oggi |
|---|---|
| `LAVORI_VISUALIZZA_PROPRI` | vede i lavori di cui è responsabile **o in cui ha task assegnate** |
| `TASK_VISUALIZZA_ASSEGNATE` | **nel catalogo dalla V1, nessuna rotta lo usa** |
| `TASK_AGGIORNA_STATO` | funziona, ma solo dalla pagina del singolo lavoro |
| `DICHIARAZIONI_COMPILA` | la pagina *Giornata*, completa |

Il risultato è che per sapere che cosa deve fare, un dipendente deve aprire
*Lavori*, entrare in ogni lavoro uno per uno, e in ognuno leggere l'elenco
delle task — che `ServizioTask.delLavoro` gli filtra correttamente sulle sue
assegnazioni, ma lavoro per lavoro. **Non esiste nessuna schermata che risponda
alla domanda «che cosa devo fare?».** E non esiste nessun calendario: l'unica
vista temporale è *Giornata*, che mostra un giorno alla volta, scelto in un
campo `<input type="date">`.

## 2. Obiettivo

Una sezione dedicata a chi esegue il lavoro, con tre risposte:

1. **Le mie task** — tutto ciò che mi è stato affidato, in tutti i lavori, in
   un elenco solo, con scadenze e stato di avanzamento.
2. **Calendario** — che cosa cade quando: inizi, scadenze, giorni già
   dichiarati, giorni lavorativi rimasti in bianco, ferie e festività.
3. **Giornata** — la registrazione di che cosa ho fatto realmente oggi: la
   pagina che già c'è, portata dentro la sezione e resa navigabile nel tempo.

Il principio che tiene insieme le tre: **nessuna delle tre inventa dati.**
Tutte e tre leggono ciò che esiste già — `assegnazione_task`, `task`,
`dichiarazione`, `voce_dichiarazione`, `festivita`, `impostazione` — e lo
dispongono dal punto di vista della persona invece che del progetto.

---

## 3. Che cosa esiste già, e si riusa

Prima di progettare conviene essere precisi su quanto è già in piedi: è molto,
ed è la ragione per cui questo piano **non chiede nessuna migrazione del
database**.

| cosa serve | dove è già | che cosa manca |
|---|---|---|
| chi è assegnato a cosa | `assegnazione_task`, con `rimossa_il IS NULL` a dire «attiva» e `uk_assegnazione_attiva` a garantirne una sola per persona e task | niente |
| le mie task aperte | `TaskRepository.trovaAperteAssegnateA(utenteId)` | restituisce id, titolo e lavoro; solo `DA_FARE` e `IN_CORSO`; senza date, stato, ore |
| i lavori in cui sono coinvolto | `LavoroRepository.trovaIdConTaskAssegnate(utenteId)` | niente |
| ore consumate di una task | `TaskRepository.sommaOreConsumateDellaTask` e `sommaOreConsumatePerTask` | manca la somma **mia**, per distinguere le mie ore da quelle del team |
| sforamento di una task | calcolato in `RigaTask.da`, mai salvato | niente |
| cambiare stato alla task | `POST /api/lavori/{id}/task/{idTask}/stato`, con `TASK_AGGIORNA_STATO` limitato dal servizio alle task a cui si è assegnati | la rotta è raggiungibile solo da `/lavori/:id` |
| la giornata | otto rotte sotto `/api/dichiarazioni`, `ServizioDichiarazioni` completo | niente |
| le mie giornate di un periodo | `GET /api/dichiarazioni?da=&a=` → `RigaGiorno[]`, con `DichiarazioneRepository.sommaOrePerGiorno` in una query sola | **nessuna pagina la chiama**: esiste e non è usata |
| giorni lavorativi e festività | `ServizioCalendario.eLavorativo`, `giorniLavorativiDopo`, tabella `festivita`, parametro `calendario.giorni_lavorativi` | nessuna rotta li espone al frontend, e `FestivitaRepository.dateTra` restituisce le date senza la descrizione |
| tetto giornaliero e finestra di correzione | `dichiarazione.ore_massime_giorno` = 8, `dichiarazione.giorni_modifica` = 1 | niente |
| il permesso per «le mie task» | `TASK_VISUALIZZA_ASSEGNATE`, ordine 52, concesso ai ruoli protetti e al ruolo Dipendente | **nessuna rotta lo nomina** |

L'ultima riga è il punto di partenza di tutto il piano: il permesso giusto è
stato scritto nella V1 e poi mai usato. Questa sezione è la rotta che gli
mancava.

## 4. Le lacune da colmare

1. **Nessuna vista trasversale ai lavori.** Le task si leggono solo sotto un
   lavoro, perché la rotta è `/api/lavori/{idLavoro}/task`. Serve una rotta che
   parta dalla persona.
2. **Nessun calendario.** `GET /api/dichiarazioni?da=&a=` restituisce già le
   giornate di un periodo e nessuno la chiama.
3. **Il frontend non sa quali giorni sono lavorativi.** Senza festività e
   `calendario.giorni_lavorativi` un calendario non può distinguere un sabato
   da un lunedì dimenticato, e finirebbe per reinventare le regole lato client
   — una seconda verità, esattamente ciò che il progetto evita sulle ore.
4. **Nessun segnale sui giorni in bianco.** Il gestionale non ha notifiche per
   scelta: «ciò che una notifica annuncerebbe è messo in evidenza nelle
   schermate». Per le giornate non dichiarate quella evidenza non esiste ancora
   da nessuna parte.
5. **`TASK_VISUALIZZA_ASSEGNATE` inutilizzato**, con la conseguenza che
   togliere quel permesso a qualcuno oggi non cambia niente.

---

## 5. Disegno della sezione

### Una sezione, tre schede

Una sola voce nella barra — **«Il mio lavoro»** — e tre schede sotto:

```
/mio-lavoro/task         Le mie task
/mio-lavoro/calendario   Calendario
/mio-lavoro/giornata     Giornata        (la pagina di oggi, spostata)
```

Tre voci separate nella barra sarebbero state più semplici da scrivere, ma la
barra è piatta e mostrerebbe a un amministratore sette voci di cui tre sue solo
in teoria. Una sezione con le schede dice anche un'altra cosa, giusta: **le tre
schermate sono tre viste dello stesso oggetto** — il lavoro di una persona — e
si attraversano l'una con l'altra. Da una task si va al giorno in cui
registrarla, dal calendario si apre la giornata.

`/giornata` resta come **reindirizzamento** a `/mio-lavoro/giornata`: è un
indirizzo che qualcuno può avere nei preferiti, e romperlo non serve a niente.

### Scheda 1 — Le mie task

Tutte le task a cui sono assegnato, di ogni lavoro, ordinate per **urgenza e
non per lavoro**: prima le `IN_CORSO`, poi le `DA_FARE`, dentro ciascun gruppo
per scadenza crescente. Le `COMPLETATA` e le `ANNULLATA` ci sono ma stanno in
fondo, richiudibili: servono a ricordare che cosa si è chiuso, non a occupare
lo spazio di ciò che resta da fare.

Per ogni task:

- titolo e descrizione, lavoro (codice e nome) e **chi è il responsabile** — è
  la persona a cui chiedere, e il piano del monte ore dice esplicitamente che
  le ore aggiuntive si chiedono «a voce al pm»: il nome va messo dove serve;
- data di inizio e scadenza, con **quanti giorni restano**, e il segno di
  ritardo quando la scadenza è passata e la task non è chiusa;
- **ore allocate**, **ore consumate in tutto** e **le mie ore** — tre numeri
  diversi che oggi il dipendente non distingue: quanto è coperto dal budget,
  quanto ha già consumato la task fra tutti gli assegnati, quanto ci ho messo
  io;
- il badge di **sforamento**, lo stesso che vede il responsabile: se la task
  sfora, chi ci lavora deve saperlo prima di continuare a registrarci ore;
- due azioni, e solo due: **«Inizia»** (`DA_FARE` → `IN_CORSO`) e
  **«Completata»** (`IN_CORSO` → `COMPLETATA`).

> **Attenzione a «Completata».** Non è un cambio di etichetta: in
> `ServizioTask.cambiaStato`, portare una task a `COMPLETATA` o `ANNULLATA`
> chiama `ServizioOre.rilasciaResiduo`, che **riduce l'allocazione alle ore
> consumate e restituisce il residuo al pool del lavoro** con un movimento
> `RILASCIO`. Un dipendente che chiude una task sta muovendo il budget del
> progetto, e il passaggio è **definitivo**: una task chiusa non si riapre.
> Tre conseguenze per il disegno:
>
> 1. il pulsante chiede conferma, e la conferma **dice il numero**:
>    «Completando questa task torneranno N ore nel pool del lavoro. Non si
>    riapre.»;
> 2. `ANNULLATA` **non si offre al dipendente**: annullare una task è decidere
>    che non si farà, ed è una decisione di chi l'ha creata;
> 3. non si inventa nessun permesso nuovo: è esattamente ciò che
>    `TASK_AGGIORNA_STATO` concede già oggi dalla pagina del lavoro. La sezione
>    lo rende solo raggiungibile — e quindi anche più facile da revocare a chi
>    non deve averlo, visto che il permesso ora conta.

### Scheda 2 — Calendario

Un mese per volta, con le frecce per scorrere e il ritorno a «oggi». Ogni
giorno è una cella, e la cella dice **che giorno è** e **che cosa c'è**:

- **non lavorativo** — fuori da `calendario.giorni_lavorativi`, oppure in
  `festivita`, e in quel caso con il nome della festività;
- **dichiarato** — ore totali, tipo di giornata (lavorata, ferie, permesso,
  malattia) e stato (bozza o inviata);
- **da compilare** — giorno lavorativo passato, oggi compreso, senza
  dichiarazione. È il segnale che oggi non esiste da nessuna parte, e il motivo
  principale per cui questa scheda serve;
- **inizi e scadenze delle mie task** — il «che cosa devo fare» del calendario:
  una task compare nel giorno in cui parte e nel giorno in cui scade.

In testa al mese, quattro numeri: giorni lavorativi, giorni dichiarati, giorni
**da compilare**, ore registrate nel mese. Il conto dei giorni da compilare è
quello che va in evidenza, e si ripete come banda in cima alla scheda
*Giornata*: è lì che si va a rimediare.

Un clic su un giorno apre `/mio-lavoro/giornata?data=…`. Il calendario è la
vista d'insieme, la giornata è il posto dove si scrive: non si duplica la
compilazione dentro le celle.

### Scheda 3 — Giornata

La pagina esiste e funziona. Tre aggiunte, nessuna riscrittura:

1. **Navigazione per giorni** — `←` e `→` accanto alla data, con il limite di
   oggi che già c'è (`max={oggi()}`), e `Oggi` per tornare. Il campo data
   resta: scorrere va bene per ieri, non per il 12 del mese scorso.
2. **La data nell'indirizzo** — `?data=2026-11-04`, così il calendario può
   linkare un giorno e un link si può incollare. Oggi lo stato della data vive
   solo in `useState`.
3. **La banda dei giorni da compilare**, con il link al calendario.

Il resto si tocca il meno possibile: il tetto giornaliero, le ore residue, il
tipo di giornata, la finestra di correzione e i messaggi del server sono già
giusti e già testati.

---

## 6. Regole

Nessuna regola nuova sulle ore: valgono quelle del monte ore, già in piedi.
Quello che segue è il comportamento della **sezione**.

### Che cosa compare in «Le mie task»

- Le task con un'**assegnazione attiva** per chi guarda, cioè
  `assegnazione_task.rimossa_il IS NULL`. Un'assegnazione rimossa non si vede
  più: la rimozione è morbida e la riga resta nel database, ma resta come
  storia, non come compito.
- **Tutti gli stati**, non solo i due aperti. Una task completata ieri deve
  essere ancora visibile oggi, altrimenti il giorno dopo averla chiusa la
  persona non trova più traccia del proprio lavoro.
- Anche le task dei **lavori chiusi o annullati**, marcate come tali. Non si
  nascondono: su quelle ore c'è stato del lavoro vero. Ma non si possono né
  avviare né completare, e non compaiono fra le task su cui registrare ore —
  quello lo decide già `trovaAperteAssegnateA`, sullo stato della task.

### Che cosa compare nel calendario

- La finestra è un **mese di calendario**, dal lunedì che lo apre alla domenica
  che lo chiude, perché la griglia va riempita per intero.
- Un giorno è **da compilare** quando è lavorativo, non è nel futuro, e non ha
  nessuna dichiarazione. Ferie e malattia **sono** dichiarazioni: un giorno di
  ferie dichiarato è a posto, non è un buco.
- Oggi conta come da compilare, ma con un tono diverso dagli arretrati: la
  giornata si chiude alla fine, non all'inizio.
- Il **futuro** non è mai da compilare — `ServizioDichiarazioni` rifiuta con
  400 una giornata futura — ma mostra inizi e scadenze: è lì che il calendario
  smette di essere un registro e diventa una previsione.

### Una asimmetria da conoscere (e da confermare)

Nel codice attuale, `ServizioDichiarazioni.modificabile` restituisce vero per
**qualunque** dichiarazione in stato `BOZZA`, senza guardare la data; la
finestra di `dichiarazione.giorni_modifica` si applica solo alle giornate già
`INVIATA`. E `prendiOCrea` crea la dichiarazione del giorno richiesto senza
limiti nel passato.

Conseguenza: **non dichiarare non scade mai, correggere dopo l'invio scade in
un giorno lavorativo.** Chi non ha dichiarato il 3 di settembre può dichiararlo
oggi; chi l'ha dichiarato e inviato non può più correggerlo da settimane.

Non è un difetto da correggere di nascosto: è il comportamento che permette di
rimediare agli arretrati, ed è ciò che rende utile il conto dei «giorni da
compilare». Ma è anche una porta aperta, e il calendario la renderà visibile —
quindi la domanda va posta prima, non dopo (vedi §14).

---

## 7. Modello dati

**Nessuna migrazione.** Non serve nessuna tabella nuova, nessuna colonna nuova
e nessun permesso nuovo, e vale la pena dire perché, perché è la verifica che
il modello esistente era giusto:

- «le task che mi sono affidate» è una lettura di `assegnazione_task`;
- «quante ore ci ho messo io» è una `SUM` su `voce_dichiarazione` unita a
  `dichiarazione` per `utente_id`;
- «in ritardo» è `scadenza < oggi AND stato NOT IN ('COMPLETATA','ANNULLATA')`,
  calcolato — come lo sforamento, e per lo stesso motivo: un flag salvato
  andrebbe aggiornato ogni notte da qualcosa, e quel qualcosa qui non c'è;
- «da compilare» è l'assenza di una riga in `dichiarazione`, incrociata con
  `calendario.giorni_lavorativi` e `festivita`;
- il permesso di vedere le proprie task è `TASK_VISUALIZZA_ASSEGNATE`, scritto
  nella V1 e concesso ai ruoli protetti e al ruolo Dipendente.

Una migrazione servirebbe solo per le cose elencate in §13 come fuori ambito —
una priorità sulle task, un tetto ore per persona — e nessuna delle due è
necessaria a questa sezione.

---

## 8. Backend

### Le rotte nuove

| metodo e indirizzo | permesso |
|---|---|
| `GET /api/mie-task` | `TASK_VISUALIZZA_ASSEGNATE` |
| `GET /api/mio-calendario?da=&a=` | `DICHIARAZIONI_COMPILA` |

Due rotte, non cinque. Tutto il resto di cui la sezione ha bisogno esiste già:
la giornata, le sue scritture, e il cambio di stato della task.

Le festività e i giorni lavorativi **non** hanno una rotta propria: viaggiano
dentro `/api/mio-calendario`, già risolti giorno per giorno. Una rotta
`GET /api/calendario/festivita` costringerebbe il frontend a reimplementare
`eLavorativo` — cioè a tenere una seconda copia di una regola che sta nei
parametri di sistema.

### `GET /api/mie-task`

Nuovo `MieTaskController` — `TaskController` è mappato su
`/api/lavori/{idLavoro}/task` e questa rotta parte dalla persona, non dal
lavoro — e un metodo `mie(UserDetails)` su `ServizioTask`.

```java
public record MiaTask(Long taskId, String titolo, String descrizione,
                      LocalDate dataInizio, LocalDate scadenza,
                      StatoTask stato,
                      BigDecimal oreAllocate,
                      BigDecimal oreConsumate,   // di tutti gli assegnati
                      BigDecimal mieOre,         // registrate da me
                      boolean sforamento,        // calcolato
                      boolean inRitardo,         // calcolato
                      long giorniAllaScadenza,   // negativo se passata
                      Long lavoroId, String codiceLavoro, String nomeLavoro,
                      StatoLavoro statoLavoro, String responsabile) { }
```

Una query nativa sola, in `TaskRepository`:

```sql
SELECT t.id, t.titolo, t.descrizione, t.data_inizio, t.scadenza, t.stato,
       t.ore_allocate,
       COALESCE(tutte.ore, 0) AS ore_consumate,
       COALESCE(mie.ore,   0) AS mie_ore,
       l.id, l.codice, l.nome, l.stato,
       r.nome, r.cognome
FROM assegnazione_task a
JOIN task   t ON t.id = a.task_id
JOIN lavoro l ON l.id = t.lavoro_id
LEFT JOIN utente r ON r.id = l.responsabile_id
LEFT JOIN (SELECT v.task_id, SUM(v.ore) AS ore
             FROM voce_dichiarazione v
            WHERE v.task_id IN (SELECT task_id FROM assegnazione_task
                                 WHERE utente_id = :utenteId AND rimossa_il IS NULL)
            GROUP BY v.task_id) tutte ON tutte.task_id = t.id
LEFT JOIN (SELECT v.task_id, SUM(v.ore) AS ore
             FROM voce_dichiarazione v
             JOIN dichiarazione d ON d.id = v.dichiarazione_id
            WHERE d.utente_id = :utenteId
            GROUP BY v.task_id) mie ON mie.task_id = t.id
WHERE a.utente_id = :utenteId AND a.rimossa_il IS NULL
ORDER BY FIELD(t.stato, 'IN_CORSO', 'DA_FARE', 'COMPLETATA', 'ANNULLATA'),
         t.scadenza, l.codice
```

Tre dettagli non casuali:

- le due sottoquery sono **ristrette alle task della persona**, non aggregate
  su tutta `voce_dichiarazione`: senza quel `IN (...)` la somma costerebbe una
  scansione completa della tabella che crescerà di più in tutto il database;
- `FIELD()` mette l'urgenza prima dell'ordine alfabetico, ed è accettabile
  perché la query è già nativa e il progetto è su MySQL — lo sono già
  `trovaSforamenti` e `trovaAperteAssegnateA`;
- `sforamento`, `inRitardo` e `giorniAllaScadenza` si calcolano **in Java**,
  nel costruttore statico del record, non in SQL: dipendono da «oggi», e un
  confronto con `CURDATE()` dentro la query renderebbe il risultato dipendente
  dal fuso del database invece che da quello dell'applicazione.

Nessun controllo di permesso oltre al `@PreAuthorize`: la rotta restituisce
**solo** le righe di chi chiede, e `a.utente_id = :utenteId` è il filtro. Non
c'è un parametro `utenteId` da passare, di proposito — se ci fosse, servirebbe
difenderlo, e la rotta per vedere il lavoro di un altro è già un'altra
(`/api/dichiarazioni/utente/{id}`, con i suoi due permessi).

### `GET /api/mio-calendario?da=&a=`

Sta nel package `dichiarazione`, perché la sua spina dorsale sono le
dichiarazioni, e aggiunge un metodo `mioCalendario` a `ServizioDichiarazioni`.

```java
public record GiornoCalendario(LocalDate data,
                               boolean lavorativo,
                               String festivita,            // null se non lo è
                               TipoGiornata tipoGiornata,   // null se non dichiarato
                               StatoDichiarazione stato,    // null se non dichiarato
                               BigDecimal oreTotali,
                               boolean daCompilare,
                               boolean modificabile,
                               List<SegnoTask> task) { }

public record SegnoTask(Long taskId, String titolo, Long lavoroId,
                        String codiceLavoro, StatoTask stato,
                        TipoSegno segno) { }   // INIZIO | SCADENZA

public record MioCalendario(LocalDate da, LocalDate a,
                            BigDecimal oreMassimeGiorno,
                            int giorniLavorativi,
                            int giorniDichiarati,
                            int giorniDaCompilare,
                            BigDecimal oreTotali,
                            List<GiornoCalendario> giorni) { }
```

Quattro letture, nessuna dentro un ciclo:

1. `DichiarazioneRepository.trovaDelPeriodo(io, da, a)` e
   `sommaOrePerGiorno(io, da, a)` — esistono già, e la seconda è nata
   esattamente per non fare una query per giornata;
2. le festività dell'intervallo **con la descrizione**: `FestivitaRepository`
   ha solo `dateTra`, che restituisce le date perché a chi conta i giorni
   lavorativi i nomi non servono. Qui servono, quindi va aggiunta
   `trovaTra(da, a)` che restituisce le entità. La vecchia resta: ha un
   chiamante che non ha bisogno di più;
3. `ServizioImpostazioni.giorniLavorativi()` — una lettura, non una per giorno;
4. gli inizi e le scadenze delle mie task nell'intervallo, con una query nuova
   in `TaskRepository`:

```sql
SELECT t.id, t.titolo, t.data_inizio, t.scadenza, t.stato,
       l.id, l.codice
FROM assegnazione_task a
JOIN task   t ON t.id = a.task_id
JOIN lavoro l ON l.id = t.lavoro_id
WHERE a.utente_id = :utenteId AND a.rimossa_il IS NULL
  AND t.stato IN ('DA_FARE', 'IN_CORSO')
  AND (t.data_inizio BETWEEN :da AND :a OR t.scadenza BETWEEN :da AND :a)
```

Poi un ciclo sui giorni dell'intervallo che compone le celle in memoria. Il
ciclo è su date, non su query: è lo stesso modo in cui
`ServizioCalendario.giorniLavorativiDopo` conta i giorni, con una sola lettura
delle chiusure per tutto l'intervallo.

**Il permesso è `DICHIARAZIONI_COMPILA`**, uno solo. I segni delle task
compaiono solo se chi chiede ha **anche** `TASK_VISUALIZZA_ASSEGNATE`: il
servizio lo verifica con `Permessi.ha` e altrimenti lascia la lista vuota.
Chiedere entrambi i permessi nell'annotazione escluderebbe dal proprio
calendario chi ha solo il primo — e il calendario delle proprie giornate è roba
sua in ogni caso.

**Un limite all'intervallo.** `da` e `a` arrivano dal client, e un intervallo di
dieci anni farebbe comporre a mano quasi quattromila celle. Il servizio rifiuta
con 400 oltre i **62 giorni**: due mesi, abbastanza per una griglia mensile
allargata al lunedì e alla domenica, e abbastanza poco da non diventare un modo
di far lavorare il server per niente. `a < da` è già 400 in `periodoDi`, e la
stessa regola va riusata.

---

## 9. Frontend

Nessuna cartella nuova: la struttura è per dominio e i due pezzi hanno un
dominio già esistente.

| file | stato |
|---|---|
| `src/task/api.ts` | esteso: `MiaTask`, `mieTask()` |
| `src/task/PaginaMieTask.tsx` | nuovo |
| `src/dichiarazione/api.ts` | esteso: `MioCalendario`, `GiornoCalendario`, `SegnoTask`, `mioCalendario()` |
| `src/dichiarazione/PaginaCalendario.tsx` | nuovo |
| `src/dichiarazione/PaginaGiornata.tsx` | data nell'indirizzo, frecce, banda degli arretrati |
| `src/componenti/MioLavoro.tsx` | nuovo: la cornice con le tre schede |
| `src/App.tsx` | rotte annidate sotto `/mio-lavoro`, reindirizzamento da `/giornata`, atterraggio |
| `src/componenti/Intestazione.tsx` | la voce «Il mio lavoro» |
| `src/App.css` | le classi del calendario |

### L'atterraggio

Oggi `App.tsx` manda chi non vede i lavori su `/utenti`, dove un dipendente non
ha niente da fare: `UTENTI_VISUALIZZA` non è nel suo ruolo, quindi vede la
schermata di non autorizzato. Va corretto, e la correzione è naturale adesso
che c'è dove andare. **L'ordine dei controlli conta**, perché l'amministratore
ha *tutti* i permessi, `DICHIARAZIONI_COMPILA` compreso:

```
ha LAVORI_VISUALIZZA_TUTTI                            →  /lavori
altrimenti ha DICHIARAZIONI_COMPILA
            o TASK_VISUALIZZA_ASSEGNATE               →  /mio-lavoro/task
altrimenti ha LAVORI_VISUALIZZA_PROPRI                →  /lavori
altrimenti                                            →  /utenti
```

Il dipendente atterra sul proprio lavoro, non sull'elenco dei progetti; chi
sovrintende a tutti i lavori atterra ancora su `/lavori`, perché per lui le
proprie task non sono il mestiere.

### Il calendario, in CSS

La griglia è `display: grid` a sette colonne, e **non introduce colori**: la
palette è fissa e i token esistenti bastano, come già fatto per il monte ore.

| stato del giorno | come si dice |
|---|---|
| non lavorativo / festività | fondo `--carta` dentro una superficie `--superficie`, testo `--inchiostro-tenue` |
| dichiarato e inviato | velo `--azione-velo`, le ore in `--inchiostro` |
| dichiarato in bozza | contorno `--riga`, le ore in `--inchiostro-tenue` |
| da compilare | `--errore-velo` e il bordo `--errore`, come `.origine-sforamento` fa per le task |
| oggi | il contorno di `--azione`, lo stesso di `:focus-visible` |
| ferie / permesso / malattia | la pillola `.origine` che c'è già, con l'etichetta |

I segni delle task sono pillole `--raggio-pillola` dentro la cella, troncate a
due con «+N» quando sono di più: una cella di calendario non è un elenco.

Il raggio segue la regola del progetto — `--raggio-controllo` sulla cella,
`--raggio-carta` sulla griglia — e le ombre restano i due livelli esistenti
(`--ombra-carta`, `--ombra-foglio`). Nessun token nuovo, nessuna tinta nuova.

---

## 10. Edge case

```
mie_task(io):
    righe = assegnazioni attive di io, con task, lavoro, responsabile
    per ogni riga:
        sforamento     = ore_consumate > ore_allocate
        in_ritardo     = scadenza < oggi e stato in (DA_FARE, IN_CORSO)
        giorni_a_scad  = scadenza - oggi            // negativo se passata
        azioni = []
        if lavoro.stato in (CHIUSO, ANNULLATO): azioni = []   // sola lettura
        elif stato == DA_FARE:  azioni = [Inizia]
        elif stato == IN_CORSO: azioni = [Completata]
    ordina per (IN_CORSO, DA_FARE, COMPLETATA, ANNULLATA), poi scadenza
```

```
mio_calendario(io, da, a):
    if a < da or giorni(da, a) > 62: rifiuta 400

    dichiarate = dichiarazioni di io fra da e a, con le ore per giorno
    chiusure   = festivita fra da e a                      // una query
    settimana  = calendario.giorni_lavorativi              // una lettura
    segni      = inizi e scadenze delle mie task aperte fra da e a,
                 se io ha TASK_VISUALIZZA_ASSEGNATE, altrimenti vuoto

    per ogni giorno g fra da e a:
        festivo      = g in chiusure
        lavorativo   = g.giorno_settimana in settimana e non festivo
        dich         = dichiarate[g]                       // puo' mancare
        da_compilare = lavorativo e g <= oggi e dich è assente
```

Casi che il disegno deve reggere, e che nel codice attuale hanno già una
risposta da riusare tale e quale:

| caso | che cosa succede |
|---|---|
| l'assegnazione mi viene rimossa | la task esce da «Le mie task»; le ore già registrate **restano** e si vedono ancora nelle giornate passate, perché `voce_dichiarazione` punta alla task e non all'assegnazione |
| la task viene completata dal responsabile | esce da `trovaAperteAssegnateA`, quindi dalla tendina della giornata; resta in «Le mie task» in fondo, in sola lettura |
| registro ore su una task non più aperta | 409 di `ServizioDichiarazioni`, invariato |
| clic su un giorno futuro nel calendario | la giornata si apre in sola lettura: `vietaSeGiornoNonScrivibile` dà 400 e `modificabile` è già falso nel futuro |
| clic su un sabato | si apre: non c'è nessuna regola che vieti di dichiarare un giorno non lavorativo, e chi ha lavorato il sabato deve poterlo scrivere. Il calendario lo segna non lavorativo, non lo blocca |
| giornata inviata e fuori finestra | sola lettura, con il messaggio che già c'è |
| completo una task con 12 ore allocate e 7 consumate | 5 ore tornano nel pool con un movimento `RILASCIO`, e la conferma lo dice prima |
| completo una task che sfora | `rilasciaResiduo` restituisce zero e non tocca niente: lo sforamento resta registrato |
| non ho nessuna task assegnata | «Le mie task» spiega a chi chiedere, con le stesse parole che usa già la giornata: «le ore si registrano solo sulle task a cui sei assegnato» |
| apro il calendario di un mese senza niente | tutte celle vuote e `giorniDaCompilare` a zero: una sezione nuova non deve sembrare rotta quando è solo vuota |

---

## 11. Permessi

Nessun permesso nuovo. Quello che cambia è che **due permessi esistenti
cominciano a contare**:

| permesso | prima | dopo |
|---|---|---|
| `TASK_VISUALIZZA_ASSEGNATE` | nel catalogo, nessuna rotta | `GET /api/mie-task`, e i segni nel calendario |
| `TASK_AGGIORNA_STATO` | solo dalla pagina del lavoro | anche da «Le mie task», con la conferma sulle ore |
| `DICHIARAZIONI_COMPILA` | la giornata | anche `GET /api/mio-calendario` |

I confini restano quelli del monte ore, e nessuno si sposta:

- **vedere le proprie task** ≠ **vedere le task di un lavoro**:
  `TASK_VISUALIZZA_LAVORO` (V7) continua a essere l'unico modo di vedere il
  quadro completo di un lavoro senza poterlo modificare;
- **registrare ore** ≠ **allocarle**: il dipendente non ha nessun permesso
  `ORE_*`, e questa sezione non gliene dà. Quando le ore non bastano lo dice al
  responsabile, che apre la richiesta — è il percorso già disegnato;
- **le proprie giornate** ≠ **quelle degli altri**: `/api/mio-calendario` non
  accetta un `utenteId`. Per il team ci sono già
  `DICHIARAZIONI_VISUALIZZA_TEAM` e `_TUTTE`, su
  `/api/dichiarazioni/utente/{id}`.

---

## 12. Ordine di lavoro

Nove passi, ciascuno verificabile prima del successivo. L'ultimo è quello che
nel monte ore è rimasto indietro, e stavolta non va rinviato.

1. **`GET /api/mie-task`** — query nel repository, record `MiaTask`, metodo su
   `ServizioTask`, `MieTaskController`. Verifica a mano con una task assegnata
   e qualche ora registrata.
2. **Test della rotta** — `@WebMvcTest` sul contratto: 200 con
   `TASK_VISUALIZZA_ASSEGNATE`, 403 senza, e la verifica che restituisca solo
   le proprie righe anche con una seconda persona assegnata alla stessa task.
3. **`GET /api/mio-calendario`** — `FestivitaRepository.trovaTra`, la query dei
   segni, il metodo sul servizio, la rotta. Limite di 62 giorni e `a < da`.
4. **Test del calendario** — con il contesto completo e il database reale,
   perché dipende dai dati: un sabato, una festività, un giorno in bianco, un
   giorno di ferie, un giorno inviato, un giorno futuro con una scadenza.
5. **La cornice `/mio-lavoro`** e lo spostamento di *Giornata* dentro, con il
   reindirizzamento e la data nell'indirizzo.
6. **`PaginaMieTask`**, con le due azioni di stato e la conferma che dice le
   ore.
7. **`PaginaCalendario`**, con la griglia, le frecce e il collegamento alla
   giornata.
8. **L'atterraggio e la barra**, nell'ordine corretto dei permessi.
9. **La prova nel browser**, con un dipendente vero:
   `giulia.verdi@gestionale.local`, `Password1!`. È il passo che il monte ore
   ha lasciato scoperto — «le schermate sono compilate e tipizzate, ma non sono
   state guardate» — e una sezione fatta per essere usata ogni mattina non si
   consegna senza averla aperta.

Da aggiornare alla fine: `README.md` (la struttura e il modello dei permessi),
`.postman/` con le due rotte nuove, e un report accanto a questo file.

---

## 13. Fuori ambito

- **Notifiche.** Nessuna, come nel resto del progetto. Il posto dove un
  dipendente scopre di avere una task nuova è «Le mie task»; il posto dove
  scopre di avere arretrati è il conto dei giorni da compilare.
- **Chiedere ore dal basso.** Il dipendente vede lo sforamento e non ha un
  pulsante per chiedere ore: il piano del monte ore dice che lo comunica a voce
  al responsabile, e aprire quella strada vorrebbe dire un permesso nuovo e un
  secondo percorso di approvazione.
- **Modificare la propria task.** Titolo, descrizione, date e stima sono di chi
  gestisce le task. La sezione è di sola lettura su tutto tranne lo stato.
- **Priorità delle task.** Oggi l'ordine è «urgenza e scadenza», che si ricava
  dai dati. Una priorità esplicita sarebbe una colonna nuova e una decisione di
  chi assegna: se servirà, è una `V9` con `task.priorita` e un default.
- **Tetto ore per persona.** Il limite resta globale
  (`dichiarazione.ore_massime_giorno`). Per part-time e straordinari diventa
  una colonna su `utente`, con default 8 — come già annotato nel report del
  monte ore.
- **Ferie come richiesta.** Dichiarare un giorno come ferie resta una
  dichiarazione, non una richiesta da approvare. Un flusso di approvazione
  delle assenze è un'altra funzionalità, con il suo stato e i suoi permessi.
- **Esportare il mese.** Nessun CSV, nessun PDF.
- **Tema scuro.** Rinviato, come nel resto del frontend: `color-scheme: light`
  e i token già pronti ad accoglierlo.

---

## 14. Da decidere

1. **La bozza nel passato resta correggibile per sempre?** È l'asimmetria del
   §6. Tre risposte possibili: lasciarla com'è e rendere visibili gli
   arretrati; chiudere anche le bozze dopo N giorni lavorativi, e allora serve
   un modo per sbloccarle; oppure chiuderle a fine mese. La terza è la più
   vicina a come si chiudono i mesi in contabilità, e la più invasiva.
2. **Quanto indietro guarda il calendario?** La proposta non mette limiti alla
   navigazione all'indietro. Se le bozze scadono (punto 1), i mesi chiusi
   diventano storia in sola lettura, e tanto vale dirlo nella cella.
3. **«Completata» al dipendente, davvero?** Oggi `TASK_AGGIORNA_STATO` glielo
   permette e la sezione lo rende facile. Se chiudere una task deve restare una
   decisione del responsabile, la risposta non è nascondere il pulsante: è
   togliere quel permesso al ruolo Dipendente, che ora che la rotta esiste ha
   un effetto vero.
4. **Le ore degli altri su una mia task.** `oreConsumate` mostra il totale di
   tutti gli assegnati. È informazione utile — dice se la task sta finendo il
   budget — ma è anche il lavoro dei colleghi. Se non deve vedersi, resta
   `mieOre` e il confronto con `oreAllocate` si perde.
5. **Il weekend nella griglia.** Mostrare sabato e domenica sempre, o
   nasconderli quando `calendario.giorni_lavorativi` è lunedì-venerdì?
   Nascosti la griglia è più pulita; mostrati, chi lavora un sabato lo trova
   dove si aspetta.
