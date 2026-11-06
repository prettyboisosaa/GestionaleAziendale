package com.anitia.backend.lavoro;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LavoroRepository extends JpaRepository<Lavoro, Long> {

    /**
     * L'ordine di ogni elenco: prima i lavori vivi, poi gli annullati, e
     * dentro i due gruppi il codice.
     *
     * <p>Gli annullati vanno in fondo perche' compaiono solo quando li si
     * chiede espressamente, per consultarli: mescolati al resto
     * spezzerebbero la lettura dell'elenco di lavoro.
     *
     * <p>E' una costante e non quattro copie della stessa {@code ORDER BY}:
     * le query che la usano sono quattro, e quattro ordinamenti scritti a
     * mano sono quattro occasioni di cambiarne tre.
     */
    String ORDINE = "CASE WHEN l.stato = com.anitia.backend.lavoro.StatoLavoro.ANNULLATO "
                  + "THEN 1 ELSE 0 END, l.codice";

    /**
     * Il lavoro, con la sua riga bloccata fino alla fine della transazione:
     * è il {@code SELECT ... FOR UPDATE} su cui poggia tutta la contabilità
     * delle ore.
     *
     * <p>Serve perché le ore libere <strong>non sono una colonna</strong>: si
     * calcolano come {@code monte_ore − somma(task.ore_allocate)}. Senza
     * lock, due allocazioni contemporanee leggono entrambe lo stesso pool,
     * trovano entrambe ore a sufficienza e allocano entrambe — e la somma
     * finale supera il monte ore senza che nessun vincolo se ne accorga.
     * {@code @Version} non basta: protegge la riga del <em>lavoro</em> da
     * scritture concorrenti, ma qui le due transazioni scrivono due
     * <em>task</em> diverse e il lavoro lo leggono soltanto.
     *
     * <p>⚠️ Va chiamato dentro un {@code @Transactional} non
     * {@code readOnly}, e <strong>prima</strong> di leggere le somme: fuori da
     * una transazione, o dopo il calcolo, il lock non protegge niente.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lavoro l WHERE l.id = :id")
    Optional<Lavoro> bloccaPerAggiornamento(@Param("id") Long id);

    /**
     * {@code LEFT JOIN FETCH} e non {@code JOIN FETCH}: una bozza può non
     * avere responsabile, e un join interno la farebbe sparire dall'elenco.
     * Il fetch esplicito evita una query per riga, dato che
     * {@code open-in-view} è disattivato.
     */
    @Query("SELECT l FROM Lavoro l LEFT JOIN FETCH l.responsabile ORDER BY "
         + ORDINE)
    List<Lavoro> trovaTutti();

    /** Come {@link #trovaTutti()}, ma senza i lavori annullati. */
    @Query("SELECT l FROM Lavoro l LEFT JOIN FETCH l.responsabile "
         + "WHERE l.stato <> com.anitia.backend.lavoro.StatoLavoro.ANNULLATO "
         + "ORDER BY " + ORDINE)
    List<Lavoro> trovaTuttiNonAnnullati();

    @Query("SELECT l.id FROM Lavoro l WHERE l.responsabile.id = :responsabileId")
    List<Long> trovaIdDiResponsabile(@Param("responsabileId") Long responsabileId);

    /**
     * Gli id dei lavori in cui la persona ha un'assegnazione <strong>attiva</strong>
     * su almeno una task. È la seconda metà del permesso
     * {@code LAVORI_VISUALIZZA_PROPRI}: «di cui è responsabile <em>o in cui ha
     * task assegnate</em>».
     *
     * <p>Query nativa perché {@code assegnazione_task} non è mappata come
     * entità, quindi JPQL non la raggiunge. Il filtro è
     * {@code rimossa_il IS NULL} e non la colonna generata {@code attiva},
     * perché quella serve al vincolo di unicità e vale NULL quando
     * l'assegnazione è stata rimossa.
     */
    @Query(value = "SELECT DISTINCT t.lavoro_id FROM task t "
                 + "JOIN assegnazione_task a ON a.task_id = t.id "
                 + "WHERE a.utente_id = :utenteId AND a.rimossa_il IS NULL",
            nativeQuery = true)
    List<Long> trovaIdConTaskAssegnate(@Param("utenteId") Long utenteId);

    @Query("SELECT l FROM Lavoro l LEFT JOIN FETCH l.responsabile "
         + "WHERE l.id IN :id ORDER BY " + ORDINE)
    List<Lavoro> trovaConId(@Param("id") Collection<Long> id);

    /** Come {@link #trovaConId(Collection)}, ma senza i lavori annullati. */
    @Query("SELECT l FROM Lavoro l LEFT JOIN FETCH l.responsabile "
         + "WHERE l.id IN :id "
         + "  AND l.stato <> com.anitia.backend.lavoro.StatoLavoro.ANNULLATO "
         + "ORDER BY " + ORDINE)
    List<Lavoro> trovaConIdNonAnnullati(@Param("id") Collection<Long> id);

    /**
     * Il numero più alto fra i codici nel formato {@code LAV-NNN}, oppure 0 se
     * non ce ne sono. Serve a generare il progressivo successivo.
     */
    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(codice, 5) AS UNSIGNED)), 0) "
                 + "FROM lavoro WHERE codice REGEXP '^LAV-[0-9]+$'",
            nativeQuery = true)
    long ultimoProgressivo();
}
