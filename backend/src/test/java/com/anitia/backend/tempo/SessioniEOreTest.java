package com.anitia.backend.tempo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.anitia.backend.task.Task;
import com.anitia.backend.task.TaskRepository;
import com.anitia.backend.utente.Utente;
import com.anitia.backend.utente.UtenteRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

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
 */
@SpringBootTest
@Transactional
class SessioniEOreTest {

    /** Giulia, dai dati di sviluppo: il dipendente senza permessi oltre il pavimento. */
    private static final long GIULIA = 4;

    @Autowired
    private SessioneTaskRepository sessioni;

    @Autowired
    private TaskRepository task;

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
        Task destinazione = unaTask();

        sessione(destinazione, "2026-11-09T09:00:00", "2026-11-09T09:45:00");
        sessione(destinazione, "2026-11-09T14:00:00", "2026-11-09T14:30:00");
        sessioni.flush();

        assertThat(task.sommaOreConsumateDellaTask(destinazione.getId()))
                .isEqualByComparingTo(new BigDecimal("1.25"));
    }

    /** Una sessione ancora aperta non ha ore: non è finita, non è uno zero misurato. */
    @Test
    void unaSessioneApertaNonEntraNellaSomma() {
        Task destinazione = unaTask();

        sessione(destinazione, "2026-11-09T09:00:00", "2026-11-09T10:00:00");
        sessioni.saveAndFlush(SessioneTask.avvia(destinazione, giulia()));

        assertThat(task.sommaOreConsumateDellaTask(destinazione.getId()))
                .isEqualByComparingTo(new BigDecimal("1.00"));
    }

    /** Una sessione annullata esce dai conti, ma la riga resta. */
    @Test
    void unaSessioneAnnullataNonEntraNellaSomma() {
        Task destinazione = unaTask();

        SessioneTask da2Ore = sessione(destinazione, "2026-11-09T09:00:00",
                "2026-11-09T11:00:00");
        sessione(destinazione, "2026-11-09T14:00:00", "2026-11-09T15:00:00");
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
        Task destinazione = unaTask();
        SessioneTask sessione = sessione(destinazione, "2026-11-09T09:00:00",
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
        Task destinazione = unaTask();
        sessioni.saveAndFlush(SessioneTask.avvia(destinazione, giulia()));

        SessioneTask seconda = SessioneTask.avvia(destinazione, giulia());

        assertThat(catchThrowable(() -> sessioni.saveAndFlush(seconda)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Molte sessioni chiuse convivono: è `in_corso` a valere NULL, non zero. */
    @Test
    void molteSessioniChiuseConvivono() {
        Task destinazione = unaTask();

        sessione(destinazione, "2026-11-09T09:00:00", "2026-11-09T10:00:00");
        sessione(destinazione, "2026-11-09T10:00:00", "2026-11-09T11:00:00");
        sessione(destinazione, "2026-11-09T11:00:00", "2026-11-09T12:00:00");
        sessioni.flush();

        assertThat(task.sommaOreConsumateDellaTask(destinazione.getId()))
                .isEqualByComparingTo(new BigDecimal("3.00"));
    }

    // --- aiuti ---------------------------------------------------------------

    /**
     * Una sessione chiusa con estremi scelti.
     *
     * <p>Passa da {@code avvia} e poi da {@code correggi}: non c'è una
     * fabbrica che prenda due istanti, e non deve esserci — una sessione nasce
     * da un avvio, e gli estremi li sposta una correzione.
     */
    private SessioneTask sessione(Task destinazione, String da, String a) {
        SessioneTask sessione = SessioneTask.avvia(destinazione, giulia());
        sessione.correggi(LocalDateTime.parse(da), LocalDateTime.parse(a));
        return sessioni.save(sessione);
    }

    /**
     * Una task qualunque del database di sviluppo.
     *
     * <p>Quale non importa: questi test misurano la somma delle sessioni che
     * scrivono loro, non il contenuto del database. Prendere la prima che
     * c'è, invece di fissare un id, li rende indipendenti da quali task
     * esistano oggi.
     */
    private Task unaTask() {
        return task.findAll().stream().findFirst().orElseThrow(
                () -> new IllegalStateException(
                        "Il database di sviluppo non ha nessuna task: questi test ne "
                                + "vogliono almeno una."));
    }

    private Utente giulia() {
        return utenti.findById(GIULIA).orElseThrow(
                () -> new IllegalStateException("Giulia (4) non c'è nei dati di sviluppo."));
    }
}
