# La stima alloca, e il lavoro dice quanto è fatto — piano di implementazione

*10 novembre 2026. Successivo al piano del tracciamento del tempo
(`V15`–`V17`): da qui le ore consumate vengono dalle sessioni di lavoro.*

## Contesto

Oggi `sforamento = ore consumate > ore allocate`, e una task nasce con
`ore_allocate = 0`. Le due cose insieme fanno sì che **qualunque minuto
registrato su una task nuova la mandi subito in sforamento**: il segnale che
dovrebbe allarmare scatta sempre, quindi non dice più niente.

Non è un'ipotesi, è nei dati veri: la task 3 ha stima 4 ore, 4 ore consumate e
0 allocate, ed è segnalata come sforante pur essendo esattamente in budget. Lo
stesso vale per la 4. Dei due lavori, nessuno ha una sola ora allocata: 100 di
monte ore a testa, stime per 12 e 20 ore, allocate zero.

Il modello non è sbagliato — stima e allocazione *sono* due cose diverse, e il
progetto lo argomenta in tre posti (`Task.oreAllocate`, la `V8`,
`monte_ore_report.md`). Quello che manca è il gesto che le collega: **creare
una task deve prenderle il budget dal pool**, invece di lasciare a un secondo
passaggio manuale un'operazione che nessuno fa.

In più serve un numero che oggi non esiste: **quanto è completo un lavoro**,
misurato sulle ore consumate rispetto a quelle previste.

## Decisioni prese

| Scelta | Decisione |
|---|---|
| Stima e allocazione | **Restano due colonne distinte.** Il modello attuale non si tocca |
| Percentuale | **ore consumate / somma delle stime delle task**, non sul monte ore |
| Pool insufficiente | **409 e la task non nasce**, col numero delle ore libere nel messaggio |
| Stima | **Obbligatoria** per creare una task (validata sul DTO; la colonna resta nullable) |
| Permesso | Basta **`TASK_GESTISCI_TUTTI` / `TASK_GESTISCI_PROPRI`**. La rotta autonoma di allocazione resta com'è e continua a pretendere `ORE_ALLOCA_*` |

### Tre regole derivate, che nessuna domanda copriva

1. **Le task annullate escono dal denominatore.** La loro stima è lavoro che
   non si farà più: tenerla dentro terrebbe la percentuale bassa per sempre.
   Le ore consumate invece restano tutte contate, annullate comprese, perché
   sono state lavorate per davvero.
2. **La percentuale può superare il 100%**, e quando lo fa dice una cosa vera:
   si è consumato più di quanto era previsto. È lo sforamento a livello di
   lavoro, e va mostrato così com'è.
3. **Senza monte ore non si crea una task con una stima.** Oggi `crea` accetta
   una task su un lavoro senza budget mentre `alloca` risponde 409: da adesso
   la creazione erediterebbe quel 409, e tanto vale dirlo con un messaggio che
   spiega cosa fare.

## Backend — l'allocazione alla nascita

### Il nuovo metodo, su `ServizioOre`

`backend/src/main/java/com/anitia/backend/ore/ServizioOre.java`

Si modella su **`rilasciaResiduo`** (righe 149-164), che è il precedente
esatto: pubblico, senza controllo di permesso, con il javadoc che dichiara
«**Non esiste una rotta che la esponga**» perché lo chiama solo `ServizioTask`
quando un cambio di stato lo giustifica.

```java
@Transactional
public void allocaStima(Task creata, BigDecimal stima, Utente autore)
```

Nell'ordine che il javadoc di `alloca` dichiara non casuale — *lock, poi
lettura delle somme, poi scrittura*:

1. `Lavoro lavoro = bloccato(creata.getLavoro().getId())` — il
   `bloccaPerAggiornamento` di `LavoroRepository`, 404 se il lavoro non c'è;
2. `monteOre == null` → **409** «Il lavoro X non ha ancora un monte ore: non si
   possono creare task con una stima finché un amministratore non lo fissa.»;
3. `BigDecimal libere = lavoro.getMonteOre().subtract(task.sommaOreAllocate(id))`;
4. `stima > libere` → **409** «Il lavoro X ha solo N ore libere, e la task «T»
   ne stima M. Alza il monte ore o riduci la stima.»;
5. `creata.alloca(stima)`;
6. `movimenti.save(MovimentoOre.allocazione(lavoro, creata, stima, "Stima della
   task «T» allocata alla creazione", autore, null))`.

