package com.anitia.backend.impostazione;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Un parametro del sistema, come la tabella {@code impostazione} della
 * migrazione V4 lo tiene: una chiave, un valore testuale e la spiegazione di
 * che cosa governa.
 *
 * <p>Il valore è una stringa anche quando rappresenta un numero o un orario.
 * È la forma che la tabella ha già, ed evita una colonna per tipo: a
 * convertirlo ci pensa {@link ServizioImpostazioni}, che è anche l'unico posto
 * dove un valore scritto male si traduce in un fallback invece che in
 * un'eccezione a metà di un'operazione.
 *
 * <p>La chiave è la chiave primaria: niente id tecnico, perché il nome del
 * parametro è già un identificatore stabile e leggibile.
 */
@Entity
@Table(name = "impostazione")
public class Impostazione {

    @Id
    @Column(length = 100)
    private String chiave;

    @Column(nullable = false, length = 500)
    private String valore;

    @Column(nullable = false, length = 255)
    private String descrizione;

    /** Valorizzata dal database, e riletta subito dopo la scrittura. */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "aggiornato_il")
    private LocalDateTime aggiornatoIl;

    /** Richiesto da JPA. */
    protected Impostazione() {
    }

    public String getChiave() {
        return chiave;
    }

    public String getValore() {
        return valore;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public LocalDateTime getAggiornatoIl() {
        return aggiornatoIl;
    }

    /**
     * Cambia il valore. Non valida niente: che cosa sia un valore accettabile
     * dipende dal parametro, e lo sa chi lo legge.
     */
    public void cambiaValore(String nuovoValore) {
        this.valore = nuovoValore;
    }
}
