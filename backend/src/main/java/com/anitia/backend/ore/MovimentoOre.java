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
 * Una variazione delle ore di un lavoro: chi, quando, quante e perché.
 *
 * <p>È la ragione per cui modificare il monte ore non cancella la stima
 * iniziale. I totali — ore libere del lavoro, ore consumate di una task — non
 * si salvano da nessuna parte e si calcolano al volo; questa tabella salva
 * invece la <strong>storia</strong>, e nessuna sua riga viene mai cancellata
 * o modificata. Non c'è nessun metodo che cambi un campo dopo la creazione, e
 * non è una dimenticanza.
 *
 * <p>Le ore sono <strong>firmate</strong>: positive quando entrano, negative
 * quando escono. I quattro metodi di creazione sono l'unica via per fare un
 * movimento proprio perché il segno giusto lo mettono loro — scriverlo a mano
 * a ogni chiamata vorrebbe dire sbagliarlo prima o poi, e il vincolo
 * {@code ck_movimento_segno} tradurrebbe l'errore in un'eccezione del
 * database invece che in un messaggio comprensibile.
 */
@Entity
@Table(name = "movimento_ore")
public class MovimentoOre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lavoro_id")
    private Lavoro lavoro;

    /**
     * La task interessata. Nulla per i movimenti che riguardano il solo monte
     * ore; obbligatoria per allocazioni e rilasci, come pretende
     * {@code ck_movimento_task}: un'allocazione senza task non vuol dire
     * niente.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    /**
     * La richiesta di ore che ha causato il movimento, se c'è.
     *
     * <p>È un id e non una relazione, al contrario di {@code Task.lavoro}:
     * quella è una relazione perché serve risalire al lavoro per decidere chi
     * può toccare la task, mentre da un movimento non si risale <em>mai</em>
     * alla richiesta. Il verso della dipendenza è l'altro — una richiesta
     * approvata produce movimenti — e tenerlo a senso unico evita un giro fra
     * le due entità che nessuno percorre.
     */
    @Column(name = "richiesta_id")
    private Long richiestaId;

    /** Firmata: positiva quando le ore entrano, negativa quando escono. */
    @Column(nullable = false, precision = 9, scale = 2)
    private BigDecimal ore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimento tipo;

    /** Facoltativo: il monte iniziale di un lavoro spesso non ha spiegazioni. */
    @Column(length = 500)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autore_id")
    private Utente autore;

    /** Valorizzata dal database, e riletta subito dopo la scrittura. */
    @Generated(event = EventType.INSERT)
    @Column(name = "creato_il")
    private LocalDateTime creatoIl;

    /** Richiesto da JPA. */
    protected MovimentoOre() {
    }

    /**
     * Il monte ore fissato alla creazione del lavoro. Da qui si rilegge la
     * stima iniziale quando l'admin l'ha poi modificata, quindi registrarlo
     * non è facoltativo.
     */
    public static MovimentoOre monteIniziale(Lavoro lavoro, BigDecimal ore, String motivo,
                                             Utente autore) {
        return nuovo(lavoro, null, null, ore.abs(), TipoMovimento.MONTE_INIZIALE, motivo, autore);
    }

    /**
     * Una variazione del monte ore. {@code differenza} è già firmata — in
     * aumento positiva, in diminuzione negativa — e passarla a zero non ha
     * senso: non sarebbe una modifica.
     *
     * @param richiestaId la richiesta approvata che la giustifica, o nullo se
     *                    l'admin ha deciso di sua iniziativa
     */
    public static MovimentoOre modificaMonte(Lavoro lavoro, BigDecimal differenza, String motivo,
                                             Utente autore, Long richiestaId) {
        if (differenza.signum() == 0) {
            throw new IllegalArgumentException("Un movimento di zero ore non è una modifica.");
        }
        return nuovo(lavoro, null, richiestaId, differenza, TipoMovimento.MODIFICA_MONTE, motivo,
                autore);
    }

    /** Ore che dal pool passano a una task. */
    public static MovimentoOre allocazione(Lavoro lavoro, Task task, BigDecimal ore, String motivo,
                                           Utente autore, Long richiestaId) {
        return nuovo(lavoro, task, richiestaId, ore.abs(), TipoMovimento.ALLOCAZIONE, motivo,
                autore);
    }

    /**
     * Ore che da una task chiusa o annullata tornano nel pool.
     *
     * <p>{@code residuo} si passa positivo — è «quante ore tornano» — e il
     * segno negativo lo mette questo metodo. Chiamarlo con un residuo
     * negativo vorrebbe dire che la task ha sforato, e in quel caso non si
     * rilascia niente: il controllo sta qui perché è l'ultimo punto in cui
     * l'errore è ancora riconoscibile.
     */
    public static MovimentoOre rilascio(Lavoro lavoro, Task task, BigDecimal residuo,
                                        String motivo, Utente autore) {
        if (residuo.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Non c'è niente da rilasciare: residuo " + residuo + ".");
        }
        return nuovo(lavoro, task, null, residuo.negate(), TipoMovimento.RILASCIO, motivo, autore);
    }

    private static MovimentoOre nuovo(Lavoro lavoro, Task task, Long richiestaId, BigDecimal ore,
                                      TipoMovimento tipo, String motivo, Utente autore) {
        MovimentoOre movimento = new MovimentoOre();
        movimento.lavoro = lavoro;
        movimento.task = task;
        movimento.richiestaId = richiestaId;
        movimento.ore = ore;
        movimento.tipo = tipo;
        movimento.motivo = motivo;
        movimento.autore = autore;
        return movimento;
    }

    public Long getId() {
        return id;
    }

    public Lavoro getLavoro() {
        return lavoro;
    }

    public Task getTask() {
        return task;
    }

    public Long getRichiestaId() {
        return richiestaId;
    }

    public BigDecimal getOre() {
        return ore;
    }

    public TipoMovimento getTipo() {
        return tipo;
    }

    public String getMotivo() {
        return motivo;
    }

    public Utente getAutore() {
        return autore;
    }

    public LocalDateTime getCreatoIl() {
        return creatoIl;
    }
}
