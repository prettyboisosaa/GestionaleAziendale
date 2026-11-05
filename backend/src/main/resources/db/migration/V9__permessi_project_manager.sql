-- =====================================================================
-- V9 - Il Project Manager vede tutti i lavori, ma modifica solo i propri
--
-- Fino a qui un PM vedeva soltanto i lavori di cui era responsabile o in cui
-- aveva task assegnate. Gli mancava il quadro d'insieme dell'azienda: non
-- poteva sapere se un altro progetto stesse consumando le persone del suo
-- team, ne' confrontare il proprio andamento con quello degli altri.
--
-- Si aggiungono quindi due permessi di SOLA LETTURA al ruolo Project Manager:
--
--   LAVORI_VISUALIZZA_TUTTI   vedere tutti i lavori
--   TASK_VISUALIZZA_LAVORO    vedere tutte le task dei lavori in cui e'
--                             coinvolto, non solo quelle assegnate a lui
--
-- ATTENZIONE a cosa NON si sta dando. TASK_GESTISCI_TUTTI resta
-- all'amministratore: la sua descrizione dice «in ogni lavoro», e darlo al
-- ruolo PM vorrebbe dire che ogni PM puo' creare, modificare e annullare le
-- task nei progetti degli altri. La distinzione _PROPRI / _TUTTI e'
-- l'impalcatura di tutto il modello dei permessi, e questa migrazione la
-- lascia in piedi: il PM continua a MODIFICARE soltanto i lavori di cui e'
-- responsabile, perche' conserva TASK_GESTISCI_PROPRI, LAVORI_MODIFICA_PROPRI
-- e ORE_ALLOCA_PROPRI, e i servizi verificano che sia lui il responsabile.
--
-- Nessun permesso nuovo nel catalogo, quindi niente da concedere ai ruoli
-- protetti: li hanno gia' tutti e due.
-- =====================================================================

-- La clausola NOT EXISTS invece di una INSERT secca: su un database dove un
-- amministratore avesse gia' concesso uno dei due permessi dall'interfaccia,
-- l'inserimento violerebbe la chiave primaria e la migrazione fallirebbe.
INSERT INTO ruolo_permesso (ruolo_id, permesso_codice)
SELECT r.id, c.codice
FROM ruolo r
CROSS JOIN (SELECT 'LAVORI_VISUALIZZA_TUTTI' AS codice
            UNION ALL SELECT 'TASK_VISUALIZZA_LAVORO') c
WHERE r.id = 2
  AND r.protetto = FALSE
  AND NOT EXISTS (SELECT 1 FROM ruolo_permesso gia
                  WHERE gia.ruolo_id = r.id
                    AND gia.permesso_codice = c.codice);


-- LAVORI_VISUALIZZA_PROPRI resta al ruolo, anche se ora e' ridondante:
-- ServizioLavori controlla prima LAVORI_VISUALIZZA_TUTTI e, trovandolo, non
-- guarda l'altro. Togliendolo si romperebbe chi avesse personalizzato il
-- ruolo, e non si guadagnerebbe niente.
