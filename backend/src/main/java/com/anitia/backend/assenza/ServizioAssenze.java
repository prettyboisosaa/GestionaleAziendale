package com.anitia.backend.assenza;

import com.anitia.backend.impostazione.ServizioCalendario;
import com.anitia.backend.utente.Utente;
import com.anitia.backend.utente.UtenteRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ferie e permessi: il dipendente chiede, l'amministratore decide.
 *
 * <p>I controlli stanno tutti qui e non nelle annotazioni dei record, perché
 * quasi tutti dipendono dal <em>tipo</em> della richiesta — un permesso
 * orario pretende la fascia, le ferie la vietano — e un
 * {@code @NotNull} non sa esprimere «obbligatorio solo se».
 *
 * <p>I codici in testa ai messaggi d'errore ({@code CAMPI_MANCANTI},
 * {@code DATA_PASSATA}, …) sono quelli che il frontend riconosce senza dover
 * interpretare il testo italiano, che resta leggibile per chi lo vede.
 */
@Service
public class ServizioAssenze {

    private final AssenzaRepository assenze;
    private final UtenteRepository utenti;
    private final ServizioCalendario calendario;

    ServizioAssenze(AssenzaRepository assenze, UtenteRepository utenti,
                    ServizioCalendario calendario) {
        this.assenze = assenze;
        this.utenti = utenti;
        this.calendario = calendario;
    }

    // --- il dipendente ------------------------------------------------------

    /**
     * Le mie richieste, le decise insieme a quelle in attesa.
     *
     * <p>Nessun controllo di permesso oltre a quello della rotta, e non è una
     * dimenticanza: la lettura è filtrata sull'id di chi chiede, quindi non
     * esiste un modo di farla parlare delle richieste di un altro.
     */
    @Transactional(readOnly = true)
    public List<RigaAssenza> mie(UserDetails chiLoChiede) {
        Utente io = utente(chiLoChiede);
        return assenze.trovaDiUtente(io.getId()).stream()
                .map(assenza -> riga(assenza, false))
                .toList();
    }

    @Transactional
    public RigaAssenza chiedi(UserDetails chiLoChiede, RichiestaAssenza richiesta) {
        Utente io = utente(chiLoChiede);
        LocalDate fine = valida(richiesta, null, io.getId());

        Assenza creata = assenze.save(Assenza.nuova(io, richiesta.tipo(),
                richiesta.dataInizio(), fine, richiesta.oraInizio(), richiesta.oraFine(),
                richiesta.dettagli()));

        return riga(creata, false);
    }

    /** Modifica una propria richiesta, solo se è ancora in attesa. */
    @Transactional
    public RigaAssenza modifica(UserDetails chiLoChiede, Long id, RichiestaAssenza richiesta) {
        Utente io = utente(chiLoChiede);
        Assenza assenza = miaInAttesa(io, id);

        LocalDate fine = valida(richiesta, id, io.getId());
        assenza.modifica(richiesta.tipo(), richiesta.dataInizio(), fine,
                richiesta.oraInizio(), richiesta.oraFine(), richiesta.dettagli());

        return riga(assenza, false);
    }

    /**
     * Ritira una propria richiesta. È l'unico modo che il dipendente ha di
     * disfarla: cancellarla porterebbe via la traccia di averla chiesta, che
     * all'amministratore serve.
     */
    @Transactional
    public RigaAssenza ritira(UserDetails chiLoChiede, Long id) {
        Utente io = utente(chiLoChiede);
        Assenza assenza = miaInAttesa(io, id);
        assenza.ritira();
        return riga(assenza, false);
    }

    // --- l'amministratore ---------------------------------------------------

    /**
     * Tutte le richieste, con i due filtri facoltativi del documento. Le
     * richieste in attesa arrivano in cima: sono le uniche su cui c'è
     * qualcosa da decidere.
     */
    @Transactional(readOnly = true)
    public List<RigaAssenza> tutte(StatoAssenza stato, Long utenteId) {
        return assenze.cerca(stato, utenteId).stream()
                .map(assenza -> riga(assenza, true))
                .toList();
    }

