# Piano di implementazione: vista dettaglio lavoro

## Come usare questo documento

Questo file è il brief operativo per implementare la pagina di dettaglio di un singolo lavoro nel gestionale. Lavora **una fase alla volta**, nell'ordine indicato. Al termine di ogni fase:

1. verifica i criteri di accettazione della fase;
2. riassumi in poche righe cosa hai fatto e cosa hai assunto;
3. **fermati e attendi conferma** prima di passare alla fase successiva.

Prima di scrivere codice, leggi il codice esistente (entità, repository, service, controller, componenti React, sistema dei permessi) e **segui le convenzioni già in uso**: naming, struttura dei package, gestione errori, stile dei componenti. Non introdurre librerie nuove oltre a `@svar-ui/react-gantt`, salvo necessità motivata.

## Contesto

- **Prodotto**: applicativo gestionale per un'azienda di informatica.
- **Stack**: backend Java/Spring con JPA e MySQL (InnoDB, utf8mb4), migrazioni versionate (Flyway-style); frontend React.
- **Lingua**: nomi di tabelle, colonne, enum e testi UI sono in italiano. Mantieni la stessa lingua.
- **Stato attuale**: esiste la migrazione V3 con le tabelle `lavoro`, `task`, `assegnazione_task`. La pagina elenco dei lavori è già stata realizzata.

### Elementi rilevanti dello schema V3 (da rileggere nel codice)

- `lavoro`: stati `BOZZA`, `ATTIVO`, `CHIUSO`; ha date di inizio e fine prevista; il responsabile (PM) può mancare solo in `BOZZA`; ha un campo `versione` per il locking ottimistico.
- `task`: appartiene a un lavoro; stati `DA_FARE`, `IN_CORSO`, `COMPLETATA`, `ANNULLATA`; ha data inizio, scadenza e `stima_ore` (nullable, > 0 se presente); ha `versione`.
- `assegnazione_task`: soft delete tramite `rimossa_il` e `rimossa_da_id`; la colonna `attiva` è **generata da MySQL e non va mappata in JPA**.

## Obiettivo funzionale

### Layout della pagina

- In alto a sinistra: **nome del lavoro**.
- Subito sotto a sinistra: **Gantt di avanzamento in sola lettura**, con `@svar-ui/react-gantt`.
- Sotto il Gantt: sezione **KPI**.
- A destra, dall'alto in basso:
  1. **lista delle task** del lavoro; le `ANNULLATA` evidenziate in **rosso**, le `COMPLETATA` in **verde**;
  2. sezione **"Richiedi ore aggiuntive"**, visibile **solo a chi ha il permesso**;
  3. **storico delle richieste di ore aggiuntive**, con motivo di ciascuna richiesta.

### KPI richiesti (formule originali)

| KPI | Formula |
|---|---|
| Time overrun % | (ore utilizzate − ore stimate) / ore stimate totali × 100 |
| Progress index % | task completate / task totali × 100 |
| On-time delivery rate % | task chiuse entro la scadenza / task completate × 100 |
| Stagnation index % | task ferme da 5 giorni / task in corso × 100 |

## Decisioni assunte (da confermare)

Le formule originali hanno casi ambigui. Applica queste assunzioni e **segnala nel riepilogo di ogni fase se una di esse risulta problematica**. Tienile centralizzate e documentate in un unico punto del codice, così da poterle cambiare facilmente.

1. **Task totali** (progress e on-time): si escludono le `ANNULLATE`.
2. **Ore stimate totali**: somma di `stima_ore` delle task non annullate **più** le ore delle richieste aggiuntive `APPROVATE`. Le task con `stima_ore` nulla contano 0.
3. **Ore utilizzate**: somma di tutte le ore registrate sul lavoro, **comprese** quelle di task poi annullate (sono ore realmente consumate).
4. **Task "ferma"**: task `IN_CORSO` per cui non c'è stato né un cambio di stato né una registrazione ore negli ultimi 5 giorni. Il denominatore è il numero di task `IN_CORSO`.
5. **On-time**: una task è in tempo se la **data** di completamento è minore o uguale alla scadenza (confronto tra date, non tra datetime).
6. **Denominatore zero**: il KPI vale `null` e la UI mostra "n/d". Mai 0%.
7. **Time overrun negativo**: è un valore valido (lavoro sotto budget), da mostrare con segno.
8. **Richiesta ore aggiuntive**: prevede un flusso di approvazione con stati `IN_ATTESA`, `APPROVATA`, `RIFIUTATA`. In questa iterazione si implementa **solo la creazione e la lettura**; l'approvazione/rifiuto è fuori ambito (vedi sezione dedicata).
9. **Permesso**: si introduce un permesso dedicato per la richiesta di ore aggiuntive (nome proposto: `LAVORO_RICHIEDI_ORE`). Verifica nel codice come sono modellati i permessi esistenti e adegua il nome alla convenzione.

---

## Fase 1: Database (migrazione V4)

