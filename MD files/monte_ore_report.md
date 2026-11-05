# Monte ore — report di implementazione

Resoconto di quanto è stato realizzato per applicare `piano_monte_ore.md` al
gestionale, di come è stato fatto, del workflow che ne risulta, e la risposta
alla domanda «perché io admin non riesco ad accettare le richieste di aggiunta
ore».

Data: 5 novembre 2026.

---

## 1. Risposta breve alla domanda

**Non c'era niente da accettare: la coda delle richieste era vuota.**

Non è un problema di permessi e non è un difetto del codice. Il database
conteneva due richieste, e **entrambe risultavano già decise**:

| id | lavoro | chieste | concesse | stato | gestita da |
|---|---|---|---|---|---|
| 1 | LAV-001 | 40 | 24 | `APPROVATA_PARZIALE` | utente 1 (admin) |
| 2 | LAV-002 | 40 | 25 | `APPROVATA_PARZIALE` | utente 1 (admin) |

La richiesta 1 era già così nei dati di sviluppo, per fare da storico. **La
richiesta 2 l'ho decisa io**, concedendo 25 ore delle 40 chieste, mentre
verificavo il percorso end-to-end. Era l'unica in attesa, e l'ho consumata.

Quindi aprendo la pagina *Richieste ore* l'amministratore trovava
«Nessuna richiesta in attesa», e nessun pulsante su cui agire. Verificato
direttamente sull'istanza attiva:

```
GET /api/richieste-ore/in-attesa   →  200  []
GET /api/autenticazione/io         →  l'admin ha tutti e 5 i permessi ORE
```

I permessi dell'amministratore sono a posto — la vista `permesso_effettivo` ne
restituisce cinque: `ORE_MONTE_GESTISCI`, `ORE_ALLOCA_TUTTI`,
`ORE_ALLOCA_PROPRI`, `ORE_RICHIEDI`, `ORE_RICHIESTE_GESTISCI`.

**Ho lasciato una richiesta nuova in attesa** per permettere la prova: la
numero 3, aperta da Rossi Marco su LAV-004, 24 ore sul pool del lavoro. Si
trova nella pagina *Richieste ore*, pulsante «Esamina».

> **Aggiornamento del 5 novembre 2026, a fine giornata.** Tutti i lavori di
> prova — e con loro quella richiesta — sono stati cancellati su richiesta,
> perché erano fittizi e il gestionale ha iniziato a raccogliere dati veri. Le
> tabelle `lavoro`, `task`, `assegnazione_task`, `movimento_ore`,
> `richiesta_ore`, `dichiarazione` e `voce_dichiarazione` sono vuote, e i
> blocchi corrispondenti sono stati rimossi da `R__dati_sviluppo.sql` perché
> non ricomparissero. Restano utenti, ruoli, permessi, team e impostazioni.
> Un backup del database di prima è in `.data/backup/` (cartella ignorata da
> git). Per rivedere il percorso delle richieste serve quindi creare un lavoro
> nuovo: i numeri citati nel resto di questo documento vanno letti come il
> racconto delle verifiche fatte quel giorno, non come lo stato attuale.

### Due cose che hanno contribuito alla confusione

1. **L'amministratore non vede le richieste già decise.** La pagina, per chi
   ha `ORE_RICHIESTE_GESTISCI`, carica solo la coda delle pendenti
   (`GET /api/richieste-ore/in-attesa`). Le richieste chiuse esistono e sono
   leggibili — `GET /api/richieste-ore` le restituisce tutte a chi ha
   `LAVORI_VISUALIZZA_TUTTI`, e compaiono nella pagina del singolo lavoro — ma
   dalla pagina *Richieste ore* non si raggiungono. È un limite
   dell'interfaccia, non dei dati. **Si corregge facilmente** affiancando alla
   coda l'elenco completo.

2. **Su un lavoro annullato non si possono aprire richieste.** LAV-001, LAV-002
   e LAV-003 risultano `ANNULLATO` nel database di sviluppo, e il servizio
   rifiuta con 409 una richiesta su un lavoro non più in corso. Le richieste si
   possono aprire solo su LAV-004 e LAV-005, gli unici attivi.

---

## 2. Che cosa è stato fatto

Il lavoro è stato diviso in nove passi, ciascuno verificato prima di passare al
successivo.

### Tre scoperte che hanno guidato il disegno

