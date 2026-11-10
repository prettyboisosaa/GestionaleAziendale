# Dashboard Dipendenti — Anitia SRL

Documento di progetto per la pagina di gestione quotidiana del dipendente all'interno del gestionale aziendale.

## Contesto

Il gestionale è basato su **permessi**: ogni utente ha un insieme di permessi assegnati e l'utente admin li possiede tutti. I moduli **Task** e **Richieste ferie** sono già in uno stato avanzato. L'obiettivo ora è la pagina di atterraggio del dipendente.

## Obiettivo

Appena il dipendente entra deve trovare:

- una panoramica dei lavori in scadenza;
- un calendario con i lavori in corso e quelli da fare;
- sotto il calendario, la lista delle task assegnate.

## Principi guida

1. **La pagina risponde a una domanda in 5 secondi:** "cosa devo fare oggi e cosa mi sta per scappare?"
2. **Calendario e lista sono due viste degli stessi dati.** Cliccando un giorno sul calendario, la lista sotto si filtra su quel giorno.
3. **Una sola dashboard per tutti i ruoli.** Cosa si vede lo decidono i permessi, non pagine diverse.
4. **Aggiornare una task deve costare un clic.** Altrimenti i dati invecchiano e la dashboard perde credibilità.

## Layout

```
┌──────────────────────────────────────────────────────────────┐
│ Buongiorno Marco · venerdì 6 novembre                        │
├─────────────┬─────────────┬───────────────┬──────────────────┤
│ Scadute  2  │ Oggi  3     │ Settimana  7  │ In corso  4      │
├─────────────┴─────────────┴───────────────┴──────────────────┤
│ CALENDARIO (vista settimana di default)                      │
│  ▬▬▬▬▬  task in corso (barra inizio → scadenza)              │
│  ●      task da fare (marker sulla scadenza)                 │
│  ░░░░░  ferie approvate / festività                          │
├──────────────────────────────────────────────────────────────┤
│ LE MIE TASK   [Tutte | Oggi | Scadute]   [giorno: 12/11 ✕]   │
│ ○ Preventivo cliente Rossi   ALTA   scade oggi   [Avvia ▸]   │
│ ◐ Revisione contratto        MEDIA  scade 10/11  [Chiudi ✓]  │
└──────────────────────────────────────────────────────────────┘
```

Le card in alto sono **cliccabili** e applicano il filtro corrispondente alla lista: un solo componente lista con più punti di ingresso.

## Calendario

| Elemento | Rappresentazione |
|---|---|
| Task in corso | Barra da data inizio (o `started_at`) a scadenza |
| Task da fare | Marker sul giorno di scadenza |
| Task completate | Nascoste di default, visibili con toggle |
| Ferie approvate | Blocco pieno |
| Ferie in attesa | Blocco tratteggiato |
| Festività | Sfondo evidenziato |

**Conflitti ferie/scadenze:** se una task scade in un giorno in cui il dipendente è in ferie, viene mostrato un avviso. È il punto di collegamento più utile tra i due moduli esistenti.

**Viste:** settimana di default su desktop, lista su mobile. Libreria consigliata: **FullCalendar** (supporta mese/settimana/lista e caricamento per intervallo visibile).

## Permessi

| Permesso | Scopo |
|---|---|
| `dashboard.view` | Accesso alla pagina |
| `task.view.own` | Vedere le proprie task |
| `task.view.team` | Vedere le task del proprio team |
| `task.view.all` | Vedere tutte le task aziendali |
| `task.update_status.own` | Cambio stato rapido sulle proprie task |
| `leave.view.team` | Vedere le ferie dei colleghi |

Regole:

- Ogni widget dichiara il permesso che richiede.
- L'ambito di default è sempre **"Le mie"**, con toggle "Team / Tutti" visibile solo a chi ha il permesso. Questo evita che l'admin, avendo tutti i permessi, si ritrovi l'intera azienda in dashboard.
- Il filtro è **sempre applicato lato server**, mai solo nascosto nel frontend.

## Backend

### Endpoint

`GET /api/me/dashboard` restituisce conteggi delle card e task urgenti in una sola chiamata, per un caricamento rapido della parte visibile.

`GET /api/me/calendar?from=2026-11-02&to=2026-11-08` restituisce gli eventi dell'intervallo visibile, normalizzati in un formato unico.

### Formato evento

```json
{
  "id": "task-142",
  "type": "task",
  "title": "Preventivo cliente Rossi",
  "start": "2026-11-03",
  "end": "2026-11-06",
  "status": "in_progress",
  "priority": "high",
  "conflict": false,
  "url": "/tasks/142"
}
```

`type` può essere `task`, `leave` o `holiday`. Il frontend non conosce la struttura delle tabelle sottostanti, quindi in futuro si possono aggiungere nuove fonti (riunioni, scadenze fiscali) senza modificare il calendario.

### Dettagli tecnici

- **Fuso orario:** "scade oggi" calcolato lato server in `Europe/Rome`.
- **Tipo della scadenza:** se non c'è un orario, salvarla come `date` e non `datetime`, per evitare task che slittano di un giorno a mezzanotte.
- **`started_at` automatico:** valorizzato quando la task passa a "in corso", così il calendario ha un inizio reale anche senza data di inizio compilata.
- **Indice:** `(assignee_id, status, due_date)`, la query eseguita a ogni accesso.

## Cambio stato rapido

Dalla lista: **Da fare → In corso → Completata** con un clic, senza aprire il dettaglio. Protetto dal permesso `task.update_status.own`.

## Roadmap

### Fase 1 — MVP

- Endpoint `/api/me/dashboard`
- Card con i conteggi (scadute, oggi, settimana, in corso)
- Lista task con filtri e cambio stato inline

### Fase 2 — Calendario

- Endpoint `/api/me/calendar`
- Calendario con task, ferie e festività
- Click sul giorno che filtra la lista
- Avviso conflitti ferie/scadenze

### Fase 3 — Estensioni

- Vista team per i responsabili
- Notifiche (riepilogo mattutino in-app o via email)
- "Focus di oggi": il dipendente fissa 2-3 task per la giornata

## Prossimi passi

- [ ] Definire lo stack (frontend, backend, database)
- [ ] Verificare lo schema attuale della tabella task (presenza di `start_date`, `started_at`, tipo di `due_date`, assegnatario singolo o multiplo)
- [ ] Creare i nuovi permessi e assegnarli ai ruoli esistenti
- [ ] Partire con la Fase 1
