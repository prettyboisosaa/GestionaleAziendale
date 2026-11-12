package com.anitia.backend.report;

import com.anitia.backend.tempo.SessioneTask;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Le somme di minuti che il report legge, raggruppate dal database.
 *
 * <p>Sta in un repository suo e non in {@code SessioneTaskRepository} perché
 * quello restituisce <em>sessioni</em> — righe da mostrare una per una — e
 * queste due query non restituiscono sessioni: restituiscono totali, e non
 * hanno nessun uso fuori dal report.
 *
 * <p>Query <strong>native</strong> come in {@code TaskRepository}, per la
 * stessa ragione: {@code sessione_task.minuti} è una colonna generata dal
 * database ({@code TIMESTAMPDIFF(MINUTE, avviata_il, chiusa_il)}) e non esiste
 * come campo dell'entità, quindi JPQL non la può sommare. Sommare in memoria
 * vorrebbe dire tirare su ogni sessione dell'azienda per contarne i minuti.
 *
 * <p>⚠️ Ogni {@code SUM} è avvolta in {@code CAST(... AS SIGNED)}. Senza,
 * MySQL risponde {@code DECIMAL} e le proiezioni qui sotto si ritrovano un
 * {@code BigDecimal} dove dichiarano un {@code Long}, con un errore di
 * conversione a runtime che nessun compilatore avrebbe visto.
 *
 * <p>Tre regole tornano in entrambe le query, e sono la convenzione di questo
 * progetto per «ore vere» (la stessa di {@code TaskRepository.sommaOreConsumate}):
 * <ul>
 *   <li>{@code annullata_il IS NULL} — una sessione annullata non è accaduta;
 *   <li>{@code chiusa_il IS NOT NULL} — una sessione aperta non ha minuti:
 *       {@code minuti} resta NULL finché non si chiude;
 *   <li>le <strong>non confermate si contano</strong>, e si contano anche a
 *       parte. È la scelta che {@code TempoDiUnaPersona} mette per iscritto:
 *       escluderle farebbe sparire lavoro vero, ma chi legge deve sapere
 *       quanta parte del numero nessuno ha ancora guardato.
 * </ul>
 *
 * <p>Il periodo si filtra su {@code avviata_il}, come
 * {@code SessioneTaskRepository.trovaDelPeriodo}: una sessione dalle 23 alle 2
 * sta per intero nel giorno in cui è cominciata. È un'approssimazione, ma è
 * <em>la stessa</em> del resto del gestionale, e due consuntivi che dividono i
 * giorni in modo diverso sarebbero peggio di uno approssimato.
 */
public interface ReportRepository extends JpaRepository<SessioneTask, Long> {

    /**
     * Quanto ha registrato ciascuno nel periodo: una riga per persona.
     *
     * <p>⚠️ I filtri delle sessioni stanno nella {@code ON} e non nella
     * {@code WHERE}, e non è una questione di stile: nella {@code WHERE} il
     * LEFT JOIN degenera in un INNER JOIN — una riga di {@code utente} senza
     * sessioni nel periodo produce {@code s.*} tutti NULL, che nessuna
     * condizione {@code WHERE} supera — e chi non ha registrato niente
     * sparirebbe dall'elenco. Proprio chi ha zero ore è una delle cose per cui
     * l'amministratore apre il report.
     *
     * <p>La {@code HAVING} tiene tutti gli attivi, anche a zero, e dei
     * disattivati solo quelli con ore nel periodo: il lavoro di chi non c'è
     * più è stato fatto per davvero e non va cancellato dal consuntivo, ma un
     * elenco di persone spente a zero ore sarebbe solo rumore.
     */
    @Query(value = """
            SELECT u.id AS utenteId, u.nome AS nome, u.cognome AS cognome,
                   u.attivo AS attivo,
                   CAST(COALESCE(SUM(s.minuti), 0) AS SIGNED) AS minutiTotali,
                   CAST(COALESCE(SUM(CASE WHEN s.confermata = 0 THEN s.minuti END), 0)
                        AS SIGNED) AS minutiNonConfermati
            FROM utente u
            LEFT JOIN sessione_task s ON s.utente_id = u.id
              AND s.annullata_il IS NULL AND s.chiusa_il IS NOT NULL
              AND s.avviata_il >= :da AND s.avviata_il < :a
            GROUP BY u.id, u.nome, u.cognome, u.attivo
            HAVING u.attivo = TRUE OR SUM(s.minuti) > 0
            ORDER BY minutiTotali DESC, u.cognome, u.nome
            """, nativeQuery = true)
    List<RigaPersona> perPersona(@Param("da") LocalDateTime da, @Param("a") LocalDateTime a);

