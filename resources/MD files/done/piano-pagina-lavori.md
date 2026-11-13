# Piano: pagina elenco Lavori (vista a card)

## Obiettivo
Aprendo la sezione **Lavori** si vedono card grandi con **nome**, **descrizione** e **stato** del lavoro. Click sulla card → dettaglio (`/lavori/:id`, step successivo).

## Da confermare prima di partire
1. Stack frontend (React / Angular / Vue).
2. Visibilità: tutti vedono tutti i lavori, o solo quelli in cui sono coinvolti? Chi vede le BOZZA?
3. Card minimale (nome, descrizione, stato) o anche codice, responsabile, scadenza?

## Backend
**Endpoint**: `GET /api/lavori?stato=&q=`

**DTO** `LavoroCardDto`: `id`, `codice`, `nome`, `descrizioneBreve`, `stato` (+ opzionali: `dataFinePrevista`, `responsabile`).

**Punti chiave**
- Usare una proiezione/DTO, non l'entità (evita N+1 e lettura della TEXT intera).
- Descrizione troncata lato query: `LEFT(descrizione, 300)`.
- Ordinamento: ATTIVO → BOZZA → CHIUSO, poi `data_fine_prevista` ASC (`CASE` nell'`ORDER BY`).
- Filtro di visibilità applicato nella query, non in memoria.
- Test: repository (filtro, ordinamento, visibilità) e controller (200/401/403).

## Frontend
**Componenti**: `LavoriPage` → `Toolbar` (ricerca + filtro stato) → `LavoriGrid` → `LavoroCard`.

**`LavoroCard`**
- Nome (max 2 righe), badge stato, descrizione troncata a 3-4 righe, footer con codice/responsabile/scadenza.
- Altezza minima fissa (~200-240 px); descrizione assente → "Nessuna descrizione".
- Intera card come link vero (accessibilità, apertura in nuova tab).

**Badge stato** (mappa unica `stato → {label, colore}`, riusabile nel dettaglio)

| Stato | Etichetta | Colore |
|---|---|---|
| BOZZA | Bozza | grigio |
| ATTIVO | Attivo | verde |
| CHIUSO | Chiuso | blu scuro |

**Griglia**: `grid-template-columns: repeat(auto-fill, minmax(320px, 1fr))`.

**Stati pagina**: loading (skeleton), vuoto, nessun risultato (con "Azzera filtri"), errore (con "Riprova").

**URL**: filtri e ricerca nella query string per ripristinarli al ritorno dal dettaglio.

## Ordine di lavoro
1. Conferma delle decisioni sopra.
2. Backend: DTO, query, endpoint, test.
3. Frontend: client API, mappa stati, `LavoroCard`, griglia.
4. Stati loading / vuoto / errore.
5. Filtro stato + ricerca.
6. Routing verso `/lavori/:id` (placeholder).
7. Verifica responsive e accessibilità.

## Fuori scope
Creazione/modifica lavoro, dettaglio con task e assegnazioni, indicatori di avanzamento.
