-- =====================================================================
-- V14 - Il pavimento entra in funzione, e cade il concetto di team
--
-- La V13 ha aggiunto la colonna. Qui si dice quali permessi sono di base, la
-- vista comincia a concederli, e si puliscono le ripetizioni che restano in
-- giro.
--
-- Nella stessa migrazione cade il concetto di team: una task e' assegnabile a
-- chiunque sia attivo, e chi e' responsabile di un lavoro alloca le persone
-- che gli servono. Il confine vero resta «sono il responsabile di questo
-- lavoro», che e' quello che i servizi verificano con eResponsabile.
--
-- Ogni istruzione qui dentro e' RIPETIBILE: se la migrazione si fermasse a
-- meta' si puo' rieseguire dopo un flyway repair senza effetti diversi. E' il
-- motivo per cui la DDL sta nella V13 e non qui.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Quali sono i permessi del pavimento
--
-- Cinque: il minimo per poter usare il gestionale, qualunque ruolo si abbia
-- scritto accanto al nome.
--
-- Nessun errore se un codice fosse scritto male: una UPDATE che non trova
-- righe non fallisce, e il pavimento resterebbe vuoto in silenzio. Lo copre
-- un test (UtenteRepositoryTest), che e' la classe che esiste proprio per
-- cogliere l'SQL che compila e poi mente.
-- ---------------------------------------------------------------------
UPDATE permesso SET base = TRUE WHERE codice IN (
    'TASK_VISUALIZZA_ASSEGNATE',   -- le proprie task, la dashboard, il calendario
    'TASK_AGGIORNA_STATO',         -- chiudere le task a cui si e' assegnati
    'LAVORI_VISUALIZZA_PROPRI',    -- i lavori in cui si e' coinvolti
    'DICHIARAZIONI_COMPILA',       -- la propria giornata
    'ASSENZE_RICHIEDI');           -- ferie e permessi per se stessi


-- ---------------------------------------------------------------------
-- 2. La vista, con un terzo ramo
--
-- I due rami esistenti non cambiano. Il nuovo concede il pavimento a chiunque
-- sia attivo, anche a chi non ha nessun ruolo, ed e' l'unico senza filtro
-- sugli scostamenti: e' questo che rende il pavimento invalicabile, anche se
-- una riga concesso = FALSE sopravvivesse alla pulizia del passo 3.
--
-- DEVE essere UNION e non UNION ALL. Con UNION ALL i permessi base di un
-- amministratore arriverebbero due volte - una dal ruolo protetto, una dal
-- pavimento - e contaPermessiEffettivi li conterebbe doppi, mentre
-- trovaCodiciPermesso restituirebbe authority duplicate.
--
-- Il filtro u.attivo = TRUE e' ripetuto in tutti e tre i rami, come diceva
-- gia' la V6: un utente disattivato non ha nessun permesso, ed e' la
-- proprieta' su cui si appoggia tutto il resto.
--
-- La UNION sta al livello piu' alto e i nomi delle colonne li decide il primo
-- SELECT. Gli altri rami li dichiarano comunque con un alias: chi un giorno
-- cambiasse l'ordine dei rami non deve poter rinominare le colonne della
-- vista senza accorgersene. (La V6 motivava questa forma dicendo che MySQL
-- non accetta tabelle derivate in una vista: era vero fino alla 8.0.13, la
-- restrizione e' caduta nella 8.0.14 e qui gira la 8.4. La forma resta quella
-- giusta, la ragione non e' piu' quella.)
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW permesso_effettivo AS
-- dai ruoli, salvo revoca per questa persona
SELECT DISTINCT ur.utente_id, rp.permesso_codice
FROM utente u
JOIN utente_ruolo   ur ON ur.utente_id = u.id
JOIN ruolo_permesso rp ON rp.ruolo_id  = ur.ruolo_id
WHERE u.attivo = TRUE
  AND NOT EXISTS (SELECT 1
                  FROM utente_permesso n
                  WHERE n.utente_id = u.id
                    AND n.permesso_codice = rp.permesso_codice
                    AND n.concesso = FALSE)
