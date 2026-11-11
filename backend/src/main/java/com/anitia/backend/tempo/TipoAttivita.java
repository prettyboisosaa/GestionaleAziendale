package com.anitia.backend.tempo;

/**
 * Che genere di attività è stata registrata in una sessione.
 *
 * <p>Nasce da una cosa che il calendario settimanale non sapeva dire: non
 * tutto il lavoro di una giornata è una task. La manutenzione per un cliente,
 * quella sui sistemi interni, una riunione, una trasferta sono tempo vero, e
 * prima di questo enum non c'era modo di registrarle — o si appendevano a una
 * task che non le riguardava, falsando il consuntivo di un lavoro.
 *
 * <p>È un <strong>enum e non una tabella</strong>: questi valori sono le
 * categorie con cui l'azienda legge le proprie ore, e cambiarle è una
 * decisione, non un dato da amministrare. Una tabella gestibile dall'interfaccia
 * vorrebbe una schermata, un permesso e una storia delle rinomine, per poi
 * scoprire che l'elenco non cambia mai.
 *
 * <p>Rispecchia {@code ck_sessione_attivita}: aggiungere un valore qui vuole
 * una migrazione, altrimenti MySQL rifiuta la scrittura. È la stessa regola di
 * {@link OrigineSessione}, e per la stessa ragione.
 *
 * <p><strong>Non è {@link OrigineSessione}.</strong> Quella dice <em>quanto il
 * dato è stato misurato</em> — il timer, una deduzione, un ricordo — e questa
 * dice <em>che cosa si stava facendo</em>. Sono due domande indipendenti: una
 * riunione può essere cronometrata col timer o scritta a mano la sera.
 */
public enum TipoAttivita {

    /**
     * Lavoro su una task di un lavoro: il caso normale, e il solo che il
     * calendario sapeva registrare prima della {@code V20}.
     */
    LAVORO_TASK,

    /**
     * Manutenzione su un lavoro già consegnato.
     *
     * <p>È l'unico valore che <strong>pretende il lavoro</strong>: una
     * manutenzione senza dire su che cosa non si può né leggere né fatturare.
     * Le sue ore non toccano il monte ore di sviluppo di quel lavoro — si
     * registrano come fatte, non come consumate: lo spiega
     * {@link SessioneTask#attivita}.
     */
    MANUTENZIONE_CLIENTE,

    /** Manutenzione dei sistemi dell'azienda, che non è di nessun cliente. */
    MANUTENZIONE_INTERNA,

    /** Assistenza a qualcuno, dentro o fuori: una mano, non una consegna. */
    SUPPORTO,

    /** Una visita sul posto. */
    SOPRALLUOGO,

    /** Tempo passato a parlare, che è lavoro anche quando non produce righe. */
    RIUNIONE,

    /** Imparare qualcosa, o insegnarlo. */
    FORMAZIONE,

    /** Le ore che l'azienda spende su sé stessa: pratiche, documenti, conti. */
    AMMINISTRAZIONE,

    /** Il viaggio, che non è il lavoro che c'è in fondo al viaggio. */
    TRASFERTA,

    /**
     * Nient'altro di questo elenco.
     *
     * <p>C'è di proposito, e serve: senza, chi deve registrare mezz'ora che
     * non sta in nessuna categoria sceglie la più vicina, e quella categoria
     * smette di significare qualcosa. Qui la descrizione libera dice il resto.
     */
    ALTRO;

    /** Il nome con cui si mostra, in italiano. */
    public String etichetta() {
        return switch (this) {
            case LAVORO_TASK -> "Lavoro su task";
            case MANUTENZIONE_CLIENTE -> "Manutenzione cliente";
            case MANUTENZIONE_INTERNA -> "Manutenzione interna";
            case SUPPORTO -> "Supporto e assistenza";
            case SOPRALLUOGO -> "Sopralluogo";
            case RIUNIONE -> "Riunione";
            case FORMAZIONE -> "Formazione";
            case AMMINISTRAZIONE -> "Amministrazione";
            case TRASFERTA -> "Trasferta";
            case ALTRO -> "Altro";
        };
    }

    /**
     * Vero se senza una task questa attività non vuol dire niente.
     *
     * <p>Il predicato sta sull'enum e non in {@code ServizioTempo} perché la
     * regola la legge anche l'interfaccia, per decidere se mostrare la tendina
     * delle task: due copie direbbero prima o poi due cose diverse, e la
     * seconda sarebbe un modulo che si apre per ricevere un 400.
     */
    public boolean richiedeTask() {
        return this == LAVORO_TASK;
    }

    /**
     * Vero se questa attività va riferita a un lavoro, pur non avendo una task.
     *
     * <p>Solo la manutenzione cliente: per tutte le altre il lavoro è
     * <em>ammesso</em> e non richiesto — una riunione può riguardare un lavoro
     * preciso o nessuno, e pretenderlo costringerebbe a scegliere a caso.
     */
    public boolean richiedeLavoro() {
        return this == MANUTENZIONE_CLIENTE;
    }
}
