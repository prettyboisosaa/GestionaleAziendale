package com.anitia.backend.tempo;

import com.anitia.backend.task.Task;
import com.anitia.backend.utente.Utente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Un periodo di lavoro continuativo di una persona su una task.
 *
 * <p>Si apre quando il dipendente avvia la task e si chiude quando mette in
 * pausa o la completa. È la grana con cui si misurano le ore lavorate, e ha
 * preso il posto della voce di dichiarazione: quella diceva «quattro ore il 6
 * novembre», questa dice da quando a quando.
 *
 * <p><strong>Due colonne non sono mappate, di proposito.</strong>
 * {@code minuti} e {@code in_corso} le calcola il database, come fa
 * {@code attiva} su {@code assegnazione_task}: se Hibernate provasse a
 * scriverle, l'INSERT verrebbe rifiutato. La durata si legge da
 * {@link #durataMinuti()}, che la ricava dagli stessi due istanti da cui la
 * ricava MySQL.
 *
 * <p>Niente setter: ogni cambiamento passa da un metodo che dice perché
 * avviene, e nessuna riga si cancella — una sessione sbagliata si annulla.
 */
@Entity
@Table(name = "sessione_task")
public class SessioneTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id")
    private Utente utente;

    @Column(name = "avviata_il", nullable = false)
    private LocalDateTime avviataIl;

    /** Null finché scorre: la fine di una cosa che non è finita non è uno zero. */
    @Column(name = "chiusa_il")
    private LocalDateTime chiusaIl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigineSessione origine;

    @Column(nullable = false)
    private boolean confermata;

    @Column(name = "annullata_il")
    private LocalDateTime annullataIl;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "creato_il")
    private LocalDateTime creatoIl;

    @Version
    @Column(nullable = false)
    private int versione;

    /** Richiesto da JPA. */
    protected SessioneTask() {
    }

    /** Il dipendente ha premuto avvia: la sessione parte adesso. */
    public static SessioneTask avvia(Task task, Utente utente) {
        SessioneTask sessione = new SessioneTask();
        sessione.task = task;
        sessione.utente = utente;
        sessione.avviataIl = LocalDateTime.now();
        sessione.origine = OrigineSessione.TIMER;
        sessione.confermata = true;
        return sessione;
    }

    /**
     * Scritta a mano: due istanti scelti da chi c'era.
     *
     * <p>È l'unica fabbrica che prende una fine, e la ragione è che qui la
     * sessione non si apre — nasce già chiusa, perché quel lavoro è già
     * accaduto. Il timer misura mentre succede, questa registra dopo.
     *
     * <p>Nasce <strong>confermata</strong>: la differenza con
     * {@link #chiudiDUfficio} è esattamente questa — là la fine è una
     * deduzione da far guardare a qualcuno, qui l'ha scelta una persona.
     * L'origine resta a dire che non è stata misurata.
     */
    public static SessioneTask manuale(Task task, Utente utente,
                                       LocalDateTime da, LocalDateTime a) {
        SessioneTask sessione = new SessioneTask();
        sessione.task = task;
        sessione.utente = utente;
        sessione.avviataIl = da;
        sessione.chiusaIl = a;
        sessione.origine = OrigineSessione.MANUALE;
        sessione.confermata = true;
        return sessione;
    }

    /** Pausa, oppure task completata: la sessione si chiude adesso. */
    public void chiudi() {
        this.chiusaIl = LocalDateTime.now();
    }

    /**
     * Chiusura d'ufficio di una sessione rimasta aperta oltre il tetto.
     *
     * <p>Resta <strong>da confermare</strong>, e l'origine lo dice: il numero
     * che ne esce non l'ha misurato nessuno, e chi c'era deve poterlo
     * sistemare. Finché non lo fa, le ore contano comunque — non contarle
     * farebbe sparire lavoro vero dal consuntivo.
     */
    public void chiudiDUfficio(LocalDateTime quando) {
        this.chiusaIl = quando;
        this.origine = OrigineSessione.AUTOMATICA;
        this.confermata = false;
    }

    /** Il dipendente ha guardato la chiusura d'ufficio e la accetta così. */
    public void conferma() {
        this.confermata = true;
    }

    /**
     * Il dipendente corregge gli estremi della propria sessione.
     *
     * <p>Correggere è anche confermare: se la riga era una chiusura d'ufficio,
     * da qui in avanti i suoi estremi li ha scelti una persona.
     */
    public void correggi(LocalDateTime avviataIl, LocalDateTime chiusaIl) {
        this.avviataIl = avviataIl;
        this.chiusaIl = chiusaIl;
        this.confermata = true;
    }

    /** La sessione non andava registrata: la riga resta, fuori dai conti. */
    public void annulla() {
        this.annullataIl = LocalDateTime.now();
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

    public LocalDateTime getAvviataIl() {
        return avviataIl;
    }

    public LocalDateTime getChiusaIl() {
        return chiusaIl;
    }

    public OrigineSessione getOrigine() {
        return origine;
    }

    public boolean isConfermata() {
        return confermata;
    }

    public LocalDateTime getAnnullataIl() {
        return annullataIl;
    }

    public LocalDateTime getCreatoIl() {
        return creatoIl;
    }

    /** Vero se sta scorrendo adesso. */
    public boolean inCorso() {
        return chiusaIl == null && annullataIl == null;
    }

    /**
     * I minuti della sessione, zero se è ancora aperta.
     *
     * <p>Non legge la colonna {@code minuti}: la ricava dagli istanti, che
     * sono la stessa fonte da cui la ricava il database. Leggere la colonna
     * vorrebbe dire mapparla, e una colonna generata mappata è un INSERT
     * rifiutato.
     */
    public long durataMinuti() {
        if (chiusaIl == null) {
            return 0;
        }
        return Duration.between(avviataIl, chiusaIl).toMinutes();
    }
}
