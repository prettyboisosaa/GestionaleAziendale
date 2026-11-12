package com.anitia.backend.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.anitia.backend.lavoro.Lavoro;
import com.anitia.backend.lavoro.LavoroRepository;
import com.anitia.backend.lavoro.StatoLavoro;
import com.anitia.backend.task.Task;
import com.anitia.backend.task.TaskImportanza;
import com.anitia.backend.task.TaskRepository;
import com.anitia.backend.tempo.SessioneTask;
import com.anitia.backend.tempo.SessioneTaskRepository;
import com.anitia.backend.tempo.TipoAttivita;
import com.anitia.backend.utente.Utente;
import com.anitia.backend.utente.UtenteRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Il consuntivo del tempo contro il database vero.
 *
 * <p>Serve perché le due query del report sono native e nessun test con i
 * repository simulati può accorgersi se sbagliano: {@code minuti} è una
 * colonna che calcola MySQL, i join sono esterni di proposito e il lavoro di
 * una sessione è un {@code COALESCE}. Quello che ne esce non è un errore
 * quando è sbagliato — è una percentuale plausibile e falsa, cioè il genere di
 * bug che nessuno segnala e tutti usano per decidere.
 *
 * <p>⚠️ Il contesto completo gira sul database di <strong>sviluppo</strong>,
 * dove ci sono dati veri. Quindi ogni prova si costruisce le proprie persone e
 * i propri lavori, con un {@code nanoTime} nel nome, e verifica
 * <strong>solo</strong> quelli, ritrovandoli per id. Il {@code @Transactional}
 * porta via tutto col rollback.
 */
@SpringBootTest
@Transactional
class ReportPersoneTest {

    /** Hash di "Password1!", lo stesso dei dati di sviluppo. */
    private static final String HASH =
            "{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq";

    /**
     * Una finestra fissa e lontana da oggi: così una prova non cambia esito a
     * seconda del giorno in cui gira. Le sole prove che guardano «adesso» sono
     * quelle sulle sessioni ancora aperte, che per definizione cominciano ora.
     */
    private static final LocalDate DA = LocalDate.of(2026, 3, 2);
    private static final LocalDate A = LocalDate.of(2026, 3, 6);

    @Autowired
    private ServizioReport servizio;

    @Autowired
    private UtenteRepository utenti;

    @Autowired
    private LavoroRepository lavori;

    @Autowired
    private TaskRepository task;

    @Autowired
    private SessioneTaskRepository sessioni;

    @Autowired
    private EntityManager entityManager;

    @Test
    void leTreFamiglieDiTempoSommanoAlTotale() {
        Postazione dove = unaPostazione();

        // 1. Sette ore su una task del lavoro.
        sessioni.saveAndFlush(SessioneTask.manuale(dove.task(), dove.persona(),
                TipoAttivita.LAVORO_TASK, "Sviluppo",
                alle(2, 9, 0), alle(2, 16, 0)));
        // 2. Un'ora sul lavoro ma fuori da ogni task.
        sessioni.saveAndFlush(SessioneTask.attivita(dove.persona(),
                TipoAttivita.MANUTENZIONE_CLIENTE, dove.lavoro(), "Patch in produzione",
                alle(3, 9, 0), alle(3, 10, 0)));
        // 3. Due ore che non riguardano nessun lavoro.
        sessioni.saveAndFlush(SessioneTask.attivita(dove.persona(),
                TipoAttivita.RIUNIONE, null, "Riunione di reparto",
                alle(4, 9, 0), alle(4, 11, 0)));

        ReportPersona suo = servizio.persona(dove.persona().getId(), DA, A);

        assertThat(suo.minutiTotali()).isEqualTo(600);
        assertThat(suo.lavori()).hasSize(1);

        VoceLavoro lavoro = suo.lavori().getFirst();
        assertThat(lavoro.lavoroId()).isEqualTo(dove.lavoro().getId());
        // Le ore senza task restano dentro il lavoro: una manutenzione per
        // quel cliente è tempo di quel cliente.
        assertThat(lavoro.minuti()).isEqualTo(480);
        assertThat(lavoro.task()).singleElement()
                .satisfies(una -> assertThat(una.minuti()).isEqualTo(420));
        assertThat(lavoro.attivitaDirette()).singleElement()
                .satisfies(una -> {
                    assertThat(una.tipo()).isEqualTo(TipoAttivita.MANUTENZIONE_CLIENTE);
                    assertThat(una.minuti()).isEqualTo(60);
                });

        assertThat(suo.senzaLavoro()).singleElement()
                .satisfies(una -> {
                    assertThat(una.tipo()).isEqualTo(TipoAttivita.RIUNIONE);
                    assertThat(una.minuti()).isEqualTo(120);
                    // L'etichetta viaggia già tradotta.
                    assertThat(una.etichetta()).isEqualTo(TipoAttivita.RIUNIONE.etichetta());
                });

        // La proprietà che tiene in piedi ogni percentuale: le parti sommano
        // al tutto. Senza, il denominatore mentirebbe.
        long sommaDellePartI = suo.lavori().stream().mapToLong(VoceLavoro::minuti).sum()
                + suo.senzaLavoro().stream().mapToLong(VoceAttivita::minuti).sum();
        assertThat(sommaDellePartI).isEqualTo(suo.minutiTotali());
    }