**Obiettivo**: colmare le lacune dello schema che impediscono di calcolare i KPI e di gestire le richieste.

**Perché serve**:
- `aggiornato_il` cambia a ogni modifica, quindi non è affidabile né come data di completamento né come data di ultimo movimento.
- Non esiste alcuna tabella delle ore utilizzate.
- Non esiste alcuna tabella delle richieste di ore aggiuntive.

**Attività**:

1. Estendere `task` con:
   - data/ora di **completamento** (nullable, valorizzata solo se lo stato è `COMPLETATA`);
   - data/ora dell'**ultimo cambio di stato** (non nulla, default al momento della creazione);
   - un vincolo di coerenza: la data di completamento è valorizzata se e solo se lo stato è `COMPLETATA`.
2. Creare la tabella **`registrazione_ore`**: riferimento a task, riferimento a utente, data di lavoro, ore (decimale, > 0 e ≤ 24), note opzionali, data di creazione. Indice su task e data.
3. Creare la tabella **`richiesta_ore_aggiuntive`**: riferimento a lavoro, utente richiedente, ore (> 0), motivo (obbligatorio), stato con vincolo sui tre valori, utente e data di valutazione (nullable), data di creazione. Indice su lavoro e data di creazione.
4. Rispettare le convenzioni V3: stesso engine, charset, collation, stile dei nomi dei vincoli (`fk_`, `ck_`, `uk_`, `idx_`).
5. **Backfill dei dati esistenti**: per le task già `COMPLETATA`, valorizzare la data di completamento con `aggiornato_il`; per tutte, valorizzare l'ultimo cambio di stato con `aggiornato_il`. Il backfill deve avvenire **prima** di aggiungere il vincolo di coerenza.
6. Inserire il nuovo permesso nei dati di riferimento, se i permessi sono su tabella; assegnarlo ai ruoli appropriati (chiedi quali).

**Criteri di accettazione**:
- La migrazione si applica su un database con dati V3 esistenti senza errori.
- La migrazione è eseguibile su database vuoto.
- I vincoli rifiutano dati incoerenti (verificalo con inserimenti di prova).
- Nessuna modifica a tabelle o colonne non citate.

---

## Fase 2: Backend

**Obiettivo**: esporre i dati della schermata e permettere la creazione di richieste di ore.

**Attività**:

1. **Entità e repository**
   - Aggiornare l'entità `Task` con i due nuovi campi.
   - Creare le entità per `registrazione_ore` e `richiesta_ore_aggiuntive` con enum Java per lo stato.
   - Ricorda: la colonna generata `attiva` di `assegnazione_task` non va mappata.
2. **Aggiornamento del service delle task** (modifica di codice esistente, fai attenzione)
   - A ogni cambio di stato aggiornare la data di ultimo cambio.
   - Passando a `COMPLETATA`, valorizzare la data di completamento.
   - Se una task completata viene riaperta, azzerare la data di completamento.
   - Individua **tutti** i punti in cui lo stato può cambiare (non solo il service principale) e coprili.
3. **Service dei KPI**
   - Recuperare i conteggi aggregati con **una query unica** (evita N+1), più una query separata per le ore extra approvate.
   - Calcolare le percentuali **in Java, non in SQL**, con arrotondamento a un decimale.
   - Gestire denominatore zero (`null`) e valori negativi.
   - Isolare le regole di business in metodi piccoli e testabili.
4. **Endpoint di dettaglio**: `GET /api/lavori/{id}/dettaglio`, che restituisce in un'unica risposta: dati del lavoro, elenco task, KPI, storico richieste ore (ordinato dalla più recente). Usare DTO dedicati; **non esporre le entità**.
5. **Endpoint di creazione richiesta**: `POST /api/lavori/{id}/richieste-ore`.
   - Protetto lato server dal permesso dedicato.
   - Validazione: ore > 0, motivo non vuoto e con lunghezza massima coerente con il DB, lavoro esistente, **lavoro non `CHIUSO`**.
   - Lo stato iniziale è `IN_ATTESA`; il richiedente è l'utente autenticato, **mai** un valore preso dal body.
6. **Gestione errori** coerente con il resto dell'applicazione: lavoro inesistente (404), permesso mancante (403), validazione (400/422), conflitti di versione.

**Criteri di accettazione**:
- Un utente senza permesso riceve 403 sull'endpoint POST, indipendentemente da cosa mostra la UI.
- I KPI sono corretti nei casi limite: lavoro senza task, solo task annullate, nessuna stima, ore sopra e sotto budget, task ferma esattamente al limite dei 5 giorni.
- L'endpoint di dettaglio esegue un numero ridotto e costante di query, indipendente dal numero di task.
- Test unitari sul calcolo dei KPI e test di integrazione su query e permessi.

---

## Fase 3: Frontend, struttura e dati

**Obiettivo**: scheletro della pagina funzionante con i dati reali.

**Attività**:

