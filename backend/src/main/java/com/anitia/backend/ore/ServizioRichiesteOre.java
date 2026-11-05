package com.anitia.backend.ore;

import com.anitia.backend.lavoro.Lavoro;
import com.anitia.backend.lavoro.LavoroRepository;
import com.anitia.backend.lavoro.ServizioLavori;
import com.anitia.backend.lavoro.StatoLavoro;
import com.anitia.backend.ore.ContestoRichiesta.TaskInSforamento;
import com.anitia.backend.sicurezza.Permessi;
import com.anitia.backend.task.StatoTask;
import com.anitia.backend.task.Task;
import com.anitia.backend.task.TaskRepository;
import com.anitia.backend.utente.Utente;
import com.anitia.backend.utente.UtenteRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Le richieste di ore: la strada che si prende quando il pool di un lavoro non
 * basta.
 *
 * <p>Sostituisce una trattativa a voce con un percorso che resta scritto. Il
 * PM apre una richiesta motivata; l'amministratore la vede con tutto il
 * contesto del lavoro — monte ore attuale e iniziale, ore allocate, ore
 * consumate, task in sforamento, storico delle richieste precedenti — e
 * decide. L'approvazione non è un messaggio: <strong>produce</strong> il
 * movimento che alza il monte ore, e se la richiesta era legata a una task
 * alloca le ore direttamente là.
 *
 * <p>Il verso dell'approvazione parziale conta: concedere meno di quanto
 * chiesto non è un rifiuto, e lo stato lo dice. Le ore che mancano restano un
 * problema aperto, e il PM lo vede.
 */
@Service
public class ServizioRichiesteOre {

    private static final String RICHIEDI = "ORE_RICHIEDI";
    private static final String GESTISCI = "ORE_RICHIESTE_GESTISCI";
    private static final String ALLOCA_TUTTI = "ORE_ALLOCA_TUTTI";
    private static final String VISUALIZZA_TUTTI = "LAVORI_VISUALIZZA_TUTTI";

    private final RichiestaOreRepository richieste;
    private final MovimentoOreRepository movimenti;
    private final LavoroRepository repositoryLavori;
    private final ServizioLavori lavori;
    private final TaskRepository task;
    private final UtenteRepository utenti;

    ServizioRichiesteOre(RichiestaOreRepository richieste, MovimentoOreRepository movimenti,
                         LavoroRepository repositoryLavori, ServizioLavori lavori,
                         TaskRepository task, UtenteRepository utenti) {
        this.richieste = richieste;
        this.movimenti = movimenti;
        this.repositoryLavori = repositoryLavori;
        this.lavori = lavori;
        this.task = task;
        this.utenti = utenti;
    }

    /**
     * Apre una richiesta di ore sul lavoro.
     *
     * <p>Chiede a chi la apre di essere il responsabile del lavoro, oltre ad
     * avere {@code ORE_RICHIEDI}: una richiesta su un lavoro che non si
     * gestisce arriverebbe all'amministratore senza che nessuno ne risponda.
     *
     * <p>Non c'è nessun controllo del tipo «il pool deve essere vuoto»: il
     * piano prevede esplicitamente il caso in cui il PM alloca le ore libere
     * rimaste e chiede soltanto la differenza, e quel caso lascia il pool a
     * zero solo <em>dopo</em>. Anche aprire una richiesta in previsione è
     * legittimo.
     */
    @Transactional
    public RigaRichiestaOre apri(UserDetails chiLoChiede, Long idLavoro,
                                 RichiestaNuoveOre richiesta) {
        Lavoro lavoro = lavori.lavoroVisibile(chiLoChiede, idLavoro);
        vietaSeNonPuoRichiedere(chiLoChiede, lavoro);

        if (lavoro.getStato() == StatoLavoro.CHIUSO
                || lavoro.getStato() == StatoLavoro.ANNULLATO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Il lavoro " + lavoro.getCodice() + " non è più in corso: non ha senso "
                            + "chiedere ore per lui.");
        }

