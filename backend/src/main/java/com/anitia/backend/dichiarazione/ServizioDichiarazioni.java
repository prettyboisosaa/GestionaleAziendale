package com.anitia.backend.dichiarazione;

import com.anitia.backend.dichiarazione.DichiarazioneRepository.OrePerGiorno;
import com.anitia.backend.assenza.ServizioAssenze;
import com.anitia.backend.impostazione.ServizioCalendario;
import com.anitia.backend.lavoro.Lavoro;
import com.anitia.backend.impostazione.ServizioImpostazioni;
import com.anitia.backend.sicurezza.Permessi;
import com.anitia.backend.task.StatoTask;
import com.anitia.backend.task.Task;
import com.anitia.backend.task.TaskRepository;
import com.anitia.backend.utente.Utente;
import com.anitia.backend.utente.UtenteRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * La dichiarazione giornaliera: dove i dipendenti registrano le ore lavorate.
 *
 * <p>Qui vivono tre regole del monte ore, e vale la pena dire perché sono
 * come sono.
 *
 * <p><strong>Si registra solo su task aperte a cui si è assegnati.</strong>
 * Senza il vincolo dell'assegnazione, le ore di una persona potrebbero finire
 * sul budget di un lavoro che non la riguarda; senza quello dello stato,
 * arriverebbero su task già chiuse, il cui residuo è tornato nel pool e magari
 * è già stato dato a qualcun altro.
 *
 * <p><strong>Il tetto giornaliero si verifica nella stessa transazione
 * dell'inserimento.</strong> È una somma sulle voci della dichiarazione del
 * giorno, e il vincolo {@code uk_dich_utente_data} garantisce che di
 * dichiarazioni per quel giorno ce ne sia una sola — altrimenti sommare le
 * voci di una direbbe che c'è spazio quando non ce n'è.
 *
 * <p><strong>Registrare oltre l'allocato è permesso.</strong> Non c'è nessun
 * controllo contro {@code task.ore_allocate}, e l'assenza è deliberata:
 * bloccare la registrazione non farebbe sparire il lavoro già fatto, lo
 * renderebbe soltanto invisibile. La task risulta in sforamento — un valore
 * calcolato, che compare da sé — e il PM la vede subito.
 */
@Service
public class ServizioDichiarazioni {

    private static final String VISUALIZZA_TUTTE = "DICHIARAZIONI_VISUALIZZA_TUTTE";
    private static final String VISUALIZZA_TEAM = "DICHIARAZIONI_VISUALIZZA_TEAM";

    /** Usati se il parametro corrispondente manca o è scritto male. */
    private static final BigDecimal ORE_MASSIME_DI_RIPIEGO = new BigDecimal("8.00");
    private static final int GIORNI_MODIFICA_DI_RIPIEGO = 1;

    private final DichiarazioneRepository dichiarazioni;
    private final TaskRepository task;
    private final UtenteRepository utenti;
    private final ServizioImpostazioni impostazioni;
    private final ServizioCalendario calendario;
    private final ServizioAssenze assenze;

    ServizioDichiarazioni(DichiarazioneRepository dichiarazioni, TaskRepository task,
                          UtenteRepository utenti, ServizioImpostazioni impostazioni,
                          ServizioCalendario calendario, ServizioAssenze assenze) {
        this.dichiarazioni = dichiarazioni;
        this.task = task;
        this.utenti = utenti;
        this.impostazioni = impostazioni;
        this.calendario = calendario;
        this.assenze = assenze;
    }

    /**
     * La mia giornata: quello che c'è scritto, più quello che serve a
     * scrivere.
     *
     * <p>Non crea niente se per quel giorno non c'è ancora nulla: una lettura
     * che lascia righe dietro di sé riempirebbe la tabella di dichiarazioni
     * vuote ogni volta che qualcuno apre il calendario. In quel caso
     * {@code Giornata.id} è nullo.
     */
    @Transactional(readOnly = true)
    public Giornata mia(UserDetails chiLoChiede, LocalDate data) {
        Utente io = utente(chiLoChiede);
        return giornata(io, data);
    }

