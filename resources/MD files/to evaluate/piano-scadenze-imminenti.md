# Piano: «Da fare oggi» → «Scadenze imminenti»

11 novembre 2026

## Contesto

Nella dashboard appena ridisegnata, il pannello in alto a sinistra è un calendario a
fasce orarie (`timeGridDay`, 7–21) che **resta vuoto**, e non per un difetto di
rendering: nel modello dati una task ha `dataInizio` e `scadenza`, due **date**, non una
fascia oraria. `ServizioDashboard.calendario` (`backend/.../dashboard/ServizioDashboard.java:100-167`)
costruisce tutti gli eventi con `LocalDate`, e `EventoCalendario` non ha nessun campo
`LocalTime`: per costruzione finisce tutto nella riga «giornata intera» in cima, e la
griglia delle ore non riceve niente.

Una griglia oraria senza dati orari è una cornice che promette una cosa che non c'è.
Al suo posto va una **dashboard delle scadenze imminenti**: i prossimi 14 giorni
raggruppati per giorno, con quello che cade in ciascuno.

Questo cambiamento **non tocca il database né il backend**: usa `GET /api/mia-dashboard`
(già caricato dalla pagina) per le scadenze e `GET /api/mio-calendario` per le
festività.

### Decisioni prese con l'utente

| Scelta | Decisione |
|---|---|
| Orizzonte | **14 giorni**, da oggi compreso |
| Divisione dei pannelli | **Sinistra = oggi e ciò che arriva · destra = tutte le task per priorità** |
| Contenuto | Scadenze delle task · **festività e chiusure** · **ore che restano su ogni task** |
| Assenze proprie | **Fuori**: niente righe di ferie o permessi |
| Azioni | **Si guarda**; il clic porta al lavoro. I pulsanti del timer restano solo in «Le mie task» |

L'ultima riga è ciò che tiene i due pannelli distinti: a sinistra si **anticipa**, a
destra si **agisce**. Senza quella divisione sarebbero due elenchi delle stesse task
con gli stessi bottoni.

> Una nota sulle assenze: le righe di ferie e permessi restano fuori come chiesto, ma
> l'avviso **«scade in un giorno in cui sei in ferie o permesso»** resta sulla riga
> della scadenza. Non è un elenco di assenze: è una proprietà di quella scadenza, il
> backend la calcola già (`RiepilogoDashboard.taskInConflitto`) e «Le mie task» la
> mostra già allo stesso modo. È l'informazione per cui un pannello di scadenze
> imminenti esiste — accorgersene prima, non il giorno stesso.

## Che cosa si vede

```text
┌────────────────────────────────────────────────────┐
│ Scadenze imminenti            2 in ritardo · 7     │
│ prossimi 14 giorni                                 │
├────────────────────────────────────────────────────┤
│ IN RITARDO                                         │
│  ● Preventivo Rossi        LAV-001 · 3 g   sforata │
│  ● Revisione contratto     LAV-004 · 1 g           │
│                                                    │
│ OGGI · mer 11                                      │
│  ● Importazione anagrafiche LAV-002 · restano 3,5 h│
│                                                    │
│ DOMANI · gio 12          — chiuso: Ponte aziendale │
│                                                    │
│ ven 13                                             │
│  ● Collaudo                LAV-002 · restano 6 h   │
│    ⚠ scade mentre sei in ferie                     │
└────────────────────────────────────────────────────┘
```

Regole di composizione:

- **«In ritardo» in cima**, sempre, prima di qualunque giorno: una scadenza mancata è
  un fatto, e un pannello di scadenze che la mette in fondo ha mancato il suo scopo.
  Ordinate dalla più vecchia.
- Poi **un gruppo per giorno**, da oggi a +13. Le intestazioni dei primi due giorni
  dicono «Oggi» e «Domani»; dal terzo, nome del giorno e data.
- **I giorni vuoti si omettono**, tranne quando portano una festività o una chiusura:
  quella è informazione anche senza scadenze — una consegna fissata su un giorno di
  chiusura si sposta prima, non il giorno stesso.
- In fondo, quante scadenze restano **oltre l'orizzonte**, con il collegamento a
  `/lavori`.

Ogni riga: punto dell'urgenza, titolo (collegato a `/lavori/{lavoroId}`), codice e nome
del lavoro, **ore che restano** (`oreAllocate − oreConsumate`) o il badge dello
sforamento quando il budget è già superato, e l'avviso del conflitto con le ferie.

## Implementazione

Tutto frontend. Nessuna migrazione, nessuna rotta nuova, nessun file di test.

### 1. Nuovo `frontend/src/dashboard/PannelloScadenze.tsx`

Sostituisce `PannelloDaFare.tsx`, che va **cancellato** insieme ai suoi `timeGridPlugin`
e `interactionPlugin`.

Props: `task: MiaTask[]`, `conflitti: Set<number>`, `carico: boolean`, `errore: string | null`
— gli stessi che `PannelloMieTask` già riceve da `PaginaDashboard`, perché la sorgente
è la stessa `riepilogo.task`. **Le scadenze non si chiedono con una rotta propria**: il
dato è già in pagina, e `MiaTask` porta urgenza, stato, `oreAllocate`, `oreConsumate`,
`sforamento` e `giorniAllaScadenza`, cioè tutto quello che serve a una riga.

Le **festività** se le carica da sé — `mioCalendario(oggi, oggi+13)` da
`dashboard/api.ts`, filtrando `tipo === 'FESTIVITA'` — con il proprio stato di
caricamento e di errore, così un errore qui non spegne le scadenze, che sono la cosa
importante del pannello. È la stessa rotta che `PannelloDaFare` già chiamava, solo su un
intervallo più largo.

Struttura interna, da costruire con `useMemo` su `task` e sulle festività:

