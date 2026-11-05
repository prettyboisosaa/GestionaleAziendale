package com.anitia.backend.task;

import com.anitia.backend.lavoro.Lavoro;
import com.anitia.backend.lavoro.ServizioLavori;
import com.anitia.backend.lavoro.StatoLavoro;
import com.anitia.backend.ore.ServizioOre;
import com.anitia.backend.sicurezza.Permessi;
import com.anitia.backend.task.AssegnazioneTaskRepository.AssegnazioneDiTask;
import com.anitia.backend.task.TaskRepository.OrePerTask;
import com.anitia.backend.utente.Utente;
import com.anitia.backend.utente.UtenteRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServizioTask {

    private static final String GESTISCI_TUTTI = "TASK_GESTISCI_TUTTI";
    private static final String GESTISCI_PROPRI = "TASK_GESTISCI_PROPRI";
    private static final String VISUALIZZA_LAVORO = "TASK_VISUALIZZA_LAVORO";
    private static final String AGGIORNA_STATO = "TASK_AGGIORNA_STATO";

    /**
     * L'ordine di «Le mie task»: urgenza, non alfabeto.
     *
     * <p>Prima ciò su cui si sta lavorando, poi ciò che aspetta, e in fondo
     * ciò che è chiuso; dentro ogni gruppo la scadenza più vicina per prima.
     * Ordinare per lavoro, come fa la pagina del progetto, qui sarebbe
     * sbagliato: chi esegue non attraversa i progetti, attraversa le
     * scadenze.
     *
     * <p>L'ordinamento è in memoria e non nel {@code ORDER BY} perché la
     * query restituisce entità con le loro relazioni già caricate, non righe
     * piatte: lo stesso motivo per cui la posizione di uno stato nell'ordine
     * è una cosa da dire in Java — {@code FIELD()} in SQL direbbe la stessa
     * cosa, ma in un posto dove non si legge insieme al resto della regola.
     */
    private static final Comparator<MiaTask> PER_URGENZA =
            Comparator.comparingInt((MiaTask riga) -> posizione(riga.stato()))
                    .thenComparing(MiaTask::scadenza)
                    .thenComparing(MiaTask::codiceLavoro)
                    .thenComparing(MiaTask::titolo);

    private final TaskRepository task;
    private final AssegnazioneTaskRepository assegnazioni;
    private final UtenteRepository utenti;
    private final ServizioLavori lavori;
    private final ServizioOre ore;

    ServizioTask(TaskRepository task, AssegnazioneTaskRepository assegnazioni,
                 UtenteRepository utenti, ServizioLavori lavori, ServizioOre ore) {
        this.task = task;
        this.assegnazioni = assegnazioni;
        this.utenti = utenti;
        this.lavori = lavori;
        this.ore = ore;
    }

    /**
     * Le task affidate a chi chiede, di ogni lavoro.
     *
     * <p>Tre letture e nessuna dentro un ciclo: le assegnazioni con task,
     * lavoro e responsabile già agganciati, e due somme raggruppate per task.
     * Con {@code open-in-view} disattivato la variante ingenua — una query per
     * riga — costerebbe un'interrogazione per ogni task della persona.
     *
     * <p>Non c'è nessun controllo di permesso oltre a quello della rotta, e
     * non è una dimenticanza: la lettura è filtrata sull'id di chi chiede,
     * quindi non esiste un modo di farla parlare delle task di un altro.
     *
     * <p>Ci sono <strong>tutti</strong> gli stati, anche {@code COMPLETATA} e
     * {@code ANNULLATA}. Una task chiusa ieri deve restare visibile oggi:
     * farla sparire il giorno dopo averla finita toglierebbe alla persona la
     * traccia del proprio lavoro.
     */
    @Transactional(readOnly = true)
    public List<MiaTask> mie(UserDetails chiLoChiede) {
        Long mio = idDi(chiLoChiede);
        if (mio == null) {
            return List.of();
        }

        Map<Long, BigDecimal> consumate = perTask(task.sommaOreConsumateDelleMieTask(mio));
        Map<Long, BigDecimal> mieOre = perTask(task.sommaMieOrePerTask(mio));

        // Una data sola per tutta la risposta: «in ritardo» e «giorni alla
        // scadenza» di righe diverse non devono poter essere calcolati a
        // cavallo della mezzanotte e risultare incoerenti fra loro.
        LocalDate oggi = LocalDate.now();

        return assegnazioni.trovaAttiveDiUtente(mio).stream()
                .map(AssegnazioneTask::getTask)
                .map(singola -> MiaTask.da(singola,
                        consumate.getOrDefault(singola.getId(), BigDecimal.ZERO),
                        mieOre.getOrDefault(singola.getId(), BigDecimal.ZERO),
                        oggi))
                .sorted(PER_URGENZA)
                .toList();
    }

    /**
     * Gli inizi e le scadenze delle task aperte di chi chiede che cadono
     * nell'intervallo, raggruppati per giorno.
     *
     * <p>Una mappa e non un elenco piatto: la data è quella della cella che
     * conterrà il segno, e tenerla anche dentro {@link SegnoTask} vorrebbe
     * dire due posti che dicono lo stesso giorno.
     *
     * <p>Il filtro sulle date è in memoria perché la lettura delle
     * assegnazioni è già quella di {@link #mie(UserDetails)} — poche righe,
     * quante le task di una persona — e una query dedicata con
     * {@code BETWEEN} su due colonne sarebbe una seconda definizione di «le
     * mie task» da tenere allineata alla prima.
     *
     * <p>Solo le task aperte: una task completata non è più qualcosa da fare,
     * e la sua vecchia scadenza in un calendario sarebbe rumore.
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, List<SegnoTask>> segniDelPeriodo(UserDetails chiLoChiede,
                                                           LocalDate da, LocalDate a) {
        Long mio = idDi(chiLoChiede);
        if (mio == null) {
            return Map.of();
        }

        Map<LocalDate, List<SegnoTask>> perGiorno = new HashMap<>();
        for (AssegnazioneTask assegnazione : assegnazioni.trovaAttiveDiUtente(mio)) {
            Task singola = assegnazione.getTask();
            if (singola.getStato() != StatoTask.DA_FARE
                    && singola.getStato() != StatoTask.IN_CORSO) {
                continue;
            }
            segna(perGiorno, singola, singola.getDataInizio(), TipoSegno.INIZIO, da, a);
            segna(perGiorno, singola, singola.getScadenza(), TipoSegno.SCADENZA, da, a);
        }
        return perGiorno;
    }

    /**
     * Le task di un lavoro che chi chiede ha diritto di vedere.
     *
     * <p>Chi non vede tutto riceve solo le task a cui è assegnato — ma di
     * quelle vede l'elenco completo degli assegnatari: sapere con chi si
     * condivide una task è utile e non è un'informazione riservata.
     */
    @Transactional(readOnly = true)
    public List<RigaTask> delLavoro(UserDetails chiLoChiede, Long idLavoro) {
        Lavoro lavoro = lavori.lavoroVisibile(chiLoChiede, idLavoro);
        Map<Long, List<Assegnatario>> perTask = assegnatariPerTask(idLavoro);
        List<Task> elenco = task.findByLavoroIdOrderByDataInizioAscIdAsc(idLavoro);

        if (!vedeTutte(chiLoChiede, lavoro)) {
            Long mio = idDi(chiLoChiede);
            elenco = elenco.stream()
                    .filter(singola -> assegnatari(perTask, singola.getId()).stream()
                            .anyMatch(persona -> persona.utenteId().equals(mio)))
                    .toList();
        }

        // Le ore consumate di tutte le task in una query, non una per task:
        // sono una SUM su voce_dichiarazione, e con open-in-view disattivato
        // la variante ingenua costerebbe un'interrogazione per riga.
        Map<Long, BigDecimal> consumate = task.sommaOreConsumatePerTask(idLavoro).stream()
                .collect(Collectors.toMap(OrePerTask::getTaskId, OrePerTask::getOre));

        return elenco.stream()
                .map(singola -> RigaTask.da(singola,
                        consumate.getOrDefault(singola.getId(), BigDecimal.ZERO),
                        assegnatari(perTask, singola.getId())))
                .toList();
    }

    /**
     * Chi può essere assegnato alle task di questo lavoro: tutti gli utenti
     * attivi per chi ha {@code TASK_GESTISCI_TUTTI}, solo il proprio team per
     * chi ha {@code TASK_GESTISCI_PROPRI}.
     *
     * <p>Serve a riempire il menu dell'assegnazione. Senza, l'interfaccia
     * dovrebbe chiedere l'elenco completo degli utenti, che richiede
     * {@code UTENTI_VISUALIZZA} — un permesso che un PM normalmente non ha.
     */
    @Transactional(readOnly = true)
    public List<Assegnatario> assegnabili(UserDetails chiLoChiede, Long idLavoro) {
        Lavoro lavoro = lavori.lavoroVisibile(chiLoChiede, idLavoro);
        vietaSeNonPuoGestire(chiLoChiede, lavoro);

        List<Utente> candidati;
        if (Permessi.ha(chiLoChiede, GESTISCI_TUTTI)) {
            candidati = utenti.findAll();
        } else {
            Long mio = idDi(chiLoChiede);
            candidati = mio == null ? List.of() : utenti.trovaTeamDi(mio);
        }

        return candidati.stream()
                .map(utente -> new Assegnatario(utente.getId(), utente.getNome(),
                        utente.getCognome()))
                .toList();
    }

    @Transactional
    public RigaTask crea(UserDetails chiLoChiede, Long idLavoro, RichiestaNuovaTask richiesta) {
        Lavoro lavoro = lavori.lavoroVisibile(chiLoChiede, idLavoro);
        vietaSeNonPuoGestire(chiLoChiede, lavoro);

        if (lavoro.getStato() == StatoLavoro.CHIUSO
                || lavoro.getStato() == StatoLavoro.ANNULLATO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Il lavoro " + lavoro.getCodice() + " non è più in corso: "
                            + "non accetta nuove task.");
        }
        if (richiesta.scadenza().isBefore(richiesta.dataInizio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La scadenza non può precedere la data di inizio.");
        }

        Task creata = task.saveAndFlush(Task.nuova(lavoro, richiesta.titolo(),
                richiesta.descrizione(), richiesta.dataInizio(), richiesta.scadenza(),
                richiesta.stimaOre()));

        // Appena creata: nessuna ora allocata — gliene darà il PM dal pool — e
        // nessuna consumata, perché non c'è ancora nessuno assegnato.
        return RigaTask.da(creata, BigDecimal.ZERO, List.of());
    }

    @Transactional
    public void assegna(UserDetails chiLoChiede, Long idLavoro, Long idTask, Long utenteId) {
        Lavoro lavoro = lavori.lavoroVisibile(chiLoChiede, idLavoro);
        vietaSeNonPuoGestire(chiLoChiede, lavoro);
        Task destinazione = taskDelLavoro(idTask, idLavoro);

        Utente destinatario = utenti.findById(utenteId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Utente non trovato."));
        if (!destinatario.isAttivo()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "L'utenza di " + destinatario.getCognome() + " non è attiva.");
        }

        // TASK_GESTISCI_PROPRI dice «assegnarle al proprio team»: chi non ha
        // TUTTI può assegnare solo a chi gli fa capo in membro_team. Nota che
        // i team non hanno ancora un'interfaccia di gestione, quindi sono
        // quelli dei dati iniziali.
        if (!Permessi.ha(chiLoChiede, GESTISCI_TUTTI)) {
            Long mio = idDi(chiLoChiede);
            if (mio == null || !utenti.eNelTeam(mio, utenteId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        destinatario.getCognome() + " " + destinatario.getNome()
                                + " non fa parte del tuo team.");
            }
        }

        try {
            assegnazioni.saveAndFlush(
                    AssegnazioneTask.nuova(destinazione, destinatario, autore(chiLoChiede)));
        } catch (DataIntegrityViolationException eccezione) {
            // uk_assegnazione_attiva: una sola assegnazione attiva per persona
            // e task. E' il vincolo a garantirlo, non il controllo qui sopra.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    destinatario.getCognome() + " " + destinatario.getNome()
                            + " è già assegnata a questa task.");
        }
    }

    /**
     * Cambia lo stato di una task e, se la sta chiudendo, restituisce al pool
     * del lavoro le ore stanziate e non consumate.
     *
     * <p>Chiudere una task è il momento in cui il budget si libera: fino a
     * quando è aperta ha diritto alle ore che le sono state date, ma dopo non
     * consumerà più niente e tenerle bloccate le renderebbe inutilizzabili per
     * il resto del lavoro. Se invece ha <strong>sforato</strong> non si tocca
     * niente: lo sforamento resta registrato così com'è, perché è un fatto
     * accaduto e non un errore da correggere.
     *
     * <p>Completata e annullata sono stati <strong>definitivi</strong>, come
     * l'annullamento di un lavoro. Riaprire una task richiederebbe di
     * riallocarle le ore che la chiusura ha restituito al pool — e nel
     * frattempo quelle ore possono essere finite altrove, quindi la
     * riapertura non sarebbe nemmeno sempre possibile. Si crea una task nuova.
     *
     * <p>Le registrazioni di ore non vengono mai cancellate, in nessuno dei
     * due casi.
     */
    @Transactional
    public RigaTask cambiaStato(UserDetails chiLoChiede, Long idLavoro, Long idTask,
                                StatoTask nuovoStato) {
        Lavoro lavoro = lavori.lavoroVisibile(chiLoChiede, idLavoro);
        Task daCambiare = taskDelLavoro(idTask, idLavoro);
        vietaSeNonPuoCambiareStato(chiLoChiede, lavoro, idTask);

        StatoTask attuale = daCambiare.getStato();
        if (attuale == nuovoStato) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La task «" + daCambiare.getTitolo() + "» è già " + etichetta(nuovoStato)
                            + ".");
        }
        if (attuale == StatoTask.COMPLETATA || attuale == StatoTask.ANNULLATA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La task «" + daCambiare.getTitolo() + "» è " + etichetta(attuale)
                            + " e non si riapre: le sue ore residue sono già tornate nel pool "
                            + "del lavoro. Crea una task nuova.");
        }

        daCambiare.cambiaStato(nuovoStato);

        // Nella stessa transazione, quindi sotto lo stesso lock sulla riga del
        // lavoro che rilasciaResiduo acquisisce: le ore liberate non possono
        // essere lette a metà da un'allocazione concorrente.
        if (nuovoStato == StatoTask.COMPLETATA || nuovoStato == StatoTask.ANNULLATA) {
            ore.rilasciaResiduo(daCambiare, autore(chiLoChiede));
        }

        return RigaTask.da(daCambiare, task.sommaOreConsumateDellaTask(idTask),
                assegnatari(assegnatariPerTask(idLavoro), idTask));
    }

    /** Rimozione morbida: la riga resta, con chi e quando l'ha toltta. */
    @Transactional
    public void rimuoviAssegnazione(UserDetails chiLoChiede, Long idLavoro, Long idTask,
                                    Long utenteId) {
        Lavoro lavoro = lavori.lavoroVisibile(chiLoChiede, idLavoro);
        vietaSeNonPuoGestire(chiLoChiede, lavoro);
        taskDelLavoro(idTask, idLavoro);

        AssegnazioneTask assegnazione = assegnazioni
                .findByTaskIdAndUtenteIdAndRimossaIlIsNull(idTask, utenteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Questa persona non è assegnata alla task."));

        assegnazione.rimuovi(autore(chiLoChiede));
    }

    // --- regole -------------------------------------------------------------

    /**
     * Vede tutte le task del lavoro chi lo gestisce in ogni lavoro, chi ha il
     * permesso di sola lettura sul lavoro, e il responsabile che può gestire i
     * propri: non si può gestire ciò che non si vede.
     */
    private boolean vedeTutte(UserDetails chiLoChiede, Lavoro lavoro) {
        if (Permessi.ha(chiLoChiede, GESTISCI_TUTTI, VISUALIZZA_LAVORO)) {
            return true;
        }
        return Permessi.ha(chiLoChiede, GESTISCI_PROPRI)
                && lavori.eResponsabile(chiLoChiede, lavoro);
    }

    /**
     * Chi può cambiare lo stato di una task: chi la gestisce, e <em>chi ci
     * lavora</em>.
     *
     * <p>È la differenza fra questo controllo e
     * {@link #vietaSeNonPuoGestire}: {@code TASK_AGGIORNA_STATO} dice
     * «aggiornare lo stato delle task <strong>a cui è assegnato</strong>», e
     * quel limite va verificato qui. Senza, un dipendente con quel permesso
     * potrebbe chiudere le task di chiunque — e con esse liberare le ore di
     * lavori che non lo riguardano.
     */
    private void vietaSeNonPuoCambiareStato(UserDetails chiLoChiede, Lavoro lavoro, Long idTask) {
        if (Permessi.ha(chiLoChiede, GESTISCI_TUTTI)) {
            return;
        }
        if (Permessi.ha(chiLoChiede, GESTISCI_PROPRI)
                && lavori.eResponsabile(chiLoChiede, lavoro)) {
            return;
        }
        if (Permessi.ha(chiLoChiede, AGGIORNA_STATO)) {
            Long mio = idDi(chiLoChiede);
            if (mio != null && assegnazioni
                    .findByTaskIdAndUtenteIdAndRimossaIlIsNull(idTask, mio).isPresent()) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Puoi cambiare lo stato solo delle task a cui sei assegnato, oppure di quelle "
                        + "dei lavori di cui sei responsabile.");
    }

    /** Come si chiama uno stato nei messaggi d'errore. */
    private static String etichetta(StatoTask stato) {
        return switch (stato) {
            case DA_FARE -> "da fare";
            case IN_CORSO -> "in corso";
            case COMPLETATA -> "completata";
            case ANNULLATA -> "annullata";
        };
    }

    /**
     * Il {@code @PreAuthorize} sul controller verifica che uno dei due permessi
     * ci sia; qui si verifica la parte che un'annotazione non può esprimere,
     * cioè che chi ha solo {@code _PROPRI} stia agendo su un lavoro di cui è
     * responsabile.
     */
    private void vietaSeNonPuoGestire(UserDetails chiLoChiede, Lavoro lavoro) {
        if (Permessi.ha(chiLoChiede, GESTISCI_TUTTI)) {
            return;
        }
        if (Permessi.ha(chiLoChiede, GESTISCI_PROPRI)
                && lavori.eResponsabile(chiLoChiede, lavoro)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Per gestire le task del lavoro " + lavoro.getCodice() + " devi esserne il "
                        + "responsabile, oppure avere il permesso su tutti i lavori.");
    }

    // --- aiuti --------------------------------------------------------------

    /** La posizione di uno stato nell'ordine di {@link #PER_URGENZA}. */
    private static int posizione(StatoTask stato) {
        return switch (stato) {
            case IN_CORSO -> 0;
            case DA_FARE -> 1;
            case COMPLETATA -> 2;
            case ANNULLATA -> 3;
        };
    }

    private static Map<Long, BigDecimal> perTask(List<OrePerTask> righe) {
        return righe.stream().collect(
                Collectors.toMap(OrePerTask::getTaskId, OrePerTask::getOre));
    }

    /**
     * Aggiunge il segno al giorno, se quel giorno cade nella finestra chiesta.
     *
     * <p>Una task che inizia prima della finestra e scade dopo non lascia
     * nessun segno nel mese che sta attraversando, ed è giusto così: il
     * calendario dice che cosa <em>cade</em> in un giorno, non che cosa è in
     * corso — quello lo dice «Le mie task».
     */
    private static void segna(Map<LocalDate, List<SegnoTask>> perGiorno, Task singola,
                              LocalDate quando, TipoSegno tipo, LocalDate da, LocalDate a) {
        if (quando.isBefore(da) || quando.isAfter(a)) {
            return;
        }
        perGiorno.computeIfAbsent(quando, giorno -> new ArrayList<>())
                .add(SegnoTask.da(singola, tipo));
    }

    private Task taskDelLavoro(Long idTask, Long idLavoro) {
        return task.findByIdAndLavoroId(idTask, idLavoro).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Task non trovata in questo lavoro."));
    }

    private Map<Long, List<Assegnatario>> assegnatariPerTask(Long idLavoro) {
        return assegnazioni.trovaAttivePerLavoro(idLavoro).stream()
                .collect(Collectors.groupingBy(AssegnazioneDiTask::getTaskId,
                        Collectors.mapping(riga -> new Assegnatario(riga.getUtenteId(),
                                riga.getNome(), riga.getCognome()), Collectors.toList())));
    }

    private static List<Assegnatario> assegnatari(Map<Long, List<Assegnatario>> perTask,
                                                  Long idTask) {
        return perTask.getOrDefault(idTask, List.of());
    }

    private Long idDi(UserDetails chiLoChiede) {
        return utenti.findByEmail(chiLoChiede.getUsername()).map(Utente::getId).orElse(null);
    }

    private Utente autore(UserDetails chiLoChiede) {
        return utenti.findByEmail(chiLoChiede.getUsername()).orElseThrow(
                () -> new IllegalStateException(
                        "Utente collegato non trovato: " + chiLoChiede.getUsername()));
    }
}
