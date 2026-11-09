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
     * assegnazioni e dalle sessioni di lavoro.
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
     * Le ore misurate sulle task di un lavoro.
     *
     * <p>Query nativa perché {@code sessione_task} non è mappata come entità
     * in questo package: il registro del tempo vive nel dominio del tempo, e
     * da qui si legge soltanto la somma.
     *
     * <p><strong>Le sessioni misurano minuti, le ore sono il totale.</strong>
     * La divisione per 60 sta qui e non nel chiamante perché qui si fa una
     * volta sulla somma: arrotondare prima, sessione per sessione, perderebbe
     * qualcosa a ogni riga — e quelle perdite si sommano, su un numero che
     * decide se un lavoro sfora il budget.
     *
     * <p>Si contano solo le sessioni <strong>chiuse e non annullate</strong>.
     * Una sessione aperta non ha minuti (la colonna generata vale NULL finché
     * non c'è una fine), quindi non entrerebbe nella somma comunque, ma il
     * filtro lo dice invece di lasciarlo dedurre.
     *
     * <p>Una sessione <em>non confermata</em> ci entra, di proposito: è una
     * chiusura d'ufficio che nessuno ha ancora guardato, ma sotto c'è lavoro
     * vero, ed escluderla farebbe sparire ore dal consuntivo. Che ci sia lo
     * dice {@code TempoDiUnaPersona.minutiNonConfermati}.
     */
    @Query(value = "SELECT ROUND(COALESCE(SUM(s.minuti), 0) / 60, 2) FROM sessione_task s "
                 + "JOIN task t ON t.id = s.task_id "
                 + "WHERE t.lavoro_id = :lavoroId "
                 + "AND s.chiusa_il IS NOT NULL AND s.annullata_il IS NULL",
            nativeQuery = true)
    BigDecimal sommaOreConsumate(@Param("lavoroId") Long lavoroId);

    /** Le ore consumate, per ciascuno dei lavori indicati. */
    @Query(value = "SELECT t.lavoro_id AS lavoroId, "
                 + "       ROUND(COALESCE(SUM(s.minuti), 0) / 60, 2) AS ore "
                 + "FROM sessione_task s JOIN task t ON t.id = s.task_id "
                 + "WHERE t.lavoro_id IN (:lavoroId) "
                 + "AND s.chiusa_il IS NOT NULL AND s.annullata_il IS NULL "
                 + "GROUP BY t.lavoro_id",
            nativeQuery = true)
    List<OrePerLavoro> sommaOreConsumatePerLavoro(@Param("lavoroId") Collection<Long> lavoroId);

    /** Le ore consumate, task per task, dentro un lavoro. */
    @Query(value = "SELECT s.task_id AS taskId, "
                 + "       ROUND(COALESCE(SUM(s.minuti), 0) / 60, 2) AS ore "
                 + "FROM sessione_task s JOIN task t ON t.id = s.task_id "
                 + "WHERE t.lavoro_id = :lavoroId "
                 + "AND s.chiusa_il IS NOT NULL AND s.annullata_il IS NULL "
                 + "GROUP BY s.task_id",
            nativeQuery = true)
    List<OrePerTask> sommaOreConsumatePerTask(@Param("lavoroId") Long lavoroId);

    /**
     * Le ore consumate da una singola task.
     *
     * <p>⚠️ È quella che legge {@code ServizioOre.rilasciaResiduo} per
     * decidere quante ore tornano nel pool quando una task si chiude, ed è il
     * solo punto in cui una somma di ore <strong>scrive</strong> sul database.
     * Chi chiude una task deve aver chiuso prima le sessioni ancora aperte su
     * di essa: i loro minuti non sarebbero contati qui, e il residuo
     * restituito risulterebbe più grande del vero — su righe di
     * {@code movimento_ore} che non si cancellano mai.
     */
    @Query(value = "SELECT ROUND(COALESCE(SUM(s.minuti), 0) / 60, 2) FROM sessione_task s "
                 + "WHERE s.task_id = :taskId "
                 + "AND s.chiusa_il IS NOT NULL AND s.annullata_il IS NULL",
            nativeQuery = true)
    BigDecimal sommaOreConsumateDellaTask(@Param("taskId") Long taskId);

    /**
     * Le task di un lavoro che hanno consumato più ore di quante ne hanno
     * allocate, dalla più in sforamento.
     *
     * <p>Lo sforamento <strong>non è una colonna</strong>: è questo confronto.
     * Metterlo in una query invece che in un flag salvato significa che
     * compare da sé appena il tempo misurato supera il budget, senza che
     * nessun codice debba ricordarsi di marcare la task — e adesso compare
     * mentre il dipendente ci sta lavorando, non la sera in cui si ricorda di
     * dichiarare.
     *
     * <p>{@code HAVING} e non {@code WHERE} perché il confronto è su una somma
     * aggregata; {@code LEFT JOIN} perché una task senza tempo registrato non
     * deve sparire dal raggruppamento — anche se poi l'{@code HAVING} la
     * scarta, dato che zero non supera nessuna allocazione. Le condizioni
     * sulla sessione stanno nella {@code ON} e non nella {@code WHERE}: nella
     * {@code WHERE} butterebbero via le task senza sessioni, che è proprio
     * quello che il {@code LEFT JOIN} serve a conservare.
     */
    @Query(value = "SELECT t.id AS taskId, t.titolo AS titolo, "
                 + "       t.ore_allocate AS oreAllocate, "
                 + "       ROUND(COALESCE(SUM(s.minuti), 0) / 60, 2) AS oreConsumate "
                 + "FROM task t LEFT JOIN sessione_task s "
                 + "  ON s.task_id = t.id "
                 + "  AND s.chiusa_il IS NOT NULL AND s.annullata_il IS NULL "
                 + "WHERE t.lavoro_id = :lavoroId "
                 + "GROUP BY t.id, t.titolo, t.ore_allocate "
                 + "HAVING ROUND(COALESCE(SUM(s.minuti), 0) / 60, 2) > t.ore_allocate "
                 + "ORDER BY (ROUND(COALESCE(SUM(s.minuti), 0) / 60, 2) - t.ore_allocate) DESC",
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
     * somma sarebbe un raggruppamento su tutta {@code sessione_task}, cioè
     * sulla tabella destinata a crescere più di ogni altra nel database — una
     * riga per ogni volta che qualcuno avvia e mette in pausa, molte al giorno
     * per persona — per poi buttarne via quasi tutte le righe in memoria.
     */
    @Query(value = "SELECT s.task_id AS taskId, "
                 + "       ROUND(COALESCE(SUM(s.minuti), 0) / 60, 2) AS ore "
                 + "FROM sessione_task s "
                 + "WHERE s.chiusa_il IS NOT NULL AND s.annullata_il IS NULL "
                 + "AND s.task_id IN (SELECT a.task_id FROM assegnazione_task a "
                 + "                  WHERE a.utente_id = :utenteId "
                 + "                    AND a.rimossa_il IS NULL) "
                 + "GROUP BY s.task_id",
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
     * <p>Il collegamento alla persona è diretto: {@code sessione_task} porta
     * {@code utente_id}, perché una sessione è di chi l'ha lavorata. Prima
     * serviva un passaggio da {@code dichiarazione}, dove viveva l'unica
     * traccia di chi avesse registrato le ore; è una delle ragioni per cui la
     * colonna sta sulla sessione.
     */
    @Query(value = "SELECT s.task_id AS taskId, "
                 + "       ROUND(COALESCE(SUM(s.minuti), 0) / 60, 2) AS ore "
                 + "FROM sessione_task s "
                 + "WHERE s.utente_id = :utenteId "
                 + "AND s.chiusa_il IS NOT NULL AND s.annullata_il IS NULL "
                 + "GROUP BY s.task_id",
            nativeQuery = true)
    List<OrePerTask> sommaMieOrePerTask(@Param("utenteId") Long utenteId);

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
