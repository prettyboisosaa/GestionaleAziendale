package com.anitia.backend.impostazione;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Un giorno di chiusura aziendale, dalla tabella {@code festivita} della
 * migrazione V4.
 *
 * <p>Serve a {@link ServizioCalendario}: i giorni di una richiesta di assenza
 * sono giorni <strong>lavorativi</strong>, e una settimana con un ponte in
 * mezzo ne ha meno di cinque.
 */
@Entity
@Table(name = "festivita")
public class Festivita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false, length = 100)
    private String descrizione;

    /** Richiesto da JPA. */
    protected Festivita() {
    }

    public Long getId() {
        return id;
    }

    public LocalDate getData() {
        return data;
    }

    public String getDescrizione() {
        return descrizione;
    }
}
