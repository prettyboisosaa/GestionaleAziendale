package com.anitia.backend.task;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssegnazioneTaskRepository extends JpaRepository<AssegnazioneTask, Long> {

    /**
     * L'assegnazione attiva di una persona su una task, se c'è.
     * {@code RimossaIlIsNull} è il modo di dire «attiva» senza toccare la
     * colonna generata.
     */
    Optional<AssegnazioneTask> findByTaskIdAndUtenteIdAndRimossaIlIsNull(Long taskId,
                                                                        Long utenteId);

    /**
     * Gli assegnatari attivi di tutte le task di un lavoro, in una query sola:
     * l'elenco li raggruppa in memoria, invece di interrogare il database una
     * volta per task.
     */
    @Query(value = "SELECT a.task_id AS taskId, u.id AS utenteId, "
                 + "       u.nome AS nome, u.cognome AS cognome "
                 + "FROM assegnazione_task a "
                 + "JOIN utente u ON u.id = a.utente_id "
                 + "JOIN task   t ON t.id = a.task_id "
                 + "WHERE t.lavoro_id = :lavoroId AND a.rimossa_il IS NULL "
                 + "ORDER BY u.cognome, u.nome",
            nativeQuery = true)
    List<AssegnazioneDiTask> trovaAttivePerLavoro(@Param("lavoroId") Long lavoroId);

    /**
     * Le assegnazioni attive di una persona, di ogni lavoro.
     *
     * <p>È la lettura su cui poggia tutta la sezione del dipendente: «Le mie
     * task» la usa per l'elenco, il calendario per sapere quali inizi e quali
     * scadenze segnare.
     *
     * <p>I tre {@code JOIN FETCH} non sono un'ottimizzazione facoltativa:
     * {@code open-in-view} è disattivato, quindi fuori dalla transazione le
     * relazioni lazy non si possono più leggere, e task, lavoro e responsabile
     * servono tutti e tre per ogni riga. Senza il fetch sarebbero tre query
     * per task invece di una per tutto.
     *
     * <p>Il responsabile è un {@code LEFT JOIN}: un lavoro può non averne
     * ancora uno, e un join interno farebbe sparire le sue task dall'elenco di
     * chi ci lavora.
     */
    @Query("SELECT a FROM AssegnazioneTask a "
         + "JOIN FETCH a.task t "
         + "JOIN FETCH t.lavoro l "
         + "LEFT JOIN FETCH l.responsabile "
         + "WHERE a.utente.id = :utenteId AND a.rimossaIl IS NULL")
    List<AssegnazioneTask> trovaAttiveDiUtente(@Param("utenteId") Long utenteId);

    /** Una riga di {@link #trovaAttivePerLavoro(Long)}. */
    interface AssegnazioneDiTask {
        Long getTaskId();

        Long getUtenteId();

        String getNome();

        String getCognome();
    }
}
