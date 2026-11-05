package com.anitia.backend.ore;

import com.anitia.backend.lavoro.Lavoro;
import com.anitia.backend.lavoro.LavoroRepository;
import com.anitia.backend.lavoro.ServizioLavori;
import com.anitia.backend.lavoro.StatoLavoro;
import com.anitia.backend.sicurezza.Permessi;
import com.anitia.backend.task.StatoTask;
import com.anitia.backend.task.Task;
import com.anitia.backend.task.TaskRepository;
import com.anitia.backend.utente.Utente;
import com.anitia.backend.utente.UtenteRepository;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * La distribuzione del monte ore fra le task di un lavoro.
 *
 * <p>Due regole governano tutto quello che c'è qui dentro.
 *
 * <p><strong>Le ore non si prendono mai da una task aperta.</strong> Non
 * esiste nessun metodo che sposti ore fra due task, e l'assenza è il punto:
 * togliere ore a una task che sta ancora lavorando sposterebbe il problema su
 * di lei — e lo nasconderebbe, perché lo sforamento comparirebbe altrove, più
 * tardi, senza che nessuno abbia deciso niente. Le ore tornano nel pool solo
 * da task chiuse o annullate, e se il pool non basta la strada è una
 * richiesta all'amministratore.
 *
 * <p><strong>Ogni allocazione blocca la riga del lavoro.</strong> Le ore
 * libere non sono una colonna: si calcolano come
 * {@code monte_ore − somma(task.ore_allocate)}. Senza il lock, due PM che
 * allocano nello stesso istante leggono entrambi lo stesso pool, trovano
 * entrambi ore a sufficienza e allocano entrambi: la somma finale sfonda il
 * monte ore e nessun vincolo del database se ne accorge, perché le due
 * scritture sono su due task diverse.
 */
@Service
public class ServizioOre {

    private static final String ALLOCA_TUTTI = "ORE_ALLOCA_TUTTI";
    private static final String ALLOCA_PROPRI = "ORE_ALLOCA_PROPRI";

    private final ServizioLavori lavori;
    private final LavoroRepository repositoryLavori;
    private final TaskRepository task;
    private final MovimentoOreRepository movimenti;
    private final UtenteRepository utenti;

    ServizioOre(ServizioLavori lavori, LavoroRepository repositoryLavori, TaskRepository task,
                MovimentoOreRepository movimenti, UtenteRepository utenti) {
        this.lavori = lavori;
        this.repositoryLavori = repositoryLavori;
        this.task = task;
        this.movimenti = movimenti;
        this.utenti = utenti;
    }