    /**
     * Registra ore su una task, creando la dichiarazione del giorno se non
     * c'è ancora.
     */
    @Transactional
    public Giornata registraOre(UserDetails chiLoChiede, LocalDate data,
                                RichiestaVoce richiesta) {
        Utente io = utente(chiLoChiede);
        vietaSeGiornoNonScrivibile(data);

        Task destinazione = task.findById(richiesta.taskId()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task non trovata."));
        vietaSeNonPuoRegistrareSu(io, destinazione);

        Dichiarazione giorno = prendiOCrea(io, data);
        vietaSeNonModificabile(giorno);

        if (!giorno.getTipoGiornata().ammetteOre()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Il " + data + " è dichiarato come " + etichetta(giorno.getTipoGiornata())
                            + ": per registrare ore cambia prima il tipo di giornata.");
        }

        // Il tetto, verificato qui dentro la transazione che inserisce: fra il
        // controllo e la scrittura non c'e' spazio per una seconda voce.
        BigDecimal massime = oreMassime();
        BigDecimal dopo = giorno.oreTotali().add(richiesta.ore());
        if (dopo.compareTo(massime) > 0) {
            BigDecimal residue = massime.subtract(giorno.oreTotali());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Il " + data + " hai già registrato " + giorno.oreTotali() + " ore sulle "
                            + massime + " della giornata: puoi aggiungerne ancora " + residue
                            + ", non " + richiesta.ore() + ".");
        }

        giorno.aggiungi(VoceDichiarazione.nuova(giorno, destinazione, richiesta.ore(),
                richiesta.descrizione()));
        dichiarazioni.saveAndFlush(giorno);

