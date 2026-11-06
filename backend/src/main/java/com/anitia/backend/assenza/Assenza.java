package com.anitia.backend.assenza;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/**
 * Una richiesta di ferie o di permesso.
 *
 * <p>Una entità per tutte e tre le tipologie, come la tabella: ciò su cui si
 * interroga il database — chi, quando, in che stato — è comune, e le
 * tipologie si distinguono solo per i campi del form, che stanno in
 * {@link #dettagli}.
 *
 * <p>Le transizioni di stato sono metodi di questa classe e non assegnamenti
 * sparsi nel servizio: {@link #approva}, {@link #rifiuta} e {@link #ritira}
 * tengono insieme lo stato e i campi dell'esito, che i vincoli
 * {@code ck_assenza_decisa} e {@code ck_assenza_nota_rifiuto} pretendono
 * coerenti fra loro.
 */
@Entity
@Table(name = "richiesta_assenza")
public class Assenza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Chi la chiede. {@code LAZY} perché l'elenco del dipendente non ha
     * bisogno del proprio nome, e quello dell'admin lo carica con un
     * {@code JOIN FETCH} esplicito.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id", nullable = false)
    private Utente utente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoAssenza tipo;

    @Column(name = "data_inizio", nullable = false)
    private LocalDate dataInizio;

    /** Uguale a {@link #dataInizio} quando l'assenza dura un giorno solo. */
    @Column(name = "data_fine", nullable = false)
    private LocalDate dataFine;

    /**
     * Nullo per le giornate intere. Le due ore vanno insieme, come pretende
     * {@code ck_assenza_ore_coppia}: questo è nullo se e solo se è nullo
     * {@link #oraFine}.
     */
    @Column(name = "ora_inizio")
    private LocalTime oraInizio;

    @Column(name = "ora_fine")
    private LocalTime oraFine;

    /**
     * I campi propri della tipologia, nella colonna JSON.
     *
     * <p>{@code @JdbcTypeCode(SqlTypes.JSON)} fa serializzare il record da
     * Jackson: la colonna resta un JSON vero, interrogabile da SQL, e il
     * codice Java continua a vedere un tipo con dei campi invece di una
     * stringa da analizzare a mano.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private DettagliAssenza dettagli;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatoAssenza stato;

    /** L'admin che ha deciso; nullo finché è in attesa, e per una ritirata. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gestita_da_id")
    private Utente gestitaDa;

    @Column(name = "gestita_il")
    private LocalDateTime gestitaIl;

    /** Obbligatoria sul rifiuto, facoltativa sull'approvazione. */
    @Column(name = "nota_esito", length = 500)
    private String notaEsito;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "creata_il")
    private LocalDateTime creataIl;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "aggiornata_il")
    private LocalDateTime aggiornataIl;

    @Version
    @Column(nullable = false)
    private int versione;

    /** Richiesto da JPA. */
    protected Assenza() {
    }

    /**
     * Una richiesta nuova, in attesa. Le date e gli orari arrivano già
     * validati da {@link ServizioAssenze}: qui si fida, perché i controlli
     * dipendono dal tipo e vanno scritti in un posto solo.
     */
    static Assenza nuova(Utente utente, TipoAssenza tipo, LocalDate dataInizio,
                         LocalDate dataFine, LocalTime oraInizio, LocalTime oraFine,
                         DettagliAssenza dettagli) {
        Assenza assenza = new Assenza();
        assenza.utente = utente;
        assenza.tipo = tipo;
        assenza.dataInizio = dataInizio;
        assenza.dataFine = dataFine;
        assenza.oraInizio = oraInizio;
        assenza.oraFine = oraFine;
        assenza.dettagli = dettagli == null ? DettagliAssenza.VUOTI : dettagli;
        assenza.stato = StatoAssenza.IN_ATTESA;
        return assenza;
    }

    /**
     * Riscrive i campi compilabili. Non tocca lo stato né l'esito: il
     * servizio permette la modifica solo quando la richiesta è in attesa,
     * quindi non c'è nessun esito da conservare.
     */
    void modifica(TipoAssenza tipo, LocalDate dataInizio, LocalDate dataFine,
                  LocalTime oraInizio, LocalTime oraFine, DettagliAssenza dettagli) {
        this.tipo = tipo;
        this.dataInizio = dataInizio;
        this.dataFine = dataFine;
        this.oraInizio = oraInizio;
        this.oraFine = oraFine;
        this.dettagli = dettagli == null ? DettagliAssenza.VUOTI : dettagli;
    }

    void approva(Utente admin, String nota) {
        this.stato = StatoAssenza.APPROVATA;
        this.gestitaDa = admin;
        this.gestitaIl = LocalDateTime.now();
        this.notaEsito = nota;
    }

    void rifiuta(Utente admin, String nota) {
        this.stato = StatoAssenza.RIFIUTATA;
        this.gestitaDa = admin;
        this.gestitaIl = LocalDateTime.now();
        this.notaEsito = nota;
    }

    /**
     * Ritiro da parte del dipendente. Non valorizza {@code gestitaDa}: non
     * l'ha decisa un admin, e {@code ck_assenza_decisa} pretende che i campi
     * dell'esito restino vuoti per questo stato.
     */
    void ritira() {
        this.stato = StatoAssenza.RITIRATA;
    }

    public Long getId() {
        return id;
    }

    public Utente getUtente() {
        return utente;
    }

    public TipoAssenza getTipo() {
        return tipo;
    }

    public LocalDate getDataInizio() {
        return dataInizio;
    }

    public LocalDate getDataFine() {
        return dataFine;
    }

    /** Nullo per le giornate intere. */
    public LocalTime getOraInizio() {
        return oraInizio;
    }

    public LocalTime getOraFine() {
        return oraFine;
    }

    public DettagliAssenza getDettagli() {
        return dettagli;
    }

    public StatoAssenza getStato() {
        return stato;
    }

    public Utente getGestitaDa() {
        return gestitaDa;
    }

    public LocalDateTime getGestitaIl() {
        return gestitaIl;
    }

    public String getNotaEsito() {
        return notaEsito;
    }

    public LocalDateTime getCreataIl() {
        return creataIl;
    }
}
