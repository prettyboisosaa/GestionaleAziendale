package com.anitia.backend.tempo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Le ore consumate, dal minuto alla cifra, contro il database vero.
 *
 * <p>Esiste per colmare un vuoto preciso: le somme delle ore consumate non
 * hanno mai avuto un test. Erano {@code SUM(voce_dichiarazione.ore)} e sono
 * diventate {@code SUM(sessione_task.minuti)/60} — una riscrittura su sette
 * query native, senza rete. Il numero che ne esce decide se una task sfora il
 * budget e quante ore tornano nel pool quando si chiude: sbagliarlo non dà
 * nessun errore, dà una cifra plausibile e falsa.
 *
 * <p>È anche l'unico posto dove si verifica che le colonne generate facciano
 * quello che si crede. {@code minuti} e {@code in_corso} le calcola MySQL e
 * non sono mappate in JPA: nessun test con il repository simulato potrebbe
 * accorgersi se smettessero di funzionare.
 *
 * <p>{@code @Transactional} su una classe di test fa rollback alla fine: le
 * sessioni che scrive qui non restano nel database di sviluppo.
 *
 * <p><strong>Ogni prova si crea la propria persona</strong>, oltre al proprio
 * lavoro e alla propria task. Non è pignoleria: il divieto di sovrapposizione
 * e il vincolo sulla sessione aperta guardano le ore di <em>una persona</em>,
 * non di una task, e questo è il database di sviluppo, dove il gestionale
 * raccoglie dati veri. Intestare le prove a una persona esistente le faceva
 * cadere appena qualcuno lavorava davvero in quelle ore — è accaduto, con
 * mezz'ora registrata a mezzogiorno. È la stessa lezione che il commento di
 * {@code unaPostazione} racconta per i lavori, portata fino in fondo: una
 * persona nuova non ha nessuna sessione, quindi l'orologio è tutto suo.
 */
@SpringBootTest
@Transactional
class SessioniEOreTest {


    @Autowired
    private SessioneTaskRepository sessioni;

    @Autowired
    private ServizioTempo tempo;

    @Autowired
    private AssegnazioneTaskRepository assegnazioni;

    @Autowired
    private TaskRepository task;

    @Autowired
    private LavoroRepository lavori;

    @Autowired
    private UtenteRepository utenti;

    /**
     * Tre quarti d'ora e mezz'ora fanno un'ora e un quarto, non «circa».
     *
     * <p>45 e 30 minuti sono scelti di proposito: nessuno dei due è un numero
     * intero di ore, e la loro somma lo è di un quarto. Se la divisione per 60
     * avvenisse per sessione invece che sulla somma, qui si vedrebbe.
     */
    @Test
    void iMinutiDiPiuSessioniDiventanoOreSullaSomma() {
        Postazione dove = unaPostazione();
        Task destinazione = dove.task();

        sessione(dove, "2026-11-09T09:00:00", "2026-11-09T09:45:00");
        sessione(dove, "2026-11-09T14:00:00", "2026-11-09T14:30:00");
        sessioni.flush();

        assertThat(task.sommaOreConsumateDellaTask(destinazione.getId()))
                .isEqualByComparingTo(new BigDecimal("1.25"));
    }

    /** Una sessione ancora aperta non ha ore: non è finita, non è uno zero misurato. */
    @Test
    void unaSessioneApertaNonEntraNellaSomma() {
        Postazione dove = unaPostazione();
        Task destinazione = dove.task();

        sessione(dove, "2026-11-09T09:00:00", "2026-11-09T10:00:00");
        sessioni.saveAndFlush(SessioneTask.avvia(destinazione, dove.persona()));

        assertThat(task.sommaOreConsumateDellaTask(destinazione.getId()))
                .isEqualByComparingTo(new BigDecimal("1.00"));
    }

