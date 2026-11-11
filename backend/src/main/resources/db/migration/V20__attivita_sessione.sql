-- Tag di attività e descrizione sulla registrazione del calendario.
--
-- Il calendario settimanale «Cosa ho fatto» registrava a mano due istanti su
-- una task, e nient'altro. Ma non tutto il lavoro di una giornata è una task:
-- la manutenzione per un cliente, quella interna, una riunione, una trasferta
-- sono tempo vero che prima non si riusciva a registrare — o si appendeva a
-- una task che non lo riguardava, falsando il consuntivo di un lavoro.
--
-- Questa migrazione aggiunge tre colonne e rende `task_id` facoltativa.


-- --- le tre colonne nuove ---------------------------------------------------
--
-- `tipo_attivita` nasce NULL e diventa NOT NULL dopo il backfill: aggiungere
-- una colonna obbligatoria a una tabella che ha già righe vuole un default
-- inventato, e l'unico valore giusto qui non è un default ma la verità su
-- quelle righe — erano tutte lavoro su task, perché era l'unica cosa che si
-- potesse scrivere.
--
-- `descrizione` sta a 500 caratteri: è una riga su cosa si è fatto, non un
-- diario. Un TEXT inviterebbe a incollarci dentro un verbale.
ALTER TABLE sessione_task
    ADD COLUMN tipo_attivita VARCHAR(30)  NULL AFTER origine,
    ADD COLUMN descrizione   VARCHAR(500) NULL AFTER tipo_attivita,
    ADD COLUMN lavoro_id     BIGINT       NULL AFTER utente_id;

UPDATE sessione_task SET tipo_attivita = 'LAVORO_TASK' WHERE tipo_attivita IS NULL;

ALTER TABLE sessione_task MODIFY tipo_attivita VARCHAR(30) NOT NULL;


-- --- la task diventa facoltativa --------------------------------------------
--
-- È il cuore del cambiamento. Una riunione o una manutenzione interna non
-- stanno sotto nessuna task, e appenderle a una qualunque vorrebbe dire
-- scrivere ore di sviluppo che non sono state fatte.
--
-- La chiave esterna `fk_sessione_task` **resta**: una colonna nullable con una
-- FK accetta NULL e verifica il resto, che è esattamente quello che serve.
--
-- Le colonne generate non cambiano, e vale la pena dire perché: `minuti`
-- dipende dai due istanti e `in_corso` da `chiusa_il`, `annullata_il` e
-- `utente_id`. Nessuna delle due guarda `task_id`, quindi
-- `uk_sessione_in_corso` continua a garantire una sola sessione aperta per
-- persona anche adesso che una sessione può non avere una task.
ALTER TABLE sessione_task MODIFY task_id BIGINT NULL;


-- --- i vincoli --------------------------------------------------------------
--
-- `lavoro_id` è il riferimento di un'attività che riguarda un lavoro senza
-- essere una sua task: la manutenzione per il cliente X. Si legge tramite
-- `SessioneTask.lavoroCitato()`, che torna il lavoro della task se la task
-- c'è, e questa colonna altrimenti — due strade per lo stesso riferimento,
-- mai popolate entrambe.
--
-- ⚠️ Queste ore **non consumano il monte ore del lavoro**, ed è una decisione,
-- non una dimenticanza. Le somme del consuntivo
-- (`TaskRepository.sommaOreConsumate` e sorelle) fanno `JOIN task ON t.id =
-- s.task_id`: una sessione senza task ne resta fuori da sé. Si registrano come
-- fatte, non come consumate — la manutenzione di un lavoro consegnato non
-- erode il budget con cui quel lavoro è stato sviluppato. Entrano invece nei
-- totali della persona, che sommano i minuti di tutte le sessioni.
ALTER TABLE sessione_task
    ADD CONSTRAINT fk_sessione_lavoro FOREIGN KEY (lavoro_id) REFERENCES lavoro (id),
    ADD CONSTRAINT ck_sessione_attivita CHECK (tipo_attivita IN (
        'LAVORO_TASK', 'MANUTENZIONE_CLIENTE', 'MANUTENZIONE_INTERNA', 'SUPPORTO',
        'SOPRALLUOGO', 'RIUNIONE', 'FORMAZIONE', 'AMMINISTRAZIONE', 'TRASFERTA',
        'ALTRO')),
    -- Il solo tipo che senza una task non vuol dire niente. L'inverso — una
    -- task su un tipo diverso — è ammesso di proposito: una riunione sul
    -- lavoro X può ben essere agganciata alla sua task.
    ADD CONSTRAINT ck_sessione_ancora CHECK (
        task_id IS NOT NULL OR tipo_attivita <> 'LAVORO_TASK');

-- Per leggere quante ore sono andate in manutenzione o in riunioni, persona
-- per persona: è la domanda che questo tag esiste per permettere.
ALTER TABLE sessione_task ADD INDEX idx_sessione_attivita (utente_id, tipo_attivita);


-- --- quello che NON cambia --------------------------------------------------
--
-- Nessun permesso nuovo. Registrare la propria giornata sta su
-- `TASK_AGGIORNA_STATO`, che è nel pavimento dalla V14: scegliere un tag
-- invece di una task non è un potere in più, è la stessa registrazione detta
-- meglio. Il riferimento al lavoro passa da `LAVORI_VISUALIZZA_PROPRI`, anch'esso
-- base, perché è `ServizioLavori.lavoroVisibile` a verificarlo.
--
-- `MANUTENZIONE_CLIENTE` pretende un lavoro, ma il vincolo **non è qui**: un
-- CHECK sulla coppia (tipo_attivita, lavoro_id) bloccherebbe per sempre una
-- regola che è di prodotto e non di integrità — se domani la manutenzione
-- interna volesse un lavoro, servirebbe una migrazione per cambiare una
-- tendina. Sta in `TipoAttivita.richiedeLavoro()`, che la leggono sia il
-- servizio sia l'interfaccia. È lo stesso confine con cui la
-- non-sovrapposizione delle ore vive in `ServizioTempo` e non nel database.
