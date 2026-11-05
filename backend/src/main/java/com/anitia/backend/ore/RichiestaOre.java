package com.anitia.backend.ore;

import com.anitia.backend.lavoro.Lavoro;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * La richiesta di ore aggiuntive che un PM rivolge all'amministratore.
 *
 * <p>Esiste per sostituire una conversazione. Prima di questa tabella, un PM a
 * cui servivano venti ore in più le chiedeva a voce o per messaggio, e
 * l'amministratore modificava il monte ore: la decisione non era collegata
 * alla richiesta, il motivo si perdeva, e nessuno poteva più dire a posteriori
 * perché un lavoro era passato da 400 a 440 ore. Qui la richiesta ha uno
 * stato, e l'approvazione <em>produce</em> il movimento che alza il monte ore.
 *
 * <p>{@link #task} è <strong>facoltativa</strong>, e la differenza è
 * sostanziale: se la richiesta nasce da una task specifica, le ore concesse
 * vanno direttamente là e non possono per distrazione finire altrove; se è
 * nulla, restano nel pool del lavoro e le distribuisce il PM.
 */
@Entity
@Table(name = "richiesta_ore")
public class RichiestaOre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lavoro_id")
    private Lavoro lavoro;

    /** Nulla se le ore concesse devono restare nel pool del lavoro. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "richiedente_id")
    private Utente richiedente;

    @Column(name = "ore_richieste", nullable = false, precision = 7, scale = 2)
    private BigDecimal oreRichieste;

    /**
     * Nulle finché la richiesta è in attesa, zero se è stata rifiutata.
     *
     * <p>I vincoli {@code ck_richiesta_esito} e {@code ck_richiesta_chiusa}
     * legano questo campo allo stato: in attesa vuol dire esattamente «senza
     * ore decise e senza chi le ha decise», e una riga a metà — gestita ma
     * senza numero — il database non la accetta.
     */
    @Column(name = "ore_concesse", precision = 7, scale = 2)
    private BigDecimal oreConcesse;

    @Column(nullable = false, length = 500)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatoRichiesta stato;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gestita_da_id")
    private Utente gestitaDa;

    @Column(name = "gestita_il")
    private LocalDateTime gestitaIl;

    /** Valorizzata dal database, e riletta subito dopo la scrittura. */
    @Generated(event = EventType.INSERT)
    @Column(name = "creata_il")
    private LocalDateTime creataIl;

    /** Richiesto da JPA. */
    protected RichiestaOre() {
    }

    public static RichiestaOre nuova(Lavoro lavoro, Task task, Utente richiedente,
                                     BigDecimal oreRichieste, String motivo) {
        RichiestaOre richiesta = new RichiestaOre();
        richiesta.lavoro = lavoro;
        richiesta.task = task;
        richiesta.richiedente = richiedente;
        richiesta.oreRichieste = oreRichieste;
        richiesta.motivo = motivo;
        // Il database ha DEFAULT 'IN_ATTESA', ma JPA scrive comunque la
        // colonna nell'INSERT: il valore va impostato qui.
        richiesta.stato = StatoRichiesta.IN_ATTESA;
        return richiesta;
    }

    /**
     * Registra l'esito. Lo stato si <strong>deduce dal numero</strong> invece
     * di arrivare da fuori: zero ore è un rifiuto, tutte quelle chieste sono
     * un'approvazione piena, una parte è un'approvazione parziale. Lasciare
     * che chi chiama scelga l'etichetta vorrebbe dire poterla far mentire sui
     * numeri — e i vincoli {@code ck_richiesta_rifiuto} e
     * {@code ck_richiesta_totale} lo rifiuterebbero comunque, ma con un
     * errore del database invece di un messaggio.
     *
     * @param oreConcesse da zero a quante ne vuole l'amministratore, anche più
     *                    di quelle chieste
     */
    public void gestisci(BigDecimal oreConcesse, Utente gestitaDa) {
        if (oreConcesse.signum() < 0) {
            throw new IllegalArgumentException("Le ore concesse non possono essere negative.");
        }
        this.oreConcesse = oreConcesse;
        this.gestitaDa = gestitaDa;
        this.gestitaIl = LocalDateTime.now();
        this.stato = oreConcesse.signum() == 0
                ? StatoRichiesta.RIFIUTATA
                : oreConcesse.compareTo(oreRichieste) >= 0
                        ? StatoRichiesta.APPROVATA
                        : StatoRichiesta.APPROVATA_PARZIALE;
    }

    public boolean eInAttesa() {
        return stato == StatoRichiesta.IN_ATTESA;
    }

    public Long getId() {
        return id;
    }

    public Lavoro getLavoro() {
        return lavoro;
    }

    /** Nulla se le ore concesse restano nel pool. */
    public Task getTask() {
        return task;
    }

    public Utente getRichiedente() {
        return richiedente;
    }

    public BigDecimal getOreRichieste() {
        return oreRichieste;
    }

    public BigDecimal getOreConcesse() {
        return oreConcesse;
    }

    public String getMotivo() {
        return motivo;
    }

    public StatoRichiesta getStato() {
        return stato;
    }

    public Utente getGestitaDa() {
        return gestitaDa;
    }

    public LocalDateTime getGestitaIl() {
        return gestitaIl;
    }

    public LocalDateTime getCreataIl() {
        return creataIl;
    }
}
