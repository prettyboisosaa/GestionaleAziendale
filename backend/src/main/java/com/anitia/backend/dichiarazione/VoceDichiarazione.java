package com.anitia.backend.dichiarazione;

import com.anitia.backend.task.Task;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Una riga di una dichiarazione: su quale task si è lavorato, per quante ore e
 * che cosa si è fatto.
 *
 * <p><strong>È il registro delle ore del gestionale.</strong> Le ore consumate
 * di una task sono {@code SUM(voce_dichiarazione.ore)} su quella task, e le
 * ore consumate di un lavoro la stessa somma su tutte le sue task: non esiste
 * nessun'altra tabella che le conti, e nessuna colonna che le riassuma. Chi
 * cerca il totale lo chiede al database.
 *
 * <p>Queste righe <strong>non vengono mai cancellate quando una task si
 * chiude o si annulla</strong>: il lavoro svolto da una persona resta
 * registrato anche se il progetto non si farà. È la stessa ragione per cui un
 * lavoro si annulla invece di essere eliminato, ed è il motivo per cui le
 * chiavi esterne della V4 non hanno ON DELETE CASCADE verso la task.
 */
@Entity
@Table(name = "voce_dichiarazione")
public class VoceDichiarazione {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dichiarazione_id")
    private Dichiarazione dichiarazione;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id")
    private Task task;

    /**
     * Le ore lavorate su questa task in questa giornata.
     *
     * <p>{@code DECIMAL(4,2)}, quindi mezze ore e quarti d'ora si scrivono
     * esatti. Il vincolo {@code ck_voce_ore} pretende che sia maggiore di
     * zero: una voce da zero ore non dice niente, e basta non scriverla.
     *
     * <p>Nessun limite superiore <em>per voce</em> nel database: il tetto è
     * sulla giornata intera, perché è la persona che non può lavorare più di
     * otto ore, non la singola task.
     */
    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal ore;

    @Column(nullable = false, length = 1000)
    private String descrizione;

    /** Valorizzate dal database, e rilette subito dopo la scrittura. */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "creato_il")
    private LocalDateTime creatoIl;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "aggiornato_il")
    private LocalDateTime aggiornatoIl;

    /** Richiesto da JPA. */
    protected VoceDichiarazione() {
    }

    /**
     * Il legame con la dichiarazione si imposta qui, non da fuori: è la
     * colonna {@code NOT NULL} che tiene insieme le due tabelle, e lasciarla
     * a carico di chi chiama vorrebbe dire poterla dimenticare.
     */
    public static VoceDichiarazione nuova(Dichiarazione dichiarazione, Task task, BigDecimal ore,
                                          String descrizione) {
        VoceDichiarazione voce = new VoceDichiarazione();
        voce.dichiarazione = dichiarazione;
        voce.task = task;
        voce.ore = ore;
        voce.descrizione = descrizione;
        return voce;
    }

    public Long getId() {
        return id;
    }

    public Dichiarazione getDichiarazione() {
        return dichiarazione;
    }

    public Task getTask() {
        return task;
    }

    public BigDecimal getOre() {
        return ore;
    }

    public String getDescrizione() {
        return descrizione;
    }

    /** Corregge ore e descrizione di una voce già scritta. */
    public void correggi(BigDecimal ore, String descrizione) {
        this.ore = ore;
        this.descrizione = descrizione;
    }
}
