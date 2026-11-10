package com.anitia.backend.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.anitia.backend.lavoro.Lavoro;
import com.anitia.backend.lavoro.LavoroRepository;
import com.anitia.backend.lavoro.StatoLavoro;
import com.anitia.backend.task.AssegnazioneTask;
import com.anitia.backend.task.AssegnazioneTaskRepository;
import com.anitia.backend.task.StatoTask;
import com.anitia.backend.task.Task;
import com.anitia.backend.task.TaskImportanza;
import com.anitia.backend.task.TaskRepository;
import com.anitia.backend.utente.Utente;
import com.anitia.backend.utente.UtenteRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;

/**
 * La card «Urgenti» della dashboard: che cosa conta.
 *
 * <p>Ha preso il posto di «Scadute», e il suo modo di rompersi è silenzioso.
 * L'urgenza deve attraversare tre punti per arrivare al numero — la colonna
 * {@code urgenza_task}, il campo di {@code MiaTask}, il predicato del
 * contatore — e se uno dei tre si scollega la card non dà un errore: dà
 * <strong>zero</strong>, per sempre, che somiglia a «non hai task urgenti».
 * È già accaduto due volte mentre l'urgenza veniva costruita, una per il DTO
 * di risposta delle task e una per quello della dashboard.
 *
 * <p>Si creano una persona e un lavoro propri: questo è il database di
 * sviluppo, dove il gestionale raccoglie dati veri, e contare le task di una
 * persona esistente darebbe un numero diverso ogni settimana. Il
 * {@code @Transactional} di classe porta via tutto col rollback.
 */
@SpringBootTest
@Transactional
class ContatoriDashboardTest {

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

    /** Una sola delle tre task è urgente, e la card conta una. */
    @Test
    void laCardContaLeSoleTaskUrgenti() {
        Utente persona = unaPersona();
        Lavoro lavoro = unLavoro(persona);

        unaTask(lavoro, persona, TaskImportanza.CRITICAL.name(), StatoTask.ATTIVO);
        unaTask(lavoro, persona, TaskImportanza.HIGH.name(), StatoTask.ATTIVO);
        unaTask(lavoro, persona, TaskImportanza.MEDIUM.name(), StatoTask.ATTIVO);

        assertThat(critiche(persona)).isEqualTo(1);
    }

    /**
     * «Alta» non entra nel conto.
     *
     * <p>La card ne mostrerebbe un numero che la sua etichetta non spiega, e
     * chi la clicca si troverebbe una lista più larga di quella che ha
     * chiesto. È il livello più alto e nient'altro.
     */
    @Test
    void laCardNonContaLeTaskDiUrgenzaAlta() {
        Utente persona = unaPersona();
        unaTask(unLavoro(persona), persona, TaskImportanza.HIGH.name(), StatoTask.ATTIVO);

        assertThat(critiche(persona)).isZero();
    }

    /**
     * Una task urgente ma chiusa non si conta: le card rispondono a «cosa devo
     * fare», e una task completata non è più niente da fare. È la stessa
     * regola degli altri tre contatori.
     */
    @Test
    void unaTaskUrgenteMaCompletataNonSiConta() {
        Utente persona = unaPersona();
        unaTask(unLavoro(persona), persona, TaskImportanza.CRITICAL.name(),
                StatoTask.COMPLETATO);

        assertThat(critiche(persona)).isZero();
    }

    /**
     * Le task più vecchie della {@code V19} non hanno urgenza, e non si
     * contano né si fanno contare come un errore.
     */
    @Test
    void unaTaskSenzaUrgenzaNonSiConta() {
        Utente persona = unaPersona();
        unaTask(unLavoro(persona), persona, null, StatoTask.ATTIVO);

        assertThat(critiche(persona)).isZero();
    }

    /**
     * Una stringa che non è nessuno dei quattro livelli vale «non lo so», non
     * un 500.
     *
     * <p>La colonna è un {@code VARCHAR} senza {@code CHECK}, quindi ci può
     * finire qualunque cosa — per esempio la stringa {@code "null"}, che è
     * quello che {@code String.valueOf} produce da un enum nullo, e che oggi
     * {@code ServizioTask} scriverebbe se la richiesta arrivasse senza
     * urgenza. La dashboard deve caricarsi comunque: un riepilogo illeggibile
     * per una riga scritta male sarebbe un prezzo assurdo.
     */
    @Test
    void unaUrgenzaIllegibileNonFaCadereLaDashboard() {
        Utente persona = unaPersona();
        unaTask(unLavoro(persona), persona, "null", StatoTask.ATTIVO);

        assertThat(critiche(persona)).isZero();
    }

    // --- aiuti ---------------------------------------------------------------

    private long critiche(Utente persona) {
        return dashboard.riepilogo(collegata(persona)).contatori().critiche();
    }

    private Utente unaPersona() {
        return utenti.save(Utente.nuovo("Prova", "Dei Contatori",
                "prova.contatori." + System.nanoTime() + "@gestionale.local",
                "{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq"));
    }

    private Lavoro unLavoro(Utente persona) {
        return lavori.save(Lavoro.nuovo(
                "LAV-CONT-" + System.nanoTime(), "Lavoro di prova dei contatori",
                null, LocalDate.now().minusDays(7), LocalDate.now().plusMonths(2),
                StatoLavoro.ATTIVO, new BigDecimal("40.00"), persona, persona));
    }

    /**
     * Una task assegnata, con l'urgenza scritta <strong>grezza</strong>.
     *
     * <p>Il parametro è una stringa e non un {@code TaskImportanza} perché
     * così è la fabbrica dell'entità, e perché serve poter scrivere anche
     * quello che l'enum non conosce: è il caso che
     * {@link #unaUrgenzaIllegibileNonFaCadereLaDashboard} verifica, e non si
     * potrebbe esprimere passando un enum.
     */
    private Task unaTask(Lavoro lavoro, Utente persona, String urgenza, StatoTask stato) {
        Task creata = task.save(Task.nuova(lavoro, "Task di prova dei contatori",
                null, LocalDate.now().minusDays(1), LocalDate.now().plusDays(10),
                new BigDecimal("8.00"), urgenza));

        if (stato != StatoTask.ATTIVO) {
            creata.cambiaStato(stato);
        }

        assegnazioni.save(AssegnazioneTask.nuova(creata, persona, persona));
        assegnazioni.flush();
        task.flush();
        return creata;
    }

    /** La persona come la vede la sicurezza: il solo pavimento. */
    private static UserDetails collegata(Utente persona) {
        return User.withUsername(persona.getEmail())
                .password(persona.getPasswordHash())
                .authorities("TASK_VISUALIZZA_ASSEGNATE", "TASK_AGGIORNA_STATO",
                        "LAVORI_VISUALIZZA_PROPRI", "ASSENZE_RICHIEDI")
                .build();
    }
}
