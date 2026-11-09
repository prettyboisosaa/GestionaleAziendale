-- =====================================================================
-- V17 - Via le dichiarazioni
--
-- La V16 ha portato le vecchie voci dentro `sessione_task`. Qui si eliminano
-- le tabelle, i permessi e le impostazioni che servivano alla giornata, e si
-- mettono al loro posto i permessi di lettura del tempo.
--
-- Tutte le istruzioni sono ripetibili: DROP ... IF EXISTS, INSERT guardati da
-- NOT EXISTS, DELETE che su un database gia' ripulito non trovano niente.
--
-- Nessun permesso nuovo per il dipendente, di proposito. TASK_AGGIORNA_STATO
-- e' gia' nel pavimento e dice gia' «aggiornare lo stato delle task a cui e'
-- assegnato»: avviare, mettere in pausa e completare SONO quello. Un permesso
-- che si concede sempre insieme a un altro e' un permesso che non esiste.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Le tabelle
--
-- Nessuna chiave esterna punta loro da fuori: l'unica e' interna, da
-- voce_dichiarazione a dichiarazione. Quindi l'ordine e' figlia prima,
-- madre dopo, e niente altro nello schema se ne accorge.
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS voce_dichiarazione;
DROP TABLE IF EXISTS dichiarazione;


-- ---------------------------------------------------------------------
-- 2. Il catalogo dei permessi
--
-- Il CHECK sulle categorie si elimina adesso e si ricrea in fondo: mentre non
-- c'e', le righe possono passare dalla vecchia categoria alla nuova senza che
-- un vincolo intermedio rifiuti uno dei due stati. MySQL non permette di
-- modificare un CHECK, va eliminato e ricreato - come fanno la V8, la V10, la
-- V12 e la V14.
-- ---------------------------------------------------------------------
ALTER TABLE permesso DROP CHECK ck_permesso_categoria;

-- I due eredi. `ordine` 60 e 61 sono gli stessi slot dei permessi che
-- sostituiscono: la categoria cambia nome, non posto.
INSERT INTO permesso (codice, categoria, descrizione, ordine)
SELECT 'TEMPO_VISUALIZZA_LAVORO', 'TEMPO',
       'Vedere il tempo di chi ha task nei lavori di cui è responsabile', 60
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM permesso WHERE codice = 'TEMPO_VISUALIZZA_LAVORO');

INSERT INTO permesso (codice, categoria, descrizione, ordine)
SELECT 'TEMPO_VISUALIZZA_TUTTI', 'TEMPO',
       'Vedere il tempo registrato da tutti', 61
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM permesso WHERE codice = 'TEMPO_VISUALIZZA_TUTTI');


-- Chi aveva il vecchio permesso ha il nuovo: si copiano le concessioni invece
-- di nominare i ruoli a mano, cosi' una personalizzazione fatta
-- dall'interfaccia non si perde nel passaggio. E' lo stesso modo in cui la
-- V14 ha spostato DICHIARAZIONI_VISUALIZZA_TEAM su _LAVORO.
INSERT INTO ruolo_permesso (ruolo_id, permesso_codice)
SELECT vecchio.ruolo_id, 'TEMPO_VISUALIZZA_LAVORO'
FROM ruolo_permesso vecchio
WHERE vecchio.permesso_codice = 'DICHIARAZIONI_VISUALIZZA_LAVORO'
  AND NOT EXISTS (SELECT 1 FROM ruolo_permesso gia
                  WHERE gia.ruolo_id = vecchio.ruolo_id
                    AND gia.permesso_codice = 'TEMPO_VISUALIZZA_LAVORO');

INSERT INTO ruolo_permesso (ruolo_id, permesso_codice)
SELECT vecchio.ruolo_id, 'TEMPO_VISUALIZZA_TUTTI'
FROM ruolo_permesso vecchio
WHERE vecchio.permesso_codice = 'DICHIARAZIONI_VISUALIZZA_TUTTE'
  AND NOT EXISTS (SELECT 1 FROM ruolo_permesso gia
                  WHERE gia.ruolo_id = vecchio.ruolo_id
                    AND gia.permesso_codice = 'TEMPO_VISUALIZZA_TUTTI');

