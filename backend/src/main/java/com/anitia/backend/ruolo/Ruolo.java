package com.anitia.backend.ruolo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Un ruolo: un insieme di permessi da assegnare agli utenti.
 *
 * <p>I ruoli sono dati, non codice: si creano e si rinominano
 * dall'applicazione. Per questo nel codice non si controlla mai il ruolo, ma
 * sempre il permesso — rinominare un ruolo dall'interfaccia non deve rompere
 * niente.
 *
 * <p>In questo passo i ruoli si leggono soltanto, quindi la classe ha solo i
 * getter.
 */
@Entity
@Table(name = "ruolo")
public class Ruolo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(length = 255)
    private String descrizione;

    /** Se vero non e' eliminabile e ha sempre tutti i permessi. */
    @Column(nullable = false)
    private boolean protetto;

    @Version
    @Column(nullable = false)
    private int versione;

    /** Richiesto da JPA. */
    protected Ruolo() {
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public boolean isProtetto() {
        return protetto;
    }

    public int getVersione() {
        return versione;
    }
}
