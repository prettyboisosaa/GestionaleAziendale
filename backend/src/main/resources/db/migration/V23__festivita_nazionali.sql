-- =====================================================================
-- V23 - Le festività nazionali italiane, dal 2026 al 2031
--
-- La tabella `festivita` esiste dalla V4 e non l'ha mai riempita nessuno:
-- né una migrazione, né un endpoint, né una schermata. Era vuota anche sul
-- database di sviluppo, ed è per questo che il difetto non si è mai visto.
--
-- Ma la leggono in due, e decidono cose:
--
--   ServizioCalendario.giornoLavorativo()  un giorno è lavorativo se è fra
--                                          `calendario.giorni_lavorativi` E
--                                          non è una festività
--   ServizioDashboard                      le chiusure nel calendario
--
-- Con la tabella vuota, il 25 aprile e Natale sono giorni lavorativi: le
-- finestre contate in giorni lavorativi scadono prima del dovuto e il
-- calendario non mostra le chiusure. Non è un difetto che si manifesta con un
-- errore — è un conto che torna sbagliato in silenzio.
--
-- ⚠️ ARRIVA FINO AL 2031. Non è un orizzonte scelto, è il punto in cui questa
-- lista finisce: nel 2032 la tabella torna a essere incompleta e il difetto
-- torna identico, senza che niente lo segnali. La soluzione vera è una
-- schermata che permetta di gestirle — il permesso esiste già dalla V1,
-- IMPOSTAZIONI_GESTISCI, e la sua descrizione dice testualmente «e il
-- calendario delle festività». Finché quella schermata non c'è, questa
-- migrazione è un tampone, e il modo di accorgersene prima di sbatterci è
-- segnarsi la scadenza.
--
-- NON c'è il santo patrono, che è l'undicesima festività per quasi tutti i
-- comuni italiani (a Milano il 7 dicembre, a Roma il 29 giugno, a Napoli il
-- 19 settembre...). Dipende da dove sta l'azienda e questa migrazione non può
-- saperlo: va aggiunto a mano, ed è un motivo in più per volere la schermata.
--
-- Le domeniche non ci sono e non servono: `calendario.giorni_lavorativi` vale
-- '1,2,3,4,5', quindi sabato e domenica sono già fuori. Per questo Pasqua non
-- compare mentre il Lunedì dell'Angelo sì — Pasqua è sempre di domenica. Le
-- date del Lunedì dell'Angelo sono calcolate con il computus gregoriano e
-- verificate una per una.
--
-- La UNIQUE su `data` (uk_festivita_data) fa sì che inserire due volte lo
-- stesso giorno fallisca: IGNORE perché questa migrazione possa essere
-- applicata anche a un database dove qualcuno avesse già inserito qualche
-- data a mano, invece di fermare l'avvio dell'applicazione.
-- =====================================================================

INSERT IGNORE INTO festivita (data, descrizione) VALUES
    -- 2026
    ('2026-01-01', 'Capodanno'),
    ('2026-01-06', 'Epifania'),
    ('2026-04-06', 'Lunedì dell''Angelo'),
    ('2026-04-25', 'Festa della Liberazione'),
    ('2026-05-01', 'Festa del Lavoro'),
    ('2026-06-02', 'Festa della Repubblica'),
    ('2026-08-15', 'Ferragosto'),
    ('2026-11-01', 'Ognissanti'),
    ('2026-12-08', 'Immacolata Concezione'),
    ('2026-12-25', 'Natale'),
    ('2026-12-26', 'Santo Stefano'),

    -- 2027
    ('2027-01-01', 'Capodanno'),
    ('2027-01-06', 'Epifania'),
    ('2027-03-29', 'Lunedì dell''Angelo'),
    ('2027-04-25', 'Festa della Liberazione'),
    ('2027-05-01', 'Festa del Lavoro'),
    ('2027-06-02', 'Festa della Repubblica'),
    ('2027-08-15', 'Ferragosto'),
    ('2027-11-01', 'Ognissanti'),
    ('2027-12-08', 'Immacolata Concezione'),
    ('2027-12-25', 'Natale'),
    ('2027-12-26', 'Santo Stefano'),

    -- 2028
    ('2028-01-01', 'Capodanno'),
    ('2028-01-06', 'Epifania'),
    ('2028-04-17', 'Lunedì dell''Angelo'),
    ('2028-04-25', 'Festa della Liberazione'),
    ('2028-05-01', 'Festa del Lavoro'),
    ('2028-06-02', 'Festa della Repubblica'),
    ('2028-08-15', 'Ferragosto'),
    ('2028-11-01', 'Ognissanti'),
    ('2028-12-08', 'Immacolata Concezione'),
    ('2028-12-25', 'Natale'),
    ('2028-12-26', 'Santo Stefano'),

    -- 2029
    ('2029-01-01', 'Capodanno'),
    ('2029-01-06', 'Epifania'),
    ('2029-04-02', 'Lunedì dell''Angelo'),
    ('2029-04-25', 'Festa della Liberazione'),
    ('2029-05-01', 'Festa del Lavoro'),
    ('2029-06-02', 'Festa della Repubblica'),
    ('2029-08-15', 'Ferragosto'),
    ('2029-11-01', 'Ognissanti'),
    ('2029-12-08', 'Immacolata Concezione'),
    ('2029-12-25', 'Natale'),
    ('2029-12-26', 'Santo Stefano'),

    -- 2030
    ('2030-01-01', 'Capodanno'),
    ('2030-01-06', 'Epifania'),
    ('2030-04-22', 'Lunedì dell''Angelo'),
    ('2030-04-25', 'Festa della Liberazione'),
    ('2030-05-01', 'Festa del Lavoro'),
    ('2030-06-02', 'Festa della Repubblica'),
    ('2030-08-15', 'Ferragosto'),
    ('2030-11-01', 'Ognissanti'),
    ('2030-12-08', 'Immacolata Concezione'),
    ('2030-12-25', 'Natale'),
    ('2030-12-26', 'Santo Stefano'),

    -- 2031
    ('2031-01-01', 'Capodanno'),
    ('2031-01-06', 'Epifania'),
    ('2031-04-14', 'Lunedì dell''Angelo'),
    ('2031-04-25', 'Festa della Liberazione'),
    ('2031-05-01', 'Festa del Lavoro'),
    ('2031-06-02', 'Festa della Repubblica'),
    ('2031-08-15', 'Ferragosto'),
    ('2031-11-01', 'Ognissanti'),
    ('2031-12-08', 'Immacolata Concezione'),
    ('2031-12-25', 'Natale'),
    ('2031-12-26', 'Santo Stefano');