```ts
type Giorno = {
  iso: string
  /** «Oggi», «Domani», «ven 13». */
  etichetta: string
  /** La descrizione della festività, se quel giorno è chiuso. */
  chiusura: string | null
  /** Vero per sabato e domenica. */
  festivo: boolean
  scadenze: MiaTask[]
}
```

- Si considerano solo le task **aperte** (`apertaTask`, `frontend/src/stato/stati.ts:57`):
  una task completata non ha più una scadenza da temere.
- `inRitardo` → gruppo «In ritardo». Altrimenti si raggruppa su `scadenza`.
- Dentro un giorno, l'ordine è per urgenza: si riusa lo **stesso confronto** di
  `PannelloMieTask.confronta` — da estrarre in `dashboard/ordineTask.ts` e importare nei
  due pannelli, invece di scriverne una seconda copia che prima o poi direbbe un'altra
  cosa.

### 2. Riuso, non codice nuovo

| Serve | Esiste già in |
|---|---|
| Il riquadro con intestazione e stati carico/vuoto/errore | `componenti/Pannello.tsx` |
| Il punto colorato dell'urgenza | `componenti/Stato.tsx` (`Punto`) + `TINTA_URGENZA` in `task/api.ts` |
| Il badge dello sforamento | `componenti/Stato.tsx` (`Pillola`), tinta `annullato` |
| «oggi», «domani», «ven» | `scadenzaBreve` in `dashboard/orario.ts:49` |
| «06/11» | `breve` in `dashboard/orario.ts:35` |
| La data di oggi in ISO | `oggiIso` in `dashboard/orario.ts:14` |
| L'ordinamento per urgenza | `confronta` in `PannelloMieTask.tsx`, da estrarre |

`piuUnGiorno` (`dashboard/orario.ts:29`) resta senza chiamanti quando
`PannelloDaFare` sparisce: va **rimossa**, aggiustando il commento che la cita per nome
in `tempo/api.ts:365`.

⚠️ **Sabato e domenica si riconoscono nel browser**, con `getDay()`. I giorni lavorativi
veri stanno nella tabella `impostazione` (`calendario.giorni_lavorativi = 1,2,3,4,5`) e
li legge solo `ServizioImpostazioni`: **non esiste nessuna rotta REST** che li esponga.
È la stessa assunzione Lun–Ven già scritta a mano in `tempo/orario.ts` e nei
`businessHours` dei calendari, e va dichiarata in un commento accanto alla costante
invece di lasciarla dedurre.

### 3. `frontend/src/dashboard/PaginaDashboard.tsx`

- Al posto di `<PannelloDaFare segnale={tempo} />` va `<PannelloScadenze …/>` con
  `task={tutte}`, `conflitti`, `carico` ed `errore` — gli stessi valori che scendono già
  in `PannelloMieTask`. **Si passa `tutte` e non `visibili`**: i filtri e l'interruttore
  «mostra completate» appartengono al pannello di destra, e un pannello di scadenze che
  si svuota perché a destra è acceso un filtro sarebbe incomprensibile.
- Il `segnale` di ricarica non serve più: le scadenze arrivano dal riepilogo, che la
  pagina già ricarica dopo ogni scrittura (`caricaRiepilogo`).

### 4. CSS in `frontend/src/App.css`

Si aggiungono le classi del nuovo elenco accanto a quelle già scritte per i pannelli
(`.righe-task`, `.riga-task`, `.pannello-piede`), riusandone la grammatica: filetto
sottile fra le righe, titolo che si taglia con l'ellissi, nota in grigio.

- `.scadenze-giorno` — il gruppo, con l'intestazione appiccicata in alto (`position: sticky`)
  mentre si scorre dentro il pannello.
- `.giorno-testata` — nome del giorno a sinistra, chiusura a destra; `.giorno-chiuso` e
  `.giorno-festivo` attenuano il gruppo, perché un giorno in cui non si lavora non deve
  chiedere la stessa attenzione di uno in cui si lavora.
- `.gruppo-ritardo` — il solo gruppo con un accento di colore, `--stato-annullato`.
- `.ore-residue` / `.ore-sforate` — il numero accanto al lavoro.
- Il pannello **non** usa `.pannello-calendario`: il corpo prende una
  `max-height: 24rem` con `overflow-y: auto`, così resta alto quanto il calendario che
  gli sta sotto e le due colonne restano in pari.

## Verifica

1. `cd frontend && npm run lint && npm run build` — devono restare puliti.
2. `cd backend && ./mvnw -o spring-boot:run` più `npm run dev`, e si guarda `/dashboard`:
   - il pannello in alto a sinistra non è più una griglia vuota;
   - con l'utente `marco.rossi@gestionale.local` (password `Password1!`), che ha task
     assegnate, si vede almeno un gruppo-giorno popolato;
   - una task con scadenza passata compare nel gruppo «In ritardo», in cima;
   - una task con `oreConsumate > oreAllocate` mostra il badge dello sforamento.
3. Per vedere una **chiusura**: `INSERT INTO festivita (data, descrizione)` con una data
   nei prossimi 14 giorni, ricaricare, e verificare che il giorno compaia segnato anche
   senza scadenze. Poi togliere la riga.
4. Per vedere il **conflitto con le ferie**: approvare un'assenza che copre la scadenza
   di una task aperta e verificare che l'avviso appaia sulla riga (è lo stesso dato che
   «Le mie task» già mostra, quindi i due pannelli devono concordare).
5. Restringere la finestra sotto i 1200px: il pannello deve restare leggibile quando la
   colonna destra scende sotto i calendari.

Nessun file di test, come per gli ultimi due interventi. Il frontend non ha un runner, e
non è questo il cambiamento in cui introdurlo.