    /**
     * Chi sarà via, fra due date: il calendario aziendale di chi approva.
     *
     * <p>Risponde a una domanda che prima non si poteva porre. {@link #tutte}
     * dice «quali richieste esistono», e per sapere chi è assente il 14
     * bisognava leggere le righe e tenere le date a mente; questo dice «chi
     * non c'è in questi giorni», che è l'informazione con cui si decide se
     * approvare una ferie.
     *
     * <p>Restituisce {@link RigaCalendarioAssenza} e non {@code RigaAssenza},
     * e non è una semplificazione gratuita: quella porta
     * {@code giorniLavorativi}, che costa una query a {@code festivita} per
     * ogni giorno di ogni assenza. Su un mese di assenze di dieci persone
     * sarebbero centinaia di query per riempire due campi che sul calendario
     * non si leggono.
     *
     * <p>Gli estremi si raddrizzano se arrivano invertiti, come fa già il
     * calendario della dashboard: un intervallo al contrario è un errore di
     * chi chiama, non una richiesta di zero giorni.
     */
    @Transactional(readOnly = true)
    public List<RigaCalendarioAssenza> calendario(LocalDate da, LocalDate a) {
        LocalDate dal = a.isBefore(da) ? a : da;
        LocalDate al = a.isBefore(da) ? da : a;

        return assenze.trovaDelPeriodo(dal, al).stream()
                .map(RigaCalendarioAssenza::da)
                .toList();
    }

    @Transactional
    public RigaAssenza approva(UserDetails chiLoChiede, Long id, RichiestaEsitoAssenza esito) {
        Assenza assenza = daDecidere(id);
        assenza.approva(utente(chiLoChiede), esito == null ? null : esito.notaPulita());
        return riga(assenza, true);
    }

    @Transactional
    public RigaAssenza rifiuta(UserDetails chiLoChiede, Long id, RichiestaEsitoAssenza esito) {
        Assenza assenza = daDecidere(id);

        String nota = esito == null ? null : esito.notaPulita();
        if (nota == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTA_OBBLIGATORIA: per rifiutare una richiesta devi scrivere perché. "
                            + "Senza una motivazione la stessa richiesta tornerà domani.");
        }

