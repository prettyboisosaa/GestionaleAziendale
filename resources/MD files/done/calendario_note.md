# Note di consegna — `calendario.md`

Dashboard del dipendente: Fase 1 (MVP) e Fase 2 (calendario). Cosa è stato
adattato, cosa esisteva già, cosa resta fuori.

## Decisioni concordate prima di iniziare

| Domanda | Scelta |
|---|---|
| Libreria del calendario | **FullCalendar**, come consiglia il documento |
| Pagina di atterraggio | **Dashboard** per chi ha `TASK_VISUALIZZA_ASSEGNATE`; gli altri restano su `/lavori` o `/utenti` |
| Priorità sulle task | **Non ora**: la Fase 1 non la elenca, e l'urgenza la dà già la scadenza |
| Ambito | **Fasi 1 e 2**; la Fase 3 resta fuori |

## Cosa c'era già, e non è stato toccato

Il documento chiede, fra i prossimi passi, di verificare lo schema. Esito:

- **`scadenza` è già `DATE`** e non `datetime`: la raccomandazione del
  documento era già rispettata, niente da fare.
- **`GET /api/mie-task`** restituiva già `inRitardo`, `giorniAllaScadenza`,
  `stato`, `scadenza`, `statoLavoro`: bastano per tutte e quattro le card
  **senza nessuna modifica al database**. La dashboard è il suo primo
  consumatore — prima quell'endpoint non lo usava nessuno.
- **`ServizioTask.segniDelPeriodo`** era impalcatura già pronta per un
  calendario, ma non esposta. Non è stata usata: produce segni per giorno,
  mentre il documento vuole barre per le task in corso. È rimasta dov'era, non
  l'ho cancellata.
- `ServizioCalendario`, `FestivitaRepository` e `AssenzaRepository` coprivano
  già festività e assenze.
- **Assegnatari multipli**: `assegnazione_task` lega più persone a una task,
  mentre il documento immagina un `assignee_id` singolo. Non cambia niente per
  la dashboard, che filtra sulle assegnazioni attive di chi guarda.

## Adattamenti

**Permessi.** Il documento propone nomi minuscoli con i punti
(`task.view.own`). Lo schema usa codici maiuscoli, e quelli che servono
esistono già: `TASK_VISUALIZZA_ASSEGNATE` per vedere la pagina,
`TASK_AGGIORNA_STATO` per il cambio stato rapido. **Nessun permesso nuovo.**
In particolare non è stato creato `dashboard.view`: qui non distinguerebbe
niente, perché la pagina mostra le proprie task e le proprie assenze, e lo si
concederebbe sempre insieme a `TASK_VISUALIZZA_ASSEGNATE`. Un permesso che si
concede sempre insieme a un altro è un permesso che non esiste.

**Rotte.** `/api/mia-dashboard` e `/api/mio-calendario` invece di
`/api/me/dashboard` e `/api/me/calendar`, per seguire `/api/mie-task` e
`/api/mie-assenze`, che sono già la convenzione del gestionale.

**Formato evento.** Campi in italiano come il resto dell'API
(`titolo`, `inizio`, `fine`, `stato`, `conflitto`, `url`), con `tipo` fra
`TASK`, `ASSENZA` e `FESTIVITA`. La traduzione verso `title/start/end` di
FullCalendar la fa il componente.

**`fine` è inclusiva.** FullCalendar, per gli eventi a giornata intera, vuole
una fine *esclusiva*: il giorno in più lo aggiunge il frontend. La conversione
sta lì di proposito — è una convenzione della libreria di disegno, non un
fatto del dominio, e un'API che dicesse «finisce il 7» per un'assenza che
finisce il 6 sarebbe una trappola.

**`started_at` non è stato aggiunto.** Il documento lo vuole per dare al
calendario un inizio reale. Qui `task.data_inizio` esiste, è obbligatorio e
non è mai nullo, quindi la barra di una task in corso ha già da dove partire:
una colonna in più avrebbe richiesto una migrazione e una modifica a
`cambiaStato`, che funziona.

**«Settimana».** Il conteggio va da oggi alla **domenica** di questa settimana,
non «fra sette giorni»: il calendario si apre sulla vista settimanale, e un
numero che nella griglia accanto non si ritrova sarebbe peggio di nessun
numero.

## File

Backend: package `dashboard` tutto nuovo (`TipoEvento`, `EventoCalendario`,
`ContatoriDashboard`, `RiepilogoDashboard`, `ServizioDashboard`,
`DashboardController`). L'unica
aggiunta a codice esistente è un metodo in `FestivitaRepository`
(`trovaTra`), perché al calendario serve anche il nome della chiusura e non
solo la data. Nessun metodo esistente è stato modificato.

Frontend: `dashboard/api.ts`, `dashboard/PaginaDashboard.tsx`, la rotta e la
voce «Oggi» nella barra, gli stili in `App.css`. FullCalendar 6.1.22
(`core`, `react`, `daygrid`, `list`, `interaction`): l'adapter React era
inizialmente finito alla 7, che appartiene a un'architettura diversa e non ha
i plugin classici — le versioni sono state riallineate tutte alla 6.

## Verifiche fatte

Backend `clean test-compile`, frontend `tsc --noEmit`, `oxlint` e
`npm run build`: tutti puliti. Contro il database vero:

- contatori esatti su tre task costruite apposta — una scaduta, una in
  scadenza oggi, una entro domenica: `{scadute: 1, oggi: 1, settimana: 2, inCorso: 0}`;
- una task portata a `IN_CORSO` diventa una barra `02/11 → 04/11`, le altre
  restano segni sul giorno di scadenza;
- **conflitto ferie/scadenza rilevato**: una task che scade l'8 novembre
  dentro ferie approvate dal 7 al 9 esce con `conflitto: true` e il suo id in
  `taskInConflitto`;
- l'assenza approvata compare come blocco `07/11 → 09/11`.

## Due cose da sapere

**La tabella `festivita` è vuota.** Il codice delle festività c'è e la query
è la stessa che usa già il conteggio dei giorni lavorativi, ma finché nessuno
inserisce delle chiusure il calendario non ne mostrerà. Non è un difetto,
è che quei dati non ci sono.

**L'admin non può chiedere assenze.** In `utente_permesso` c'è uno
scostamento che revoca `ASSENZE_RICHIEDI` all'utente 1, impostato il
6 novembre alle 14:38. Il 403 su `/api/mie-assenze` è quindi il comportamento
corretto, non un guasto: lo scostamento per utente vince sul ruolo, ed è
esattamente ciò che deve fare. Si toglie dalla pagina Permessi.

**Peso del bundle.** FullCalendar porta il build da 320 KB a 565 KB
(94 → 168 KB compressi), e Vite avvisa di superare i 500 KB. Si rimedia
caricando la dashboard con `React.lazy`, così il calendario non viene
scaricato da chi non apre quella pagina: non l'ho fatto perché cambia il modo
in cui si carica la pagina di atterraggio, ed è una cosa da decidere insieme.

## Dati di prova lasciati nel database

Lavoro `LAV-001` «Portale clienti» con tre task assegnate all'admin (una in
corso, una scaduta, una in scadenza), la terza assegnata anche a Giulia Verdi,
e per Giulia delle ferie approvate dal 7 al 9 novembre. Servivano a verificare
contatori, barre e conflitti; si cancellano quando non servono più.