Il javadoc deve dire **perché qui non si controlla nessun permesso sulle ore**:
l'autorizzazione è già avvenuta ed è quella di gestire le task del lavoro, e le
ore non possono finire su una task qualunque — solo su quella che sta nascendo
nella stessa transazione.

### `ServizioTask.crea` (righe 226-248)

Una riga in più e un commento da riscrivere. L'ordine diventa:

`lavoroVisibile` → `vietaSeNonPuoGestire` → 409 lavoro chiuso → 400 date →
`saveAndFlush` → **`ore.allocaStima(creata, richiesta.stimaOre(), autore(chiLoChiede))`**
→ `RigaTask.da(creata, BigDecimal.ZERO, List.of())`.

`RigaTask` legge `oreAllocate` dall'entità, quindi la risposta porta già le ore
allocate senza altre modifiche. Il commento alle righe 245-247 («nessuna ora
allocata — gliene darà il PM dal pool») diventa falso: resta vero solo che le
consumate sono zero.

**Due cose da sapere su questo ordine.**

L'INSERT precede il lock, ma l'invariante regge: le somme si leggono *dentro*
`allocaStima`, cioè dopo il lock. Se il controllo di capienza fallisce, il
`ResponseStatusException` rotola indietro tutta la transazione di `crea` e la
riga appena inserita con il `saveAndFlush` **non resta**: la task non nasce.

Sull'ordine di acquisizione — `crea` inserisce su `task` e poi blocca `lavoro`,
mentre `alloca` fa il contrario — non c'è ciclo possibile: la riga che `crea`
inserisce è nuova, nessun'altra transazione la vede prima del commit, quindi
nessuno può aspettarla. Bloccare prima di inserire separerebbe il lock dal
controllo di capienza in due posti diversi, che è peggio.

### La stima obbligatoria

`task/RichiestaNuovaTask.java`, righe 32-34: a `@Positive` si aggiunge
`@NotNull("La stima in ore è obbligatoria: è lei che prende le ore dal monte
ore del lavoro")`.

La colonna `stima_ore` **resta nullable** nel database. La task 6 ha NULL, ed è
chiusa: inventarle una stima sarebbe fabbricare un dato. Il vincolo vive
sull'ingresso, dove nascono le righe nuove, e il commento su `Task.stimaOre` va
aggiornato per dirlo.

`Task.nuova` (righe 114-132) continua a impostare `oreAllocate = ZERO`: è
`allocaStima` ad aggiungere le ore subito dopo, sotto il lock. Il commento alle
righe 127-129 va riscritto — non più «gliele alloca il PM in un secondo
momento», ma «le aggiunge `ServizioOre.allocaStima` nella stessa transazione,
dopo aver bloccato la riga del lavoro». Il javadoc di `oreAllocate` (71-89) non
si cancella: la distinzione fra stima e allocazione resta vera, e va solo
integrata col fatto che adesso la prima inizializza la seconda.

## Backend — la percentuale

### Due query nuove in `TaskRepository`

Native, come le somme delle ore consumate, per non dover scrivere il confronto
con un enum in JPQL:

```sql
-- sommaStimeOre(lavoroId)
SELECT COALESCE(SUM(stima_ore), 0) FROM task
WHERE lavoro_id = :lavoroId AND stato <> 'ANNULLATO'

-- sommaStimePerLavoro(ids) -> List<OrePerLavoro>
SELECT lavoro_id AS lavoroId, COALESCE(SUM(stima_ore), 0) AS ore
FROM task WHERE lavoro_id IN (:lavoroId) AND stato <> 'ANNULLATO'
GROUP BY lavoro_id
```

La seconda riusa la proiezione **`OrePerLavoro`** che già esiste (righe
250-255): è generica, `lavoroId` + `ore`. Vale anche per lei l'avvertenza sul
`IN` con collezione vuota.

### Dove si calcola

Una volta sola, e il modulo delle ore ne è il padrone. Su `RiepilogoOre`:

```java
public static BigDecimal completamento(BigDecimal oreConsumate, BigDecimal sommaStime)
```

`null` se `sommaStime` è zero — un lavoro senza task non è «completo allo 0%»,
la domanda non ha risposta, ed è la stessa dottrina di `oreLibere` null quando
il monte ore non è fissato. Altrimenti
`oreConsumate.multiply(CENTO).divide(sommaStime, 0, RoundingMode.HALF_UP)`:
`BigDecimal` e non virgola mobile come tutto il resto, scala 0 perché un
intero percentuale è quello che si legge.