    /**
     * Alloca ore dal pool del lavoro a una sua task.
     *
     * <p>L'ordine delle operazioni non è casuale: prima il permesso, poi il
     * lock, poi la lettura delle somme, poi la scrittura. Leggere le ore
     * libere prima di aver bloccato il lavoro renderebbe il controllo di
     * capienza una fotografia già vecchia nel momento in cui la si guarda.
     *
     * <p>Se il pool non basta, il 409 dice <strong>quante ore libere ci
     * sono</strong>: è il numero con cui il frontend precompila la richiesta
     * all'amministratore, e senza di quello il PM dovrebbe calcolare a mano la
     * differenza.
     *
     * @return la situazione del lavoro dopo l'allocazione
     */
    @Transactional
    public RiepilogoOre alloca(UserDetails chiLoChiede, Long idLavoro, Long idTask,
                               BigDecimal ore, String motivo) {
        // Prima di tutto: chi non può vedere il lavoro non deve nemmeno
        // scoprire che esiste.
        Lavoro visibile = lavori.lavoroVisibile(chiLoChiede, idLavoro);
        vietaSeNonPuoAllocare(chiLoChiede, visibile);

        // Il lock parte da qui e dura fino al commit. Da questo punto in poi
        // nessun altro puo' allocare ore su questo lavoro.
        Lavoro lavoro = bloccato(idLavoro);

        if (lavoro.getStato() == StatoLavoro.CHIUSO
                || lavoro.getStato() == StatoLavoro.ANNULLATO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Il lavoro " + lavoro.getCodice() + " non è più in corso: non accetta "
                            + "nuove allocazioni di ore.");
        }
        if (lavoro.getMonteOre() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Il lavoro " + lavoro.getCodice() + " non ha ancora un monte ore: "
                            + "deve fissarlo un amministratore prima che si possano "
                            + "distribuire le ore.");
        }

        Task destinazione = taskDelLavoro(idTask, idLavoro);
        if (!aperta(destinazione)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La task «" + destinazione.getTitolo() + "» non è più aperta: allocarle "
                            + "ore non servirebbe a niente.");
        }

        BigDecimal libere = lavoro.getMonteOre().subtract(task.sommaOreAllocate(idLavoro));
        if (ore.compareTo(libere) > 0) {
            // Il messaggio porta il numero perche' e' quello che serve a chi
            // legge: la differenza fra quello che ha chiesto e le ore libere
            // e' esattamente quanto deve chiedere all'amministratore.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Il lavoro " + lavoro.getCodice() + " ha solo " + libere
                            + " ore libere, e ne servono " + ore
                            + ". Alloca quelle che restano e chiedi la differenza a un "
                            + "amministratore.");
        }

        destinazione.alloca(ore);
        movimenti.save(MovimentoOre.allocazione(lavoro, destinazione, ore, motivo,
                autore(chiLoChiede), null));

        return riepilogo(lavoro);
    }

    /**
     * Riduce l'allocazione di una task alle ore che ha davvero consumato, e
     * restituisce il residuo al pool del lavoro.
     *
     * <p>Si chiama alla chiusura o all'annullamento di una task — è il
     * momento in cui le ore stanziate e non usate tornano disponibili. Prima
     * di quel momento non si tocca niente: una task in corso ha diritto alle
     * ore che le sono state date.
     *
     * <p>Se la task ha sforato non fa nulla e lo sforamento resta registrato
     * così com'è: è un fatto accaduto, non un errore da correggere.
     *
     * <p><strong>Non esiste una rotta che la esponga</strong>, e non è una
     * mancanza: la chiama soltanto {@code ServizioTask} quando cambia lo
     * stato di una task, perché è il cambio di stato a giustificarla. Invocata
     * da sola libererebbe le ore di una task ancora al lavoro, che poi non
     * avrebbe più il budget per finire.
     *
     * @param chiusa la task appena portata a completata o annullata, già
     *               gestita dalla transazione che chiama
     * @return quante ore sono tornate nel pool, zero se nessuna
     */
    @Transactional
    public BigDecimal rilasciaResiduo(Task chiusa, Utente autore) {
        Lavoro lavoro = bloccato(chiusa.getLavoro().getId());

        BigDecimal consumate = task.sommaOreConsumateDellaTask(chiusa.getId());
        BigDecimal residuo = chiusa.rilasciaResiduo(consumate);
        if (residuo.signum() == 0) {
            return BigDecimal.ZERO;
        }

        movimenti.save(MovimentoOre.rilascio(lavoro, chiusa, residuo,
                "Task «" + chiusa.getTitolo() + "» chiusa con " + consumate
                        + " ore: residuo restituito al pool",
                autore));
        return residuo;
    }

    /** La situazione delle ore di un lavoro, senza modificarlo. */
    @Transactional(readOnly = true)
    public RiepilogoOre riepilogo(UserDetails chiLoChiede, Long idLavoro) {
        return riepilogo(lavori.lavoroVisibile(chiLoChiede, idLavoro));
    }

    private RiepilogoOre riepilogo(Lavoro lavoro) {
        return RiepilogoOre.da(lavoro,
                task.sommaOreAllocate(lavoro.getId()),
                task.sommaOreConsumate(lavoro.getId()));
    }

    // --- regole -------------------------------------------------------------

    /**
     * Il {@code @PreAuthorize} sulla rotta verifica che uno dei due permessi
     * ci sia; qui si verifica la parte che un'annotazione non può esprimere,
     * cioè che chi ha solo {@code _PROPRI} stia distribuendo il budget di un
     * lavoro di cui è responsabile.
     */
    private void vietaSeNonPuoAllocare(UserDetails chiLoChiede, Lavoro lavoro) {
        if (Permessi.ha(chiLoChiede, ALLOCA_TUTTI)) {
            return;
        }
        if (Permessi.ha(chiLoChiede, ALLOCA_PROPRI)
                && lavori.eResponsabile(chiLoChiede, lavoro)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Per distribuire le ore del lavoro " + lavoro.getCodice() + " devi esserne il "
                        + "responsabile, oppure avere il permesso su tutti i lavori.");
    }

    /**
     * Aperta vuol dire che ci si può ancora lavorare. Una task completata o
     * annullata non consuma più ore, quindi allocargliene non ha senso.
     */
    private static boolean aperta(Task task) {
        return task.getStato() == StatoTask.DA_FARE || task.getStato() == StatoTask.IN_CORSO;
    }

    // --- aiuti --------------------------------------------------------------

    private Lavoro bloccato(Long idLavoro) {
        return repositoryLavori.bloccaPerAggiornamento(idLavoro).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lavoro non trovato."));
    }

    private Task taskDelLavoro(Long idTask, Long idLavoro) {
        return task.findByIdAndLavoroId(idTask, idLavoro).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Task non trovata in questo lavoro."));
    }

    private Utente autore(UserDetails chiLoChiede) {
        return utenti.findByEmail(chiLoChiede.getUsername()).orElseThrow(
                () -> new IllegalStateException(
                        "Utente collegato non trovato: " + chiLoChiede.getUsername()));
    }
}