    /**
     * Una sessione con una task porta anche una colonna {@code lavoro_id}
     * rimasta da un'altra vita. Deve vincere il lavoro della task: è quello
     * che dice {@code SessioneTask.lavoroCitato()}, ed è il motivo per cui la
     * query usa {@code COALESCE(t.lavoro_id, s.lavoro_id)} in quest'ordine e
     * non nell'altro.
     */
    @Test
    void conUnaTaskVinceIlLavoroDellaTask() {
        Postazione dove = unaPostazione();
        Lavoro altro = unLavoro(dove.persona());

        SessioneTask sessione = sessioni.saveAndFlush(SessioneTask.manuale(
                dove.task(), dove.persona(), TipoAttivita.LAVORO_TASK, "Sviluppo",
                alle(2, 9, 0), alle(2, 11, 0)));

        // Si scrive in SQL perché l'API Java non permette questa combinazione,
        // ed è proprio per questo che va provata: il database sì.
        entityManager.createNativeQuery(
                        "UPDATE sessione_task SET lavoro_id = :lavoro WHERE id = :id")
                .setParameter("lavoro", altro.getId())
                .setParameter("id", sessione.getId())
                .executeUpdate();

        ReportPersona suo = servizio.persona(dove.persona().getId(), DA, A);

        assertThat(suo.lavori()).singleElement()
                .satisfies(uno -> assertThat(uno.lavoroId()).isEqualTo(dove.lavoro().getId()));
    }

    @Test
    void laSessioneAnnullataNonConta() {
        Postazione dove = unaPostazione();
        SessioneTask sessione = sessioni.saveAndFlush(SessioneTask.manuale(
                dove.task(), dove.persona(), TipoAttivita.LAVORO_TASK, "Sviluppo",
                alle(2, 9, 0), alle(2, 11, 0)));
        sessione.annulla();
        sessioni.saveAndFlush(sessione);

        assertThat(servizio.persona(dove.persona().getId(), DA, A).minutiTotali()).isZero();
    }

    /**
     * Una sessione ancora aperta non ha minuti, e il report non la chiude:
     * leggere non deve scrivere. Il numero resta prudente, mai gonfiato.
     */
    @Test
    void laSessioneApertaNonConta() {
        Postazione dove = unaPostazione();
        sessioni.saveAndFlush(SessioneTask.avvia(dove.task(), dove.persona()));

        LocalDate oggi = LocalDate.now();
        ReportPersona suo = servizio.persona(dove.persona().getId(), oggi, oggi);

        assertThat(suo.minutiTotali()).isZero();
        assertThat(suo.lavori()).isEmpty();
    }

    /**
     * Le ore che nessuno ha ancora guardato stanno <strong>nel</strong>
     * totale, perché escluderle farebbe sparire lavoro vero, e anche accanto,
     * perché chi legge deve sapere quanta parte del numero è una deduzione. È
     * la regola che {@code TempoDiUnaPersona} mette per iscritto.
     */
    @Test
    void laNonConfermataContaNelTotaleEanchePerSe() {
        Postazione dove = unaPostazione();
        SessioneTask sessione = SessioneTask.manuale(dove.task(), dove.persona(),
                TipoAttivita.LAVORO_TASK, "Dimenticata aperta",
                alle(2, 9, 0), alle(2, 12, 0));
        // Chiusura d'ufficio: resta da confermare, con l'ora di fine che le è
        // stata attribuita.
        sessione.chiudiDUfficio(alle(2, 12, 0));
        sessioni.saveAndFlush(sessione);

        ReportPersona suo = servizio.persona(dove.persona().getId(), DA, A);

        assertThat(suo.minutiTotali()).isEqualTo(180);
        assertThat(suo.minutiNonConfermati()).isEqualTo(180);
        assertThat(suo.lavori()).singleElement()
                .satisfies(uno -> assertThat(uno.minutiNonConfermati()).isEqualTo(180));
    }

    /**
     * L'intervallo ha gli estremi <strong>compresi</strong>: una sessione
     * avviata alle 23:59 dell'ultimo giorno è dentro, una del giorno dopo è
     * fuori. È il confine che si sbaglia più facilmente, e si sbaglia in
     * silenzio.
     */
    @Test
    void gliEstremiSonoCompresi() {
        Postazione dove = unaPostazione();
        sessioni.saveAndFlush(SessioneTask.manuale(dove.task(), dove.persona(),
                TipoAttivita.LAVORO_TASK, "Ultima sera",
                alle(6, 23, 59), alle(7, 0, 59)));
        sessioni.saveAndFlush(SessioneTask.manuale(dove.task(), dove.persona(),
                TipoAttivita.LAVORO_TASK, "Il giorno dopo",
                alle(7, 0, 0), alle(7, 2, 0)));

        // Solo la prima: un'ora, e per intero nel giorno in cui è cominciata,
        // anche se finisce dopo la mezzanotte.
        assertThat(servizio.persona(dove.persona().getId(), DA, A).minutiTotali())
                .isEqualTo(60);
    }