Prima di scrivere codice ho letto lo schema esistente, e tre cose hanno
cambiato il piano:

1. **Le tabelle `dichiarazione` e `voce_dichiarazione` (migrazione V4) erano
   già il «registro ore» del piano**, e nessuno le usava: esistevano nello
   schema senza una riga di codice Java. `voce_dichiarazione` aveva già task e
   descrizione, e `dichiarazione(utente_id, data)` aveva il vincolo di unicità
   `uk_dich_utente_data`. **Mancava solo la colonna `ore`.** Riusarle invece di
   creare la tabella `registro_ore` prevista dal piano ha evitato due posti che
   registrano il lavoro svolto sulle task.

2. **`task.stima_ore` era già le «ore stimate»** del piano. Non è stata
   rinominata: le è stata affiancata `ore_allocate`.

3. **I permessi `DICHIARAZIONI_*` e `TASK_AGGIORNA_STATO` erano già nel
   catalogo dalla V1, ma nessuna rotta li usava.** Sono stati riusati, non
   duplicati.

### Una lacuna colmata lungo la strada

`Task.cambiaStato()` non aveva chiamanti: non esisteva nessuna rotta per
chiudere o annullare una task. Il piano però poggia su quel momento — «alla
chiusura di una task le ore non consumate tornano nel pool» — quindi la rotta
mancante è stata creata.

### Passo 1 — Schema

**`backend/src/main/resources/db/migration/V8__monte_ore.sql`** (nuovo)

- `lavoro.monte_ore DECIMAL(9,2) NULL` — nullo vuol dire «non ancora fissato»,
  che non è zero: zero direbbe che le ore sono esaurite.
- `task.ore_allocate DECIMAL(7,2) NOT NULL DEFAULT 0`.
- `voce_dichiarazione.ore DECIMAL(4,2) NOT NULL` — aggiunta in tre passaggi
  (colonna nullable, backfill, `NOT NULL` + CHECK), perché la tabella aveva già
  righe e una colonna obbligatoria le avrebbe fatte rifiutare.
- tabella `richiesta_ore`, con sei vincoli che impediscono righe a metà: una
  richiesta è «in attesa» esattamente quando non ha né ore decise né chi le ha
  decise, «rifiutata» vuol dire zero ore concesse, «approvata» vuol dire tutte
  quelle chieste.
- tabella `movimento_ore`, con `ore` **firmata** e un CHECK che lega il segno al
  tipo: un rilascio registrato positivo gonfierebbe il saldo del lavoro.
- categoria di permessi `ORE` con cinque codici, concessi ai ruoli protetti
  (come impongono i commenti della V1 e il precedente della V7) e al ruolo
  Project Manager per i due che gli competono.
- parametro `dichiarazione.ore_massime_giorno = 8` nella tabella
  `impostazione`.

**`backend/src/main/resources/db/dev/R__dati_sviluppo.sql`** (modificato)

Dati di prova resi coerenti con i nuovi invarianti: LAV-001 con 264 ore libere
dopo un aumento concesso, LAV-002 col pool a zero e la task «Mappatura dati» in
sforamento di 4 ore, una richiesta in attesa e una già approvata in parte, e
dodici movimenti che spiegano ogni numero.

### Passi 2–4 — Monte ore, allocazione, chiusura

Nuovo package **`com.anitia.backend.ore`**:

| file | ruolo |
|---|---|
| `TipoMovimento.java` | i quattro tipi di movimento |
| `MovimentoOre.java` | l'entità, senza setter |
| `MovimentoOreRepository.java` | storico e lettura del monte ore iniziale |
| `RigaMovimento.java` | il movimento come lo vede il frontend |
| `RiepilogoOre.java` | monte / allocate / consumate / libere |
| `ServizioOre.java` | allocazione e rilascio del residuo |
| `RichiestaAllocazione.java` | il corpo della richiesta di allocazione |
| `OreController.java` | le rotte sotto il lavoro |

Modificati: `Lavoro` (campo `monteOre`, `cambiaMonteOre`), `Task` (campo
`oreAllocate`, `alloca`, `rilasciaResiduo`), `LavoroRepository`
(`bloccaPerAggiornamento`), `TaskRepository` (otto query di somma),
`RigaLavoro`, `RigaTask`, `ServizioLavori`, `ServizioTask`, `LavoriController`,
`TaskController`, più i nuovi `RichiestaMonteOre` e `RichiestaStatoTask`.

