package com.anitia.backend.dichiarazione;

import com.anitia.backend.utente.Utente;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * La dichiarazione di una giornata: che tipo di giorno è stato e, se si è
 * lavorato, su quali task e per quante ore.
 *
 * <p><strong>Una sola per persona e per giorno</strong>, e lo garantisce il
 * vincolo {@code uk_dich_utente_data} della migrazione V4, non il codice. È
 * quel vincolo a rendere affidabile il tetto giornaliero delle ore: se
 * esistessero due dichiarazioni per lo stesso giorno, sommare le voci di una
 * sola direbbe che c'è ancora spazio quando non ce n'è.
 *
 * <p>Le voci stanno qui come collezione, al contrario delle assegnazioni di
 * una task che vivono in un repository a parte: una dichiarazione <em>è</em>
 * l'insieme delle sue voci, non ha senso senza, si leggono e si scrivono
 * sempre insieme, e sono poche — al massimo quante le task su cui una persona
 * lavora in un giorno. Per questo la cascata e {@code orphanRemoval} sono
 * sicuri qui, mentre sulle task sarebbero un modo di perdere lo storico.
 */
@Entity
@Table(name = "dichiarazione")
public class Dichiarazione {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id")
    private Utente utente;

    @Column(nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_giornata", nullable = false, length = 20)
    private TipoGiornata tipoGiornata;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatoDichiarazione stato;

    /**
     * Quando è stata consegnata. Il vincolo {@code ck_dich_invio} pretende
     * che sia valorizzata appena lo stato non è più BOZZA: per questo la
     * scrive {@link #invia()} e non si tocca da fuori.
     */
    @Column(name = "inviata_il")
    private LocalDateTime inviataIl;

    @OneToMany(mappedBy = "dichiarazione", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("id")
    private List<VoceDichiarazione> voci = new ArrayList<>();

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
    protected Dichiarazione() {
    }

    /** Una giornata lavorata, ancora in bozza e senza voci. */
    public static Dichiarazione nuova(Utente utente, LocalDate data) {
        Dichiarazione dichiarazione = new Dichiarazione();
        dichiarazione.utente = utente;
        dichiarazione.data = data;
        // Il database ha dei DEFAULT, ma JPA scrive comunque le colonne
        // nell'INSERT: i valori vanno impostati qui.
        dichiarazione.tipoGiornata = TipoGiornata.LAVORATA;
        dichiarazione.stato = StatoDichiarazione.BOZZA;
        return dichiarazione;
    }

    /**
     * Aggiunge una voce. Il tetto giornaliero non si verifica qui: dipende da
     * un parametro di sistema, e un'entità non va a leggere il database.
     * Lo verifica {@code ServizioDichiarazioni} prima di chiamare questo
     * metodo.
     */
    public void aggiungi(VoceDichiarazione voce) {
        if (!tipoGiornata.ammetteOre()) {
            throw new IllegalStateException(
                    "Una giornata di tipo " + tipoGiornata + " non ammette ore.");
        }
        voci.add(voce);
    }

    /**
     * Toglie una voce, restituendo {@code false} se quella voce non c'è.
     *
     * <p>Qui la cancellazione è fisica, al contrario di quasi tutto il resto
     * del gestionale: una voce sbagliata — task giusta, ore sbagliate — non è
     * storia da conservare, è un errore di battitura. Lo storico che conta è
     * quello delle ore allocate e dei movimenti, che non si cancella mai.
     */
    public boolean togli(Long idVoce) {
        return voci.removeIf(voce -> voce.getId().equals(idVoce));
    }

    /** La somma delle ore delle voci: è il totale su cui si misura il tetto giornaliero. */
    public BigDecimal oreTotali() {
        return voci.stream()
                .map(VoceDichiarazione::getOre)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Cambia il tipo di giornata.
     *
     * <p>Passare a ferie, permesso o malattia <strong>svuota le voci</strong>:
     * un giorno di ferie con delle ore sopra sarebbe una contraddizione, e
     * lasciarle significherebbe conteggiarle nei report delle ore lavorate.
     * Chi chiama deve sapere che le perde, e per questo il servizio lo dice
     * nel messaggio prima di farlo.
     */
    public void cambiaTipo(TipoGiornata nuovoTipo) {
        this.tipoGiornata = nuovoTipo;
        if (!nuovoTipo.ammetteOre()) {
            voci.clear();
        }
    }

    /**
     * La consegna. {@code ck_dich_invio} pretende che {@code inviata_il} sia
     * valorizzata quando lo stato non è BOZZA: i due campi si scrivono qui
     * insieme, e non da fuori, perché separarli violerebbe il vincolo.
     */
    public void invia() {
        this.stato = StatoDichiarazione.INVIATA;
        this.inviataIl = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Utente getUtente() {
        return utente;
    }

    public LocalDate getData() {
        return data;
    }

    public TipoGiornata getTipoGiornata() {
        return tipoGiornata;
    }

    public StatoDichiarazione getStato() {
        return stato;
    }

    public LocalDateTime getInviataIl() {
        return inviataIl;
    }

    /** Non modificabile da fuori: si passa da {@link #aggiungi} e {@link #togli}. */
    public List<VoceDichiarazione> getVoci() {
        return List.copyOf(voci);
    }

    public int getVersione() {
        return versione;
    }
}
