package com.anitia.backend.tempo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessioneTaskRepository extends JpaRepository<SessioneTask, Long> {

    /**
     * La sessione che sta scorrendo per questa persona, se ce n'è una.
     *
     * <p>Può essere una sola: lo garantisce {@code uk_sessione_in_corso}, non
     * questa query. Per questo torna un {@code Optional} e non una lista — se
     * un giorno ne trovasse due, il vincolo sarebbe stato tolto e la cosa
     * giusta è accorgersene qui.
     */
    @Query("SELECT s FROM SessioneTask s JOIN FETCH s.task t JOIN FETCH t.lavoro "
         + "WHERE s.utente.id = :utenteId AND s.chiusaIl IS NULL AND s.annullataIl IS NULL")
    Optional<SessioneTask> trovaInCorso(@Param("utenteId") Long utenteId);

    /** La sessione in corso su una task precisa, per chiuderla quando la task si chiude. */
    @Query("SELECT s FROM SessioneTask s WHERE s.task.id = :taskId "
         + "AND s.chiusaIl IS NULL AND s.annullataIl IS NULL")
    List<SessioneTask> trovaInCorsoDellaTask(@Param("taskId") Long taskId);

    /**
     * Le sessioni di una persona in un intervallo, dalla più recente.
     *
     * <p>Il filtro è su {@code avviataIl}: una sessione appartiene al giorno in
     * cui è cominciata, anche se è stata chiusa dopo la mezzanotte. Metterla a
     * cavallo di due giorni vorrebbe dire spezzarla, e spezzarla vorrebbe dire
     * inventare l'istante in cui tagliarla.
     */
    @Query("SELECT s FROM SessioneTask s JOIN FETCH s.task t JOIN FETCH t.lavoro "
         + "WHERE s.utente.id = :utenteId AND s.annullataIl IS NULL "
         + "AND s.avviataIl >= :da AND s.avviataIl < :a "
         + "ORDER BY s.avviataIl DESC")
    List<SessioneTask> trovaDelPeriodo(@Param("utenteId") Long utenteId,
                                       @Param("da") LocalDateTime da,
                                       @Param("a") LocalDateTime a);

    /** Le chiusure d'ufficio che questa persona non ha ancora guardato. */
    @Query("SELECT s FROM SessioneTask s JOIN FETCH s.task t JOIN FETCH t.lavoro "
         + "WHERE s.utente.id = :utenteId AND s.annullataIl IS NULL "
         + "AND s.confermata = FALSE ORDER BY s.avviataIl")
    List<SessioneTask> trovaDaConfermare(@Param("utenteId") Long utenteId);

    /**
     * Quante chiusure d'ufficio sono in attesa, per persona.
     *
     * <p>È quello che un amministratore guarda per sapere a chi chiedere: non
     * può correggerle lui, quindi la sola cosa che gli serve è l'elenco di chi
     * deve farlo.
     */
    @Query(value = "SELECT s.utente_id AS utenteId, COUNT(*) AS quante "
                 + "FROM sessione_task s "
                 + "WHERE s.annullata_il IS NULL AND s.confermata = FALSE "
                 + "GROUP BY s.utente_id",
            nativeQuery = true)
    List<DaConfermare> contaDaConfermare();

    interface DaConfermare {
        Long getUtenteId();

        long getQuante();
    }

    /**
     * Chiude d'ufficio tutte le sessioni ancora aperte sulle task di un lavoro.
     *
     * <p>Serve ad {@code annulla} di un lavoro, che porta le sue task ad
     * {@code ANNULLATO} con una UPDATE di massa <strong>scavalcando</strong>
     * {@code ServizioTask.cambiaStato}: senza questa, le sessioni aperte su
     * quelle task resterebbero aperte per sempre, e la persona non riuscirebbe
     * più ad avviarne un'altra perché il vincolo di unicità glielo
     * impedirebbe.
     *
     * <p>Le chiude ad adesso e non al tetto: la sessione finisce nel momento
     * in cui il lavoro viene annullato, che è un istante noto e vero.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE sessione_task s "
                 + "JOIN task t ON t.id = s.task_id "
                 + "SET s.chiusa_il = NOW(), s.origine = 'AUTOMATICA', s.confermata = FALSE "
                 + "WHERE t.lavoro_id = :lavoroId "
                 + "AND s.chiusa_il IS NULL AND s.annullata_il IS NULL",
            nativeQuery = true)
    int chiudiTutteDelLavoro(@Param("lavoroId") Long lavoroId);
}