### Passo 5 — Registrazione delle ore

Nuovi package **`dichiarazione`** (13 file) e **`impostazione`** (6 file). Il
secondo serve a leggere i parametri di sistema e a contare i giorni
*lavorativi* della finestra di correzione, usando `festivita` e
`calendario.giorni_lavorativi`.

### Passo 6 — Richieste di ore

In `ore`: `StatoRichiesta`, `RichiestaOre`, `RichiestaOreRepository`,
`RigaRichiestaOre`, `RichiestaNuoveOre`, `RichiestaEsito`,
`ContestoRichiesta`, `ServizioRichiesteOre`, `RichiesteOreController`.

### Passi 7–9 — Frontend

| file | stato |
|---|---|
| `src/ore/api.ts` | nuovo |
| `src/ore/PaginaRichiesteOre.tsx` | nuovo |
| `src/dichiarazione/api.ts` | nuovo |
| `src/dichiarazione/PaginaGiornata.tsx` | nuovo |
| `src/lavoro/PaginaLavoro.tsx` | riscritta |
| `src/lavoro/api.ts`, `src/task/api.ts` | estesi |
| `src/lavoro/PaginaNuovoLavoro.tsx` | campo monte ore |
| `src/App.tsx`, `src/componenti/Intestazione.tsx` | rotte e voci di menu |
| `src/App.css` | classi nuove, solo con i token esistenti |

---

## 3. Come è stato fatto — le decisioni che contano

### I totali non si salvano mai

È il principio che tiene insieme tutto. Non esiste nessuna colonna «ore
libere», «ore consumate» o «in sforamento»:

```
ore libere del lavoro = monte_ore − SUM(task.ore_allocate)
ore consumate di una task = SUM(voce_dichiarazione.ore)
task in sforamento = ore consumate > ore allocate
```

Si calcolano al volo, con query raggruppate. Un campo mantenuto in parallelo
sarebbe una seconda verità, e prima o poi divergerebbe da quella vera. La
conseguenza pratica è che **lo sforamento compare da sé** appena un dipendente
registra la giornata che supera il budget: nessun codice deve ricordarsi di
marcare la task.

### Ogni allocazione blocca la riga del lavoro

Siccome le ore libere non sono una colonna, due allocazioni contemporanee
leggerebbero lo stesso pool, troverebbero entrambe ore a sufficienza e
allocherebbero entrambe — e la somma finale sfonderebbe il monte ore senza che
nessun vincolo se ne accorga, perché le due scritture sono su due *task*
diverse. Il blocco ottimistico `@Version` non basta: protegge la riga del
lavoro, che qui viene solo letta.

Per questo c'è `LavoroRepository.bloccaPerAggiornamento`, un
`SELECT ... FOR UPDATE`, e l'ordine delle operazioni non è casuale: **permesso,
lock, lettura delle somme, scrittura.** Leggere le ore libere prima di aver
bloccato il lavoro renderebbe il controllo di capienza una fotografia già
vecchia nel momento in cui la si guarda.

### La storia si salva, e non si cancella

`movimento_ore` registra ogni variazione con autore, data e motivo. È la
ragione per cui modificare il monte ore **non cancella la stima iniziale**: il
valore di partenza resta leggibile dal movimento `MONTE_INIZIALE`, e a fine
lavoro si può confrontare preventivo e consuntivo. Il motivo è obbligatorio
sia sulla modifica del monte ore sia sull'allocazione.

Le ore sono firmate — positive quando entrano, negative quando escono — così il
saldo è una somma e non una lettura caso per caso.

### Le ore non si prendono mai da una task aperta

Non esiste nessun metodo che sposti ore fra due task, e l'assenza è
deliberata: togliere ore a una task che sta ancora lavorando sposterebbe il
problema su di lei e lo nasconderebbe, perché lo sforamento comparirebbe
altrove, più tardi, senza che nessuno abbia deciso niente. Le ore tornano nel
pool **solo** da task chiuse o annullate.

### Registrare oltre l'allocato è permesso

Nessun controllo contro `task.ore_allocate` al momento della registrazione.
Bloccarla non farebbe sparire il lavoro già fatto: lo renderebbe invisibile. La
task risulta in sforamento, e il responsabile la vede subito.

### Separazione dei poteri

Quattro confini, tutti verificati da test:

