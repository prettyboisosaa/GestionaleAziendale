-- =====================================================================
-- V5 - Lo stato ANNULLATO per i lavori
--
-- Un lavoro avviato non si cancella: cancellarlo trascinerebbe via le sue
-- task e, con quelle, le voci delle dichiarazioni giornaliere gia' inviate,
-- cioe' la registrazione del lavoro svolto dalle persone. Le chiavi esterne
-- della V3 e della V4 non hanno ON DELETE CASCADE proprio per impedirlo.
-- Si annulla, e la riga resta.
--
-- MySQL non permette di modificare un CHECK: va eliminato e ricreato.
-- =====================================================================

ALTER TABLE lavoro DROP CHECK ck_lavoro_stato;

ALTER TABLE lavoro ADD CONSTRAINT ck_lavoro_stato
    CHECK (stato IN ('BOZZA', 'ATTIVO', 'CHIUSO', 'ANNULLATO'));


-- Conseguenza non ovvia del nuovo stato: ck_lavoro_responsabile pretendeva un
-- responsabile per ogni stato tranne BOZZA. Annullare una bozza che non ha
-- ancora un responsabile avrebbe quindi violato il vincolo. ANNULLATO va
-- aggiunto fra gli stati in cui il responsabile puo' mancare.
ALTER TABLE lavoro DROP CHECK ck_lavoro_responsabile;

ALTER TABLE lavoro ADD CONSTRAINT ck_lavoro_responsabile
    CHECK (stato IN ('BOZZA', 'ANNULLATO') OR responsabile_id IS NOT NULL);
