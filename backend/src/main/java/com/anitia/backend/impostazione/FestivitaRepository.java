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
}