        assenza.rifiuta(utente(chiLoChiede), nota);
        return riga(assenza, true);
    }

    // --- regole -------------------------------------------------------------

    /**
     * Tutti i controlli su una richiesta in arrivo, nell'ordine in cui
     * conviene darli a chi compila: prima la forma dei campi, poi le date,
     * poi il confronto con le altre richieste.
     *
     * @param escludi l'id della richiesta che si sta modificando, perché non
     *                si sovrapponga a se stessa; null quando è nuova
     * @return la data di fine effettiva, già risolta
     */
    private LocalDate valida(RichiestaAssenza richiesta, Long escludi, Long utenteId) {
        TipoAssenza tipo = richiesta.tipo();
        LocalDate inizio = richiesta.dataInizio();
        LocalDate fine = richiesta.fineEffettiva();

        // --- i campi propri della tipologia ---
        if (tipo.richiedeOrario() && (richiesta.oraInizio() == null
                || richiesta.oraFine() == null)) {
            throw campiMancanti("un permesso orario ha bisogno dell'ora di inizio e di quella "
                    + "di fine.");
        }
        if (tipo.vietaOrario() && richiesta.oraInizio() != null) {
            throw campiMancanti("le ferie si chiedono a giornate intere: togli gli orari.");
        }
        if ((richiesta.oraInizio() == null) != (richiesta.oraFine() == null)) {
            throw campiMancanti("indica sia l'ora di inizio sia quella di fine, oppure "
                    + "nessuna delle due.");
        }
        if (tipo.richiedeMotivo() && richiesta.motivo() == null) {
            throw campiMancanti("scegli il motivo del permesso retribuito.");
        }
        if (!tipo.richiedeMotivo() && richiesta.motivo() != null) {
            throw campiMancanti("il motivo si indica solo sul permesso retribuito.");
        }
        if (richiesta.motivo() != null && richiesta.motivo().richiedeNote()
                && (richiesta.note() == null || richiesta.note().isBlank())) {
            throw campiMancanti("col motivo «Altro» le note sono obbligatorie: spiega di "
                    + "cosa si tratta.");
        }

        // --- le date ---
        if (fine.isBefore(inizio)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "CAMPI_MANCANTI: l'ultimo giorno non può precedere il primo.");
        }
        // Una fascia oraria sta dentro un giorno solo: a cavallo di due
        // giorni sarebbe due permessi. Lo ribadisce ck_assenza_fascia.
        if (richiesta.oraInizio() != null && !inizio.equals(fine)) {
            throw campiMancanti("un permesso a ore riguarda un giorno solo.");
        }
        if (richiesta.oraInizio() != null
                && !richiesta.oraFine().isAfter(richiesta.oraInizio())) {
            throw campiMancanti("l'ora di fine deve venire dopo quella di inizio.");
        }
        // Le assenze si chiedono prima, non dopo: una richiesta per ieri non
        // e' una richiesta, e il permesso di approvarla non esiste.
        if (inizio.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "DATA_PASSATA: non si può chiedere un'assenza per un giorno già "
                            + "passato.");
        }

        // --- il confronto con le altre ---
        List<Assenza> scontri = assenze.trovaSovrapposte(utenteId, inizio, fine, escludi);
        if (!scontri.isEmpty()) {
            Assenza prima = scontri.getFirst();
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "RICHIESTA_SOVRAPPOSTA: hai già una richiesta "
                            + prima.getStato().etichetta() + " dal "
                            + prima.getDataInizio() + " al " + prima.getDataFine()
                            + ". Modifica quella, oppure ritirala.");
        }

        return fine;
    }

    private static ResponseStatusException campiMancanti(String dettaglio) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "CAMPI_MANCANTI: " + dettaglio);
    }

    /**
     * Una mia richiesta ancora in attesa. Un 404 e non un 403 quando è di un
     * altro: l'esistenza delle richieste altrui non va rivelata a chi non ha
     * il permesso di vederle.
     */
    private Assenza miaInAttesa(Utente io, Long id) {
        Assenza assenza = assenze.findById(id)
                .filter(trovata -> trovata.getUtente().getId().equals(io.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Richiesta non trovata."));

        if (!assenza.getStato().inAttesa()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "RICHIESTA_CHIUSA: la richiesta è " + assenza.getStato().etichetta()
                            + " e non si può più toccare.");
        }
        return assenza;
    }

    /** Una richiesta che l'amministratore può ancora decidere. */
    private Assenza daDecidere(Long id) {
        Assenza assenza = assenze.trovaConUtente(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Richiesta non trovata."));

        if (!assenza.getStato().inAttesa()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "RICHIESTA_CHIUSA: la richiesta è già " + assenza.getStato().etichetta()
                            + ".");
        }
        return assenza;
    }

    /**
     * La riga per il frontend, col riepilogo calcolato.
     *
     * <p>I giorni lavorativi li conta {@code ServizioCalendario}, che conosce
     * le festività e i giorni di lavoro configurati: contarli nel browser
     * vorrebbe dire una seconda definizione di «giorno lavorativo».
     */
    private RigaAssenza riga(Assenza assenza, boolean conRichiedente) {
        Integer giorni = assenza.getOraInizio() != null
                ? null
                : giorniLavorativi(assenza.getDataInizio(), assenza.getDataFine());
        return RigaAssenza.da(assenza, conRichiedente, giorni);
    }

    /** Quanti giorni lavorativi ci sono fra le due date, estremi compresi. */
    private int giorniLavorativi(LocalDate da, LocalDate a) {
        int quanti = 0;
        for (LocalDate giorno = da; !giorno.isAfter(a); giorno = giorno.plusDays(1)) {
            if (calendario.eLavorativo(giorno)) {
                quanti++;
            }
        }
        return quanti;
    }

    private Utente utente(UserDetails chiLoChiede) {
        return utenti.findByEmail(chiLoChiede.getUsername()).orElseThrow(
                () -> new IllegalStateException(
                        "Utente collegato non trovato: " + chiLoChiede.getUsername()));
    }
}