-- E i ruoli protetti hanno tutti i permessi: ogni migrazione che ne aggiunge
-- uno deve concederlo anche a loro, come avverte la V1. La copia qui sopra lo
-- fa gia' per chi aveva i vecchi, questo copre il caso in cui non li avesse.
INSERT INTO ruolo_permesso (ruolo_id, permesso_codice)
SELECT r.id, p.codice
FROM ruolo r CROSS JOIN permesso p
WHERE r.protetto = TRUE
  AND p.categoria = 'TEMPO'
  AND NOT EXISTS (SELECT 1 FROM ruolo_permesso gia
                  WHERE gia.ruolo_id = r.id AND gia.permesso_codice = p.codice);


-- Via i tre vecchi: prima gli scostamenti e le concessioni, poi il catalogo,
-- perche' le chiavi esterne pretendono quest'ordine.
--
-- DICHIARAZIONI_COMPILA era nel pavimento, messo la' dalla V14. Cancellando
-- la riga esce dal pavimento da se': il terzo ramo di permesso_effettivo
-- concede «i permessi con base = TRUE», e questo non esiste piu'. Il pavimento
-- passa da cinque codici a quattro, e il quinto non serviva piu' a niente -
-- compilare la giornata non e' piu' una cosa che si fa.
DELETE FROM utente_permesso WHERE permesso_codice IN (
    'DICHIARAZIONI_COMPILA', 'DICHIARAZIONI_VISUALIZZA_LAVORO',
    'DICHIARAZIONI_VISUALIZZA_TUTTE');

DELETE FROM ruolo_permesso WHERE permesso_codice IN (
    'DICHIARAZIONI_COMPILA', 'DICHIARAZIONI_VISUALIZZA_LAVORO',
    'DICHIARAZIONI_VISUALIZZA_TUTTE');

DELETE FROM permesso WHERE codice IN (
    'DICHIARAZIONI_COMPILA', 'DICHIARAZIONI_VISUALIZZA_LAVORO',
    'DICHIARAZIONI_VISUALIZZA_TUTTE');


-- La categoria DICHIARAZIONI non ha piu' righe, TEMPO ne ha due.
--
-- ATTENZIONE a chi scrivera' la V18: questa lista va copiata DA QUI. Non
-- dalla V1, non dalla V12, non dalla V14 - quella aveva ancora DICHIARAZIONI
-- e non aveva TEMPO.
ALTER TABLE permesso ADD CONSTRAINT ck_permesso_categoria
    CHECK (categoria IN ('UTENTI', 'RUOLI', 'LAVORI', 'TASK',
                         'TEMPO', 'ORE', 'ASSENZE', 'SISTEMA'));


-- ---------------------------------------------------------------------
-- 3. Le impostazioni
--
-- Il tetto di una sessione prende il posto del tetto giornaliero: la domanda
-- non e' piu' «quante ore puo' dichiarare in un giorno» ma «dopo quante ore
-- una sessione aperta e' da considerare dimenticata».
--
-- Il progetto non ha processi schedulati e non ne acquisisce uno qui: la
-- chiusura al tetto e' pigra, la fa il servizio la prima volta che legge o
-- scrive qualcosa per quella persona. E' lo stesso ragionamento che MiaTask
-- mette per iscritto a proposito di `inRitardo` - «un flag su una colonna
-- andrebbe riscritto ogni notte da qualcosa che in questo progetto non
-- esiste».
-- ---------------------------------------------------------------------
INSERT INTO impostazione (chiave, valore, descrizione)
SELECT 'tempo.ore_massime_sessione', '8',
       'Ore dopo cui una sessione aperta viene chiusa d''ufficio e resta da confermare'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM impostazione
                  WHERE chiave = 'tempo.ore_massime_sessione');

-- `dichiarazione.giorni_modifica` contava in giorni lavorativi la finestra per
-- correggere una giornata inviata. Adesso la finestra e' «oggi», che non ha
-- bisogno di aritmetica sul calendario: non serve un parametro per dirlo.
DELETE FROM impostazione WHERE chiave IN (
    'dichiarazione.ore_massime_giorno', 'dichiarazione.giorni_modifica');