    /**
     * Su che cosa è andato il tempo di <em>una</em> persona: una riga per
     * task, per attività di un lavoro e per attività senza lavoro.
     *
     * <p>Una query piatta e non tre, perché le tre famiglie di tempo (task di
     * un lavoro, attività su un lavoro senza task, attività senza lavoro) sono
     * la stessa tabella guardata da tre angoli: tenerle insieme garantisce che
     * le parti sommino al tutto, e quindi che le percentuali non mentano. A
     * distinguerle pensa {@link ServizioReport}, leggendo quali colonne sono
     * NULL.
     *
     * <p>⚠️ I due join sono <strong>esterni</strong> e il lavoro è
     * {@code COALESCE(t.lavoro_id, s.lavoro_id)}: una sessione può citare il
     * lavoro per il tramite della sua task, <em>oppure</em> direttamente senza
     * task (è il caso di {@code MANUTENZIONE_CLIENTE}), oppure non citarne
     * nessuno (una riunione). È la stessa regola che
     * {@code SessioneTask.lavoroCitato()} applica in Java. Con un JOIN interno
     * le ultime due famiglie svanirebbero, e il totale del report non
     * tornerebbe con quello che la persona vede nel proprio calendario.
     *
     * <p>Il {@code CASE} sul tipo di attività serve a non spezzare una task:
     * dentro una task il tipo è sempre {@code LAVORO_TASK} e raggrupparci
     * sopra non aggiunge niente, mentre per le attività senza task il tipo
     * <em>è</em> l'unica cosa che le descrive.
     */
    @Query(value = """
            SELECT COALESCE(t.lavoro_id, s.lavoro_id) AS lavoroId,
                   l.codice AS lavoroCodice, l.nome AS lavoroNome,
                   s.task_id AS taskId, t.titolo AS taskTitolo,
                   CASE WHEN s.task_id IS NULL THEN s.tipo_attivita END AS tipoAttivita,
                   CAST(SUM(s.minuti) AS SIGNED) AS minuti,
                   CAST(COALESCE(SUM(CASE WHEN s.confermata = 0 THEN s.minuti END), 0)
                        AS SIGNED) AS minutiNonConfermati
            FROM sessione_task s
            LEFT JOIN task t ON t.id = s.task_id
            LEFT JOIN lavoro l ON l.id = COALESCE(t.lavoro_id, s.lavoro_id)
            WHERE s.utente_id = :utenteId
              AND s.annullata_il IS NULL AND s.chiusa_il IS NOT NULL
              AND s.avviata_il >= :da AND s.avviata_il < :a
            GROUP BY lavoroId, l.codice, l.nome, s.task_id, t.titolo,
                     CASE WHEN s.task_id IS NULL THEN s.tipo_attivita END
            ORDER BY minuti DESC
            """, nativeQuery = true)
    List<RigaDettaglio> dettaglioDiUnaPersona(@Param("utenteId") Long utenteId,
                                              @Param("da") LocalDateTime da,
                                              @Param("a") LocalDateTime a);

    /** Una riga della panoramica: una persona e i suoi totali. */
    interface RigaPersona {
        Long getUtenteId();

        String getNome();

        String getCognome();

        /**
         * Boxed e non {@code boolean}: {@code attivo} è un TINYINT(1) che il
         * driver consegna come {@code Boolean}, ma una proiezione nativa non
         * promette mai di non restituire NULL.
         */
        Boolean getAttivo();

        Long getMinutiTotali();

        Long getMinutiNonConfermati();
    }

    /**
     * Una riga del dettaglio. Quasi tutto è annullabile, ed è il punto: è
     * dalla combinazione dei NULL che si capisce di quale famiglia di tempo si
     * tratta.
     */
    interface RigaDettaglio {
        /** NULL per le attività che non appartengono a nessun lavoro. */
        Long getLavoroId();

        String getLavoroCodice();

        String getLavoroNome();

        /** NULL per le attività registrate senza task. */
        Long getTaskId();

        String getTaskTitolo();

        /** Valorizzato <strong>solo</strong> quando la task manca. */
        String getTipoAttivita();

        Long getMinuti();

        Long getMinutiNonConfermati();
    }
}
