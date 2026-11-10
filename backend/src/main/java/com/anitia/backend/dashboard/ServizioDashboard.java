package com.anitia.backend.dashboard;

import com.anitia.backend.assenza.Assenza;
import com.anitia.backend.assenza.AssenzaRepository;
import com.anitia.backend.assenza.DettagliAssenza;
import com.anitia.backend.assenza.StatoAssenza;
import com.anitia.backend.impostazione.Festivita;
import com.anitia.backend.impostazione.FestivitaRepository;
import com.anitia.backend.task.MiaTask;
import com.anitia.backend.task.ServizioTask;
import com.anitia.backend.task.StatoTask;
import com.anitia.backend.task.TaskImportanza;
import com.anitia.backend.utente.Utente;
import com.anitia.backend.utente.UtenteRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La pagina di atterraggio del dipendente: cosa c'è da fare oggi e cosa sta
 * per scappare.
 *
 * <p>Questo servizio non legge niente per conto proprio che qualcun altro
 * sappia già leggere: le task gliele dà {@link ServizioTask#mie}, le assenze
 * il loro repository, le festività il loro. È una composizione, e di proposito:
 * la regola di «quali sono le mie task» è già scritta una volta, e riscriverla
 * qui vorrebbe dire due definizioni da tenere allineate.
 *
 * <p>Nessun controllo di permesso oltre a quello delle rotte: tutte le letture
 * sono filtrate sull'id di chi chiede, quindi non c'è modo di farle parlare
 * delle cose di un altro.
 */
@Service
public class ServizioDashboard {

    private final ServizioTask task;
    private final AssenzaRepository assenze;
    private final FestivitaRepository festivita;
    private final UtenteRepository utenti;

    ServizioDashboard(ServizioTask task, AssenzaRepository assenze,
                      FestivitaRepository festivita, UtenteRepository utenti) {
        this.task = task;
        this.assenze = assenze;
        this.festivita = festivita;
        this.utenti = utenti;
    }

    /**
     * I conteggi delle card e le task che li compongono, in una risposta sola.
     *
     * <p>Una data sola per tutta la risposta: «scade oggi» e «scaduta» di righe
     * diverse non devono poter essere calcolati a cavallo della mezzanotte e
     * risultare in disaccordo fra loro. È la stessa cura che prende
     * {@link ServizioTask#mie}.
     */
    @Transactional(readOnly = true)
    public RiepilogoDashboard riepilogo(UserDetails chiLoChiede) {
        List<MiaTask> mie = task.mie(chiLoChiede);
        LocalDate oggi = LocalDate.now();

        // La domenica di questa settimana: LocalDate.with(DayOfWeek) si muove
        // dentro la settimana ISO, che va da lunedi' a domenica. Di domenica
        // restituisce oggi stesso, quindi il conteggio settimanale coincide
        // con quello di oggi: e' corretto, la settimana finisce li'.
        LocalDate fineSettimana = oggi.with(DayOfWeek.SUNDAY);

        ContatoriDashboard contatori = new ContatoriDashboard(
                // Urgenti al posto di scadute: il ritardo lo dicono già la
                // lista («in ritardo di tre giorni») e il calendario, mentre
                // l'urgenza decisa dal PM non si vedeva da nessuna parte.
                conta(mie, riga -> aperta(riga)
                        && riga.urgenzaTask() == TaskImportanza.CRITICAL),
                conta(mie, riga -> aperta(riga) && riga.scadenza().isEqual(oggi)),
                conta(mie, riga -> aperta(riga)
                        && !riga.scadenza().isBefore(oggi)
                        && !riga.scadenza().isAfter(fineSettimana)),
                conta(mie, riga -> riga.stato() == StatoTask.IN_CORSO));

        return new RiepilogoDashboard(contatori, mie, inConflitto(chiLoChiede, mie));
    }

    /**
     * Gli eventi dell'intervallo visibile, normalizzati: task, assenze e
     * festività con la stessa forma.
     *
     * <p>Il filtro sulle date delle task è in memoria e non in una query
     * dedicata, per la stessa ragione per cui lo è in
     * {@code ServizioTask.segniDelPeriodo}: la lettura è già quella di «le mie
     * task» — poche righe, quante le task di una persona — e una query con un
     * {@code BETWEEN} sarebbe una seconda definizione di «le mie task» da
     * tenere allineata alla prima.
     */
    @Transactional(readOnly = true)
    public List<EventoCalendario> calendario(UserDetails chiLoChiede, LocalDate da, LocalDate a) {
        if (a.isBefore(da)) {
            LocalDate scambio = da;
            da = a;
            a = scambio;
        }

        Long mio = idDi(chiLoChiede);
        List<EventoCalendario> eventi = new ArrayList<>();

        // Le assenze servono due volte: come eventi propri, e per sapere quali
        // scadenze cadono in un giorno di ferie. Si leggono una volta sola.
        List<Assenza> impegni = mio == null
                ? List.of()
                : assenze.trovaSovrapposte(mio, da, a, null);

        for (MiaTask riga : task.mie(chiLoChiede)) {
            // Le task annullate non compaiono mai, nemmeno nei giorni passati:
            // annullare un lavoro annulla le sue task, quindi questo filtro
            // toglie dal calendario anche i lavori che non si faranno.
            if (riga.stato() == StatoTask.ANNULLATO) {
                continue;
            }

            // Una task APERTA e' una barra dal suo inizio alla scadenza; una
            // chiusa e' un segno sul giorno in cui scadeva. E' la differenza
            // fra «questo e' da fare, in questo periodo» e «questo e' andato
            // cosi'».
            //
            // Il discrimine era IN_CORSO, e non andava: IN_CORSO si diventa
            // premendo avvia, mentre una task appena assegnata nasce ATTIVO.
            // Una task ATTIVO cominciata il 10 e in scadenza il 28 veniva
            // collocata solo il 28, quindi chi la deve fare non la vedeva
            // nella settimana in cui la deve fare — compariva nel calendario
            // tre settimane dopo. Proprio il lavoro appena affidato, che e' la
            // cosa che il calendario esiste per mostrare, era l'unico
            // invisibile.
            //
            // Chiedere al dipendente di premere avvia per vedere il proprio
            // lavoro sul calendario sarebbe poi l'adempimento che il
            // tracciamento automatico ha tolto: il timer misura le ore, non
            // dichiara che una task esiste.
            LocalDate inizio = riga.stato().aperta()
                    ? riga.dataInizio()
                    : riga.scadenza();
            LocalDate fine = riga.scadenza();

            if (inizio.isAfter(a) || fine.isBefore(da)) {
                continue;
            }

            eventi.add(EventoCalendario.task(riga.taskId(), riga.titolo(), inizio, fine,
                    riga.stato().name(), copertaDaFerie(impegni, riga.scadenza()),
                    riga.lavoroId()));
        }

        for (Assenza assenza : impegni) {
            eventi.add(EventoCalendario.assenza(assenza.getId(), titolo(assenza),
                    assenza.getDataInizio(), assenza.getDataFine(),
                    assenza.getStato().name()));
        }

        for (Festivita chiusura : festivita.trovaTra(da, a)) {
            eventi.add(EventoCalendario.festivita(chiusura.getData(), chiusura.getDescrizione()));
        }

        return eventi;
    }

    // --- aiuti --------------------------------------------------------------

    /**
     * Gli id delle task aperte che scadono in un giorno di assenza
     * <strong>approvata</strong>.
     *
     * <p>Solo approvate: una richiesta ancora in attesa potrebbe essere
     * rifiutata, e segnalare un conflitto che forse non ci sarà farebbe
     * spostare una scadenza per niente.
     */
    private List<Long> inConflitto(UserDetails chiLoChiede, List<MiaTask> mie) {
        List<MiaTask> aperte = mie.stream().filter(ServizioDashboard::aperta).toList();
        Long mio = idDi(chiLoChiede);
        if (aperte.isEmpty() || mio == null) {
            return List.of();
        }

        // Una lettura sola, sull'intervallo che copre tutte le scadenze
        // aperte, invece di una per task.
        LocalDate prima = aperte.stream().map(MiaTask::scadenza).min(LocalDate::compareTo)
                .orElseThrow();
        LocalDate ultima = aperte.stream().map(MiaTask::scadenza).max(LocalDate::compareTo)
                .orElseThrow();
        List<Assenza> ferie = assenze.trovaApprovateDelPeriodo(mio, prima, ultima);
        if (ferie.isEmpty()) {
            return List.of();
        }

        return aperte.stream()
                .filter(riga -> copre(ferie, riga.scadenza()))
                .map(MiaTask::taskId)
                .toList();
    }

    /** Vero se fra gli impegni ce n'è uno approvato che copre quel giorno. */
    private static boolean copertaDaFerie(List<Assenza> impegni, LocalDate giorno) {
        return impegni.stream()
                .filter(assenza -> assenza.getStato() == StatoAssenza.APPROVATA)
                .anyMatch(assenza -> copre(assenza, giorno));
    }

    private static boolean copre(List<Assenza> assenze, LocalDate giorno) {
        return assenze.stream().anyMatch(assenza -> copre(assenza, giorno));
    }

    private static boolean copre(Assenza assenza, LocalDate giorno) {
        return !giorno.isBefore(assenza.getDataInizio())
                && !giorno.isAfter(assenza.getDataFine());
    }

    /** «Ferie», oppure «Permesso retribuito · Visita medica». */
    private static String titolo(Assenza assenza) {
        DettagliAssenza dettagli = assenza.getDettagli();
        if (dettagli == null || dettagli.motivo() == null) {
            return assenza.getTipo().etichetta();
        }
        return assenza.getTipo().etichetta() + " · " + dettagli.motivo().etichetta();
    }

    private static boolean aperta(MiaTask riga) {
        return riga.stato().aperta();
    }

    private static long conta(List<MiaTask> mie, Predicate<MiaTask> regola) {
        return mie.stream().filter(regola).count();
    }

    private Long idDi(UserDetails chiLoChiede) {
        return utenti.findByEmail(chiLoChiede.getUsername()).map(Utente::getId).orElse(null);
    }
}
