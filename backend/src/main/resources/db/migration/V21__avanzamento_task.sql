-- Due istanti su una task, per poter misurare l'avanzamento di un lavoro.
--
-- La pagina di un lavoro mostra le ore e gli stati, ma non riesce a dire due
-- cose che un responsabile chiede: quante consegne sono arrivate in tempo, e
-- quante task sono ferme. Nessuna delle due si ricava dai dati che ci sono.
--
-- `aggiornato_il` non basta, e vale la pena dire perche': cambia a **ogni**
-- modifica della riga. Una task completata il 3 e ritoccata nel titolo il 20
-- direbbe di essere stata completata il 20, e nessuno potrebbe accorgersene
-- guardando il dato.


-- --- le due colonne ---------------------------------------------------------
--
-- `completato_il`: quando la task e' passata a COMPLETATO. Resta nulla su
-- tutte le altre.
--
-- `stato_aggiornato_il`: l'ultimo cambio di stato, qualunque esso sia. Nasce
-- nullable e diventa NOT NULL dopo il riempimento: una colonna obbligatoria
-- su una tabella che ha gia' righe vorrebbe un valore inventato per tutte.
ALTER TABLE task
    ADD COLUMN completato_il       DATETIME NULL AFTER stato,
    ADD COLUMN stato_aggiornato_il DATETIME NULL AFTER completato_il;


-- --- il riempimento, e cio' che NON si riempie -------------------------------
--
-- `stato_aggiornato_il` prende `aggiornato_il`: e' un'approssimazione, ma
-- innocua per quello che deve misurare. Serve a rispondere a «da quanto e'
-- ferma questa task», e una task ferma da sempre resta ferma qualunque sia
-- l'istante da cui si conta.
UPDATE task SET stato_aggiornato_il = aggiornato_il WHERE stato_aggiornato_il IS NULL;

ALTER TABLE task MODIFY stato_aggiornato_il DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- `completato_il` **non si riempie**, ed e' una decisione.
--
-- Riempirla con `aggiornato_il` darebbe una data di consegna sbagliata su ogni
-- task toccata dopo la chiusura, e sarebbe una data dall'aspetto vero: il KPI
-- delle consegne in tempo misurerebbe un fatto inventato. Le task completate
-- prima di questa migrazione restano quindi senza data, escono dal conto, e il
-- KPI dichiara su quante task e' calcolato. Si popola da qui in avanti.


-- --- il vincolo di coerenza -------------------------------------------------
--
-- In **una sola direzione**: se c'e' una data di completamento, lo stato e'
-- COMPLETATO. L'inverso non si puo' pretendere — sarebbe violato dalla prima
-- task completata ieri, che per la decisione qui sopra ha la data nulla.
--
-- ⚠️ Questo CHECK ha una conseguenza sul codice, e non e' facoltativa:
-- `TaskRepository.annullaTutteDelLavoro` e' una UPDATE in blocco che porta ad
-- ANNULLATO tutte le task di un lavoro annullato, scavalcando
-- `Task.cambiaStato`. Senza azzerare anche `completato_il`, una task
-- completata che viene annullata violerebbe questo vincolo e farebbe fallire
-- l'annullamento del lavoro. La query e' stata estesa in questa stessa
-- modifica.
ALTER TABLE task ADD CONSTRAINT ck_task_completato
    CHECK (completato_il IS NULL OR stato = 'COMPLETATO');


-- --- quello che NON cambia --------------------------------------------------
--
-- Nessuna tabella nuova. Le ore utilizzate si leggono da `sessione_task`
-- (V15), che e' il registro del tempo di questo gestionale: una seconda
-- tabella delle ore registrate sarebbe un doppio libro contabile, ed e'
-- esattamente cio' che la V17 ha finito di smontare cancellando
-- `voce_dichiarazione`. Le richieste di ore stanno in `richiesta_ore` (V8),
-- con il loro motivo e il loro flusso di approvazione.
--
-- Nessun permesso nuovo: chiedere ore passa da `ORE_RICHIEDI` e deciderle da
-- `ORE_RICHIESTE_GESTISCI`, entrambi gia' in uso.
--
-- Nessun indice nuovo: le query dei KPI partono da `lavoro_id`, coperto da
-- `idx_task_lavoro_stato`, e l'ultima sessione per task da `idx_sessione_task`.
