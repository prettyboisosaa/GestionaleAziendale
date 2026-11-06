package com.anitia.backend.task;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * Quante task ha un lavoro. Serve a decidere se il lavoro può essere
     * cancellato: {@code task.lavoro_id} è una chiave esterna senza
     * ON DELETE CASCADE, e con almeno una task la cancellazione è impossibile
     * — e sarebbe sbagliata, perché le task sono referenziate dalle
     * assegnazioni e dalle voci delle dichiarazioni.
     *
     * <p>Il nome attraversa la relazione: {@code lavoro.id}.
     */
    long countByLavoroId(Long lavoroId);

    /**
     * Porta ad {@code ANNULLATO} tutte le task del lavoro che non lo sono
     * ancora, comprese quelle completate: annullare un lavoro annulla tutto
     * quello che gli sta sotto, perche' una task completata di un lavoro che
     * non si fara' non e' un risultato da esibire.
     *
     * <p>Aggiornamento in blocco e non un ciclo sulle entita': un lavoro
     * grosso ha decine di task, e caricarle tutte per cambiare una colonna
     * vorrebbe dire altrettante SELECT e altrettante UPDATE.
     *
     * <p>Il prezzo e' che un UPDATE cosi' non passa dal ciclo di vita di JPA:
     * non incrementa {@code versione} e non aggiorna le entita' gia' in
     * memoria. Da qui {@code flushAutomatically}, che manda al database le
     * modifiche pendenti prima di questa, e {@code clearAutomatically}, che
     * svuota il contesto dopo: senza, una task letta prima nella stessa
     * transazione sovrascriverebbe lo stato appena impostato.
     *
     * <p>Le ore residue non si rilasciano: il monte ore libero di un lavoro
     * che non si fara' e' un numero senza significato, e restituirlo al pool
     * lo farebbe sembrare disponibile.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Task t SET t.stato = com.anitia.backend.task.StatoTask.ANNULLATO "
         + "WHERE t.lavoro.id = :lavoroId "
         + "  AND t.stato <> com.anitia.backend.task.StatoTask.ANNULLATO")
    int annullaTutteDelLavoro(@Param("lavoroId") Long lavoroId);

    List<Task> findByLavoroIdOrderByDataInizioAscIdAsc(Long lavoroId);

    /**
     * La task, ma solo se appartiene a quel lavoro.
     *
     * <p>È il controllo che impedisce di operare su una task di un lavoro che
     * non si può vedere: con il solo id della task, un indirizzo come
     * {@code /api/lavori/3/task/1} toccherebbe la task 1 anche se sta nel
     * lavoro 1. Chiedendo la coppia, quel caso diventa un 404.
     */
    Optional<Task> findByIdAndLavoroId(Long id, Long lavoroId);

    // --- somme delle ore ----------------------------------------------------
    //
    // Nessuno di questi totali è salvato in una colonna, ed è una scelta: le
    // ore libere di un lavoro e le ore consumate di una task si ricavano
    // sempre da qui. Un campo mantenuto in parallelo sarebbe una seconda
    // verità, e prima o poi divergerebbe da quella vera.
    //
    // Le varianti «PerLavoro» esistono perché gli elenchi mostrano più lavori
    // o più task insieme: con una query per riga, e open-in-view disattivato,
    // si pagherebbero N interrogazioni per una schermata sola.

    /** Le ore allocate alle task di un lavoro. Zero se non ne ha nessuna. */
    @Query("SELECT COALESCE(SUM(t.oreAllocate), 0) FROM Task t WHERE t.lavoro.id = :lavoroId")
    BigDecimal sommaOreAllocate(@Param("lavoroId") Long lavoroId);

    /**
     * Le ore allocate, per ciascuno dei lavori indicati. I lavori senza task
     * non compaiono fra i risultati: chi legge deve trattare l'assenza come
     * zero.
     *
     * <p>⚠️ {@code IN} con una collezione vuota non è SQL valido: chi chiama
     * deve fermarsi prima, come fa già {@code ServizioLavori.elenco}.
     */
    @Query(value = "SELECT lavoro_id AS lavoroId, COALESCE(SUM(ore_allocate), 0) AS ore "
                 + "FROM task WHERE lavoro_id IN (:lavoroId) GROUP BY lavoro_id",
            nativeQuery = true)
    List<OrePerLavoro> sommaOreAllocatePerLavoro(@Param("lavoroId") Collection<Long> lavoroId);

    /**
     * Le ore registrate dai dipendenti sulle task di un lavoro.
     *
     * <p>Query nativa perché {@code voce_dichiarazione} non è mappata come
     * entità in questo package: il registro delle ore vive nel dominio delle
     * dichiarazioni, e da qui si legge soltanto la somma.
     */
    @Query(value = "SELECT COALESCE(SUM(v.ore), 0) FROM voce_dichiarazione v "
                 + "JOIN task t ON t.id = v.task_id WHERE t.lavoro_id = :lavoroId",
            nativeQuery = true)
    BigDecimal sommaOreConsumate(@Param("lavoroId") Long lavoroId);

    /** Le ore consumate, per ciascuno dei lavori indicati. */
    @Query(value = "SELECT t.lavoro_id AS lavoroId, COALESCE(SUM(v.ore), 0) AS ore "
                 + "FROM voce_dichiarazione v JOIN task t ON t.id = v.task_id "
                 + "WHERE t.lavoro_id IN (:lavoroId) GROUP BY t.lavoro_id",
            nativeQuery = true)
    List<OrePerLavoro> sommaOreConsumatePerLavoro(@Param("lavoroId") Collection<Long> lavoroId);

    /** Le ore consumate, task per task, dentro un lavoro. */
    @Query(value = "SELECT v.task_id AS taskId, COALESCE(SUM(v.ore), 0) AS ore "
                 + "FROM voce_dichiarazione v JOIN task t ON t.id = v.task_id "
                 + "WHERE t.lavoro_id = :lavoroId GROUP BY v.task_id",
            nativeQuery = true)
    List<OrePerTask> sommaOreConsumatePerTask(@Param("lavoroId") Long lavoroId);

    /** Le ore consumate da una singola task. */
    @Query(value = "SELECT COALESCE(SUM(v.ore), 0) FROM voce_dichiarazione v "
                 + "WHERE v.task_id = :taskId",
            nativeQuery = true)
    BigDecimal sommaOreConsumateDellaTask(@Param("taskId") Long taskId);

    /**
     * Le task di un lavoro che hanno consumato più ore di quante ne hanno
     * allocate, dalla più in sforamento.
     *
     * <p>Lo sforamento <strong>non è una colonna</strong>: è questo confronto.
     * Metterlo in una query invece che in un flag salvato significa che
     * compare da sé appena un dipendente registra la giornata che fa superare
     * il budget, senza che nessun codice debba ricordarsi di marcare la task.
     *
     * <p>{@code HAVING} e non {@code WHERE} perché il confronto è su una somma
     * aggregata; {@code LEFT JOIN} perché una task senza ore registrate non
     * deve sparire dal raggruppamento — anche se poi l'{@code HAVING} la
     * scarta, dato che zero non supera nessuna allocazione.
     */
    @Query(value = "SELECT t.id AS taskId, t.titolo AS titolo, "
                 + "       t.ore_allocate AS oreAllocate, "
                 + "       COALESCE(SUM(v.ore), 0) AS oreConsumate "
                 + "FROM task t LEFT JOIN voce_dichiarazione v ON v.task_id = t.id "
                 + "WHERE t.lavoro_id = :lavoroId "
                 + "GROUP BY t.id, t.titolo, t.ore_allocate "
                 + "HAVING COALESCE(SUM(v.ore), 0) > t.ore_allocate "
                 + "ORDER BY (COALESCE(SUM(v.ore), 0) - t.ore_allocate) DESC",
            nativeQuery = true)
    List<Sforamento> trovaSforamenti(@Param("lavoroId") Long lavoroId);

    /** Una riga di {@link #trovaSforamenti(Long)}. */
    interface Sforamento {
        Long getTaskId();

        String getTitolo();

        BigDecimal getOreAllocate();

        BigDecimal getOreConsumate();
    }

    /**
     * Le ore consumate, da chiunque, sulle task a cui una persona è
     * assegnata.
     *
     * <p>Serve a «Le mie task»: il numero da confrontare con
     * {@code ore_allocate} per sapere se una task sta finendo il budget è il
     * totale di <em>tutti</em> gli assegnati, non solo il proprio.
     *
     * <p>La sottoquery nel {@code IN} non è un dettaglio: senza di essa la
     * somma sarebbe un raggruppamento su tutta {@code voce_dichiarazione},
     * cioè sulla tabella destinata a crescere più di ogni altra nel database,
     * per poi buttarne via quasi tutte le righe in memoria.
     */
    @Query(value = "SELECT v.task_id AS taskId, COALESCE(SUM(v.ore), 0) AS ore "
                 + "FROM voce_dichiarazione v "
                 + "WHERE v.task_id IN (SELECT a.task_id FROM assegnazione_task a "
                 + "                    WHERE a.utente_id = :utenteId "
                 + "                      AND a.rimossa_il IS NULL) "
                 + "GROUP BY v.task_id",
            nativeQuery = true)
    List<OrePerTask> sommaOreConsumateDelleMieTask(@Param("utenteId") Long utenteId);

    /**
     * Le ore che una persona ha registrato, task per task.
     *
     * <p>Distinta dalla precedente perché risponde a un'altra domanda:
     * «quanto ci ho messo io», non «quanto resta alla task». Tenerle in un
     * numero solo nasconderebbe il caso che conta su una task condivisa — la
     * task è quasi esaurita, ma non per opera mia.
     *
     * <p>Il collegamento alla persona passa da {@code dichiarazione}, perché è
     * lì che vive {@code utente_id}: una voce appartiene alla giornata, e la
     * giornata a chi l'ha compilata.
     */
    @Query(value = "SELECT v.task_id AS taskId, COALESCE(SUM(v.ore), 0) AS ore "
                 + "FROM voce_dichiarazione v "
                 + "JOIN dichiarazione d ON d.id = v.dichiarazione_id "
                 + "WHERE d.utente_id = :utenteId "
                 + "GROUP BY v.task_id",
            nativeQuery = true)
    List<OrePerTask> sommaMieOrePerTask(@Param("utenteId") Long utenteId);

    /**
     * Le task aperte a cui una persona è assegnata: quelle su cui può
     * registrare ore.
     *
     * <p>Il filtro è esattamente la regola del monte ore — «un dipendente
     * registra ore solo sulle task aperte a cui è assegnato» — scritta una
     * volta qui, così la tendina della dichiarazione non può proporre una
     * task che il servizio poi rifiuterebbe.
     *
     * <p>Il lavoro deve essere aperto quanto la task: una task attiva dentro
     * un lavoro completato o annullato non e' selezionabile, e proporla nella
     * tendina vorrebbe dire far scegliere qualcosa che il servizio poi
     * rifiuta.
     *
     * <p>{@code a.rimossa_il IS NULL} e non la colonna generata
     * {@code attiva}: quella serve al vincolo di unicità e vale NULL quando
     * l'assegnazione è stata rimossa. Query nativa perché
     * {@code assegnazione_task} non è mappata come entità raggiungibile da
     * qui.
     */
    @Query(value = "SELECT t.id AS taskId, t.titolo AS titolo, "
                 + "       l.id AS lavoroId, l.codice AS codiceLavoro, l.nome AS nomeLavoro "
                 + "FROM task t "
                 + "JOIN lavoro l ON l.id = t.lavoro_id "
                 + "JOIN assegnazione_task a ON a.task_id = t.id "
                 + "WHERE a.utente_id = :utenteId AND a.rimossa_il IS NULL "
                 + "  AND t.stato IN ('ATTIVO', 'IN_CORSO') "
                 + "  AND l.stato IN ('ATTIVO', 'IN_CORSO') "
                 + "ORDER BY l.codice, t.titolo",
            nativeQuery = true)
    List<TaskAperta> trovaAperteAssegnateA(@Param("utenteId") Long utenteId);

    /** Una riga di {@link #trovaAperteAssegnateA(Long)}. */
    interface TaskAperta {
        Long getTaskId();

        String getTitolo();

        Long getLavoroId();

        String getCodiceLavoro();

        String getNomeLavoro();
    }

    /** Una riga delle somme raggruppate per lavoro. */
    interface OrePerLavoro {
        Long getLavoroId();

        BigDecimal getOre();
    }

    /** Una riga delle somme raggruppate per task. */
    interface OrePerTask {
        Long getTaskId();

        BigDecimal getOre();
    }
}