    /** Una sessione annullata esce dai conti, ma la riga resta. */
    @Test
    void unaSessioneAnnullataNonEntraNellaSomma() {
        Postazione dove = unaPostazione();
        Task destinazione = dove.task();

        SessioneTask da2Ore = sessione(dove, "2026-11-09T09:00:00",
                "2026-11-09T11:00:00");
        sessione(dove, "2026-11-09T14:00:00", "2026-11-09T15:00:00");
        da2Ore.annulla();
        sessioni.flush();

        assertThat(task.sommaOreConsumateDellaTask(destinazione.getId()))
                .isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(sessioni.findById(da2Ore.getId())).isPresent();
    }

    /**
     * La colonna generata {@code minuti} conta i minuti fra i due istanti.
     *
     * <p>Si legge attraverso {@link SessioneTask#durataMinuti()}, che la
     * ricava in Java dagli stessi istanti: se le due strade dessero numeri
     * diversi, la somma delle ore e quello che l'interfaccia mostra
     * divergerebbero.
     */
    @Test
    void laDurataInJavaCoincideConQuellaDelDatabase() {
        Postazione dove = unaPostazione();
        Task destinazione = dove.task();
        SessioneTask sessione = sessione(dove, "2026-11-09T09:00:00",
                "2026-11-09T12:30:00");
        sessioni.flush();

        assertThat(sessione.durataMinuti()).isEqualTo(210);
        assertThat(task.sommaOreConsumateDellaTask(destinazione.getId()))
                .isEqualByComparingTo(new BigDecimal("3.50"));
    }

