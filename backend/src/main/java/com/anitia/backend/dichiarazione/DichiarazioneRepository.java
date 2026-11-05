package com.anitia.backend.dichiarazione;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DichiarazioneRepository extends JpaRepository<Dichiarazione, Long> {

    /**
     * La dichiarazione di una persona per un giorno, con le sue voci e le
     * task a cui si riferiscono.
     *
     * <p>Al massimo una: lo garantisce {@code uk_dich_utente_data}.
     *
     * <p>{@code LEFT JOIN FETCH} sulle voci perché una giornata può non averne
     * ancora nessuna — appena creata, o di ferie — e un join interno la
     * farebbe sparire. Il fetch delle task serve a mostrare i titoli:
     * {@code open-in-view} è disattivato, quindi leggerli dopo la transazione
     * fallirebbe.
     */
    @Query("SELECT d FROM Dichiarazione d "
         + "LEFT JOIN FETCH d.voci v "
         + "LEFT JOIN FETCH v.task "
         + "WHERE d.utente.id = :utenteId AND d.data = :data")
    Optional<Dichiarazione> trovaDelGiorno(@Param("utenteId") Long utenteId,
                                           @Param("data") LocalDate data);

    /**
     * Le dichiarazioni di una persona in un intervallo, dalla più recente.
     * Senza le voci: serve all'elenco dei giorni, che mostra solo il totale.
     */
    @Query("SELECT d FROM Dichiarazione d "
         + "WHERE d.utente.id = :utenteId AND d.data BETWEEN :da AND :a "
         + "ORDER BY d.data DESC")
    List<Dichiarazione> trovaDelPeriodo(@Param("utenteId") Long utenteId,
                                        @Param("da") LocalDate da,
                                        @Param("a") LocalDate a);

    /**
     * Le ore totali per ciascuna dichiarazione di un periodo, in una query
     * sola: l'elenco dei giorni mostra il totale di ognuno, e sommare le voci
     * in memoria richiederebbe di caricarle tutte.
     */
    @Query(value = "SELECT d.id AS dichiarazioneId, COALESCE(SUM(v.ore), 0) AS ore "
                 + "FROM dichiarazione d LEFT JOIN voce_dichiarazione v "
                 + "  ON v.dichiarazione_id = d.id "
                 + "WHERE d.utente_id = :utenteId AND d.data BETWEEN :da AND :a "
                 + "GROUP BY d.id",
            nativeQuery = true)
    List<OrePerGiorno> sommaOrePerGiorno(@Param("utenteId") Long utenteId,
                                         @Param("da") LocalDate da,
                                         @Param("a") LocalDate a);

    /** Una riga di {@link #sommaOrePerGiorno}. */
    interface OrePerGiorno {
        Long getDichiarazioneId();

        java.math.BigDecimal getOre();
    }
}
