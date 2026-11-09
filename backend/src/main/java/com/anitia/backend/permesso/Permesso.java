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

    /**
     * Vero per i permessi del pavimento: ogni utente attivo li ha, qualunque
     * ruolo abbia, e non si possono togliere.
     *
     * <p>A farlo valere è la vista {@code permesso_effettivo}, non questo
     * campo: il terzo ramo della vista li concede a chiunque sia attivo, senza
     * passare dai ruoli e senza guardare gli scostamenti. Qui il flag serve a
     * due cose sole — spiegarlo all'interfaccia, e far rifiutare la scrittura
     * di uno scostamento che non avrebbe comunque effetto.
     */
    @Column(nullable = false)
    private boolean base;

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

    public boolean isBase() {
        return base;
    }
}