UNION
-- aggiunti a questa persona
SELECT up.utente_id, up.permesso_codice
FROM utente u
JOIN utente_permesso up ON up.utente_id = u.id
WHERE u.attivo = TRUE
  AND up.concesso = TRUE
UNION
-- il pavimento: non passa dai ruoli e non ha deroghe
SELECT u.id AS utente_id, p.codice AS permesso_codice
FROM utente u
CROSS JOIN permesso p
WHERE u.attivo = TRUE
  AND p.base = TRUE;


-- ---------------------------------------------------------------------
-- 3. Gli scostamenti sui permessi di base
--
-- Dal passo 2 la vista li ignora, quindi lasciarli vorrebbe dire tenere nel
-- database righe che non hanno nessun effetto e che l'interfaccia
-- mostrerebbe come se ne avessero.
--
-- SU QUESTO DATABASE sono quattro righe, e vanno nominate perche' una delle
-- quattro e' una decisione presa da una persona, non un residuo:
--
--   utente 1 (Anna, amministratore)  ASSENZE_RICHIEDI         concesso = 0
--   utente 3 (Marco, PM)             TASK_AGGIORNA_STATO      concesso = 1
--   utente 3 (Marco, PM)             TASK_VISUALIZZA_ASSEGNATE concesso = 1
--
-- Le due di Marco sono pura ridondanza: il pavimento gli da' gia' entrambi.
-- La prima e' il contrario: a suo tempo l'amministratore si e' fatto togliere
-- ASSENZE_RICHIEDI. Il pavimento rende quella revoca impossibile per
-- costruzione, quindi la riga sparisce e Anna torna a poter chiedere ferie.
-- Non e' un effetto collaterale, e' la conseguenza diretta della scelta di
-- rendere il pavimento invalicabile: chiedere ferie non e' un privilegio.
-- Se un giorno servisse di nuovo escludere qualcuno dalle ferie, la strada e'
-- togliere ASSENZE_RICHIEDI dal pavimento, non rimettere uno scostamento.
--
-- La riga di Marco su TASK_GESTISCI_TUTTI non e' toccata: non e' un permesso
-- di base.
-- ---------------------------------------------------------------------
DELETE FROM utente_permesso
WHERE permesso_codice IN (SELECT codice FROM permesso WHERE base = TRUE);


-- ---------------------------------------------------------------------
-- 4. I ruoli non ripetono il pavimento
--
-- Dai ruoli non protetti si togliono i codici di base, cosi' un ruolo si
-- legge per quello che aggiunge sopra il pavimento. I ruoli protetti si
-- lasciano intatti, perche' l'invariante dichiarata dalla V1 - «i protetti
-- hanno tutti i permessi» - resti vera alla lettera.
--
-- Non cambia i permessi effettivi di nessuno: tutto quello che questa DELETE
-- toglie, il ramo 3 della vista lo rida'. E' igiene, e ha un prezzo da sapere:
-- ruolo_permesso non e' scrivibile dall'applicazione, quindi quello che esce
-- da qui non si rimette dall'interfaccia ma solo con un'altra migrazione.
--
-- Svuota da sola il ruolo Dipendente, che oggi ha esattamente i cinque
-- permessi di base: resta come etichetta negli elenchi, e non concede piu'
-- niente che il pavimento non dia gia' a tutti.
-- ---------------------------------------------------------------------
DELETE rp FROM ruolo_permesso rp
JOIN ruolo    r ON r.id     = rp.ruolo_id
JOIN permesso p ON p.codice = rp.permesso_codice
WHERE r.protetto = FALSE
  AND p.base = TRUE;