    /**
     * Il vincolo che regge tutto: una sola sessione aperta per persona.
     *
     * <p>Non è un controllo del servizio ma {@code uk_sessione_in_corso}, e
     * va verificato qui perché è l'unica cosa che protegge da due clic
     * contemporanei da due finestre. Se cadesse, le ore di una persona
     * potrebbero sovrapporsi e non sarebbero più sommabili.
     */
    @Test
    void unaSolaSessioneApertaPerPersona() {
        Postazione dove = unaPostazione();
        Task destinazione = dove.task();
        sessioni.saveAndFlush(SessioneTask.avvia(destinazione, dove.persona()));

        SessioneTask seconda = SessioneTask.avvia(destinazione, dove.persona());

        assertThat(catchThrowable(() -> sessioni.saveAndFlush(seconda)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Molte sessioni chiuse convivono: è `in_corso` a valere NULL, non zero. */
    @Test
    void molteSessioniChiuseConvivono() {
        Postazione dove = unaPostazione();
        Task destinazione = dove.task();

        sessione(dove, "2026-11-09T09:00:00", "2026-11-09T10:00:00");
        sessione(dove, "2026-11-09T10:00:00", "2026-11-09T11:00:00");
        sessione(dove, "2026-11-09T11:00:00", "2026-11-09T12:00:00");
        sessioni.flush();

        assertThat(task.sommaOreConsumateDellaTask(destinazione.getId()))
                .isEqualByComparingTo(new BigDecimal("3.00"));
    }

    // --- registraManuale, il percorso intero --------------------------------

    /**
     * Scrivere a mano, dalla richiesta alla riga: è l'unico posto dove il
     * percorso completo di {@code registraManuale} viene percorso davvero.
     *
     * <p>I test del controller hanno il servizio simulato, e questi altri si
     * fermano al repository: in mezzo c'è la catena che decide — assegnazione,
     * lavoro aperto, finestra, sovrapposizione, stato della task — e nessuna
     * delle due sponde la attraversa.
     *
     * <p>Verifica anche il passaggio di stato: la task nasce {@code ATTIVO} e
     * registrarci un'ora la porta {@code IN_CORSO}, perché averci lavorato
     * <em>è</em> averla messa in corso.
     */
    @Test
    void registrareAManoScriveLaSessioneEMetteLaTaskInCorso() {
        Postazione dove = unaPostazione();
        assertThat(dove.task().getStato()).isEqualTo(StatoTask.ATTIVO);

        Intervallo quando = unIntervalloScrivibile();

        tempo.registraManuale(collegata(dove.persona()),
                dove.task().getLavoro().getId(), dove.task().getId(),
                quando.da(), quando.a());
        sessioni.flush();

        assertThat(sessioni.trovaDelPeriodo(dove.persona().getId(),
                quando.da(), quando.a().plusMinutes(1)))
                .singleElement()
                .satisfies(scritta -> {
                    assertThat(scritta.getOrigine()).isEqualTo(OrigineSessione.MANUALE);
                    assertThat(scritta.isConfermata()).isTrue();
                    assertThat(scritta.inCorso()).isFalse();
                });

        assertThat(dove.task().getStato()).isEqualTo(StatoTask.IN_CORSO);
    }

    /**
     * Su una task a cui non si è assegnati non si scrive, e non è una
     * questione di permessi: Giulia ha il pavimento, ma il tempo è di chi
     * l'ha lavorato.
     */
    @Test
    void registrareAManoSuUnaTaskNonAssegnataNonSiPuo() {
        // La task c'è e il lavoro è suo: manca solo l'assegnazione.
        Postazione dove = unaPostazioneSenzaAssegnazione();
        Intervallo quando = unIntervalloScrivibile();

        assertThat(catchThrowable(() -> tempo.registraManuale(collegata(dove.persona()),
                dove.task().getLavoro().getId(), dove.task().getId(),
                quando.da(), quando.a())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Non sei assegnato");
    }

    /** Due intervalli accavallati: il secondo viene respinto e nomina il primo. */
    @Test
    void registrareAManoSopraUnAltraSessioneNonSiPuo() {
        Postazione dove = unaPostazione();
        Intervallo quando = unIntervalloScrivibile();

        tempo.registraManuale(collegata(dove.persona()),
                dove.task().getLavoro().getId(), dove.task().getId(),
                quando.da(), quando.a());
        sessioni.flush();

        assertThat(catchThrowable(() -> tempo.registraManuale(collegata(dove.persona()),
                dove.task().getLavoro().getId(), dove.task().getId(),
                quando.da(), quando.a())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("non si sovrappongono");
    }

    /** La settimana scorsa è chiusa: il consuntivo di un lavoro deve potersi fermare. */
    @Test
    void registrareAManoNellaSettimanaScorsaNonSiPuo() {
        Postazione dove = unaPostazione();
        LocalDateTime laSettimanaScorsa = LocalDate.now()
                .with(DayOfWeek.MONDAY).minusDays(3).atTime(9, 0);

        assertThat(catchThrowable(() -> tempo.registraManuale(collegata(dove.persona()),
                dove.task().getLavoro().getId(), dove.task().getId(),
                laSettimanaScorsa, laSettimanaScorsa.plusHours(2))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("non si scrive più");
    }

    /** Del tempo che non è ancora passato non è un ricordo: è un proposito. */
    @Test
    void registrareAManoNelFuturoNonSiPuo() {
        Postazione dove = unaPostazione();
        LocalDateTime domani = LocalDateTime.now().plusDays(1);

        assertThat(catchThrowable(() -> tempo.registraManuale(collegata(dove.persona()),
                dove.task().getLavoro().getId(), dove.task().getId(),
                domani, domani.plusHours(1))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("TEMPO_NEL_FUTURO");
    }

    // --- la sessione scritta a mano ------------------------------------------

    /**
     * Il CHECK della {@code V18} accetta {@code MANUALE}, e la colonna
     * generata conta i minuti di un intervallo che nessun orologio ha
     * misurato.
     *
     * <p>Vale la pena averlo contro il database vero: {@code origine} è
     * vincolata da {@code ck_sessione_origine}, e una migrazione che avesse
     * ricreato il CHECK copiando male la lista non darebbe nessun errore di
     * compilazione — darebbe un rifiuto alla prima scrittura di un utente.
     */
    @Test
    void unaSessioneManualeSiScriveEHaIMinutiGiusti() {
        Postazione dove = unaPostazione();
        Task destinazione = dove.task();

        SessioneTask aMano = sessioni.saveAndFlush(SessioneTask.manuale(
                destinazione, dove.persona(),
                LocalDateTime.parse("2026-11-09T14:00:00"),
                LocalDateTime.parse("2026-11-09T17:30:00")));

        assertThat(aMano.getOrigine()).isEqualTo(OrigineSessione.MANUALE);
        assertThat(aMano.durataMinuti()).isEqualTo(210);
        assertThat(task.sommaOreConsumateDellaTask(destinazione.getId()))
                .isEqualByComparingTo(new BigDecimal("3.50"));
    }

    /**
     * Una sessione manuale nasce confermata, e non chiede niente a nessuno.
     *
     * <p>È la differenza con la chiusura d'ufficio, e
     * {@code ck_sessione_conferma} la rende una regola del database:
     * {@code AUTOMATICA} è l'unica origine che può restare in attesa, perché
     * è la sola la cui ora di fine non l'ha scelta una persona.
     */
    @Test
    void unaSessioneManualeNonRestaDaConfermare() {
        Postazione dove = unaPostazione();
        Task destinazione = dove.task();
        sessioni.saveAndFlush(SessioneTask.manuale(destinazione, dove.persona(),
                LocalDateTime.parse("2026-11-09T14:00:00"),
                LocalDateTime.parse("2026-11-09T15:00:00")));

        assertThat(sessioni.trovaDaConfermare(dove.persona().getId())).isEmpty();
    }

    /**
     * Una sessione manuale non occupa {@code in_corso}, quindi non toglie a
     * nessuno la possibilità di avviare il timer.
     *
     * <p>Nasce già chiusa, e la colonna generata vale NULL: scrivere a mano
     * un'ora di ieri mentre una task sta scorrendo adesso deve restare
     * possibile, e il vincolo di unicità non deve accorgersene.
     */
    @Test
    void unaSessioneManualeNonOccupaIlPostoDiQuellaInCorso() {
        Postazione dove = unaPostazione();
        Task destinazione = dove.task();
        sessioni.saveAndFlush(SessioneTask.manuale(destinazione, dove.persona(),
                LocalDateTime.parse("2026-11-09T14:00:00"),
                LocalDateTime.parse("2026-11-09T15:00:00")));

        // Se `in_corso` non fosse NULL per la manuale, questa seconda
        // scrittura violerebbe uk_sessione_in_corso.
        assertThat(sessioni.saveAndFlush(SessioneTask.avvia(destinazione, dove.persona())).inCorso())
                .isTrue();
    }

    // --- le sovrapposizioni --------------------------------------------------

    /**
     * Due sessioni che coprono lo stesso istante si trovano.
     *
     * <p>È il controllo che tiene in piedi la sommabilità delle ore, e fino
     * alla griglia settimanale non serviva: scriveva solo il timer, e il timer
     * chiude la precedente quando ne apre un'altra. Da quando un intervallo lo
     * si scrive a mano, due ore registrate due volte diventerebbero quattro
     * nel consuntivo di un lavoro.
     */
    @Test
    void unaSessioneCheCopreLoStessoIstanteSiTrova() {
        Postazione dove = unaPostazione();
        Task destinazione = dove.task();
        sessione(dove, "2026-11-09T09:00:00", "2026-11-09T11:00:00");
        sessioni.flush();

        assertThat(sessioni.trovaSovrapposte(dove.persona().getId(),
                LocalDateTime.parse("2026-11-09T10:00:00"),
                LocalDateTime.parse("2026-11-09T12:00:00"), null))
                .hasSize(1);
    }

    /**
     * Due sessioni attaccate <strong>non</strong> si sovrappongono.
     *
     * <p>È il confine che conta davvero: finire una task alle 11 e cominciarne
     * un'altra alle 11 è la giornata normale di chi passa da una cosa
     * all'altra, e un controllo che lo vietasse renderebbe la griglia
     * inutilizzabile. L'intervallo è semiaperto da entrambe le parti.
     */
    @Test
    void dueSessioniAttaccateNonSiSovrappongono() {
        Postazione dove = unaPostazione();
        Task destinazione = dove.task();
        sessione(dove, "2026-11-09T09:00:00", "2026-11-09T11:00:00");
        sessioni.flush();

        assertThat(sessioni.trovaSovrapposte(dove.persona().getId(),
                LocalDateTime.parse("2026-11-09T11:00:00"),
                LocalDateTime.parse("2026-11-09T13:00:00"), null))
                .isEmpty();

        assertThat(sessioni.trovaSovrapposte(dove.persona().getId(),
                LocalDateTime.parse("2026-11-09T07:00:00"),
                LocalDateTime.parse("2026-11-09T09:00:00"), null))
                .isEmpty();
    }

    /** Correggendo una sessione, quella non deve trovarsi in conflitto con sé. */
    @Test
    void laSessioneCheSiCorreggeNonSiSovrapponeASeStessa() {
        Postazione dove = unaPostazione();
        Task destinazione = dove.task();
        SessioneTask sola = sessione(dove, "2026-11-09T09:00:00",
                "2026-11-09T11:00:00");
        sessioni.flush();

        assertThat(sessioni.trovaSovrapposte(dove.persona().getId(),
                LocalDateTime.parse("2026-11-09T09:30:00"),
                LocalDateTime.parse("2026-11-09T11:30:00"), sola.getId()))
                .isEmpty();
    }

    /** Una sessione annullata è fuori dai conti, quindi non è un ostacolo. */
    @Test
    void unaSessioneAnnullataNonSiSovrappone() {
        Postazione dove = unaPostazione();
        Task destinazione = dove.task();
        SessioneTask annullata = sessione(dove, "2026-11-09T09:00:00",
                "2026-11-09T11:00:00");
        annullata.annulla();
        sessioni.flush();

        assertThat(sessioni.trovaSovrapposte(dove.persona().getId(),
                LocalDateTime.parse("2026-11-09T09:30:00"),
                LocalDateTime.parse("2026-11-09T10:30:00"), null))
                .isEmpty();
    }

    /**
     * Una sessione aperta fa da ostacolo a tutto quello che le viene dopo.
     *
     * <p>Si comporta come se si estendesse all'infinito, ed è un filo severo
     * di proposito: l'alternativa sarebbe decidere dentro la query fino a
     * quando arriva «adesso». Una sessione che sta scorrendo è esattamente
     * quella su cui non si deve scrivere a mano.
     */
    @Test
    void unaSessioneApertaOstacolaQuelloCheVieneDopo() {
        Postazione dove = unaPostazione();
        Task destinazione = dove.task();
        SessioneTask aperta = sessioni.saveAndFlush(
                SessioneTask.avvia(destinazione, dove.persona()));

        LocalDateTime dopo = aperta.getAvviataIl().plusHours(1);

        assertThat(sessioni.trovaSovrapposte(dove.persona().getId(), dopo, dopo.plusHours(2), null))
                .hasSize(1);
    }

    // --- aiuti ---------------------------------------------------------------

    /**
     * Una sessione chiusa con estremi scelti, intestata alla persona della
     * postazione.
     *
     * <p>Passa da {@code avvia} e poi da {@code correggi}: non c'è una
     * fabbrica che prenda due istanti, e non deve esserci — una sessione nasce
     * da un avvio, e gli estremi li sposta una correzione.
     */
    private SessioneTask sessione(Postazione dove, String da, String a) {
        SessioneTask sessione = SessioneTask.avvia(dove.task(), dove.persona());
        sessione.correggi(LocalDateTime.parse(da), LocalDateTime.parse(a));
        return sessioni.save(sessione);
    }

    /**
     * Una persona creata da questo test, con il suo lavoro e la sua task
     * assegnata.
     *
     * <p><strong>Perché non si riusa Giulia.</strong> Il divieto di
     * sovrapposizione guarda le ore di <em>una persona</em>, non di una task:
     * una sessione vera di Giulia — e questo è il database di sviluppo, dove
     * il gestionale raccoglie dati veri — occupa un pezzo di orologio e fa
     * cadere qualunque intervallo gli finisca addosso. È accaduto: questi test
     * hanno cominciato a fallire perché qualcuno aveva lavorato mezz'ora a
     * mezzogiorno.
     *
     * <p>È lo stesso ragionamento per cui {@link #unaPostazione()} si scrive il
     * proprio lavoro invece di pescarne uno, portato dove serviva: una persona
     * nuova non ha nessuna sessione, quindi l'orologio è tutto suo e il test
     * dice sempre la stessa cosa. Il {@code @Transactional} di classe la porta
     * via col rollback, e l'email porta un {@code nanoTime} perché è unica.
     */
    private Postazione unaPostazione() {
        return unaPostazione(true);
    }

    /** La stessa postazione senza l'assegnazione: serve a verificarne il divieto. */
    private Postazione unaPostazioneSenzaAssegnazione() {
        return unaPostazione(false);
    }

    private Postazione unaPostazione(boolean assegnata) {
        Utente persona = utenti.save(Utente.nuovo("Prova", "Delle Sessioni",
                "prova.sessioni." + System.nanoTime() + "@gestionale.local",
                "{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq"));

        Lavoro contenitore = lavori.save(Lavoro.nuovo(
                "LAV-TEST-" + System.nanoTime(), "Lavoro di prova delle sessioni",
                null, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31),
                StatoLavoro.ATTIVO, new BigDecimal("40.00"), persona, persona));

        Task destinazione = task.save(Task.nuova(contenitore,
                "Task di prova delle sessioni", null,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                new BigDecimal("8.00"), TaskImportanza.MEDIUM.name()));

        if (assegnata) {
            assegnazioni.save(AssegnazioneTask.nuova(destinazione, persona, persona));
        }
        assegnazioni.flush();

        return new Postazione(persona, destinazione);
    }

    private record Postazione(Utente persona, Task task) {
    }

    /**
     * La persona come la vede la sicurezza: i quattro permessi del pavimento e
     * nient'altro.
     *
     * <p>È un {@code User} di Spring e non un {@code UtenteCollegato}, il cui
     * costruttore è chiuso nel suo package: al servizio serve il nome utente
     * per ritrovare la persona e i permessi per i controlli, e di un
     * {@code UserDetails} non guarda altro. Il pavimento e non di più, perché
     * è il caso che conta: un dipendente deve poter scrivere le proprie ore
     * senza nessun permesso aggiuntivo.
     */
    private static UserDetails collegata(Utente persona) {
        return User.withUsername(persona.getEmail())
                .password(persona.getPasswordHash())
                .authorities("TASK_VISUALIZZA_ASSEGNATE", "TASK_AGGIORNA_STATO",
                        "LAVORI_VISUALIZZA_PROPRI", "ASSENZE_RICHIEDI")
                .build();
    }

    private record Intervallo(LocalDateTime da, LocalDateTime a) {
    }

    /**
     * Un intervallo già passato e dentro la settimana corrente, qualunque sia
     * il momento in cui il test gira.
     *
     * <p>Si costruisce da adesso andando indietro, e si ferma al lunedì: così
     * non cade mai nel futuro — che il servizio rifiuta — né nella settimana
     * scorsa — che la finestra ha chiuso. Un intervallo fisso come «martedì
     * alle nove» passerebbe o fallirebbe a seconda del giorno in cui gira la
     * suite, e questo file ha già imparato una volta che un test che passa
     * per caso non è un test.
     *
     * <p>Resta un momento in cui non c'è spazio: il lunedì nel primo minuto
     * dopo la mezzanotte. Lì il test si dichiara non applicabile invece di
     * fallire, perché non ci sarebbe niente di rotto da segnalare.
     */
    private Intervallo unIntervalloScrivibile() {
        LocalDateTime fine = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime lunedi = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime inizio = fine.minusHours(1).isBefore(lunedi)
                ? lunedi
                : fine.minusHours(1);

        assumeTrue(fine.isAfter(inizio),
                "È lunedì appena passata la mezzanotte: non c'è un intervallo "
                        + "già passato dentro questa settimana.");

        return new Intervallo(inizio, fine);
    }
}
