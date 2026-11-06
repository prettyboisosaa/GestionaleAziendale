package com.anitia.backend.impostazione;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FestivitaRepository extends JpaRepository<Festivita, Long> {

    /**
     * Le date di chiusura in un intervallo, estremi compresi.
     *
     * <p>Solo le date e non le entita' intere: a chi conta i giorni
     * lavorativi serve sapere quali giorni saltare, non come si chiamano.
     */
    @Query("SELECT f.data FROM Festivita f WHERE f.data BETWEEN :da AND :a")
    List<LocalDate> dateTra(@Param("da") LocalDate da, @Param("a") LocalDate a);

    /**
     * Le chiusure in un intervallo, questa volta intere.
     *
     * <p>Al calendario, a differenza di chi conta i giorni lavorativi, serve
     * anche come si chiamano: «Natale» scritto nella cella dice perché quel
     * giorno è grigio, mentre una casella grigia e muta no.
     */
    @Query("SELECT f FROM Festivita f WHERE f.data BETWEEN :da AND :a ORDER BY f.data")
    List<Festivita> trovaTra(@Param("da") LocalDate da, @Param("a") LocalDate a);
}
