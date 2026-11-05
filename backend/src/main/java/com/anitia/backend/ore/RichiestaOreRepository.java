package com.anitia.backend.ore;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RichiestaOreRepository extends JpaRepository<RichiestaOre, Long> {

    /**
     * Le righe con tutto quello che serve a mostrarle: lavoro, task,
     * richiedente e chi le ha gestite.
     *
     * <p>I fetch sono espliciti perché {@code open-in-view} è disattivato:
     * leggere il nome del richiedente dopo la transazione fallirebbe, e farlo
     * dentro costerebbe quattro query per riga.
     */
    String CON_TUTTO = "SELECT r FROM RichiestaOre r "
            + "JOIN FETCH r.lavoro "
            + "LEFT JOIN FETCH r.task "
            + "JOIN FETCH r.richiedente "
            + "LEFT JOIN FETCH r.gestitaDa ";

    @Query(CON_TUTTO + "WHERE r.id = :id")
    Optional<RichiestaOre> trovaCompleta(@Param("id") Long id);

    /** Tutte le richieste, dalla più recente: per chi vede tutti i lavori. */
    @Query(CON_TUTTO + "ORDER BY r.creataIl DESC")
    List<RichiestaOre> trovaTutte();

    /**
     * Le richieste ancora da decidere, dalla più vecchia: chi aspetta da più
     * tempo va guardato per primo.
     *
     * <p>È l'elenco che l'amministratore apre, e in assenza di notifiche è
     * anche l'unico posto in cui una richiesta si fa notare — per questo
     * l'ordine è quello dell'attesa e non quello di inserimento.
     */
    @Query(CON_TUTTO + "WHERE r.stato = com.anitia.backend.ore.StatoRichiesta.IN_ATTESA "
         + "ORDER BY r.creataIl")
    List<RichiestaOre> trovaInAttesa();

    /** Lo storico delle richieste di un lavoro, dalla più recente. */
    @Query(CON_TUTTO + "WHERE r.lavoro.id = :lavoroId ORDER BY r.creataIl DESC")
    List<RichiestaOre> trovaDelLavoro(@Param("lavoroId") Long lavoroId);

    /**
     * Le richieste aperte da una persona, dalla più recente.
     *
     * <p>In mancanza di notifiche, è così che un PM scopre com'è finita la
     * sua richiesta: la ritrova in elenco con lo stato aggiornato.
     */
    @Query(CON_TUTTO + "WHERE r.richiedente.id = :utenteId ORDER BY r.creataIl DESC")
    List<RichiestaOre> trovaDiRichiedente(@Param("utenteId") Long utenteId);

    /**
     * Le richieste dei lavori indicati, dalla più recente: serve a chi vede
     * solo i propri lavori.
     *
     * <p>⚠️ {@code IN} con una collezione vuota non è SQL valido: chi chiama
     * deve fermarsi prima.
     */
    @Query(CON_TUTTO + "WHERE r.lavoro.id IN :lavoroId ORDER BY r.creataIl DESC")
    List<RichiestaOre> trovaDeiLavori(@Param("lavoroId") Collection<Long> lavoroId);
}