### Dove si espone

Entrambi i record di lettura guadagnano `percentualeCompletamento` e un
parametro `sommaStime` nella fabbrica:

- **`ore/RiepilogoOre.java`** — testa della pagina del lavoro e risposta di
  ogni allocazione riuscita. La fabbrica `da(...)` (riga 28) è chiamata solo da
  `ServizioOre.riepilogo(Lavoro)` (righe 172-176): una riga da aggiungere.
- **`lavoro/RigaLavoro.java`** — elenco dei lavori e intestazione. La fabbrica
  (riga 51) è chiamata dai due `conOre` di `ServizioLavori`: quello singolo
  (righe 251-256) prende `sommaStimeOre`, quello dell'elenco (righe 101-115)
  aggiunge una terza mappa da `sommaStimePerLavoro`, riusando l'helper
  `perLavoro(...)` che c'è già. Restano tre query per tutto l'elenco, non tre
  per riga.

## Migrazione `V18__stima_allocata.sql`

**Nessuna DDL**: non c'è nessuna colonna nuova — la percentuale è derivata e
l'allocazione usa colonne che esistono. Quindi un file solo, di dati, e la
regola DDL-separata-dai-dati non si applica.

Riallinea **le sole task aperte** con una stima e nessuna allocazione, e solo
dove il pool la copre. Oggi è una riga sola: la task 2, stima 4, su un lavoro
con 100 ore libere. Le chiuse non si toccano — allocare un budget a lavoro
finito non significa niente, e contraddirebbe `rilasciaResiduo`, che a
chiusura riporta l'allocazione alle ore consumate.

Due istruzioni, in quest'ordine: prima l'`INSERT` dei movimenti (leggendo
`stima_ore`), poi l'`UPDATE` delle allocazioni.

- **Autore del movimento: `lavoro.responsabile_id`**, non l'utente 1.
  `autore_id` è NOT NULL, e su un database di produzione appena creato l'utente
  1 **non esiste** — col profilo `prod` il seed `db/dev` non viene eseguito. Il
  responsabile è garantito non nullo per i lavori non in bozza, ed è anche
  l'attribuzione giusta: è il suo budget.
- **Idempotente**, con `NOT EXISTS` su un movimento `ALLOCAZIONE` il cui motivo
  inizia con `Allineamento del 10 novembre 2026`, sulla stessa task.
- ⚠️ **Trappola MySQL**: il controllo di capienza ha bisogno della somma delle
  ore allocate della stessa tabella `task` che si sta aggiornando, e MySQL
  rifiuta la sottoquery diretta (errore 1093). Va fatta entrare come tabella
  derivata in `JOIN (SELECT lavoro_id, SUM(ore_allocate) ... GROUP BY
  lavoro_id) AS somme`, che viene materializzata e quindi è ammessa.
- Il motivo del movimento dice che è un allineamento e porta la data, così la
  storia non spaccia per decisione di qualcuno una riga scritta da una
  migrazione.

Su un database vuoto la migrazione non fa niente, e va bene: non ci sono task.

## Frontend

- **`task/api.ts`** — `NuovaTask.stimaOre` passa da `number | null` a `number`.
  `RigaTask.stimaOre` resta `number | null`: le righe storiche esistono.
- **`lavoro/api.ts`** e **`ore/api.ts`** — `percentualeCompletamento: number | null`
  su `RigaLavoro` e `RiepilogoOre`.
- **`lavoro/PaginaLavoro.tsx`**
  - campo stima (righe 567-582): diventa `required`, e la nota alle righe
    578-581 — «Facoltativa. Non alloca niente: le ore si prendono dal monte ore
    dopo, task per task.» — va riscritta nel suo contrario: obbligatoria, e le
    ore vengono prese dal monte ore subito. È l'etichetta che insegna la nuova
    regola, quindi è la prima da sistemare.
  - `inviaNuovaTask` (righe 264-283): la riga 272 oggi manda `null` se il campo
    è vuoto; non deve più poter partire senza un numero.
  - riquadro monte ore (righe 423-440): una quarta cifra, «Completamento», col
    formattatore che rende `'—'` sul null come fa `ore(...)` (righe 78-88).
