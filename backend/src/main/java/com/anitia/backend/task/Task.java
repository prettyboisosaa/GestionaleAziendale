package com.anitia.backend.task;

import com.anitia.backend.lavoro.Lavoro;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Una task di un lavoro: l'unità di lavoro a cui si assegnano le persone.
 *
 * <p>Le persone non stanno qui: l'assegnazione vive nella tabella
 * {@code assegnazione_task}, che tiene anche chi ha assegnato e chi ha
 * rimosso. Una task può quindi avere più assegnatari, e la loro storia non
 * viene cancellata.
 */
@Entity
@Table(name = "task")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Il lavoro a cui appartiene, obbligatorio.
     *
     * <p>È una relazione e non un {@code long lavoroId} perché serve risalire
     * al lavoro per decidere chi può toccare la task: il permesso
     * {@code TASK_GESTISCI_PROPRI} parla dei «propri lavori», cioè di quelli
     * di cui si è responsabili. Con il solo id servirebbe una query in più
     * ogni volta.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lavoro_id")
    private Lavoro lavoro;

    @Column(nullable = false, length = 200)
    private String titolo;

    private String descrizione;

    @Column(name = "data_inizio", nullable = false)
    private LocalDate dataInizio;

    @Column(nullable = false)
    private LocalDate scadenza;

    /**
     * Ore stimate, facoltative. {@code BigDecimal} e non {@code double}:
     * la colonna è un {@code DECIMAL(7,2)} e un tipo a virgola mobile
     * introdurrebbe errori di arrotondamento su valori che andranno sommati.
     * Il vincolo {@code ck_task_stima} pretende che sia maggiore di zero.
     */
    @Column(name = "stima_ore", precision = 7, scale = 2)
    private BigDecimal stimaOre;

    /**
     * Le ore del monte ore del lavoro che sono coperte da budget per questa
     * task.
     *
     * <p>È una cosa <strong>diversa</strong> da {@link #stimaOre}, e la
     * distinzione è il punto: la stima dice quante ore servono secondo il PM,
     * l'allocazione quante gliene sono state concesse. Confonderle in un
     * numero solo nasconde il caso che conta — serve più di quanto è coperto —
     * e infatti è la differenza fra le due che precompila una richiesta di ore
     * all'amministratore.
     *
     * <p>Mai nulla: una task senza allocazione ha davvero zero ore coperte, e
     * lo impone {@code ck_task_allocate} insieme al DEFAULT 0 della colonna.
     *
     * <p>Qui non ci sono le ore <em>consumate</em>, di proposito: si ricavano
     * da {@code SUM(voce_dichiarazione.ore)}. Tenerle anche qui vorrebbe dire
     * aggiornare due posti a ogni registrazione di ore, e prima o poi
     * divergerebbero.
     */
    @Column(name = "ore_allocate", nullable = false, precision = 7, scale = 2)
    private BigDecimal oreAllocate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatoTask stato;

    /** Valorizzate dal database, e rilette subito dopo la scrittura. */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "creato_il")
    private LocalDateTime creatoIl;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "aggiornato_il")
    private LocalDateTime aggiornatoIl;

    @Version
    @Column(nullable = false)
    private int versione;

    /** Richiesto da JPA. */
    protected Task() {
    }

    /** Una task nuova, da fare. */
    public static Task nuova(Lavoro lavoro, String titolo, String descrizione,
                             LocalDate dataInizio, LocalDate scadenza, BigDecimal stimaOre) {
        Task task = new Task();
        task.lavoro = lavoro;
        task.titolo = titolo;
        task.descrizione = descrizione;
        task.dataInizio = dataInizio;
        task.scadenza = scadenza;
        task.stimaOre = stimaOre;
        // Il database ha DEFAULT 'ATTIVO', ma JPA scrive comunque la colonna
        // nell'INSERT: il valore va impostato qui.
        task.stato = StatoTask.ATTIVO;
        // Stesso discorso per il DEFAULT 0 di ore_allocate. Una task nasce
        // senza budget: le ore gliele alloca il PM dal pool del lavoro, in un
        // secondo momento e con un movimento che lo registra.
        task.oreAllocate = BigDecimal.ZERO;
        return task;
    }

    /**
     * Aggiunge ore all'allocazione.
     *
     * <p>Qui non c'è nessun controllo sulla capienza del pool del lavoro, e
     * non è una dimenticanza: «ci sono abbastanza ore libere?» è una domanda
     * sul lavoro e su <em>tutte</em> le sue task, che questa entità non vede.
     * La risponde {@code ServizioOre}, che prima blocca la riga del lavoro —
     * altrimenti due allocazioni contemporanee si spartirebbero due volte le
     * stesse ore libere.
     */
    public void alloca(BigDecimal ore) {
        if (ore.signum() <= 0) {
            throw new IllegalArgumentException("Le ore da allocare devono essere positive.");
        }
        this.oreAllocate = this.oreAllocate.add(ore);
    }

    /**
     * Riduce l'allocazione alle ore effettivamente consumate, e restituisce
     * quante ne sono tornate libere.
     *
     * <p>Si chiama alla chiusura o all'annullamento di una task: le ore
     * stanziate e non usate non devono restare bloccate su una task che non
     * consumerà più niente.
     *
     * <p>Se la task ha <strong>sforato</strong> — consumate più delle allocate
     * — non tocca niente e restituisce zero: lo sforamento resta registrato
     * così com'è, perché è un fatto accaduto e non un errore da correggere.
     */
    public BigDecimal rilasciaResiduo(BigDecimal oreConsumate) {
        BigDecimal residuo = this.oreAllocate.subtract(oreConsumate);
        if (residuo.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        this.oreAllocate = oreConsumate;
        return residuo;
    }

    /** Cambia lo stato della task. */
    public void cambiaStato(StatoTask nuovoStato) {
        this.stato = nuovoStato;
    }

    public Long getId() {
        return id;
    }

    public Lavoro getLavoro() {
        return lavoro;
    }

    public String getTitolo() {
        return titolo;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public LocalDate getDataInizio() {
        return dataInizio;
    }

    public LocalDate getScadenza() {
        return scadenza;
    }

    public BigDecimal getStimaOre() {
        return stimaOre;
    }

    /** Mai nullo: zero se alla task non sono ancora state allocate ore. */
    public BigDecimal getOreAllocate() {
        return oreAllocate;
    }

    public StatoTask getStato() {
        return stato;
    }

    public LocalDateTime getCreatoIl() {
        return creatoIl;
    }

    public int getVersione() {
        return versione;
    }
}