- **creare un lavoro** ≠ **fissarne il budget** — `LAVORI_CREA` non basta a
  mandare `monteOre`, serve `ORE_MONTE_GESTISCI`. Il controllo è nel servizio e
  non in un'annotazione, perché dipende da *che cosa* contiene la richiesta.
- **gestire le task** ≠ **distribuire il budget** — `TASK_GESTISCI_TUTTI` non
  dà `ORE_ALLOCA_*`.
- **chiedere ore** ≠ **concederle** — un PM che potesse approvare le proprie
  richieste trasformerebbe tutto in un giro burocratico senza controllo.
- **aggiornare lo stato** vale solo **sulle task a cui si è assegnati**, per chi
  ha solo `TASK_AGGIORNA_STATO`.

### Il «parziale» come caso di prima classe

Lo stato di una richiesta si **deduce dal numero** delle ore concesse, non
arriva da fuori: zero è un rifiuto, tutte quelle chieste un'approvazione piena,
una parte un'approvazione parziale. Far scegliere l'etichetta a chi chiama
vorrebbe dire poterla far mentire sui numeri, e due CHECK del database lo
impedirebbero comunque — ma con un errore SQL invece di un messaggio leggibile.

### L'amministratore decide con il contesto

Era il difetto più serio del piano iniziale: vedeva «Bianchi chiede 40 ore» e
non sapeva niente del resto. `GET /api/richieste-ore/{id}/contesto` restituisce
in una chiamata il monte ore attuale **e iniziale**, le ore allocate, le ore
consumate, le task in sforamento con il conto di quanto sforano, e lo storico
delle richieste precedenti di quel lavoro.

---

## 4. Il workflow attuale

### Ruoli e permessi

| ruolo | permessi ORE | che cosa può fare |
|---|---|---|
| **Amministratore** (protetto) | tutti e 5 | fissa e modifica il monte ore, decide le richieste, alloca su qualunque lavoro |
| **Project Manager** | `ORE_ALLOCA_PROPRI`, `ORE_RICHIEDI` | distribuisce il budget dei lavori di cui è responsabile, apre richieste |
| **Dipendente** | nessuno | registra le ore con `DICHIARAZIONI_COMPILA`, aggiorna lo stato delle proprie task |

Chi vede il lavoro vede le sue ore: **non esiste un permesso di sola lettura
sul monte ore**, perché andrebbe concesso a tutti quelli che vedono i lavori e
quindi non distinguerebbe niente.

### Il percorso, passo per passo

**1. L'admin crea il lavoro con il monte ore.**
Pagina *Nuovo lavoro*; il campo «Monte ore» compare solo con
`ORE_MONTE_GESTISCI`. Si può lasciare vuoto e fissarlo dopo — vuoto vuol dire
«da decidere», non zero. Alla creazione viene registrato il movimento
`MONTE_INIZIALE`.

**2. L'admin può modificare il monte ore in seguito.**
Pagina del lavoro → sezione *Monte ore* → «Modifica». Il motivo è obbligatorio.
**Non può scendere sotto la somma delle ore già allocate**: quelle sono impegni
presi su task esistenti, e tagliarle le farebbe sparire senza che nessuno
chiuda o riduca niente. Il rifiuto è un 409 che dice quante ore sono allocate.
La prima volta che un lavoro riceve un budget il movimento è `MONTE_INIZIALE`;
dalla seconda è `MODIFICA_MONTE` con la differenza firmata.

**3. Il PM crea le task con le ore stimate.**
La stima non alloca niente: è «quante ore servono secondo me».

**4. Il PM alloca le ore dal pool, task per task.**
Pagina del lavoro → colonna *Ore* → «Alloca ore». Ore e motivo. Se il pool non
basta la risposta è un 409 che **dice quante ore libere ci sono**, e
l'interfaccia apre la richiesta all'amministratore **già compilata con la
differenza**.

**5. Ogni giorno il dipendente registra le ore.**
Voce di menu *Giornata*. La tendina propone solo **le task aperte a cui è
assegnato**. Ore e descrizione — la descrizione è obbligatoria. Massimo 8 ore
al giorno (parametro `dichiarazione.ore_massime_giorno`), verificate nella
stessa transazione dell'inserimento. Si può dichiarare la giornata come ferie,
permesso o malattia; con delle ore già scritte il passaggio viene rifiutato
invece di cancellarle di nascosto. L'invio è una consegna, non una chiusura: la
giornata resta correggibile per i giorni *lavorativi* che dice
`dichiarazione.giorni_modifica` (oggi 1).