        return giornata(io, data);
    }

    /** Corregge ore e descrizione di una voce già scritta. */
    @Transactional
    public Giornata correggiVoce(UserDetails chiLoChiede, LocalDate data, Long idVoce,
                                 RichiestaVoce richiesta) {
        Utente io = utente(chiLoChiede);
        Dichiarazione giorno = miaDelGiorno(io, data);
        vietaSeNonModificabile(giorno);

        VoceDichiarazione voce = giorno.getVoci().stream()
                .filter(singola -> singola.getId().equals(idVoce))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Voce non trovata in questa giornata."));

        // Il tetto va ricontrollato sulla differenza: correggere 2 ore in 6
        // puo' sfondare la giornata anche se la voce c'era gia'.
        BigDecimal massime = oreMassime();
        BigDecimal dopo = giorno.oreTotali().subtract(voce.getOre()).add(richiesta.ore());
        if (dopo.compareTo(massime) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Con questa correzione il " + data + " arriverebbe a " + dopo
                            + " ore, oltre le " + massime + " della giornata.");
        }

        voce.correggi(richiesta.ore(), richiesta.descrizione());
        dichiarazioni.saveAndFlush(giorno);

        return giornata(io, data);
    }

    /**
     * Toglie una voce.
     *
     * <p>È l'unica cancellazione fisica del gestionale, e si limita a questo:
     * una voce con le ore sbagliate è un errore di battitura, non storia da
     * conservare. Lo storico che conta — le ore allocate, i movimenti — non si
     * cancella mai.
     */
    @Transactional
    public Giornata togliVoce(UserDetails chiLoChiede, LocalDate data, Long idVoce) {
        Utente io = utente(chiLoChiede);
        Dichiarazione giorno = miaDelGiorno(io, data);
        vietaSeNonModificabile(giorno);

        if (!giorno.togli(idVoce)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Voce non trovata in questa giornata.");
        }
        dichiarazioni.saveAndFlush(giorno);

        return giornata(io, data);
    }

    /**
     * Cambia il tipo di giornata: lavorata, ferie, permesso, malattia.
     *
     * <p>Passare a un tipo non lavorato con delle ore già scritte viene
     * <strong>rifiutato</strong> invece di cancellarle in silenzio: sono il
     * lavoro di una giornata, e chi ha sbagliato il tipo deve poter decidere
     * lui di buttarle.
     */
    @Transactional
    public Giornata cambiaTipo(UserDetails chiLoChiede, LocalDate data, TipoGiornata nuovoTipo) {
        Utente io = utente(chiLoChiede);
        vietaSeGiornoNonScrivibile(data);

        Dichiarazione giorno = prendiOCrea(io, data);
        vietaSeNonModificabile(giorno);

        if (!nuovoTipo.ammetteOre() && giorno.oreTotali().signum() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Il " + data + " ha " + giorno.oreTotali() + " ore registrate: togli prima "
                            + "le voci, poi dichiara la giornata come "
                            + etichetta(nuovoTipo) + ".");
        }

        giorno.cambiaTipo(nuovoTipo);
        dichiarazioni.saveAndFlush(giorno);

        return giornata(io, data);
    }

    /**
     * Consegna la giornata.
     *
     * <p>Resta correggibile per i giorni lavorativi che dice il parametro
     * {@code dichiarazione.giorni_modifica}: l'invio è una consegna, non una
     * chiusura a chiave.
     */
    @Transactional
    public Giornata invia(UserDetails chiLoChiede, LocalDate data) {
        Utente io = utente(chiLoChiede);
        Dichiarazione giorno = miaDelGiorno(io, data);

        if (giorno.getStato() == StatoDichiarazione.INVIATA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La giornata del " + data + " è già stata inviata.");
        }
        if (giorno.getTipoGiornata().ammetteOre() && giorno.oreTotali().signum() == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La giornata del " + data + " non ha nessuna ora registrata: aggiungi "
                            + "almeno una voce, oppure dichiarala come ferie, permesso o "
                            + "malattia.");
        }

        giorno.invia();
        dichiarazioni.saveAndFlush(giorno);

        return giornata(io, data);
    }

    /**
     * Le mie giornate di un periodo, dalla più recente, con il totale di
     * ciascuna.
     */
    @Transactional(readOnly = true)
    public List<RigaGiorno> mieDelPeriodo(UserDetails chiLoChiede, LocalDate da, LocalDate a) {
        Utente io = utente(chiLoChiede);
        return periodoDi(io.getId(), da, a);
    }

    /**
     * Le giornate di un'altra persona.
     *
     * <p>{@code DICHIARAZIONI_VISUALIZZA_TUTTE} le vede tutte;
     * {@code DICHIARAZIONI_VISUALIZZA_TEAM} solo quelle dei dipendenti del
     * proprio team, e il limite va verificato qui perché dipende dai dati.
     */
    @Transactional(readOnly = true)
    public List<RigaGiorno> diUnaPersona(UserDetails chiLoChiede, Long utenteId, LocalDate da,
                                         LocalDate a) {
        if (!Permessi.ha(chiLoChiede, VISUALIZZA_TUTTE)) {
            Utente io = utente(chiLoChiede);
            boolean suo = Permessi.ha(chiLoChiede, VISUALIZZA_TEAM)
                    && utenti.eNelTeam(io.getId(), utenteId);
            if (!suo) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Puoi vedere solo le dichiarazioni dei dipendenti del tuo team.");
            }
        }
        return periodoDi(utenteId, da, a);
    }

    // --- regole -------------------------------------------------------------

    /**
     * Una giornata nel futuro non si dichiara: le ore si registrano su quello
     * che è già stato fatto.
     */
    private void vietaSeGiornoNonScrivibile(LocalDate data) {
        if (data.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Il " + data + " non è ancora arrivato: non si può dichiarare una "
                            + "giornata futura.");
        }
    }

    /**
     * Il limite del piano: «un dipendente registra ore solo sulle task aperte
     * a cui è assegnato».
     *
     * <p>L'assegnazione si controlla con una query nativa su
     * {@code assegnazione_task}, la stessa che riempie la tendina: così la
     * tendina non può proporre qualcosa che poi viene rifiutato.
     */
    private void vietaSeNonPuoRegistrareSu(Utente io, Task destinazione) {
        // 422 e non 409: la richiesta è ben formata, è il valore scelto che
        // non è selezionabile. Il codice in testa al messaggio è quello che
        // il frontend riconosce per mostrare l'avviso giusto senza dover
        // interpretare il testo.
        if (!destinazione.getStato().aperta()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "TASK_NON_SELEZIONABILE: la task «" + destinazione.getTitolo()
                            + "» è " + destinazione.getStato().etichetta()
                            + " e non accetta registrazioni di ore.");
        }

        // Anche il lavoro deve essere aperto: una task attiva dentro un
        // lavoro completato o annullato non è selezionabile. Senza questo
        // controllo, annullare un lavoro lascerebbe registrabili le sue task
        // che fossero rimaste aperte.
        Lavoro lavoro = destinazione.getLavoro();
        if (!lavoro.getStato().selezionabile()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "LAVORO_CHIUSO: il lavoro " + lavoro.getCodice() + " è "
                            + lavoro.getStato().etichetta()
                            + " e non accetta registrazioni di ore.");
        }

        boolean assegnata = task.trovaAperteAssegnateA(io.getId()).stream()
                .anyMatch(riga -> riga.getTaskId().equals(destinazione.getId()));
        if (!assegnata) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Non sei assegnato alla task «" + destinazione.getTitolo()
                            + "»: chiedi al responsabile del lavoro di assegnartela.");
        }
    }

    /**
     * La finestra di correzione. Una dichiarazione in bozza si modifica
     * sempre; una inviata, per i giorni lavorativi che dice il parametro.
     */
    private void vietaSeNonModificabile(Dichiarazione giorno) {
        if (!modificabile(giorno.getData(), giorno.getStato())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La giornata del " + giorno.getData() + " è stata inviata e il termine per "
                            + "correggerla è passato. Chiedi a un amministratore.");
        }
    }

    private boolean modificabile(LocalDate data, StatoDichiarazione stato) {
        if (data.isAfter(LocalDate.now())) {
            return false;
        }
        if (stato == StatoDichiarazione.BOZZA) {
            return true;
        }
        int giorni = impostazioni.intero(ServizioImpostazioni.GIORNI_MODIFICA,
                GIORNI_MODIFICA_DI_RIPIEGO);
        return calendario.giorniLavorativiDopo(data, LocalDate.now()) <= giorni;
    }

    private BigDecimal oreMassime() {
        return impostazioni.decimale(ServizioImpostazioni.ORE_MASSIME_GIORNO,
                ORE_MASSIME_DI_RIPIEGO);
    }

    // --- aiuti --------------------------------------------------------------

    private Giornata giornata(Utente io, LocalDate data) {
        BigDecimal massime = oreMassime();
        List<TaskDichiarabile> disponibili = task.trovaAperteAssegnateA(io.getId()).stream()
                .map(TaskDichiarabile::da)
                .toList();

        // Una lettura sola per tutta la giornata: se ne cava un avviso, non
        // un divieto, quindi non serve ripeterla a ogni scrittura.
        boolean assente = assenze.assenteIl(io.getId(), data);

        Optional<Dichiarazione> esistente = dichiarazioni.trovaDelGiorno(io.getId(), data);
        return esistente
                .map(giorno -> Giornata.da(giorno, massime,
                        modificabile(data, giorno.getStato()), assente, disponibili))
                .orElseGet(() -> Giornata.vuota(data, massime,
                        modificabile(data, StatoDichiarazione.BOZZA), assente, disponibili));
    }

    /**
     * La dichiarazione del giorno, creandola se non c'è.
     *
     * <p>La {@code DataIntegrityViolationException} su
     * {@code uk_dich_utente_data} si traduce in un 409 comprensibile: fra la
     * lettura e l'inserimento due richieste contemporanee dello stesso
     * dipendente possono entrambe trovare la giornata vuota, e la garanzia
     * vera è il vincolo — non il controllo qui sopra.
     */
    private Dichiarazione prendiOCrea(Utente io, LocalDate data) {
        return dichiarazioni.trovaDelGiorno(io.getId(), data).orElseGet(() -> {
            try {
                return dichiarazioni.saveAndFlush(Dichiarazione.nuova(io, data));
            } catch (DataIntegrityViolationException eccezione) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La giornata del " + data + " è stata aperta in un'altra finestra. "
                                + "Ricarica la pagina.");
            }
        });
    }

    private Dichiarazione miaDelGiorno(Utente io, LocalDate data) {
        return dichiarazioni.trovaDelGiorno(io.getId(), data).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Per il " + data + " non hai dichiarato niente."));
    }

    private List<RigaGiorno> periodoDi(Long utenteId, LocalDate da, LocalDate a) {
        if (a.isBefore(da)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La data finale non può precedere quella iniziale.");
        }

        // Le ore di tutte le giornate in una query, non una per giornata.
        Map<Long, BigDecimal> totali = dichiarazioni.sommaOrePerGiorno(utenteId, da, a).stream()
                .collect(Collectors.toMap(
                        OrePerGiorno::getDichiarazioneId,
                        OrePerGiorno::getOre));

        return dichiarazioni.trovaDelPeriodo(utenteId, da, a).stream()
                .map(giorno -> RigaGiorno.da(giorno,
                        totali.getOrDefault(giorno.getId(), BigDecimal.ZERO)))
                .toList();
    }

    private Utente utente(UserDetails chiLoChiede) {
        return utenti.findByEmail(chiLoChiede.getUsername()).orElseThrow(
                () -> new IllegalStateException(
                        "Utente collegato non trovato: " + chiLoChiede.getUsername()));
    }

    private static String etichetta(TipoGiornata tipo) {
        return switch (tipo) {
            case LAVORATA -> "giornata lavorata";
            case FERIE -> "ferie";
            case PERMESSO -> "permesso";
            case MALATTIA -> "malattia";
        };
    }
}
