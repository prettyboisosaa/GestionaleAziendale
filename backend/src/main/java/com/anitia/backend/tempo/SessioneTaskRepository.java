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
     *
     * <p>Il join sulla task resta <strong>interno</strong> mentre le altre
     * query di questa interfaccia sono passate a {@code LEFT JOIN} per le
     * sessioni senza task: una sessione <em>aperta</em> ha sempre una task,
     * perché la apre solo il timer e il timer parte da una task. Le attività
     * senza task nascono già chiuse, scritte a mano. Se un giorno qualcosa
     * aprisse una sessione senza task, questa query la salterebbe — ed è
     * meglio che se ne accorga qui, dove la regola è scritta, che non si veda
     * affatto.
     */
    @Query("SELECT s FROM SessioneTask s JOIN FETCH s.task t JOIN FETCH t.lavoro "
         + "WHERE s.utente.id = :utenteId AND s.chiusaIl IS NULL AND s.annullataIl IS NULL")
    Optional<SessioneTask> trovaInCorso(@Param("utenteId") Long utenteId);

    /**
     * La sessione in corso su una task precisa, per chiuderla quando la task
     * si chiude. Interroga la task, quindi le attività senza task non la
     * riguardano: nessuna di loro è mai aperta.
     */
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
     *
     * <p>⚠️ I join sono <strong>esterni</strong> e devono restarlo. Una
     * sessione può non avere una task — una riunione, una manutenzione
     * interna — e con un join interno quelle righe spariscono dalla settimana
     * senza che nessun errore lo dica: la griglia mostrerebbe un giorno
     * mezzo vuoto e il totale non tornerebbe. Lo stesso vale per
     * {@code s.lavoro}, che è popolato solo quando la task manca.
     */
    @Query("SELECT s FROM SessioneTask s LEFT JOIN FETCH s.task t LEFT JOIN FETCH t.lavoro "
         + "LEFT JOIN FETCH s.lavoro "
         + "WHERE s.utente.id = :utenteId AND s.annullataIl IS NULL "
         + "AND s.avviataIl >= :da AND s.avviataIl < :a "
         + "ORDER BY s.avviataIl DESC")
    List<SessioneTask> trovaDelPeriodo(@Param("utenteId") Long utenteId,
                                       @Param("da") LocalDateTime da,
                                       @Param("a") LocalDateTime a);

    /**
     * Le sessioni di una persona che coprono un pezzo dell'intervallo dato.
     *
     * <p>Serve a tenere in piedi la proprietà su cui si appoggia tutto il
     * resto: le ore di una persona non si sovrappongono, quindi si possono
     * sommare senza chiedersi quale sia quella vera.
     * {@code uk_sessione_in_corso} garantisce una sola sessione <em>aperta</em>
     * e non si occupa di quelle chiuse — finora non serviva, perché scriveva
     * solo il timer e il timer chiude la precedente quando ne apre un'altra.
     *
     * <p>{@code escludi} è la sessione che si sta correggendo: senza, si
     * troverebbe sempre sovrapposta a sé stessa. Con {@code null} non esclude
     * nulla, ed è il caso dell'inserimento.
     *
     * <p>Due intervalli si sovrappongono se ciascuno comincia prima che
     * l'altro finisca. Una sessione <strong>aperta</strong> conta come se si
     * estendesse all'infinito: è un filo severo, ma l'alternativa sarebbe
     * decidere dentro la query fino a quando arriva «adesso», e una sessione
     * che sta scorrendo è esattamente quella su cui non si deve scrivere.
     *
     * <p>⚠️ Anche qui i join sono <strong>esterni</strong>, e qui il danno di
     * un join interno sarebbe peggiore che altrove: le attività senza task non
     * farebbero da ostacolo a niente, e si potrebbero scrivere due ore sopra
     * una riunione già registrata. Le ore di una persona si sommano solo
     * perché non si sovrappongono.
     */
    @Query("SELECT s FROM SessioneTask s LEFT JOIN FETCH s.task t LEFT JOIN FETCH t.lavoro "
         + "LEFT JOIN FETCH s.lavoro "
         + "WHERE s.utente.id = :utenteId AND s.annullataIl IS NULL "
         + "AND (:escludi IS NULL OR s.id <> :escludi) "
         + "AND s.avviataIl < :a AND (s.chiusaIl IS NULL OR s.chiusaIl > :da) "
         + "ORDER BY s.avviataIl")
    List<SessioneTask> trovaSovrapposte(@Param("utenteId") Long utenteId,
                                        @Param("da") LocalDateTime da,
                                        @Param("a") LocalDateTime a,
                                        @Param("escludi") Long escludi);

    /** Le chiusure d'ufficio che questa persona non ha ancora guardato. */
    @Query("SELECT s FROM SessioneTask s LEFT JOIN FETCH s.task t LEFT JOIN FETCH t.lavoro "
         + "LEFT JOIN FETCH s.lavoro "
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
     *
     * <p>Il {@code JOIN} sulla task resta interno e non guarda
     * {@code s.lavoro_id}: le attività che citano un lavoro senza averne una
     * task nascono già chiuse, quindi non ce n'è mai una da chiudere qui.
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