    @Test
    void lIntervalloRovescioEunErroreDiChiChiede() {
        Postazione dove = unaPostazione();

        Throwable caduta = catchThrowable(
                () -> servizio.persona(dove.persona().getId(), A, DA));

        assertThat(caduta).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) caduta).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void laPersonaCheNonEsisteDa404() {
        Throwable caduta = catchThrowable(() -> servizio.persona(-1L, DA, A));

        assertThat(caduta).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) caduta).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void senzaDatiIlTotaleEzeroEleListeSonoVuote() {
        Postazione dove = unaPostazione();

        ReportPersona suo = servizio.persona(dove.persona().getId(), DA, A);

        assertThat(suo.minutiTotali()).isZero();
        assertThat(suo.lavori()).isEmpty();
        assertThat(suo.senzaLavoro()).isEmpty();
        // Il nome arriva comunque: la persona esiste, è il suo tempo che non c'è.
        assertThat(suo.cognome()).isEqualTo("Del Report");
    }

    /**
     * Chi non ha registrato niente resta in elenco: «zero ore» è una delle
     * cose per cui si apre un report. È la prova che i filtri delle sessioni
     * stanno nella {@code ON} e non nella {@code WHERE} — nella {@code WHERE}
     * il LEFT JOIN degenererebbe e questa riga sparirebbe.
     */
    @Test
    void chiEattivoCompareAncheConZeroOre() {
        Postazione dove = unaPostazione();

        assertThat(sua(dove.persona()))
                .isNotNull()
                .satisfies(una -> assertThat(una.minutiTotali()).isZero());
    }

    @Test
    void ilDisattivatoSenzaOreNonCompare() {
        Postazione dove = unaPostazione();
        spegni(dove.persona());

        assertThat(sua(dove.persona())).isNull();
    }

    /**
     * Il disattivato con ore nel periodo invece c'è: il suo lavoro è stato
     * fatto per davvero, e toglierlo dal consuntivo vorrebbe dire che quelle
     * ore non sono andate da nessuna parte.
     */
    @Test
    void ilDisattivatoConOreCompareEsiVede() {
        Postazione dove = unaPostazione();
        sessioni.saveAndFlush(SessioneTask.manuale(dove.task(), dove.persona(),
                TipoAttivita.LAVORO_TASK, "Prima di andarsene",
                alle(2, 9, 0), alle(2, 11, 0)));
        spegni(dove.persona());

        assertThat(sua(dove.persona()))
                .isNotNull()
                .satisfies(una -> {
                    assertThat(una.minutiTotali()).isEqualTo(120);
                    assertThat(una.attivo()).isFalse();
                });
    }

    /** La riga di questa persona nella panoramica, oppure null se non c'è. */
    private RiepilogoPersona sua(Utente persona) {
        return servizio.persone(DA, A).persone().stream()
                .filter(una -> una.utenteId().equals(persona.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Si spegne in SQL perché {@code Utente.disattiva()} è chiuso nel suo
     * package: qui non interessa la via per cui si arriva allo stato, ma che
     * il report lo legga.
     */
    private void spegni(Utente persona) {
        utenti.flush();
        entityManager.createNativeQuery(
                        "UPDATE utente SET attivo = FALSE, disattivato_il = NOW() WHERE id = :id")
                .setParameter("id", persona.getId())
                .executeUpdate();
        entityManager.clear();
    }

    /** Un giorno di marzo 2026, dentro la finestra delle prove. */
    private static LocalDateTime alle(int giorno, int ora, int minuto) {
        return LocalDateTime.of(2026, 3, giorno, ora, minuto);
    }

    private Postazione unaPostazione() {
        Utente persona = utenti.saveAndFlush(Utente.nuovo("Prova", "Del Report",
                "prova.report." + System.nanoTime() + "@gestionale.local", HASH));

        Lavoro contenitore = unLavoro(persona);

        Task destinazione = task.saveAndFlush(Task.nuova(contenitore,
                "Task di prova del report", null,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                new BigDecimal("8.00"), TaskImportanza.MEDIUM.name()));

        return new Postazione(persona, contenitore, destinazione);
    }

    private Lavoro unLavoro(Utente responsabile) {
        return lavori.saveAndFlush(Lavoro.nuovo(
                "LAV-REPORT-" + System.nanoTime(), "Lavoro di prova del report",
                null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 31),
                StatoLavoro.ATTIVO, new BigDecimal("40.00"), responsabile, responsabile));
    }

    private record Postazione(Utente persona, Lavoro lavoro, Task task) {
    }
}
