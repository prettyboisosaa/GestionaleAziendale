package com.anitia.backend.permesso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una voce del catalogo dei permessi.
 *
 * <p>Il catalogo è fisso: cambia solo con una migrazione, mai
 * dall'applicazione. Per questo la chiave primaria è il {@code codice} —
 * la stessa stringa che il codice Java controlla nei {@code @PreAuthorize} —
 * e non un id generato.
 *
 * <p>Solo getter: qui non si scrive niente.
 */
@Entity
@Table(name = "permesso")
public class Permesso {

    @Id
    @Column(length = 50)
    private String codice;

    @Column(nullable = false, length = 30)
    private String categoria;

    @Column(nullable = false, length = 255)
    private String descrizione;

    /** L'ordine in cui mostrarlo, deciso dalle migrazioni. */
    @Column(nullable = false)
    private int ordine;

    /** Richiesto da JPA. */
    protected Permesso() {
    }

    public String getCodice() {
        return codice;
    }

    public String getCategoria() {
        return categoria;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public int getOrdine() {
        return ordine;
    }
}
