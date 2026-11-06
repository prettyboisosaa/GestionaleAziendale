package com.anitia.backend.utente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Un utente del gestionale. L'email è il nome utente per il login.
 *
 * <p>Che cosa può fare dipende solo dai permessi dei suoi ruoli: qui non c'è
 * nessun riferimento ai ruoli perché per l'autenticazione bastano le
 * credenziali, e i permessi si leggono dalla vista {@code permesso_effettivo}.
 */
@Entity
@Table(name = "utente")
public class Utente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, length = 100)
    private String cognome;

    @Column(nullable = false, length = 255)
    private String email;

    /** Hash BCrypt con prefisso {@code {bcrypt}}, mai la password in chiaro. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /** Se è falso l'utente non accede e non ha nessun permesso. */
    @Column(nullable = false)
    private boolean attivo;

    /**
     * Quando è stato disattivato, nullo se è attivo. Il vincolo
     * {@code ck_utente_disattivato} tiene i due campi d'accordo.
     *
     * <p>Serve all'interfaccia per dire «disattivato il 3 marzo» invece del
     * solo badge grigio. Non si ricava da {@code aggiornatoIl}, che cambia a
     * ogni modifica successiva e dopo una riattivazione racconterebbe quella.
     */
    @Column(name = "disattivato_il")
    private LocalDateTime disattivatoIl;

    /**
     * Valorizzate dal database, grazie ai DEFAULT dichiarati nella migrazione.
     *
     * <p>{@code @Generated} dice a Hibernate due cose: non scriverle mai, e
     * rileggerle subito dopo l'INSERT o l'UPDATE. Senza la rilettura, dopo un
     * {@code save()} resterebbero nulle nell'oggetto in memoria, e la risposta
     * della creazione mostrerebbe una data vuota.
     */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "creato_il")
    private LocalDateTime creatoIl;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "aggiornato_il")
    private LocalDateTime aggiornatoIl;

    /** Blocco ottimistico. */
    @Version
    @Column(nullable = false)
    private int versione;

    /** Richiesto da JPA. */
    protected Utente() {
    }

    /**
     * Un utente nuovo, attivo. La password arriva gia' sotto forma di hash:
     * qui dentro non deve passare mai una password in chiaro.
     */
    public static Utente nuovo(String nome, String cognome, String email, String passwordHash) {
        Utente utente = new Utente();
        utente.nome = nome;
        utente.cognome = cognome;
        utente.email = email;
        utente.passwordHash = passwordHash;
        utente.attivo = true;
        return utente;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getCognome() {
        return cognome;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isAttivo() {
        return attivo;
    }

    /** Nullo se l'utente è attivo. */
    public LocalDateTime getDisattivatoIl() {
        return disattivatoIl;
    }

    /**
     * Chiude l'utenza: niente accesso e nessun permesso, ma la riga resta,
     * perché il suo nome compare nello storico di lavori, task e
     * dichiarazioni.
     *
     * <p>Non è idempotente di proposito: il servizio rifiuta già la
     * disattivazione di chi è spento, e sovrascrivere la data qui
     * cancellerebbe quella vera.
     */
    void disattiva() {
        this.attivo = false;
        this.disattivatoIl = LocalDateTime.now();
    }

    /** Riapre l'utenza. La data di disattivazione non serve più. */
    void riattiva() {
        this.attivo = true;
        this.disattivatoIl = null;
    }

    public LocalDateTime getCreatoIl() {
        return creatoIl;
    }

    public LocalDateTime getAggiornatoIl() {
        return aggiornatoIl;
    }

    public int getVersione() {
        return versione;
    }
}