- **`lavoro/PaginaLavori.tsx`** — una colonna «Completamento» fra Stato e
  Periodo. La pagina oggi non mostra nessun numero di ore, ma `RigaLavoro` li
  porta già tutti: è il posto dove si scorrono i progetti, ed è lì che la
  domanda «quanto è fatto» si fa.

## Documentazione

- **`README.md`**, sezione «Il monte ore»: una riga nel blocco delle formule,
  `completamento del lavoro = ore consumate / SUM(task.stima_ore) non annullate`,
  e il passo 2 di «Il percorso, in breve» — «il responsabile crea le task e
  alloca le ore dal pool» — diventa un gesto solo invece di due.
- **`monte_ore_report.md`**: è un documento storico e non si riscrive, ma le
  righe 93-94 e 307-308 («La stima non alloca niente: è «quante ore servono
  secondo me»») ora dicono il falso. Ci va una postilla datata in fondo, che
  rimanda a questo piano.

## Test

**Nuovi, contro il database vero** (`@SpringBootTest @Transactional`, dati
costruiti dal test come fa `SessioniEOreTest`):

- creare una task con una stima alloca quelle ore e scrive **un solo**
  movimento `ALLOCAZIONE`; le ore libere del lavoro scendono di conseguenza;
- stima oltre le ore libere → 409, e **nessuna riga task** resta nel database
  (è il rollback del `saveAndFlush`, la parte che si romperebbe in silenzio);
- lavoro senza monte ore → 409;
- la percentuale: consumate/stime arrotondata, `null` quando non ci sono task,
  oltre 100 quando si è consumato più del previsto, e le annullate fuori dal
  denominatore.

**Da aggiornare**, nello stile `@WebMvcTest` di `TaskControllerTest`:
`creazioneSenzaStimaRisponde400` (nuovo, validazione del DTO) e
`creazioneOltreIlPoolRisponde409ConLeOreLibere` col messaggio letterale.
`creazioneConStimaNegativaRisponde400` (riga 177) resta, e la fixture `riga()`
(righe 91-98) già immagina stima 8 / allocate 8: non va toccata.

**Da non toccare**: `OreControllerTest.allocazioneConIlSoloPermessoSulleTaskRisponde403`
resta valido e verde — riguarda la rotta autonoma di allocazione, che non
cambia e continua a pretendere `ORE_ALLOCA_*`. E `SessioniEOreTest`, che
costruisce le task con `Task.nuova` diretto e quindi non passa dal servizio.

## Verifica

1. **Prima della migrazione**, annotare: `SELECT id, stima_ore, ore_allocate
   FROM task`, le ore libere dei due lavori e il conteggio dei movimenti (6).
2. Backup con `docker exec gestionale-db mysqldump`, poi avvio.
3. Dopo: la task 2 ha `ore_allocate = 4.00`, esiste **un** movimento nuovo di
   tipo `ALLOCAZIONE` con autore il responsabile di LAV-001, le ore libere di
   LAV-001 sono 96, e le task chiuse sono rimaste a zero.
4. `./mvnw -o clean test` — la suite intera, non solo i test nuovi.
5. Giro HTTP sul backend avviato: creare una task **senza stima** → 400; con
   una stima oltre le ore libere → 409 col numero; con una stima valida → 201,
   e la risposta porta già `oreAllocate`; subito dopo
   `GET /api/lavori/1/ore` → le ore libere scese e la percentuale aggiornata.
6. `npm run build` e `npm run lint`, poi a schermo: la nota del campo stima
   riscritta, il campo che non si invia vuoto, la cifra «Completamento» nel
   riquadro e la colonna nell'elenco.

## Fuori ambito, dichiarato

- **Nessuna percentuale per singola task.** Per una task il confronto esiste
  già ed è `oreConsumate / oreAllocate`, che è anche quello che fa scattare lo
  sforamento. La richiesta era sul progetto.
- **`stima_ore` non diventa NOT NULL** nel database: c'è una riga storica con
  NULL, e il vincolo sta sull'ingresso.
- **`sommaOreAllocate` non cambia**: continua a sommare le task di ogni stato.
  Le annullate hanno già l'allocazione ridotta alle ore consumate da
  `rilasciaResiduo`, quindi non gonfiano niente.
- **La rotta autonoma di allocazione resta identica**, permesso compreso: serve
  ancora per dare a una task più ore della sua stima.
- **Le task chiuse non vengono riallineate.**