1. Hook per il caricamento del dettaglio (segui la libreria di data fetching già usata nel progetto), con stati di caricamento, errore e dato vuoto.
2. Pagina a due colonne: titolo in alto, Gantt e KPI a sinistra, pannello laterale a destra. Su schermi stretti, le colonne si impilano.
3. Verifica del permesso nella UI tramite il meccanismo esistente. **È solo una scelta di esperienza d'uso**: la sicurezza è nel backend.
4. Registrazione della rotta e collegamento dalla pagina elenco dei lavori.

**Criteri di accettazione**:
- La pagina si apre dall'elenco, mostra titolo e struttura corretta.
- Errore 404/403 gestito con messaggio comprensibile, senza pagina bianca.

---

## Fase 4: Frontend, componenti

**Obiettivo**: realizzare i cinque blocchi visivi.

**Attività**:

1. **Gantt in sola lettura**
   - Mappare le task nel formato richiesto dalla libreria.
   - La **fine è esclusiva**: aggiungere un giorno alla scadenza perché il giorno di scadenza risulti incluso.
   - Disabilitare ogni interazione (trascinamento, editor, creazione link). Verifica che la prop di sola lettura sia supportata dalla versione installata; in caso contrario, blocca gli eventi di modifica.
   - Colorare le barre per stato, coerentemente con la lista task.
   - Avanzamento: `COMPLETATA` 100%, `DA_FARE` e `ANNULLATA` 0%, `IN_CORSO` provvisoriamente 50% (non esiste ancora un dato migliore; segnalalo nel riepilogo).
   - Il contenitore deve avere **altezza esplicita**, altrimenti il componente collassa.
   - Verifica i nomi reali delle classi CSS generate dalla libreria nel DOM prima di scrivere i selettori: dipendono dalla versione.
2. **Lista task** a destra: tutte le task del lavoro, con titolo, stato e scadenza; rosso per le annullate, verde per le completate. Non affidarti al solo colore: aggiungi anche testo o icona per l'accessibilità.
3. **Sezione KPI** sotto il Gantt: quattro card (nome, valore, suggerimento esplicativo). Valori nulli mostrati come "n/d". Il time overrun mostra il segno.
4. **Form "Richiedi ore aggiuntive"**: campi ore e motivo, validazione lato client allineata a quella del server, stato di invio, gestione errori. Visibile solo con il permesso.
5. **Storico richieste**: data, richiedente, ore, **motivo**, stato. Stato vuoto gestito.
6. Dopo l'invio di una richiesta, aggiornare lo storico (e i KPI, se rilevanti) senza ricaricare la pagina.

**Criteri di accettazione**:
- Il Gantt non permette alcuna modifica.
- Il form non compare senza permesso e, se invocato comunque, il server rifiuta.
- Nessun errore in console; layout corretto a larghezza desktop e ridotta.
- Pagina utilizzabile con tastiera e con colori non essenziali per distinguere gli stati.

---

## Fase 5: Test finali e verifica manuale

1. Unit test sul calcolo KPI (se non già completi in Fase 2).
2. Integration test su permessi e validazioni dell'endpoint POST.
3. Test UI: stato di caricamento, errore, permesso assente, lavoro senza task, lavoro con molte task.
4. Verifica manuale con dati realistici: task senza stima, date a cavallo di mesi e anni, lavoro `BOZZA` senza responsabile, lavoro `CHIUSO`.
5. Controllo prestazioni su un lavoro con centinaia di task.

---

## Fuori ambito (non implementare senza richiesta esplicita)

- Approvazione o rifiuto delle richieste di ore.
- Inserimento o modifica delle ore registrate da questa pagina.
- Storico completo dei cambi di stato delle task (eventuale tabella dedicata in futuro).
- Modifica dei lavori o delle task dal Gantt.
- Dipendenze tra task (link nel Gantt).

## Rischi e attenzioni

| Rischio | Cosa fare |
|---|---|
| Task storiche senza data di completamento reale | Backfill con `aggiornato_il` e segnalare l'approssimazione |
| Stato task modificato in più punti del codice | Cercare tutti i punti di scrittura dello stato e coprirli |
| Gantt: stili e altezza dipendenti dalla versione | Verificare nel DOM, non assumere |
| KPI fuorvianti con molte task senza stima | Proporre un indicatore "stima incompleta" nel riepilogo |
| UI e permessi non allineati | Il controllo vero è sempre nel backend |

## Domande aperte da girare al referente

1. Chi può richiedere ore aggiuntive (ruoli) e chi le approverà in futuro?
2. Chi registra le ore e con quale granularità?
3. Le assunzioni sui KPI (sezione "Decisioni assunte") sono corrette?
4. Per le task già completate prima della migrazione, è accettabile usare `aggiornato_il` come data di completamento?

## Definizione di completato

- Tutte le fasi chiuse con i criteri di accettazione verificati.
- Test automatici verdi.
- Nessuna modifica fuori ambito.
- Riepilogo finale con: file creati/modificati, assunzioni adottate, punti da validare e debito tecnico noto.
