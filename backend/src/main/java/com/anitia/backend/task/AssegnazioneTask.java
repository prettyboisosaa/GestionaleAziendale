package com.anitia.backend.task;

import com.anitia.backend.utente.Utente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * L'assegnazione di una persona a una task.
 *
 * <p><strong>La colonna {@code attiva} non è mappata, di proposito.</strong>
 * Nello schema è
 * {@code GENERATED ALWAYS AS (IF(rimossa_il IS NULL, 1, NULL)) STORED}: la
 * calcola MySQL, e il commento della migrazione V3 dice esplicitamente di non
 * mapparla. Se Hibernate provasse a scriverla, l'INSERT verrebbe rifiutato.
 *
 * <p>Quella colonna serve al vincolo
 * {@code uk_assegnazione_attiva (task_id, utente_id, attiva)}, che garantisce
 * <em>una sola assegnazione attiva per persona e task</em>: con una riga
 * rimossa il valore è NULL, e in MySQL i NULL non collidono fra loro, quindi la
 * stessa persona può essere riassegnata dopo una rimozione. È la garanzia
 * vera, e la sua violazione va tradotta in un 409.
 *
 * <p>La rimozione è <strong>morbida</strong>: si valorizzano {@code rimossaIl}
 * e {@code rimossaDa}, la riga resta. Lo storico di chi ha lavorato a cosa non
 * si butta via — la stessa ragione per cui un lavoro si annulla invece di
 * essere cancellato.
 */
@Entity
@Table(name = "assegnazione_task")
public class AssegnazioneTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id")
    private Utente utente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assegnata_da_id")
    private Utente assegnataDa;

    @Generated(event = EventType.INSERT)
    @Column(name = "assegnata_il")
    private LocalDateTime assegnataIl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rimossa_da_id")
    private Utente rimossaDa;

    @Column(name = "rimossa_il")
    private LocalDateTime rimossaIl;

    /** Richiesto da JPA. */
    protected AssegnazioneTask() {
    }

    public static AssegnazioneTask nuova(Task task, Utente utente, Utente assegnataDa) {
        AssegnazioneTask assegnazione = new AssegnazioneTask();
        assegnazione.task = task;
        assegnazione.utente = utente;
        assegnazione.assegnataDa = assegnataDa;
        return assegnazione;
    }

    /**
     * Rimozione morbida. Il vincolo {@code ck_ass_rimozione} pretende che
     * {@code rimossa_il} e {@code rimossa_da_id} siano valorizzati o nulli
     * insieme: per questo si scrivono entrambi qui, e non da fuori.
     */
    public void rimuovi(Utente da) {
        this.rimossaDa = da;
        this.rimossaIl = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Task getTask() {
        return task;
    }

    public Utente getUtente() {
        return utente;
    }

    public LocalDateTime getRimossaIl() {
        return rimossaIl;
    }
}
