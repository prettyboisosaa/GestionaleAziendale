package com.anitia.backend.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.anitia.backend.lavoro.Lavoro;
import com.anitia.backend.lavoro.LavoroRepository;
import com.anitia.backend.lavoro.StatoLavoro;
import com.anitia.backend.task.AssegnazioneTask;
import com.anitia.backend.task.AssegnazioneTaskRepository;
import com.anitia.backend.task.StatoTask;
import com.anitia.backend.task.TaskImportanza;
import com.anitia.backend.task.Task;
import com.anitia.backend.task.TaskRepository;
import com.anitia.backend.utente.Utente;
import com.anitia.backend.utente.UtenteRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;

/**
 * Che cosa il calendario della dashboard mostra, e dove.
 *
 * <p>Nasce da un difetto preciso. Il calendario collocava come barra dal
 * proprio inizio alla scadenza soltanto le task {@code IN_CORSO}, e tutte le
 * altre come un segno sul giorno di scadenza. Ma {@code IN_CORSO} si diventa
 * <strong>premendo avvia</strong>: una task appena affidata nasce
 * {@code ATTIVO}, quindi una task cominciata il 10 e in scadenza il 28
 * compariva solo nella settimana del 28. Chi la doveva fare non la vedeva
 * nella settimana in cui la doveva fare — e il lavoro appena affidato è
 * esattamente la cosa che un calendario esiste per mostrare.
 *
 * <p>Questi test fissano la regola che l'ha sostituita: <strong>aperta</strong>
 * — {@code ATTIVO} o {@code IN_CORSO} — è una barra dall'inizio alla
 * scadenza, chiusa è un segno sul giorno in cui scadeva.
 *
 * <p><strong>Si creano una persona e un lavoro propri.</strong> Questo è il
 * database di sviluppo, dove il gestionale raccoglie dati veri: un test che
 * leggesse le task di una persona esistente direbbe una cosa diversa ogni
 * settimana. Il {@code @Transactional} di classe porta via tutto col rollback.
 */
@SpringBootTest
@Transactional
class CalendarioDashboardTest {

    @Autowired
    private ServizioDashboard dashboard;

    @Autowired
    private UtenteRepository utenti;

    @Autowired
    private LavoroRepository lavori;

    @Autowired
    private TaskRepository task;

    @Autowired
    private AssegnazioneTaskRepository assegnazioni;

    /**
     * Il caso che era rotto: una task appena affidata, cominciata ieri e in
     * scadenza il mese prossimo, si vede <strong>questa</strong> settimana.
     */
    @Test
    void unaTaskApertaSiVedeNellaSettimanaInCuiVaFatta() {
        LocalDate lunedi = LocalDate.now().with(DayOfWeek.MONDAY);
        Postazione dove = unaPostazione(StatoTask.ATTIVO,
                lunedi.minusDays(1), lunedi.plusWeeks(3));

        assertThat(dashboard.calendario(dove.collegata(), lunedi, lunedi.plusDays(6)))
                .filteredOn(evento -> evento.tipo() == TipoEvento.TASK)
                .singleElement()
                .satisfies(evento -> {
                    assertThat(evento.titolo()).isEqualTo(dove.task().getTitolo());
                    // La barra copre tutto il periodo, non solo la scadenza.
                    assertThat(evento.inizio()).isEqualTo(lunedi.minusDays(1));
                    assertThat(evento.fine()).isEqualTo(lunedi.plusWeeks(3));
                });
    }

    /** Lo stesso vale per una task su cui il timer è già passato. */
    @Test
    void unaTaskInCorsoSiVedeComePrima() {
        LocalDate lunedi = LocalDate.now().with(DayOfWeek.MONDAY);
        Postazione dove = unaPostazione(StatoTask.IN_CORSO,
                lunedi.minusDays(1), lunedi.plusWeeks(3));

        assertThat(dashboard.calendario(dove.collegata(), lunedi, lunedi.plusDays(6)))
                .filteredOn(evento -> evento.tipo() == TipoEvento.TASK)
                .hasSize(1);
    }

    /**
     * Una task chiusa è un segno sul giorno in cui scadeva, non una barra:
     * «è andata così» non occupa le settimane in cui ci si è lavorato.
     */
    @Test
    void unaTaskCompletataStaSoloSulGiornoDellaScadenza() {
        LocalDate lunedi = LocalDate.now().with(DayOfWeek.MONDAY);
        Postazione dove = unaPostazione(StatoTask.COMPLETATO,
                lunedi.minusDays(1), lunedi.plusWeeks(3));

        // Nella settimana corrente non c'è: la sua scadenza è fra tre
        // settimane, e una task chiusa vive solo lì.
        assertThat(dashboard.calendario(dove.collegata(), lunedi, lunedi.plusDays(6)))
                .filteredOn(evento -> evento.tipo() == TipoEvento.TASK)
                .isEmpty();

        assertThat(dashboard.calendario(dove.collegata(),
                lunedi.plusWeeks(3), lunedi.plusWeeks(3).plusDays(6)))
                .filteredOn(evento -> evento.tipo() == TipoEvento.TASK)
                .singleElement()
                .satisfies(evento ->
                        assertThat(evento.inizio()).isEqualTo(lunedi.plusWeeks(3)));
    }

    /** Una task annullata non compare mai: non c'è niente da farci. */
    @Test
    void unaTaskAnnullataNonCompare() {
        LocalDate lunedi = LocalDate.now().with(DayOfWeek.MONDAY);
        Postazione dove = unaPostazione(StatoTask.ANNULLATO,
                lunedi.minusDays(1), lunedi.plusWeeks(3));

        assertThat(dashboard.calendario(dove.collegata(),
                lunedi.minusWeeks(1), lunedi.plusWeeks(5)))
                .filteredOn(evento -> evento.tipo() == TipoEvento.TASK)
                .isEmpty();
    }

    // --- aiuti ---------------------------------------------------------------

    private record Postazione(Utente persona, Task task) {

        /** La persona come la vede la sicurezza: il solo pavimento. */
        UserDetails collegata() {
            return User.withUsername(persona.getEmail())
                    .password(persona.getPasswordHash())
                    .authorities("TASK_VISUALIZZA_ASSEGNATE", "TASK_AGGIORNA_STATO",
                            "LAVORI_VISUALIZZA_PROPRI", "ASSENZE_RICHIEDI")
                    .build();
        }
    }

    private Postazione unaPostazione(StatoTask stato, LocalDate inizio, LocalDate scadenza) {
        Utente persona = utenti.save(Utente.nuovo("Prova", "Del Calendario",
                "prova.calendario." + System.nanoTime() + "@gestionale.local",
                "{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq"));

        Lavoro contenitore = lavori.save(Lavoro.nuovo(
                "LAV-CAL-" + System.nanoTime(), "Lavoro di prova del calendario",
                null, inizio, scadenza, StatoLavoro.ATTIVO,
                new BigDecimal("40.00"), persona, persona));

        Task destinazione = task.save(Task.nuova(contenitore,
                "Task di prova del calendario", null, inizio, scadenza,
                new BigDecimal("8.00"), TaskImportanza.MEDIUM.name()));

        // Lo stato si imposta dopo: una task nasce ATTIVO, e i passaggi li
        // fa il servizio con i suoi controlli. Qui interessa lo stato finale.
        if (stato != StatoTask.ATTIVO) {
            destinazione.cambiaStato(stato);
        }

        assegnazioni.save(AssegnazioneTask.nuova(destinazione, persona, persona));
        assegnazioni.flush();
        task.flush();

        return new Postazione(persona, destinazione);
    }
}
