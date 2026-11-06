package com.anitia.backend.lavoro;

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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Un lavoro: il progetto a cui si attaccano le task.
 *
 * <p>Ha <strong>un solo</strong> responsabile, il PM. Le altre persone non si
 * collegano al lavoro ma alle sue task, tramite {@code assegnazione_task}: è
 * per questo che la descrizione del permesso {@code LAVORI_VISUALIZZA_PROPRI}
 * dice «di cui è responsabile o in cui ha task assegnate».
 */
@Entity
@Table(name = "lavoro")
public class Lavoro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Progressivo generato, nel formato {@code LAV-001}. Unico. */
    @Column(nullable = false, length = 30)
    private String codice;

    @Column(nullable = false, length = 200)
    private String nome;

    private String descrizione;

    @Column(name = "data_inizio", nullable = false)
    private LocalDate dataInizio;

    @Column(name = "data_fine_prevista", nullable = false)
    private LocalDate dataFinePrevista;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatoLavoro stato;

    /**
     * Quando e' stato annullato, nullo se non lo e'. Il vincolo
     * { ck_lavoro_annullato} tiene i due campi d'accordo: la data c'e'
     * se e solo se lo stato e' { ANNULLATO}.
     *
     * <p>Non si ricava da { aggiornatoIl}, che cambia a ogni modifica
     * successiva e dopo la prima racconterebbe quella.
     */
    @Column(name = "annullato_il")
    private LocalDateTime annullatoIl;

    /**
     * Il budget di ore del lavoro, da distribuire fra le sue task.
     *
     * <p><strong>Nullo vuol dire «non ancora fissato»</strong>, non «zero».
     * Sono due affermazioni diverse — la seconda direbbe che il budget è
     * esaurito — e distinguerle serve alle bozze, che si creano prima di
     * sapere quante ore costeranno, e ai lavori nati prima della migrazione
     * V8.
     *
     * <p>{@code BigDecimal} e non {@code double}, come {@code Task.stimaOre}:
     * la colonna è un {@code DECIMAL(9,2)} e questo valore va sommato e
     * sottratto, operazioni in cui la virgola mobile accumula errori.
     *
     * <p>Qui non ci sono né le ore allocate né quelle libere, di proposito:
     * {@code ore libere = monte_ore − somma(task.ore_allocate)} si calcola al
     * volo. Un campo salvato sarebbe una seconda verità da tenere in
     * sincronia con la prima.
     */
    @Column(name = "monte_ore", precision = 9, scale = 2)
    private BigDecimal monteOre;

    /**
     * Il PM. Può essere nullo solo in bozza: lo impone
     * {@code ck_lavoro_responsabile}, e il servizio lo ricontrolla per dare un
     * messaggio comprensibile invece di un errore del database.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsabile_id")
    private Utente responsabile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creato_da_id")
    private Utente creatoDa;

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
    protected Lavoro() {
    }

    public static Lavoro nuovo(String codice, String nome, String descrizione,
                               LocalDate dataInizio, LocalDate dataFinePrevista,
                               StatoLavoro stato, BigDecimal monteOre, Utente responsabile,
                               Utente creatoDa) {
        Lavoro lavoro = new Lavoro();
        lavoro.codice = codice;
        lavoro.nome = nome;
        lavoro.descrizione = descrizione;
        lavoro.dataInizio = dataInizio;
        lavoro.dataFinePrevista = dataFinePrevista;
        lavoro.stato = stato;
        lavoro.monteOre = monteOre;
        lavoro.responsabile = responsabile;
        lavoro.creatoDa = creatoDa;
        return lavoro;
    }

    /**
     * Cambia il monte ore.
     *
     * <p>La regola «mai sotto la somma delle ore già allocate alle task» non
     * sta qui: per verificarla servono le task, che l'entità non conosce. La
     * applica {@code ServizioOre.cambiaMonteOre}, dentro la transazione che
     * tiene bloccata la riga del lavoro — altrimenti fra il controllo e la
     * scrittura ci starebbe un'allocazione di qualcun altro.
     *
     * <p>Chiamarlo non basta a lasciare traccia: ogni variazione va
     * accompagnata da un {@code MovimentoOre}, ed è quello che permette di
     * rileggere il monte ore iniziale dopo la modifica.
     */
    public void cambiaMonteOre(BigDecimal nuovoValore) {
        this.monteOre = nuovoValore;
    }

    /**
     * Porta il lavoro nello stato annullato. È l'alternativa alla
     * cancellazione per un lavoro che ha già delle task.
     */
    public void annulla() {
        this.stato = StatoLavoro.ANNULLATO;
        this.annullatoIl = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getCodice() {
        return codice;
    }

    public String getNome() {
        return nome;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public LocalDate getDataInizio() {
        return dataInizio;
    }

    public LocalDate getDataFinePrevista() {
        return dataFinePrevista;
    }

    public StatoLavoro getStato() {
        return stato;
    }

    /** Nullo se il lavoro non e' annullato. */
    public LocalDateTime getAnnullatoIl() {
        return annullatoIl;
    }

    /** Nullo se il monte ore non è ancora stato fissato. */
    public BigDecimal getMonteOre() {
        return monteOre;
    }

    public Utente getResponsabile() {
        return responsabile;
    }

    public Utente getCreatoDa() {
        return creatoDa;
    }

    public LocalDateTime getCreatoIl() {
        return creatoIl;
    }

    public int getVersione() {
        return versione;
    }
}