**6. Se una task sfora, si vede da sé.**
`ore consumate > ore allocate` è calcolato: appena il dipendente registra la
giornata che supera il budget, la task compare col badge «sfora di N» nella
pagina del lavoro.

**7. Il PM chiede le ore che mancano.**
Pagina del lavoro → *Richieste di ore* → «Chiedi ore», oppure in automatico
dopo un'allocazione rifiutata. Indicando una task, **le ore concesse le vengono
allocate direttamente**: è la garanzia che non finiscano altrove. Senza task,
restano nel pool. Il motivo è obbligatorio ed è quello su cui si decide.

**8. L'admin decide.**
Voce di menu *Richieste ore* → coda delle pendenti, dalla più vecchia →
«Esamina» → il quadro completo del lavoro → un numero: `0` rifiuta, tutte
approva, una parte approva parzialmente. Concedere ore **alza il monte ore** con
un movimento collegato alla richiesta, e se la richiesta era legata a una task
le alloca là. Una richiesta già decisa non si decide una seconda volta.

**9. Alla chiusura di una task il residuo torna nel pool.**
Pagina del lavoro → colonna *Stato* → `Completata` o `Annullata`.
`residuo = allocate − consumate`; se positivo l'allocazione scende alle ore
davvero consumate e il residuo torna disponibile con un movimento `RILASCIO`.
Se la task ha sforato non si tocca niente: è un fatto accaduto, non un errore
da correggere. Completata e annullata sono **definitive** — riaprire
richiederebbe di riallocare ore che nel frattempo possono essere finite
altrove.

**10. Tutto resta leggibile.**
Pagina del lavoro → *Storico delle ore*: ogni movimento con tipo, ore firmate,
task, autore, motivo e la richiesta da cui nasce.

### Le rotte

