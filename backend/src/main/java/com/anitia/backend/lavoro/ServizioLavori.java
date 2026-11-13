package com.anitia.backend.lavoro;

import com.anitia.backend.ore.MovimentoOre;
import com.anitia.backend.ore.MovimentoOreRepository;
import com.anitia.backend.ore.RigaMovimento;
import com.anitia.backend.sicurezza.Permessi;
import com.anitia.backend.task.TaskRepository;
import com.anitia.backend.tempo.SessioneTaskRepository;
import com.anitia.backend.task.TaskRepository.ContiTask;
import com.anitia.backend.task.TaskRepository.OrePerLavoro;
import com.anitia.backend.utente.Utente;
import com.anitia.backend.utente.UtenteRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServizioLavori {

    /**
     * Il permesso di chi è responsabile di un lavoro: gli lascia modificare e
     * annullare i <em>propri</em> lavori, ed è anche il requisito per poter
     * essere nominato responsabile. Due effetti dalla stessa casella, come
     * dice la descrizione nel catalogo.
     */
    static final String PERMESSO_RESPONSABILE = "LAVORI_MODIFICA_PROPRI";

    private static final String MODIFICA_TUTTI = "LAVORI_MODIFICA_TUTTI";
    private static final String VISUALIZZA_TUTTI = "LAVORI_VISUALIZZA_TUTTI";
    private static final String VISUALIZZA_PROPRI = "LAVORI_VISUALIZZA_PROPRI";
    private static final String MONTE_GESTISCI = "ORE_MONTE_GESTISCI";

    private final LavoroRepository lavori;
    private final UtenteRepository utenti;
    private final TaskRepository task;
    private final MovimentoOreRepository movimenti;
    private final SessioneTaskRepository sessioni;

    ServizioLavori(LavoroRepository lavori, UtenteRepository utenti, TaskRepository task,
                   MovimentoOreRepository movimenti, SessioneTaskRepository sessioni) {
        this.lavori = lavori;
        this.utenti = utenti;
        this.task = task;
        this.movimenti = movimenti;
        this.sessioni = sessioni;
    }

    /**
     * I lavori che chi chiede ha diritto di vedere: tutti con
     * {@code LAVORI_VISUALIZZA_TUTTI}, altrimenti quelli di cui è
     * responsabile.
     *
     * <p>«Propri» vuol dire quello che dice la descrizione del permesso: i
     * lavori di cui è responsabile <strong>o in cui ha task assegnate</strong>.
     * Si raccolgono prima gli id dalle due fonti e poi si caricano i lavori in
     * una volta, così il responsabile arriva con un {@code JOIN FETCH} e non
     * una query per riga.
     */
    @Transactional(readOnly = true)
    public List<RigaLavoro> elenco(UserDetails chiLoChiede, boolean includiAnnullati) {
        if (Permessi.ha(chiLoChiede, VISUALIZZA_TUTTI)) {
            return conOre(includiAnnullati
                    ? lavori.trovaTutti()
                    : lavori.trovaTuttiNonAnnullati());
        }

        Optional<Utente> utente = utenti.findByEmail(chiLoChiede.getUsername());
        if (utente.isEmpty()) {
            return List.of();
        }

        Set<Long> propri = idPropri(utente.get().getId());

        // IN con una lista vuota non e' SQL valido.
        if (propri.isEmpty()) {
            return List.of();
        }

        return conOre(includiAnnullati
                ? lavori.trovaConId(propri)
                : lavori.trovaConIdNonAnnullati(propri));
    }

    /**
     * Attacca a ciascun lavoro la situazione delle sue ore.
     *
     * <p>Tre query per tutto l'elenco, non tre per riga: con
     * {@code open-in-view} disattivato e una ventina di lavori, la variante
     * ingenua sarebbe sessanta interrogazioni per una schermata sola.
     *
     * <p>La terza sono i conteggi delle task, che la scheda di ogni lavoro
     * mostra: quante ne ha, quante ne ha chiuse, quante sono urgenti o in
     * ritardo. Costano un raggruppamento sulla stessa tabella da cui vengono
     * già le ore allocate, e senza di loro una scheda dovrebbe chiedere le
     * task di quel lavoro per contarle — cioè una richiesta per riquadro.
     */
    private List<RigaLavoro> conOre(List<Lavoro> elenco) {
        if (elenco.isEmpty()) {
            return List.of();
        }

        List<Long> id = elenco.stream().map(Lavoro::getId).toList();
        Map<Long, BigDecimal> allocate = perLavoro(task.sommaOreAllocatePerLavoro(id));
        Map<Long, BigDecimal> consumate = perLavoro(task.sommaOreConsumatePerLavoro(id));
        Map<Long, ContiTask> conti = task.contiTaskPerLavoro(id).stream()
                .collect(Collectors.toMap(ContiTask::getLavoroId, riga -> riga));

        return elenco.stream()
                .map(lavoro -> RigaLavoro.da(lavoro,
                        ore(allocate, lavoro.getId()),
                        ore(consumate, lavoro.getId()),
                        // Null quando il lavoro non ha nessuna task: il
                        // raggruppamento omette le righe vuote, e `RigaLavoro`
                        // legge quel null come quattro zeri.
                        conti.get(lavoro.getId())))
                .toList();
    }

    private static Map<Long, BigDecimal> perLavoro(Collection<OrePerLavoro> righe) {
        return righe.stream().collect(
                Collectors.toMap(OrePerLavoro::getLavoroId, OrePerLavoro::getOre));
    }

    /**
     * Zero e non null quando il lavoro non compare fra i risultati: un lavoro
     * senza task ha davvero zero ore allocate, e le somme raggruppate
     * omettono le righe vuote.
     */
    private static BigDecimal ore(Map<Long, BigDecimal> somme, Long idLavoro) {
        return somme.getOrDefault(idLavoro, BigDecimal.ZERO);
    }

    /**
     * Il lavoro, se chi chiede ha diritto di vederlo: 404 se non esiste, 403
     * se non gli compete.
     *
     * <p>È la stessa regola di {@link #elenco(UserDetails)}, estratta qui
     * perché la usa anche il servizio delle task: duplicarla vorrebbe dire
     * poterla cambiare in un posto e non nell'altro.
     */
    @Transactional(readOnly = true)
    public Lavoro lavoroVisibile(UserDetails chiLoChiede, Long idLavoro) {
        Lavoro lavoro = trova(idLavoro);

        if (Permessi.ha(chiLoChiede, VISUALIZZA_TUTTI)) {
            return lavoro;
        }

        boolean suo = Permessi.ha(chiLoChiede, VISUALIZZA_PROPRI)
                && utenti.findByEmail(chiLoChiede.getUsername())
                        .map(utente -> idPropri(utente.getId()).contains(idLavoro))
                        .orElse(false);

        if (!suo) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Questo lavoro non ti compete.");
        }
        return lavoro;
    }

    @Transactional(readOnly = true)
    public RigaLavoro dettaglio(UserDetails chiLoChiede, Long idLavoro) {
        return conOre(lavoroVisibile(chiLoChiede, idLavoro));
    }

    /**
     * Vero se chi chiede è il responsabile del lavoro, cioè il suo PM.
     *
     * <p>Sta qui, e non ricopiata nei servizi che la usano, perché è la
     * seconda metà di tre permessi diversi — {@code LAVORI_MODIFICA_PROPRI},
     * {@code TASK_GESTISCI_PROPRI}, {@code ORE_ALLOCA_PROPRI} — e tre copie
     * della stessa regola di sicurezza sono tre occasioni di correggerne una
     * sola.
     *
     * <p>Il confronto è sull'email e non sull'id perché è l'unica cosa che lo
     * {@link UserDetails} porta con sé: risalire all'id costerebbe una query
     * a ogni controllo.
     */
    public boolean eResponsabile(UserDetails chiLoChiede, Lavoro lavoro) {
        Utente responsabile = lavoro.getResponsabile();
        return responsabile != null
                && responsabile.getEmail().equalsIgnoreCase(chiLoChiede.getUsername());
    }

    /**
     * Lo storico delle ore del lavoro: ogni variazione con autore, data e
     * motivo.
     *
     * <p>Per leggerlo basta poter vedere il lavoro. Non c'è un permesso
     * dedicato di sola lettura sulle ore, e non è una dimenticanza: andrebbe
     * concesso a tutti quelli che vedono i lavori, cioè non distinguerebbe
     * niente.
     */
    @Transactional(readOnly = true)
    public List<RigaMovimento> storicoMovimenti(UserDetails chiLoChiede, Long idLavoro) {
        lavoroVisibile(chiLoChiede, idLavoro);
        return movimenti.trovaDelLavoro(idLavoro).stream().map(RigaMovimento::da).toList();
    }

    /**
     * Cambia il monte ore del lavoro, registrando il movimento che spiega la
     * variazione.
     *
     * <p>Il monte ore <strong>non può scendere sotto la somma delle ore già
     * allocate</strong>: quelle ore sono impegni presi su task esistenti, e
     * tagliarle qui le farebbe sparire senza che nessuno chiuda o riduca
     * niente. Chi vuole ridurre il budget deve prima liberare le task.
     *
     * <p>La riga del lavoro resta bloccata per tutta la transazione: fra la
     * lettura della somma allocata e la scrittura del nuovo monte ore ci
     * starebbe altrimenti un'allocazione di qualcun altro, e il controllo
     * risulterebbe superato per un valore che nel frattempo è cambiato.
     */
    @Transactional
    public RigaLavoro cambiaMonteOre(UserDetails chiLoChiede, Long idLavoro,
                                     BigDecimal nuovoValore, String motivo) {
        // Il permesso di vedere il lavoro prima di tutto: senza, l'esistenza
        // stessa del lavoro non va rivelata.
        lavoroVisibile(chiLoChiede, idLavoro);

        Lavoro lavoro = lavori.bloccaPerAggiornamento(idLavoro).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lavoro non trovato."));

        BigDecimal allocate = task.sommaOreAllocate(idLavoro);
        if (nuovoValore.compareTo(allocate) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Il lavoro " + lavoro.getCodice() + " ha già " + allocate
                            + " ore allocate alle task: il monte ore non può scendere a "
                            + nuovoValore + ". Chiudi o riduci delle task, poi riprova.");
        }

        BigDecimal precedente = lavoro.getMonteOre();
        if (precedente != null && precedente.compareTo(nuovoValore) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Il monte ore del lavoro " + lavoro.getCodice() + " è già " + nuovoValore
                            + ".");
        }

        Utente autore = autore(chiLoChiede);
        lavoro.cambiaMonteOre(nuovoValore);

        // Un lavoro che non aveva budget non sta subendo una modifica: il suo
        // monte ore viene fissato adesso, e questo e' il movimento da cui si
        // rileggera' la stima iniziale.
        movimenti.save(precedente == null
                ? MovimentoOre.monteIniziale(lavoro, nuovoValore, motivo, autore)
                : MovimentoOre.modificaMonte(lavoro, nuovoValore.subtract(precedente), motivo,
                        autore, null));

        return conOre(lavoro);
    }

    /**
     * La situazione di un lavoro solo: le sue due somme di ore e i conteggi
     * delle sue task.
     *
     * <p>I conteggi si chiedono con la stessa query dell'elenco, su una
     * collezione di un elemento: una seconda query che contasse le task di un
     * lavoro solo direbbe le stesse cose con un'altra {@code WHERE}, e sarebbe
     * il secondo posto da correggere il giorno in cui «in ritardo» cambia
     * definizione.
     */
    private RigaLavoro conOre(Lavoro lavoro) {
        return RigaLavoro.da(lavoro,
                task.sommaOreAllocate(lavoro.getId()),
                task.sommaOreConsumate(lavoro.getId()),
                task.contiTaskPerLavoro(List.of(lavoro.getId())).stream().findFirst()
                        .orElse(null));
    }

    /**
     * Gli id dei lavori «propri»: quelli di cui è responsabile e quelli in cui
     * ha task assegnate, come dice la descrizione del permesso.
     */
    private Set<Long> idPropri(Long utenteId) {
        Set<Long> propri = new LinkedHashSet<>(lavori.trovaIdDiResponsabile(utenteId));
        propri.addAll(lavori.trovaIdConTaskAssegnate(utenteId));
        return propri;
    }

    @Transactional(readOnly = true)
    public List<CandidatoResponsabile> responsabiliPossibili() {
        return utenti.trovaConPermesso(PERMESSO_RESPONSABILE).stream()
                .map(CandidatoResponsabile::da)
                .toList();
    }

    /**
     * Annulla un lavoro: non si farà, ma la riga resta. È l'operazione da
     * usare su qualunque lavoro che abbia già delle task, perché cancellarlo
     * porterebbe via anche il tempo registrato sulle sue task.
     *
     * <p>Il {@code @PreAuthorize} sul controller verifica che uno dei due
     * permessi ci sia; qui si verifica la parte che un'annotazione non può
     * esprimere, cioè che chi ha solo {@code LAVORI_MODIFICA_PROPRI} stia
     * annullando un lavoro di cui è responsabile.
     *
     * <p>Il controllo del permesso viene <strong>prima</strong> di quelli
     * sullo stato: a un estraneo si risponde 403, non un 409 che gli
     * racconterebbe in che stato si trova un lavoro che non gli compete.
     */
    @Transactional
    public RigaLavoro annulla(UserDetails chiLoChiede, Long idLavoro) {
        Lavoro lavoro = trova(idLavoro);

        if (!Permessi.ha(chiLoChiede, MODIFICA_TUTTI) && !eResponsabile(chiLoChiede, lavoro)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Per annullare il lavoro " + lavoro.getCodice() + " devi esserne il "
                            + "responsabile, oppure avere il permesso su tutti i lavori.");
        }

        if (lavoro.getStato() == StatoLavoro.ANNULLATO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Il lavoro " + lavoro.getCodice() + " è già annullato.");
        }
        if (lavoro.getStato() == StatoLavoro.COMPLETATO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Il lavoro " + lavoro.getCodice() + " è concluso: un lavoro completato "
                            + "non si annulla.");
        }

        lavoro.annulla();
        // Nessun save(): l'entità è gestita dalla transazione, e Hibernate
        // scrive l'UPDATE al commit. La colonna versione protegge da due
        // modifiche contemporanee.

        // La risposta si costruisce PRIMA dell'aggiornamento in blocco qui
        // sotto, che svuota il contesto di persistenza e lascerebbe il lavoro
        // staccato: leggerne il responsabile, che è pigro, fallirebbe. Le due
        // somme non cambiano comunque, perché annullare le task ne toccherà
        // lo stato e non le ore.
        RigaLavoro risposta = conOre(lavoro);

        // Tutte le task del lavoro diventano annullate, comprese quelle
        // completate: un lavoro che non si farà non lascia dietro di sé task
        // ancora aperte, né risultati da esibire. Le ore già registrate dai
        // dipendenti restano dove sono, nello storico e nei report, perché
        // sono lavoro svolto davvero.
        //
        // Le ore allocate e non consumate NON tornano nel pool: il monte ore
        // libero di un lavoro annullato è un numero senza significato, e
        // restituirlo lo farebbe sembrare disponibile per altro.
        task.annullaTutteDelLavoro(idLavoro);

        // E con loro si chiudono le sessioni ancora aperte. Senza questa riga
        // resterebbero aperte per sempre: annullaTutteDelLavoro e' una UPDATE
        // in blocco e scavalca ServizioTask.cambiaStato, che e' il posto in cui
        // normalmente una sessione viene chiusa. E una sessione aperta su una
        // task annullata non e' solo una riga sbagliata: il vincolo di unicita'
        // impedirebbe a quella persona di avviarne un'altra, su qualunque task.
        // L'istante lo decide la JVM, lo stesso orologio che ha scritto
        // `avviata_il`: la durata che ne esce e' la distanza fra due letture
        // dello stesso orologio. Vedi chiudiTutteDelLavoro.
        sessioni.chiudiTutteDelLavoro(idLavoro, LocalDateTime.now());

        return risposta;
    }

    /**
     * Cancella davvero il lavoro, ma solo se è una bozza senza task: è
     * l'unico caso in cui nessuna altra riga lo referenzia, e serve a
     * correggere una bozza creata per errore. In tutti gli altri casi la
     * strada è {@link #annulla(Long)}.
     */
    @Transactional
    public void elimina(Long idLavoro) {
        Lavoro lavoro = trova(idLavoro);

        if (lavoro.getStato() != StatoLavoro.BOZZA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Si può cancellare solo un lavoro in bozza. Il lavoro "
                            + lavoro.getCodice() + " va annullato.");
        }

        long quante = task.countByLavoroId(idLavoro);
        if (quante > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Il lavoro " + lavoro.getCodice() + " ha " + quante
                            + " task e non può essere cancellato: annullalo, così lo storico "
                            + "delle assegnazioni e del tempo lavorato resta.");
        }

        lavori.delete(lavoro);
    }

    private Lavoro trova(Long idLavoro) {
        return lavori.findById(idLavoro).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Lavoro non trovato."));
    }

    private Utente autore(UserDetails chiLoChiede) {
        return utenti.findByEmail(chiLoChiede.getUsername()).orElseThrow(
                () -> new IllegalStateException(
                        "Utente collegato non trovato: " + chiLoChiede.getUsername()));
    }

    @Transactional
    public RigaLavoro crea(UserDetails chiLoChiede, RichiestaNuovoLavoro richiesta) {
        // I due vincoli del database, ricontrollati qui solo per poter dire
        // cosa non va: ck_lavoro_date e ck_lavoro_stato.
        if (richiesta.dataFinePrevista().isBefore(richiesta.dataInizio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La data di fine prevista non può precedere quella di inizio.");
        }
        if (richiesta.stato() == StatoLavoro.COMPLETATO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un lavoro non può essere creato già chiuso.");
        }
        // Creare un lavoro e fissarne il budget sono due poteri distinti: il
        // @PreAuthorize sulla rotta verifica il primo, questo il secondo.
        // Senza il controllo, chi può creare lavori deciderebbe anche quante
        // ore costano.
        if (richiesta.monteOre() != null && !Permessi.ha(chiLoChiede, MONTE_GESTISCI)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Non hai il permesso di fissare il monte ore: crea il lavoro senza, "
                            + "e lo imposterà un amministratore.");
        }

        Utente responsabile = scegliResponsabile(richiesta);
        String emailAutore = chiLoChiede.getUsername();
        Utente autore = utenti.findByEmail(emailAutore).orElseThrow(
                () -> new IllegalStateException("Utente collegato non trovato: " + emailAutore));

        String codice = "LAV-%03d".formatted(lavori.ultimoProgressivo() + 1);

        try {
            Lavoro creato = lavori.saveAndFlush(Lavoro.nuovo(codice, richiesta.nome(),
                    richiesta.descrizione(), richiesta.dataInizio(),
                    richiesta.dataFinePrevista(), richiesta.stato(), richiesta.monteOre(),
                    responsabile, autore));

            // Il monte ore iniziale va registrato come movimento, altrimenti
            // la prima modifica dell'admin lo sovrascriverebbe senza lasciare
            // modo di risalire alla stima di partenza — che e' proprio il
            // confronto che serve a fine lavoro.
            if (richiesta.monteOre() != null && richiesta.monteOre().signum() > 0) {
                movimenti.save(MovimentoOre.monteIniziale(creato, richiesta.monteOre(),
                        "Monte ore fissato alla creazione del lavoro", autore));
            }

            // Appena creato: nessuna task, quindi nessuna ora allocata o
            // consumata e nessun conteggio. Le query di conOre darebbero zero
            // su tutto, e si evitano.
            return RigaLavoro.da(creato, BigDecimal.ZERO, BigDecimal.ZERO, null);
        } catch (DataIntegrityViolationException eccezione) {
            // uk_lavoro_codice. Fra il calcolo del progressivo e questo INSERT
            // due creazioni contemporanee possono scegliere lo stesso codice,
            // e la garanzia vera e' il vincolo. Ritentare qui non si puo': la
            // transazione e' gia' segnata per il rollback. Se un giorno
            // servisse, la via e' una tabella di contatori.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Il codice " + codice + " è appena stato assegnato a un altro lavoro. Riprova.");
        }
    }

    /**
     * Il responsabile scelto, se è fra quelli ammessi. Nullo solo per una
     * bozza senza responsabile.
     */
    private Utente scegliResponsabile(RichiestaNuovoLavoro richiesta) {
        if (richiesta.responsabileId() == null) {
            if (richiesta.stato() == StatoLavoro.ATTIVO) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Un lavoro attivo deve avere un responsabile: scegline uno, "
                                + "oppure salvalo come bozza.");
            }
            return null;
        }

        return utenti.trovaConPermesso(PERMESSO_RESPONSABILE).stream()
                .filter(utente -> utente.getId().equals(richiesta.responsabileId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "L'utente scelto non può essere responsabile di un lavoro: "
                                + "non ne ha il permesso, oppure non è attivo."));
    }
}
