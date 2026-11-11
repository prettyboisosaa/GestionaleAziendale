package com.anitia.backend.tempo;

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
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Un periodo di lavoro continuativo di una persona, di solito su una task.
 *
 * <p>Si apre quando il dipendente avvia la task e si chiude quando mette in
 * pausa o la completa. È la grana con cui si misurano le ore lavorate, e ha
 * preso il posto della voce di dichiarazione: quella diceva «quattro ore il 6
 * novembre», questa dice da quando a quando.
 *
 * <p><strong>La task è facoltativa</strong> dalla {@code V20}. Una riunione,
 * una manutenzione interna, una trasferta sono ore vere che non stanno sotto
 * nessuna task, e appenderle a una qualunque vorrebbe dire scrivere ore di
 * sviluppo che non sono state fatte. Che cosa sia la sessione lo dice sempre
 * {@link TipoAttivita}, che invece non manca mai: senza task è l'unica cosa
 * che la descrive. Il timer, che apre le sessioni, ha sempre una task — una
 * sessione senza task nasce solo scritta a mano.
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

    /** Null per un'attività che non è una task: una riunione, una trasferta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id")
    private Utente utente;

    /**
     * Il lavoro a cui l'attività si riferisce pur non essendo una sua task:
     * la manutenzione per il cliente X.
     *
     * <p>Popolato <strong>solo</strong> quando {@link #task} è null: con la
     * task il lavoro si legge da lei, e due copie dello stesso riferimento
     * sono due occasioni di farle dire cose diverse. Si legge sempre da
     * {@link #lavoroCitato()}, che sa da dove prenderlo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lavoro_id")
    private Lavoro lavoro;

    @Column(name = "avviata_il", nullable = false)
    private LocalDateTime avviataIl;

    /** Null finché scorre: la fine di una cosa che non è finita non è uno zero. */
    @Column(name = "chiusa_il")
    private LocalDateTime chiusaIl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigineSessione origine;

    /**
     * Che genere di attività è. Non manca mai, nemmeno dove manca la task:
     * è la sola cosa che dica di una sessione che cosa si stava facendo.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_attivita", nullable = false, length = 30)
    private TipoAttivita tipoAttivita;

    /** Cosa si è fatto, a parole. Sempre facoltativa: nessuno la impone. */
    @Column(length = 500)
    private String descrizione;

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
        // Il timer parte da una task, quindi il tipo non si chiede a nessuno:
        // premere avvia su una task *è* dire che si sta lavorando a quella.
        sessione.tipoAttivita = TipoAttivita.LAVORO_TASK;
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
     *
     * <p>{@code tipoAttivita} arriva da fuori anche se c'è una task: una
     * riunione sul lavoro X può ben essere agganciata alla sua task, e
     * imporre {@code LAVORO_TASK} qui direbbe che quelle due ore sono state
     * sviluppo. Lo vieta il servizio, non questa fabbrica: chi chiama ha già
     * verificato che il tipo ammetta una task.
     */
    public static SessioneTask manuale(Task task, Utente utente,
                                       TipoAttivita tipoAttivita, String descrizione,
                                       LocalDateTime da, LocalDateTime a) {
        SessioneTask sessione = new SessioneTask();
        sessione.task = task;
        sessione.utente = utente;
        sessione.tipoAttivita = tipoAttivita;
        sessione.descrizione = descrizione;
        sessione.avviataIl = da;
        sessione.chiusaIl = a;
        sessione.origine = OrigineSessione.MANUALE;
        sessione.confermata = true;
        return sessione;
    }

    /**
     * Un'attività che non è una task: una riunione, una manutenzione, una
     * trasferta.
     *
     * <p>Nasce come {@link #manuale} — già chiusa, confermata, di origine
     * {@code MANUALE} — e se ne separa per una cosa sola: non ha una task.
     * Sono due fabbriche e non una con un parametro null perché è esattamente
     * quella differenza che decide il resto, e un {@code null} passato da
     * fuori non la dichiara.
     *
     * <p>{@code lavoro} è facoltativo. Dove c'è, dice su che cosa è stata
     * fatta la manutenzione; dove manca, l'attività non riguarda nessun
     * lavoro — la manutenzione dei sistemi interni, una formazione.
     *
     * <p><strong>Queste ore non consumano il monte ore del lavoro citato.</strong>
     * Non per una scelta scritta qui, ma per come il consuntivo è fatto: le
     * somme di {@code TaskRepository} partono dalle task, e una sessione
     * senza task ne resta fuori da sé. È il comportamento voluto — la
     * manutenzione di un lavoro consegnato non erode il budget con cui quel
     * lavoro è stato sviluppato. Nei totali della persona invece entra, perché
     * quelli sommano i minuti di tutte le sessioni, ed è tempo lavorato.
     */
    public static SessioneTask attivita(Utente utente, TipoAttivita tipoAttivita,
                                        Lavoro lavoro, String descrizione,
                                        LocalDateTime da, LocalDateTime a) {
        SessioneTask sessione = new SessioneTask();
        sessione.utente = utente;
        sessione.lavoro = lavoro;
        sessione.tipoAttivita = tipoAttivita;
        sessione.descrizione = descrizione;
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

    /**
     * Il dipendente corregge che cosa era quell'attività, e cosa ci ha fatto.
     *
     * <p>Separato da {@link #correggi}, che sposta gli istanti: quello
     * riscrive una misura, questo riscrive un racconto, e una correzione può
     * ben essere solo dell'uno o solo dell'altro.
     *
     * <p><strong>La task non si cambia da qui</strong>, e non c'è un metodo
     * che lo faccia: una sessione è il tempo passato su <em>quella</em> task,
     * e spostarlo su un'altra non è correggere — è dire che si è lavorato a
     * qualcos'altro. La strada per quello è annullare e riscrivere. Il tipo
     * invece si corregge, perché chi registra può sbagliare tendina.
     *
     * <p>Sostituisce invece di aggiornare il non-null: una descrizione
     * cancellata deve poter tornare vuota, e un {@code null} che vuol dire
     * «lascia com'era» renderebbe impossibile svuotarla.
     */
    public void ridescrivi(TipoAttivita tipoAttivita, Lavoro lavoro, String descrizione) {
        this.tipoAttivita = tipoAttivita;
        // Con una task il lavoro si legge da lei: tenere anche la colonna
        // vorrebbe dire poterla far puntare altrove.
        this.lavoro = task == null ? lavoro : null;
        this.descrizione = descrizione;
    }

    /** La sessione non andava registrata: la riga resta, fuori dai conti. */
    public void annulla() {
        this.annullataIl = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    /** Null per un'attività che non è una task. */
    public Task getTask() {
        return task;
    }

    public Utente getUtente() {
        return utente;
    }

    public TipoAttivita getTipoAttivita() {
        return tipoAttivita;
    }

    /** Null se chi ha registrato non ha scritto niente. */
    public String getDescrizione() {
        return descrizione;
    }

    /**
     * Il lavoro di cui questa sessione parla, da qualunque delle due strade
     * arrivi: quello della task se c'è una task, la colonna altrimenti.
     *
     * <p>Null se l'attività non riguarda nessun lavoro — la manutenzione
     * interna, una formazione.
     *
     * <p>Sta qui e non in chi legge perché è la sola risposta giusta alla
     * domanda «di che lavoro è questa riga», e chi la pone non deve sapere
     * quale delle due colonne guardare.
     */
    public Lavoro lavoroCitato() {
        return task == null ? lavoro : task.getLavoro();
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
