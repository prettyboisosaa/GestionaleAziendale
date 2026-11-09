package com.anitia.backend.tempo;

import com.anitia.backend.impostazione.ServizioImpostazioni;
import com.anitia.backend.lavoro.Lavoro;
import com.anitia.backend.lavoro.ServizioLavori;
import com.anitia.backend.sicurezza.Permessi;
import com.anitia.backend.task.AssegnazioneTaskRepository;
import com.anitia.backend.task.ServizioTask;
import com.anitia.backend.task.StatoTask;
import com.anitia.backend.task.Task;
import com.anitia.backend.task.TaskRepository;
import com.anitia.backend.utente.Utente;
import com.anitia.backend.utente.UtenteRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Il tempo lavorato, misurato invece che dichiarato.
 *
 * <p>Ha preso il posto della dichiarazione giornaliera: non c'è niente da
 * compilare e niente da consegnare. Il dipendente avvia una task, la mette in
 * pausa, la completa, e le ore escono da sé.
 *
 * <p><strong>Una sola sessione aperta per persona.</strong> È la proprietà su
 * cui si appoggia tutto il resto: le ore di una persona non si sovrappongono,
 * quindi si possono sommare senza chiedersi quale sia quella vera. Non la
 * garantisce questo servizio ma {@code uk_sessione_in_corso}, perché fra un
 * controllo e una scrittura ci sta un secondo clic da un'altra finestra.
 * Avviare una task mentre un'altra scorre <strong>chiude quella</strong>: è un
 * clic per passare da una cosa all'altra, che è il minimo sforzo possibile.
 *
 * <p><strong>Il timer dimenticato.</strong> Chi avvia una task e va a casa
 * lascerebbe scorrere il tempo per sempre. Il tetto di
 * {@code tempo.ore_massime_sessione} lo interrompe, ma non c'è nessun
 * processo notturno a farlo: la chiusura è <em>pigra</em>, la fa
 * {@link #chiudiSeOltreIlTetto} la prima volta che si guarda il tempo di
 * quella persona. È lo stesso ragionamento che {@code MiaTask} mette per
 * iscritto a proposito del ritardo — un flag da riscrivere ogni notte vorrebbe
 * un meccanismo che in questo progetto non esiste.
 *
 * <p>Una sessione chiusa così resta <strong>da confermare</strong>: la sua ora
 * di fine non l'ha misurata nessuno. Conta comunque nei totali, perché non
 * contarla farebbe sparire lavoro vero dal consuntivo di un lavoro, ma dice di
 * sé che è una deduzione.
 */
@Service
public class ServizioTempo {

    private static final String VISUALIZZA_TUTTI = "TEMPO_VISUALIZZA_TUTTI";
    private static final String VISUALIZZA_LAVORO = "TEMPO_VISUALIZZA_LAVORO";

    /** Se il parametro manca o è illeggibile. Otto ore, come una giornata. */
    private static final int ORE_MASSIME_DI_RIPIEGO = 8;

    private final SessioneTaskRepository sessioni;
    private final TaskRepository task;
    private final AssegnazioneTaskRepository assegnazioni;
    private final UtenteRepository utenti;
    private final ServizioLavori lavori;
    private final ServizioTask servizioTask;
    private final ServizioImpostazioni impostazioni;

    ServizioTempo(SessioneTaskRepository sessioni, TaskRepository task,
                  AssegnazioneTaskRepository assegnazioni, UtenteRepository utenti,
                  ServizioLavori lavori, ServizioTask servizioTask,
                  ServizioImpostazioni impostazioni) {
        this.sessioni = sessioni;
        this.task = task;
        this.assegnazioni = assegnazioni;
        this.utenti = utenti;
        this.lavori = lavori;
        this.servizioTask = servizioTask;
        this.impostazioni = impostazioni;
    }

    // --- quello che vede chi lavora ------------------------------------------

    /**
     * La situazione di chi chiede: cosa sta scorrendo, quanto ha fatto oggi, e
     * che cosa gli resta da confermare.
     *
     * <p>Passa dalla chiusura al tetto, come ogni rotta del tempo: è il
     * momento in cui una sessione dimenticata ieri sera viene interrotta.
     */
    @Transactional
    public SituazioneTempo miaSituazione(UserDetails chiLoChiede) {
        Utente io = autore(chiLoChiede);
        chiudiSeOltreIlTetto(io.getId());

        LocalDateTime adesso = LocalDateTime.now();
        LocalDate oggi = adesso.toLocalDate();

        SessioneInCorso inCorso = sessioni.trovaInCorso(io.getId())
                .map(sessione -> SessioneInCorso.da(sessione, adesso))
                .orElse(null);

        long minutiOggi = sessioni
                .trovaDelPeriodo(io.getId(), oggi.atStartOfDay(), oggi.plusDays(1).atStartOfDay())
                .stream()
                .mapToLong(SessioneTask::durataMinuti)
                .sum();

        return new SituazioneTempo(inCorso, minutiOggi,
                sessioni.trovaDaConfermare(io.getId()).stream().map(RigaSessione::da).toList());
    }

    // --- avvia, pausa, completa ---------------------------------------------

    /**
     * Comincia a lavorare su una task.
     *
     * <p>Se un'altra sessione sta scorrendo, la chiude: passare da una task
     * all'altra è un clic, non due. Se la task era ancora {@code ATTIVO},
     * passa a {@code IN_CORSO} — cominciare a lavorarci <em>è</em> metterla in
     * corso, e chiedere al dipendente di dirlo anche con la tendina sarebbe
     * l'adempimento che questo cambiamento ha tolto.
     */
    @Transactional
    public SituazioneTempo avvia(UserDetails chiLoChiede, Long idLavoro, Long idTask) {
        Utente io = autore(chiLoChiede);
        Lavoro lavoro = lavori.lavoroVisibile(chiLoChiede, idLavoro);
        Task destinazione = taskDelLavoro(idTask, idLavoro);

        // Serve un'assegnazione attiva, e NON basta poter gestire la task.
        //
        // È la differenza fra «posso disporre di questa task» e «ci ho
        // lavorato io»: il timer misura il tempo di chi lo avvia, e metterlo
        // su una task di qualcun altro vorrebbe dire scrivere le proprie ore
        // nel consuntivo del lavoro di un collega. Un responsabile che
        // davvero ci lavora ha già la strada: assegnarsi la task, cosa che
        // il suo permesso di gestione gli consente.
        //
        // Per questo qui non si riusa vietaSeNonPuoCambiareStato, che invece
        // lascia passare chi ha TASK_GESTISCI_TUTTI o è responsabile del
        // lavoro: cambiare lo stato di una task altrui è legittimo,
        // registrarci il proprio tempo no.
        if (assegnazioni.findByTaskIdAndUtenteIdAndRimossaIlIsNull(idTask, io.getId())
                .isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Non sei assegnato alla task «" + destinazione.getTitolo()
                            + "»: puoi registrare tempo solo sulle task che ti sono "
                            + "state affidate.");
        }

        if (lavoro.getStato().chiuso()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Il lavoro " + lavoro.getCodice() + " non è più in corso: non si "
                            + "registra tempo sulle sue task.");
        }
        if (!destinazione.getStato().aperta()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La task «" + destinazione.getTitolo() + "» è "
                            + destinazione.getStato().etichetta()
                            + ": non si può più lavorarci.");
        }

        chiudiSeOltreIlTetto(io.getId());

        Optional<SessioneTask> aperta = sessioni.trovaInCorso(io.getId());
        if (aperta.isPresent()) {
            if (aperta.get().getTask().getId().equals(idTask)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Stai già lavorando a «" + destinazione.getTitolo() + "».");
            }
            aperta.get().chiudi();
        }

        if (destinazione.getStato() == StatoTask.ATTIVO) {
            destinazione.cambiaStato(StatoTask.IN_CORSO);
        }

        try {
            // saveAndFlush e non save: il vincolo di unicità deve pronunciarsi
            // adesso, non al commit, così il 409 esce da qui e non da un punto
            // qualunque più tardi. È il modo in cui anche l'assegnazione di
            // una task si difende da due clic contemporanei.
            sessioni.saveAndFlush(SessioneTask.avvia(destinazione, io));
        } catch (DataIntegrityViolationException eccezione) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Hai già una task avviata in un'altra finestra. Ricarica la pagina.");
        }

        return miaSituazione(chiLoChiede);
    }

    /** Smette di lavorare, senza chiudere la task. */
    @Transactional
    public SituazioneTempo pausa(UserDetails chiLoChiede) {
        Utente io = autore(chiLoChiede);
        chiudiSeOltreIlTetto(io.getId());

        sessioni.trovaInCorso(io.getId()).orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Non hai nessuna task avviata."))
                .chiudi();

        return miaSituazione(chiLoChiede);
    }

    /**
     * Ha finito: chiude la sessione e poi la task.
     *
     * <p>In quest'ordine, e non è indifferente. Chiudere la task fa tornare le
     * ore non consumate nel pool del lavoro, e quel conto legge le ore
     * consumate: se la sessione fosse ancora aperta i suoi minuti non ci
     * sarebbero, e il residuo restituito sarebbe più grande del vero.
     */
    @Transactional
    public SituazioneTempo completa(UserDetails chiLoChiede, Long idLavoro, Long idTask) {
        Utente io = autore(chiLoChiede);
        chiudiSeOltreIlTetto(io.getId());

        sessioni.trovaInCorso(io.getId())
                .filter(sessione -> sessione.getTask().getId().equals(idTask))
                .ifPresent(SessioneTask::chiudi);

        // Da qui in poi è il cambio di stato di sempre, con i suoi controlli e
        // il suo rilascio delle ore residue: completare una task non è
        // un'operazione del tempo, e non se ne fa una seconda versione.
        servizioTask.cambiaStato(chiLoChiede, idLavoro, idTask, StatoTask.COMPLETATO);

        return miaSituazione(chiLoChiede);
    }

    // --- conferme e correzioni ----------------------------------------------

    /** Ho guardato la chiusura d'ufficio e va bene così. */
    @Transactional
    public SituazioneTempo conferma(UserDetails chiLoChiede, Long idSessione) {
        mia(chiLoChiede, idSessione).conferma();
        return miaSituazione(chiLoChiede);
    }

    /**
     * Corregge gli estremi di una propria sessione.
     *
     * <p>Si corregge <strong>oggi</strong>, oppure sempre se la sessione è una
     * chiusura d'ufficio da confermare. La seconda metà non è una scappatoia:
     * una sessione chiusa al tetto nasce di notte o la mattina dopo, e
     * bloccarla con la sua giornata renderebbe la conferma una formalità su un
     * numero che nessuno può più sistemare. Il dato che il sistema si è
     * dedotto lo deve poter correggere chi c'era.
     *
     * <p>Nessuna correzione per l'amministratore, di proposito: le ore sono di
     * chi le ha lavorate. Una sessione che nessuno conferma resta non
     * confermata e segnalata, e la strada è chiederlo alla persona.
     */
    @Transactional
    public SituazioneTempo correggi(UserDetails chiLoChiede, Long idSessione,
                                    LocalDateTime avviataIl, LocalDateTime chiusaIl) {
        SessioneTask sessione = mia(chiLoChiede, idSessione);
        vietaSeFuoriDallaFinestra(sessione);

        if (!chiusaIl.isAfter(avviataIl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fine deve venire dopo l'inizio.");
        }
        if (chiusaIl.isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Non si registra tempo nel futuro.");
        }

        sessione.correggi(avviataIl, chiusaIl);
        return miaSituazione(chiLoChiede);
    }

    /** Questa sessione non andava registrata. La riga resta, fuori dai conti. */
    @Transactional
    public SituazioneTempo annulla(UserDetails chiLoChiede, Long idSessione) {
        SessioneTask sessione = mia(chiLoChiede, idSessione);
        vietaSeFuoriDallaFinestra(sessione);
        sessione.annulla();
        return miaSituazione(chiLoChiede);
    }

    // --- quello che vede chi legge il tempo degli altri ----------------------

    /**
     * Il tempo di una persona in un intervallo.
     *
     * <p>{@code TEMPO_VISUALIZZA_TUTTI} vede chiunque;
     * {@code TEMPO_VISUALIZZA_LAVORO} solo chi ha una task attiva in un lavoro
     * di cui chi chiede è responsabile, e il limite va verificato qui perché
     * dipende dai dati.
     */
    @Transactional
    public TempoDiUnaPersona diUnaPersona(UserDetails chiLoChiede, Long utenteId,
                                          LocalDate da, LocalDate a) {
        if (a.isBefore(da)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "L'intervallo finisce prima di cominciare.");
        }
        if (!Permessi.ha(chiLoChiede, VISUALIZZA_TUTTI)) {
            Utente io = autore(chiLoChiede);
            boolean suo = Permessi.ha(chiLoChiede, VISUALIZZA_LAVORO)
                    && utenti.eNeiLavoriDi(io.getId(), utenteId);
            if (!suo) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Puoi vedere il tempo solo di chi lavora ai lavori di cui sei "
                                + "responsabile.");
            }
        }

        // Anche la persona guardata va messa in pari: altrimenti una sua
        // sessione dimenticata resterebbe aperta e fuori dal totale finche'
        // non torna lei a guardare il proprio tempo.
        chiudiSeOltreIlTetto(utenteId);

        List<SessioneTask> trovate = sessioni.trovaDelPeriodo(utenteId, da.atStartOfDay(),
                a.plusDays(1).atStartOfDay());

        long totali = trovate.stream().mapToLong(SessioneTask::durataMinuti).sum();
        long nonConfermati = trovate.stream()
                .filter(sessione -> !sessione.isConfermata())
                .mapToLong(SessioneTask::durataMinuti)
                .sum();

        return new TempoDiUnaPersona(utenteId, da, a, totali, nonConfermati,
                trovate.stream().map(RigaSessione::da).toList());
    }

    /** Chi ha chiusure d'ufficio in attesa, e quante. Serve a sapere a chi chiedere. */
    @Transactional(readOnly = true)
    public List<SessioneTaskRepository.DaConfermare> daConfermare() {
        return sessioni.contaDaConfermare();
    }

    // --- regole --------------------------------------------------------------

    /**
     * Interrompe una sessione rimasta aperta oltre il tetto.
     *
     * <p>Chiude a {@code avviata_il + tetto} e non ad adesso: il tetto è il
     * punto in cui il dato ha smesso di essere credibile, e usare adesso
     * vorrebbe dire scrivere come lavorate anche le ore passate fra la
     * dimenticanza e il momento in cui qualcuno è tornato a guardare.
     */
    private void chiudiSeOltreIlTetto(Long utenteId) {
        int tetto = impostazioni.intero(ServizioImpostazioni.ORE_MASSIME_SESSIONE,
                ORE_MASSIME_DI_RIPIEGO);

        sessioni.trovaInCorso(utenteId).ifPresent(sessione -> {
            LocalDateTime limite = sessione.getAvviataIl().plusHours(tetto);
            if (limite.isBefore(LocalDateTime.now())) {
                sessione.chiudiDUfficio(limite);
            }
        });
    }

    /**
     * La finestra di correzione: oggi, oppure sempre se è da confermare.
     */
    private void vietaSeFuoriDallaFinestra(SessioneTask sessione) {
        if (!sessione.isConfermata()) {
            return;
        }
        if (!sessione.getAvviataIl().toLocalDate().isEqual(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Il tempo del " + sessione.getAvviataIl().toLocalDate()
                            + " non si corregge più: si sistema entro la giornata.");
        }
    }

    /** La sessione, se è di chi chiede. Altrimenti non esiste. */
    private SessioneTask mia(UserDetails chiLoChiede, Long idSessione) {
        Utente io = autore(chiLoChiede);
        return sessioni.findById(idSessione)
                .filter(sessione -> sessione.getUtente().getId().equals(io.getId()))
                .filter(sessione -> sessione.getAnnullataIl() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Sessione non trovata."));
    }

    private Task taskDelLavoro(Long idTask, Long idLavoro) {
        return task.findById(idTask)
                .filter(trovata -> trovata.getLavoro().getId().equals(idLavoro))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Task non trovata in questo lavoro."));
    }

    private Utente autore(UserDetails chiLoChiede) {
        return utenti.findByEmail(chiLoChiede.getUsername()).orElseThrow(
                () -> new IllegalStateException(
                        "Utente collegato non trovato: " + chiLoChiede.getUsername()));
    }
}
