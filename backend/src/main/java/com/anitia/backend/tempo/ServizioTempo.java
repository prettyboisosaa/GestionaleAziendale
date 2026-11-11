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
import java.time.DayOfWeek;
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

    /**
     * Una settimana di lavoro, da lunedì a domenica.
     *
     * <p>Alimenta la griglia a fasce orarie della dashboard. Non aggiunge
     * niente ai dati: legge le stesse sessioni che il timer scrive, e le
     * restituisce con i loro due istanti invece che sommate in un totale —
     * è la collocazione nella giornata che qui serve.
     *
     * <p>La settimana la decide il <strong>lunedì</strong>, non il giorno
     * chiesto: {@code with(DayOfWeek.MONDAY)} sulla settimana ISO riporta
     * anche la domenica al lunedì che la precede, così la domenica sera cade
     * nella settimana che si sta chiudendo e non in quella dopo. È la stessa
     * convenzione con cui il calendario in pagina è impostato su
     * {@code firstDay=1}: due definizioni diverse sposterebbero di un giorno
     * il confine fra una settimana e l'altra.
     *
     * <p>Passa dalla chiusura al tetto come ogni rotta del tempo: guardare la
     * propria settimana è un'occasione buona come un'altra per interrompere
     * una sessione rimasta aperta ieri sera.
     */
    @Transactional
    public SettimanaDiTempo miaSettimana(UserDetails chiLoChiede, LocalDate giorno) {
        Utente io = autore(chiLoChiede);
        chiudiSeOltreIlTetto(io.getId());

        LocalDate lunedi = giorno.with(DayOfWeek.MONDAY);

        // L'intervallo è semiaperto, come vuole trovaDelPeriodo: dalla
        // mezzanotte del lunedì a quella del lunedì dopo. La domenica entra
        // tutta, e nessuna sessione finisce in due settimane.
        List<SessioneTask> trovate = sessioni.trovaDelPeriodo(io.getId(),
                lunedi.atStartOfDay(), lunedi.plusDays(7).atStartOfDay());

        long minuti = trovate.stream().mapToLong(SessioneTask::durataMinuti).sum();

        return new SettimanaDiTempo(lunedi, lunedi.plusDays(6), minuti,
                lunedi.isEqual(inizioSettimanaCorrente()),
                trovate.stream().map(RigaSessione::da).toList());
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

        vietaSeNonAssegnato(io, destinazione);
        vietaSeNonSiPuoLavorare(lavoro, destinazione);

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

    /**
     * Registra a mano del lavoro già fatto: due istanti su una task.
     *
     * <p>È la rete per chi si dimentica il timer, e non un ritorno alla
     * dichiarazione giornaliera. La differenza non è formale: là si compilava
     * una giornata intera ogni sera, qui si rimedia a un buco su un
     * calendario che per il resto si riempie da sé. L'origine
     * {@code MANUALE} tiene separato quello che è stato misurato da quello
     * che è stato ricordato, e la griglia lo disegna tratteggiato.
     *
     * <p>I controlli sono quelli di {@link #avvia} — l'assegnazione, il
     * lavoro aperto, la task aperta — più i tre che riguardano un intervallo
     * scelto da una persona invece che misurato da un orologio: non finisce
     * nel futuro, sta nella settimana corrente, non si accavalla a un'altra
     * sessione. L'ordine conta: si rifiuta per la ragione più vicina a quello
     * che chi scrive ha sbagliato.
     *
     * <p>Nessun tetto alle ore, qui: il tetto serve a interrompere un timer
     * dimenticato, e un intervallo scritto a mano ha già una fine. Le ore
     * assurde le ferma la finestra della settimana e il divieto di
     * sovrapposizione — in una settimana non ci stanno più di sette giorni di
     * ore che non si accavallano.
     *
     * <p>Resta come scorciatoia della rotta che nomina la task
     * nell'indirizzo: compone la richiesta e lascia fare a
     * {@link #registraAttivita}. Due strade per la stessa scrittura
     * divergerebbero alla prima regola aggiunta a una sola delle due.
     */
    @Transactional
    public SituazioneTempo registraManuale(UserDetails chiLoChiede, Long idLavoro,
                                           Long idTask, LocalDateTime da,
                                           LocalDateTime a) {
        // idLavoro serve ancora: la rotta lo porta, e una task chiesta a un
        // lavoro che non è il suo deve restare un 404 e non diventare una
        // scrittura sul lavoro giusto per caso.
        taskDelLavoro(idTask, idLavoro);
        return registraAttivita(chiLoChiede, RichiestaAttivita.suTask(idTask, da, a));
    }

    /**
     * Registra a mano un'attività già fatta: due istanti, un tipo, e quello
     * che quel tipo pretende.
     *
     * <p>Da qui passa tutto quello che il calendario scrive, task o non task.
     * Con {@code LAVORO_TASK} è la registrazione di sempre — la rete per chi
     * si dimentica il timer. Con gli altri tipi è la cosa che prima non si
     * poteva dire: una riunione, una manutenzione, una trasferta sono ore vere
     * che non stanno sotto nessuna task, e appenderle a una qualunque
     * scriveva ore di sviluppo che non erano state fatte.
     *
     * <p><strong>L'ordine dei controlli è il messaggio.</strong> Si rifiuta
     * per la ragione più vicina a quello che chi scrive ha sbagliato: prima la
     * coerenza di quello che ha scelto nel modulo, poi i permessi su ciò che
     * ha nominato, poi l'intervallo. Dire «non sei assegnato a questa task» a
     * chi ha solo dimenticato di scegliere il lavoro è una risposta a una
     * domanda che non ha fatto.
     *
     * <p>Nessun tetto alle ore, qui: il tetto serve a interrompere un timer
     * dimenticato, e un intervallo scritto a mano ha già una fine. Le ore
     * assurde le ferma la finestra della settimana e il divieto di
     * sovrapposizione — in una settimana non ci stanno più di sette giorni di
     * ore che non si accavallano.
     */
    @Transactional
    public SituazioneTempo registraAttivita(UserDetails chiLoChiede,
                                            RichiestaAttivita richiesta) {
        Utente io = autore(chiLoChiede);
        TipoAttivita tipo = richiesta.tipoAttivita();
        vietaSeIncoerente(tipo, richiesta.taskId(), richiesta.lavoroId());

        Task destinazione = null;
        Lavoro citato = null;
        if (richiesta.taskId() != null) {
            destinazione = task.findById(richiesta.taskId()).orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Task non trovata."));
            // Il lavoro si ricava dalla task e si fa verificare comunque: la
            // visibilità è di chi chiede, non della task.
            Lavoro suo = lavori.lavoroVisibile(chiLoChiede,
                    destinazione.getLavoro().getId());
            vietaSeNonAssegnato(io, destinazione);
            vietaSeNonSiPuoLavorare(suo, destinazione);
        } else if (richiesta.lavoroId() != null) {
            // Niente controllo di assegnazione: un'attività che non è una task
            // non si assegna a nessuno, e pretenderlo vorrebbe dire che la
            // manutenzione di un lavoro consegnato la può registrare solo chi
            // ha ancora una task aperta su quel lavoro. Basta vederlo.
            citato = lavori.lavoroVisibile(chiLoChiede, richiesta.lavoroId());
        }

        LocalDateTime da = richiesta.avviataIl();
        LocalDateTime a = richiesta.chiusaIl();
        vietaSeIntervalloImpossibile(da, a);
        vietaSeFuoriDallaSettimana(da.toLocalDate());

        // Prima di cercare le sovrapposizioni si mette in pari il timer: una
        // sessione rimasta aperta da ieri sera risulterebbe aperta anche
        // adesso, e farebbe da ostacolo a tutto quello che le viene dopo.
        // Chiusa al tetto, torna a occupare le sole ore che le competono.
        chiudiSeOltreIlTetto(io.getId());
        vietaSeSiSovrappone(io.getId(), da, a, null);

        String descrizione = ripulita(richiesta.descrizione());

        if (destinazione == null) {
            sessioni.save(SessioneTask.attivita(io, tipo, citato, descrizione, da, a));
            return miaSituazione(chiLoChiede);
        }

        // Averci lavorato è averla messa in corso, come per l'avvio del
        // timer: chiedere di dirlo anche con la tendina sarebbe l'adempimento
        // che questo cambiamento ha tolto. Vale solo dove c'è una task: una
        // riunione non mette in corso niente.
        if (destinazione.getStato() == StatoTask.ATTIVO) {
            destinazione.cambiaStato(StatoTask.IN_CORSO);
        }

        sessioni.save(SessioneTask.manuale(destinazione, io, tipo, descrizione, da, a));

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
     * <p>Si corregge entro la <strong>settimana</strong> in cui si è
     * lavorato, oppure sempre se la sessione è una chiusura d'ufficio da
     * confermare: la finestra la spiega {@link #vietaSeFuoriDallaFinestra}.
     *
     * <p>La correzione non può portare la sessione <strong>sopra un'altra</strong>:
     * spostarla di due ore potrebbe farla finire addosso a quella del
     * pomeriggio, e le ore di una persona non si sovrappongono. Era un
     * controllo che mancava, perché prima della griglia settimanale una
     * correzione nasceva da un timer appena fermato e non da un orario
     * riscritto a mano.
     *
     * <p>Nessuna correzione per l'amministratore, di proposito: le ore sono di
     * chi le ha lavorate. Una sessione che nessuno conferma resta non
     * confermata e segnalata, e la strada è chiederlo alla persona.
     *
     * <p>Si correggono anche <strong>il tipo di attività, il lavoro citato e
     * la descrizione</strong>: non sono misure ma cose scritte da una persona,
     * e chi sbaglia tendina deve poter rimediare senza buttare via la riga. La
     * task no — quella si cambia annullando e riscrivendo, e il perché sta su
     * {@link RichiestaCorrezione}.
     */
    @Transactional
    public SituazioneTempo correggi(UserDetails chiLoChiede, Long idSessione,
                                    RichiestaCorrezione richiesta) {
        SessioneTask sessione = mia(chiLoChiede, idSessione);
        vietaSeFuoriDallaFinestra(sessione);

        LocalDateTime avviataIl = richiesta.avviataIl();
        LocalDateTime chiusaIl = richiesta.chiusaIl();
        TipoAttivita tipo = richiesta.tipoAttivita();

        boolean haTask = sessione.getTask() != null;
        vietaSeIncoerente(tipo, haTask ? sessione.getTask().getId() : null,
                richiesta.lavoroId());

        Lavoro citato = null;
        if (!haTask && richiesta.lavoroId() != null) {
            citato = lavori.lavoroVisibile(chiLoChiede, richiesta.lavoroId());
        }

        vietaSeIntervalloImpossibile(avviataIl, chiusaIl);

        // Anche la destinazione deve stare nella finestra, altrimenti la
        // finestra la si aggira spostando una sessione invece di scriverla.
        //
        // Ma solo se una finestra c'è: una chiusura d'ufficio di tre settimane
        // fa si corregge **sul suo giorno**, e pretendere che finisca in
        // questa settimana costringerebbe a datarla male per poterla
        // sistemare. Chi c'era deve poter dire quando ha smesso, non quando
        // gli conviene dirlo.
        if (sessione.isConfermata()) {
            vietaSeFuoriDallaSettimana(avviataIl.toLocalDate());
        }

        vietaSeSiSovrappone(sessione.getUtente().getId(), avviataIl, chiusaIl, idSessione);

        sessione.correggi(avviataIl, chiusaIl);
        sessione.ridescrivi(tipo, citato, ripulita(richiesta.descrizione()));
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
     * Serve un'assegnazione attiva, e <strong>non</strong> basta poter gestire
     * la task.
     *
     * <p>È la differenza fra «posso disporre di questa task» e «ci ho lavorato
     * io»: il tempo è di chi l'ha lavorato, e scriverlo su una task di qualcun
     * altro vorrebbe dire mettere le proprie ore nel consuntivo del lavoro di
     * un collega. Un responsabile che davvero ci lavora ha già la strada:
     * assegnarsi la task, cosa che il suo permesso di gestione gli consente.
     *
     * <p>Per questo non si riusa {@code vietaSeNonPuoCambiareStato}, che
     * invece lascia passare chi ha {@code TASK_GESTISCI_TUTTI} o è
     * responsabile del lavoro: cambiare lo stato di una task altrui è
     * legittimo, registrarci il proprio tempo no.
     *
     * <p>Vale identico per il timer e per la scrittura a mano: cambia il modo
     * in cui l'ora viene fuori, non di chi è.
     */
    private void vietaSeNonAssegnato(Utente io, Task destinazione) {
        if (assegnazioni
                .findByTaskIdAndUtenteIdAndRimossaIlIsNull(destinazione.getId(), io.getId())
                .isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Non sei assegnato alla task «" + destinazione.getTitolo()
                            + "»: puoi registrare tempo solo sulle task che ti sono "
                            + "state affidate.");
        }
    }

    /**
     * Su un lavoro chiuso o su una task non più aperta non si registra tempo,
     * né col timer né a mano.
     *
     * <p>Non è una formalità: chiudere una task restituisce al pool del lavoro
     * le ore allocate e non consumate, e quel conto è già stato fatto.
     * Scriverci dentro altre ore dopo lo smentirebbe.
     */
    private void vietaSeNonSiPuoLavorare(Lavoro lavoro, Task destinazione) {
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
    }

    /** Il lunedì della settimana in corso: il confine della finestra. */
    static LocalDate inizioSettimanaCorrente() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    /**
     * La finestra di scrittura: <strong>la settimana corrente</strong>, da
     * lunedì a domenica, oppure sempre se la sessione è una chiusura d'ufficio
     * da confermare.
     *
     * <p>Era la giornata, e andava bene quando l'unica scrittura possibile era
     * correggere una misura appena presa: di un timer lasciato scorrere ci si
     * accorge subito. Da quando il calendario settimanale permette di
     * <em>riempire</em> un giorno che non è stato registrato, lo scopo è
     * diverso — rimediare a una dimenticanza — e un giorno di finestra lo
     * manca per definizione, perché di un giovedì non registrato ci si accorge
     * il venerdì.
     *
     * <p>Non si va oltre, però, perché le ore di una persona non sono un
     * diario personale: entrano nel consuntivo di un lavoro, su cui qualcuno
     * decide. La settimana è il periodo che si chiude e si guarda; lasciar
     * riscrivere un mese dopo vorrebbe dire che nessun consuntivo è mai fermo.
     *
     * <p>L'eccezione per le chiusure d'ufficio resta, e non è una scappatoia:
     * una sessione chiusa al tetto nasce di notte o la mattina dopo, e
     * bloccarla col suo periodo renderebbe la conferma una formalità su un
     * numero che nessuno può più sistemare. Il dato che il sistema si è
     * dedotto lo deve poter correggere chi c'era.
     */
    private void vietaSeFuoriDallaFinestra(SessioneTask sessione) {
        if (!sessione.isConfermata()) {
            return;
        }
        vietaSeFuoriDallaSettimana(sessione.getAvviataIl().toLocalDate());
    }

    /**
     * Vieta un intervallo che non può essere accaduto: rovesciato, o nel
     * futuro.
     *
     * <p>Gli stessi due controlli servono a chi scrive e a chi corregge, e
     * stavano scritti due volte. Il codice in testa al messaggio è la
     * convenzione con cui l'interfaccia riconosce l'errore senza leggerne il
     * testo.
     */
    private static void vietaSeIntervalloImpossibile(LocalDateTime da, LocalDateTime a) {
        if (!a.isAfter(da)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "INTERVALLO_ROVESCIATO: la fine deve venire dopo l'inizio.");
        }
        if (a.isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "TEMPO_NEL_FUTURO: non si registra tempo che non è ancora passato.");
        }
    }

    /**
     * Vieta una registrazione che non dice quello che il suo tipo pretende.
     *
     * <p>Tre regole, e tutte e tre vengono da {@link TipoAttivita}, che le
     * pubblica perché le legge anche l'interfaccia: una tendina che si apre
     * per far ricevere un 400 ha mentito a chi l'ha compilata. Qui si
     * verificano comunque, perché un modulo non è un confine.
     *
     * <p>L'inverso del primo controllo esiste e il terzo non ce l'ha: una
     * task su un tipo diverso da {@code LAVORO_TASK} è <strong>ammessa</strong>
     * di proposito — una riunione sul lavoro X può ben essere agganciata alla
     * sua task, e quelle ore restano ore di riunione. Quello che non si può è
     * il contrario: {@code LAVORO_TASK} senza task non dice niente.
     */
    private static void vietaSeIncoerente(TipoAttivita tipo, Long idTask, Long idLavoro) {
        if (tipo.richiedeTask() && idTask == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "TASK_RICHIESTA: «" + tipo.etichetta() + "» vuole la task su cui "
                            + "hai lavorato.");
        }
        if (tipo.richiedeLavoro() && idLavoro == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "LAVORO_RICHIESTO: «" + tipo.etichetta() + "» vuole il lavoro a cui "
                            + "si riferisce.");
        }
    }

    /**
     * La descrizione come va salvata: senza spazi ai bordi, e null se non
     * c'era niente.
     *
     * <p>Una stringa vuota e un campo mai compilato sono la stessa cosa — «non
     * l'ha scritto nessuno» — e salvarli diversamente vorrebbe dire due modi
     * di essere vuoto da distinguere a ogni lettura.
     */
    private static String ripulita(String descrizione) {
        if (descrizione == null) {
            return null;
        }
        String pulita = descrizione.strip();
        return pulita.isEmpty() ? null : pulita;
    }

    /** Lo stesso confine, per un giorno che non è ancora una sessione. */
    private void vietaSeFuoriDallaSettimana(LocalDate giorno) {
        if (giorno.isBefore(inizioSettimanaCorrente())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Il tempo del " + giorno + " non si scrive più: si sistema "
                            + "entro la settimana in cui si è lavorato.");
        }
    }

    /**
     * Vieta che due sessioni della stessa persona coprano lo stesso istante.
     *
     * <p>Era già vero, ma per caso. {@code uk_sessione_in_corso} garantisce
     * una sola sessione <em>aperta</em>, non l'assenza di sovrapposizioni fra
     * sessioni <em>chiuse</em>: finora bastava, perché scriveva solo il timer
     * e il timer chiude la precedente quando ne apre un'altra. Da quando si
     * può scrivere un intervallo a mano la cosa è raggiungibile, e romperebbe
     * la proprietà su cui questa classe dichiara di poggiare tutto il resto —
     * che le ore si possano sommare senza chiedersi quale sia quella vera. Due
     * ore scritte due volte diventerebbero quattro nel consuntivo di un
     * lavoro.
     *
     * <p>Il controllo sta qui e non in un vincolo del database, a differenza
     * dell'unicità: un CHECK non guarda le altre righe, e il vincolo di
     * esclusione che servirebbe MySQL non lo ha. Resta aperta la finestra fra
     * la lettura e la scrittura, ma a percorrerla dovrebbe essere la stessa
     * persona che inserisce due intervalli accavallati nello stesso istante da
     * due finestre del browser: un rischio che non vale una tabella di
     * appoggio.
     *
     * <p>Il messaggio <strong>nomina</strong> la sessione in conflitto. Un
     * «si sovrappone» senza dire a che cosa è un errore su cui non si può
     * agire: chi lo legge non sa che cosa spostare.
     */
    private void vietaSeSiSovrappone(Long utenteId, LocalDateTime da,
                                     LocalDateTime a, Long escludi) {
        sessioni.trovaSovrapposte(utenteId, da, a, escludi).stream().findFirst()
                .ifPresent(scontro -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "In quelle ore hai già «" + comeSiChiama(scontro)
                                    + "», dalle " + ora(scontro.getAvviataIl())
                                    + (scontro.getChiusaIl() == null
                                            ? " e ancora in corso"
                                            : " alle " + ora(scontro.getChiusaIl()))
                                    + ". Le ore non si sovrappongono.");
                });
    }

    /**
     * Come si chiama una sessione in un messaggio d'errore: il titolo della
     * task, oppure l'etichetta del tipo di attività se una task non c'è.
     *
     * <p>Serve perché {@code getTask()} può essere null da quando una sessione
     * può essere una riunione. Chi legge «in quelle ore hai già Riunione»
     * capisce che cosa spostare esattamente come chi legge il titolo di una
     * task — e la descrizione non entra qui, perché può essere lunga e un
     * messaggio d'errore non è un posto dove leggerla.
     */
    private static String comeSiChiama(SessioneTask sessione) {
        return sessione.getTask() == null
                ? sessione.getTipoAttivita().etichetta()
                : sessione.getTask().getTitolo();
    }

    /** Solo l'ora e i minuti: il giorno chi legge il messaggio lo sa già. */
    private static String ora(LocalDateTime istante) {
        return String.format("%02d:%02d", istante.getHour(), istante.getMinute());
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