        Task destinazione = null;
        if (richiesta.taskId() != null) {
            destinazione = task.findByIdAndLavoroId(richiesta.taskId(), idLavoro).orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Task non trovata in questo lavoro."));
            if (destinazione.getStato() == StatoTask.COMPLETATA
                    || destinazione.getStato() == StatoTask.ANNULLATA) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La task «" + destinazione.getTitolo() + "» non è più aperta: le ore "
                                + "concesse non potrebbero esserle allocate.");
            }
        }

        RichiestaOre aperta = richieste.saveAndFlush(RichiestaOre.nuova(lavoro, destinazione,
                autore(chiLoChiede), richiesta.oreRichieste(), richiesta.motivo()));

        return RigaRichiestaOre.da(aperta);
    }

    /**
     * La decisione dell'amministratore.
     *
     * <p>Tutto in una transazione con la riga del lavoro bloccata: l'aumento
     * del monte ore e l'allocazione alla task sono due scritture che devono
     * valere insieme, e fra le due un'allocazione concorrente leggerebbe un
     * pool a metà strada.
     *
     * <p>Il movimento che alza il monte ore <strong>cita la richiesta</strong>.
     * Senza quel riferimento, a distanza di mesi un aumento approvato sarebbe
     * indistinguibile da uno deciso di iniziativa dell'amministratore.
     */
    @Transactional
    public RigaRichiestaOre gestisci(UserDetails chiLoChiede, Long idRichiesta,
                                     BigDecimal oreConcesse) {
        RichiestaOre richiesta = richieste.trovaCompleta(idRichiesta).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Richiesta non trovata."));

        if (!richiesta.eInAttesa()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La richiesta è già stata gestita il " + richiesta.getGestitaIl()
                            + ": sono state concesse " + richiesta.getOreConcesse() + " ore.");
        }

        Utente amministratore = autore(chiLoChiede);
        Task destinazione = richiesta.getTask();

        if (oreConcesse.signum() > 0) {
            // Il lock prima di toccare monte ore e allocazioni.
            Lavoro lavoro = repositoryLavori
                    .bloccaPerAggiornamento(richiesta.getLavoro().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Lavoro non trovato."));

            BigDecimal precedente = lavoro.getMonteOre();
            BigDecimal nuovo = (precedente == null ? BigDecimal.ZERO : precedente)
                    .add(oreConcesse);
            lavoro.cambiaMonteOre(nuovo);

            // Un lavoro senza monte ore che riceve ore da una richiesta: il
            // suo budget nasce adesso, e questo e' il movimento da cui si
            // rileggera' la stima iniziale.
            movimenti.save(precedente == null
                    ? MovimentoOre.monteIniziale(lavoro, oreConcesse,
                            "Monte ore nato dall'approvazione della richiesta "
                                    + richiesta.getId(),
                            amministratore)
                    : MovimentoOre.modificaMonte(lavoro, oreConcesse,
                            "Ore concesse sulla richiesta " + richiesta.getId() + " di "
                                    + richiesta.getRichiedente().getCognome(),
                            amministratore, richiesta.getId()));

            // Se la richiesta nasceva da una task, le ore ci vanno dritte: e'
            // la fix che impedisce alle ore concesse di finire altrove.
            if (destinazione != null) {
                destinazione.alloca(oreConcesse);
                movimenti.save(MovimentoOre.allocazione(lavoro, destinazione, oreConcesse,
                        "Ore concesse, allocate alla task della richiesta", amministratore,
                        richiesta.getId()));
            }
        }

        richiesta.gestisci(oreConcesse, amministratore);
        richieste.saveAndFlush(richiesta);

        return RigaRichiestaOre.da(richiesta);
    }

    /**
     * Il quadro completo su cui l'amministratore decide.
     *
     * <p>Una query per blocco, non una per riga: cinque interrogazioni per
     * disegnare la schermata, invece di una per ogni task del lavoro.
     */
    @Transactional(readOnly = true)
    public ContestoRichiesta contesto(UserDetails chiLoChiede, Long idRichiesta) {
        RichiestaOre richiesta = richieste.trovaCompleta(idRichiesta).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Richiesta non trovata."));

        Long idLavoro = richiesta.getLavoro().getId();
        // Chi non vede il lavoro non vede la richiesta: vale anche per chi ha
        // il permesso di gestirle, perche' la schermata mostra i numeri del
        // lavoro.
        Lavoro lavoro = lavori.lavoroVisibile(chiLoChiede, idLavoro);

        List<TaskInSforamento> sforamenti = task.trovaSforamenti(idLavoro).stream()
                .map(riga -> new TaskInSforamento(riga.getTaskId(), riga.getTitolo(),
                        riga.getOreAllocate(), riga.getOreConsumate(),
                        riga.getOreConsumate().subtract(riga.getOreAllocate())))
                .toList();

        return new ContestoRichiesta(
                RigaRichiestaOre.da(richiesta),
                RiepilogoOre.da(lavoro, task.sommaOreAllocate(idLavoro),
                        task.sommaOreConsumate(idLavoro)),
                movimenti.monteIniziale(idLavoro).orElse(null),
                sforamenti,
                richieste.trovaDelLavoro(idLavoro).stream().map(RigaRichiestaOre::da).toList());
    }

    /**
     * Le richieste ancora da decidere, dalla più vecchia.
     *
     * <p>In mancanza di notifiche, è questo elenco a fare da campanello: per
     * questo l'ordine è quello dell'attesa, e non quello di inserimento.
     */
    @Transactional(readOnly = true)
    public List<RigaRichiestaOre> inAttesa() {
        return richieste.trovaInAttesa().stream().map(RigaRichiestaOre::da).toList();
    }

    /**
     * Le richieste che chi chiede ha diritto di vedere: tutte quelle dei
     * lavori che vede, se li vede tutti, altrimenti quelle dei propri.
     *
     * <p>È così che un PM scopre com'è finita la sua richiesta, in assenza di
     * notifiche: la ritrova in elenco con lo stato aggiornato.
     */
    @Transactional(readOnly = true)
    public List<RigaRichiestaOre> mie(UserDetails chiLoChiede) {
        if (Permessi.ha(chiLoChiede, VISUALIZZA_TUTTI)) {
            return richieste.trovaTutte().stream().map(RigaRichiestaOre::da).toList();
        }
        Utente io = autore(chiLoChiede);
        return richieste.trovaDiRichiedente(io.getId()).stream()
                .map(RigaRichiestaOre::da)
                .toList();
    }

    /** Lo storico delle richieste di un lavoro, per chi può vedere quel lavoro. */
    @Transactional(readOnly = true)
    public List<RigaRichiestaOre> delLavoro(UserDetails chiLoChiede, Long idLavoro) {
        lavori.lavoroVisibile(chiLoChiede, idLavoro);
        return richieste.trovaDelLavoro(idLavoro).stream().map(RigaRichiestaOre::da).toList();
    }

    // --- regole -------------------------------------------------------------

    /**
     * Il {@code @PreAuthorize} verifica che {@code ORE_RICHIEDI} ci sia; qui
     * si verifica che chi apre la richiesta sia il responsabile del lavoro —
     * oppure abbia il permesso su tutti i lavori, nel qual caso in genere può
     * già allocare da sé e la richiesta la apre per conto di qualcun altro.
     */
    private void vietaSeNonPuoRichiedere(UserDetails chiLoChiede, Lavoro lavoro) {
        if (Permessi.ha(chiLoChiede, ALLOCA_TUTTI) || Permessi.ha(chiLoChiede, GESTISCI)) {
            return;
        }
        if (Permessi.ha(chiLoChiede, RICHIEDI) && lavori.eResponsabile(chiLoChiede, lavoro)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Per chiedere ore sul lavoro " + lavoro.getCodice() + " devi esserne il "
                        + "responsabile.");
    }

    private Utente autore(UserDetails chiLoChiede) {
        return utenti.findByEmail(chiLoChiede.getUsername()).orElseThrow(
                () -> new IllegalStateException(
                        "Utente collegato non trovato: " + chiLoChiede.getUsername()));
    }
}