-- ---------------------------------------------------------------------
-- 5. Via i team
--
-- DICHIARAZIONI_VISUALIZZA_TEAM non si cancella ma si risagoma sul lavoro,
-- che e' l'unita' con cui si alloca davvero. Cancellarlo avrebbe lasciato un
-- PM senza nessuna visibilita' sulle ore dichiarate sul suo progetto, e
-- l'unico ripiego sarebbe stato dargli DICHIARAZIONI_VISUALIZZA_TUTTE, cioe'
-- tutta l'azienda.
--
-- Prima il codice nuovo con gli stessi titolari del vecchio, poi i tre
-- vecchi: le chiavi esterne di ruolo_permesso e utente_permesso pretendono
-- quest'ordine.
-- ---------------------------------------------------------------------
INSERT INTO permesso (codice, categoria, descrizione, ordine)
SELECT 'DICHIARAZIONI_VISUALIZZA_LAVORO', 'DICHIARAZIONI',
       'Vedere le dichiarazioni di chi ha task nei lavori di cui è responsabile', 61
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM permesso
                  WHERE codice = 'DICHIARAZIONI_VISUALIZZA_LAVORO');

INSERT INTO ruolo_permesso (ruolo_id, permesso_codice)
SELECT vecchio.ruolo_id, 'DICHIARAZIONI_VISUALIZZA_LAVORO'
FROM ruolo_permesso vecchio
WHERE vecchio.permesso_codice = 'DICHIARAZIONI_VISUALIZZA_TEAM'
  AND NOT EXISTS (SELECT 1 FROM ruolo_permesso gia
                  WHERE gia.ruolo_id = vecchio.ruolo_id
                    AND gia.permesso_codice = 'DICHIARAZIONI_VISUALIZZA_LAVORO');

DELETE FROM utente_permesso WHERE permesso_codice IN (
    'TEAM_GESTISCI', 'TEAM_VISUALIZZA_PROPRIO', 'DICHIARAZIONI_VISUALIZZA_TEAM');

DELETE FROM ruolo_permesso WHERE permesso_codice IN (
    'TEAM_GESTISCI', 'TEAM_VISUALIZZA_PROPRIO', 'DICHIARAZIONI_VISUALIZZA_TEAM');

DELETE FROM permesso WHERE codice IN (
    'TEAM_GESTISCI', 'TEAM_VISUALIZZA_PROPRIO', 'DICHIARAZIONI_VISUALIZZA_TEAM');

DROP TABLE IF EXISTS membro_team;


-- La categoria TEAM non ha piu' righe. MySQL non permette di modificare un
-- CHECK: va eliminato e ricreato, come fa la V8 e come ha fatto la V12.
--
-- ATTENZIONE a chi scrivera' la V15: questa lista va copiata DA QUI, non
-- dalla V1 e non dalla V12. TEAM non c'e' piu'.
ALTER TABLE permesso DROP CHECK ck_permesso_categoria;

ALTER TABLE permesso ADD CONSTRAINT ck_permesso_categoria
    CHECK (categoria IN ('UTENTI', 'RUOLI', 'LAVORI', 'TASK',
                         'DICHIARAZIONI', 'ORE', 'ASSENZE', 'SISTEMA'));


-- ---------------------------------------------------------------------
-- 6. LAVORI_MODIFICA_PROPRI dice quello che fa
--
-- Era nel catalogo come «modificare i lavori di cui e' responsabile», ma non
-- autorizzava niente: l'unico uso era trovaConPermesso, per riempire l'elenco
-- dei candidati responsabile. Da questa migrazione il permesso autorizza
-- davvero l'annullamento dei propri lavori, e la descrizione dichiara
-- entrambi i significati, perche' la casella spuntata nell'interfaccia ne
-- produce due effetti.
-- ---------------------------------------------------------------------
UPDATE permesso
SET descrizione = 'Modificare e annullare i lavori di cui è responsabile, ed esserne nominato responsabile'
WHERE codice = 'LAVORI_MODIFICA_PROPRI';
