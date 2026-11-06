package com.anitia.backend.assenza;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssenzaRepository extends JpaRepository<Assenza, Long> {

    /**
     * L'ordine di ogni elenco: prima le richieste da decidere, poi le altre,
     * e dentro i due gruppi la data di inizio più recente.
     *
     * <p>Le richieste in attesa vanno in cima perché sono le uniche su cui
     * c'è qualcosa da fare — il dipendente le può ritirare, l'admin le deve
     * decidere. Mescolate alle decise si perderebbero fra le vecchie.
     */
    String ORDINE = "CASE WHEN a.stato = com.anitia.backend.assenza.StatoAssenza.IN_ATTESA "
                  + "THEN 0 ELSE 1 END, a.dataInizio DESC, a.id DESC";

    /** Le richieste di una persona. */
    @Query("SELECT a FROM Assenza a WHERE a.utente.id = :utenteId ORDER BY " + ORDINE)
    List<Assenza> trovaDiUtente(@Param("utenteId") Long utenteId);

    /**
     * Tutte le richieste, con chi le ha chieste già agganciato.
     *
     * <p>{@code JOIN FETCH} e non una lettura pigra: con
     * {@code open-in-view} disattivato, costruire la risposta leggerebbe il
     * nome di ogni richiedente con una query a parte — una per riga.
     *
     * <p>I due filtri sono facoltativi e si spengono passando {@code null}:
     * una query sola con {@code IS NULL OR} invece di quattro combinazioni di
     * metodi, che sarebbero quattro {@code ORDER BY} da tenere allineati.
     */
    @Query("SELECT a FROM Assenza a JOIN FETCH a.utente "
         + "WHERE (a.stato = :stato OR :stato IS NULL) "
         + "  AND (a.utente.id = :utenteId OR :utenteId IS NULL) "
         + "ORDER BY " + ORDINE)
    List<Assenza> cerca(@Param("stato") StatoAssenza stato,
                        @Param("utenteId") Long utenteId);

    /** La richiesta con il richiedente già caricato. */
    @Query("SELECT a FROM Assenza a JOIN FETCH a.utente WHERE a.id = :id")
    Optional<Assenza> trovaConUtente(@Param("id") Long id);

    /**
     * Le richieste della persona che si sovrappongono all'intervallo e che
     * <strong>impegnano</strong> il calendario, cioè quelle in attesa o
     * approvate: una rifiutata o ritirata non occupa nessun giorno, e
     * contarla impedirebbe di richiedere di nuovo gli stessi giorni dopo un
     * rifiuto.
     *
     * <p>Due intervalli si sovrappongono quando ciascuno comincia non dopo
     * la fine dell'altro. È il confronto sulle sole date, non sugli orari:
     * due permessi orari nello stesso giorno in fasce diverse risultano
     * quindi sovrapposti. È la scelta più semplice e la più prudente — chi ha
     * due permessi lo stesso giorno li chiede come uno — e il documento non
     * dice altrimenti.
     *
     * <p>{@code :escludi} serve alla modifica: una richiesta non si
     * sovrappone a se stessa, e senza questo filtro non sarebbe più
     * modificabile.
     */
    @Query("SELECT a FROM Assenza a "
         + "WHERE a.utente.id = :utenteId "
         + "  AND (a.id <> :escludi OR :escludi IS NULL) "
         + "  AND a.stato IN (com.anitia.backend.assenza.StatoAssenza.IN_ATTESA, "
         + "                  com.anitia.backend.assenza.StatoAssenza.APPROVATA) "
         + "  AND a.dataInizio <= :a AND a.dataFine >= :da "
         + "ORDER BY a.dataInizio")
    List<Assenza> trovaSovrapposte(@Param("utenteId") Long utenteId,
                                   @Param("da") LocalDate da,
                                   @Param("a") LocalDate a,
                                   @Param("escludi") Long escludi);

    /**
     * Le assenze approvate di una persona che toccano l'intervallo: è la
     * lettura che serve a un calendario e all'avviso sulla registrazione di
     * ore in un giorno di assenza.
     */
    @Query("SELECT a FROM Assenza a "
         + "WHERE a.utente.id = :utenteId "
         + "  AND a.stato = com.anitia.backend.assenza.StatoAssenza.APPROVATA "
         + "  AND a.dataInizio <= :a AND a.dataFine >= :da "
         + "ORDER BY a.dataInizio")
    List<Assenza> trovaApprovateDelPeriodo(@Param("utenteId") Long utenteId,
                                           @Param("da") LocalDate da,
                                           @Param("a") LocalDate a);
}