| metodo e indirizzo | permesso |
|---|---|
| `POST /api/lavori` | `LAVORI_CREA` (+ `ORE_MONTE_GESTISCI` se c'è `monteOre`) |
| `POST /api/lavori/{id}/monte-ore` | `ORE_MONTE_GESTISCI` |
| `GET /api/lavori/{id}/ore` | `LAVORI_VISUALIZZA_*` |
| `GET /api/lavori/{id}/movimenti` | `LAVORI_VISUALIZZA_*` |
| `POST /api/lavori/{id}/task/{idTask}/ore` | `ORE_ALLOCA_TUTTI` \| `ORE_ALLOCA_PROPRI` |
| `POST /api/lavori/{id}/task/{idTask}/stato` | `TASK_AGGIORNA_STATO` \| `TASK_GESTISCI_*` |
| `POST /api/lavori/{id}/richieste-ore` | `ORE_RICHIEDI` \| `ORE_ALLOCA_TUTTI` \| `ORE_RICHIESTE_GESTISCI` |
| `GET /api/lavori/{id}/richieste-ore` | `LAVORI_VISUALIZZA_*` |
| `GET /api/richieste-ore` | `ORE_RICHIEDI` \| `ORE_RICHIESTE_GESTISCI` \| `LAVORI_VISUALIZZA_TUTTI` |
| `GET /api/richieste-ore/in-attesa` | `ORE_RICHIESTE_GESTISCI` |
| `GET /api/richieste-ore/{id}/contesto` | `ORE_RICHIESTE_GESTISCI` |
| `POST /api/richieste-ore/{id}/gestisci` | `ORE_RICHIESTE_GESTISCI` |
| `GET /api/dichiarazioni/{data}` | `DICHIARAZIONI_COMPILA` |
| `GET /api/dichiarazioni?da=&a=` | `DICHIARAZIONI_COMPILA` |
| `GET /api/dichiarazioni/utente/{id}?da=&a=` | `DICHIARAZIONI_VISUALIZZA_TUTTE` \| `_TEAM` |
| `POST /api/dichiarazioni/{data}/voci` | `DICHIARAZIONI_COMPILA` |
| `PUT /api/dichiarazioni/{data}/voci/{idVoce}` | `DICHIARAZIONI_COMPILA` |
| `DELETE /api/dichiarazioni/{data}/voci/{idVoce}` | `DICHIARAZIONI_COMPILA` |
| `POST /api/dichiarazioni/{data}/tipo` | `DICHIARAZIONI_COMPILA` |
| `POST /api/dichiarazioni/{data}/invia` | `DICHIARAZIONI_COMPILA` |

---

## 5. Verifiche eseguite

**147 test backend**, tutti verdi, di cui 54 nuovi:

| classe | test |
|---|---|
| `LavoriControllerTest` | 29 |
| `TaskControllerTest` | 26 |
| `DichiarazioniControllerTest` | 24 |
| `RichiesteOreControllerTest` | 18 |
| `OreControllerTest` | 12 |
| altri preesistenti | 38 |

Frontend: `npm run build` (compreso `tsc -b`) e `npm run lint` puliti.

Oltre ai test, questi percorsi sono stati eseguiti **contro MySQL reale**:

- monte ore fissato la prima volta → `MONTE_INIZIALE`; modificato poi →
  `MODIFICA_MONTE` con la differenza firmata, e il valore iniziale ancora
  leggibile;
- abbassare il monte ore sotto le ore allocate → 409 col numero;
- 40 ore di budget, 30 allocate → 10 libere; chiederne 25 → 409 «ha solo 10.00
  ore libere»; allocare le 10 → pool a zero;
- chiusura di una task con 40 allocate e 0 consumate → 40 ore tornate nel pool
  con un `RILASCIO`; riaprirla → 409;
- registrazione ore: tetto di 8 ore con il residuo nel messaggio, task non
  assegnata → 403, task chiusa → 409, giornata futura → 400, ferie con ore già
  scritte → 409, invio e finestra di correzione;
- 4 ore registrate su una task con 0 allocate → accettate, e `sforamento=true`
  comparso da sé;
- richiesta: 25 ore concesse su 40 → `APPROVATA_PARZIALE`, monte 150→175, task
  20→45 ore (e non sfora più), entrambi i movimenti citano la richiesta; il PM
  che prova a decidere la propria → 403; rigestirla → 409;
- `LAVORI_CREA` senza `ORE_MONTE_GESTISCI` che manda `monteOre` → 403; lo
  stesso lavoro senza monte ore → 201.

### Non verificato

**La prova visiva nel browser.** È l'unico passo rimasto: le schermate sono
compilate, tipizzate sulle risposte reali del server e senza avvisi di lint, ma
non sono state guardate.

---

## 6. Fuori ambito e cose da riprendere

- **Notifiche**: nessuna. Il progetto non ha infrastruttura di notifica o
  email. Al loro posto ci sono il banner delle richieste in attesa sulla pagina
  del lavoro e la pagina *Richieste ore*, dove l'esito si va a vedere. Erano
  escluse d'accordo in fase di piano.
- **Storico delle richieste per l'amministratore**: oggi la pagina *Richieste
  ore* gli mostra solo la coda delle pendenti. Il limite descritto nella
  sezione 1.
- **Tetto per dipendente**: il limite è un parametro globale. Per part-time e
  straordinari diventerebbe una colonna su `utente`, con default 8.
- **Gestione dei team da interfaccia**: `membro_team` e `TEAM_GESTISCI`
  esistono, ma i team sono ancora quelli dei dati di sviluppo. Conta perché un
  PM può assegnare task solo al proprio team.

---

## 7. Scostamenti dal piano iniziale

| nel piano | com'è stato fatto | perché |
|---|---|---|
| tabella `registro_ore` nuova | colonna `ore` su `voce_dichiarazione` | quelle tabelle erano già il registro ore, inutilizzate |
| ore come stringhe nel JSON | numeri | il piano si basava sul tipo `stimaOre: string \| null` del codice esistente, che era **sbagliato**: il server manda numeri. Corretto |
| `Task.ore_allocate` nel passo 3 | nel passo 2 | una query del repository lo richiedeva già |
| `cambiaMonteOre` in `ServizioOre` | in `ServizioLavori` | altrimenti i due servizi si iniettavano a vicenda e Spring non avviava |
| — | `eResponsabile` spostato su `ServizioLavori` | stava diventando la terza copia della stessa regola di sicurezza |
| — | task completate e annullate definitive | riaprirle richiederebbe di riallocare ore che possono essere finite altrove. Decisione non coperta dal piano |
| — | classe CSS `.secondario` aggiunta | era usata nel markup ma non esisteva: il pulsante «Nuova task» era senza stile |
