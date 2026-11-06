package com.anitia.backend.task;

/**
 * Gli stati di una task. I valori combaciano con il vincolo
 * {@code ck_task_stato}, riscritto dalla migrazione V10: tenerli in un enum
 * fa sì che siano scritti in un posto solo.
 *
 * <p>Sono gli stessi quattro nomi di {@link com.anitia.backend.lavoro.StatoLavoro},
 * che ne ha in più soltanto {@code BOZZA}. Prima della V10 una task era
 * {@code DA_FARE} e {@code COMPLETATA} mentre un lavoro era {@code CHIUSO}:
 * due vocabolari per dire la stessa cosa, che chi legge una schermata con
 * entrambi doveva tradurre a mente.
 *
 * <p>Aggiungere un valore qui non basta: il CHECK nel database va modificato
 * con una migrazione, altrimenti MySQL rifiuta il salvataggio.
 */
public enum StatoTask {

    /** Creata, non ancora iniziata. */
    ATTIVO,

    IN_CORSO,

    COMPLETATO,

    /** Annullata: non si farà, ma la riga e le sue assegnazioni restano. */
    ANNULLATO;

    /**
     * Vero se la task è ancora in gioco: ci si possono registrare ore e la si
     * può scegliere nella tendina della dichiarazione.
     *
     * <p>La domanda sta qui e non ripetuta nei servizi perché la pongono in
     * quattro punti diversi — la query della tendina, il controllo del
     * servizio, i segni del calendario, il cambio di stato — e quattro copie
     * della stessa condizione sono quattro occasioni di aggiornarne tre.
     */
    public boolean aperta() {
        return this == ATTIVO || this == IN_CORSO;
    }

    /**
     * Vero se lo stato è definitivo. Una task completata o annullata non si
     * riapre: le sue ore residue sono già tornate nel pool del lavoro, e nel
     * frattempo possono essere finite altrove.
     */
    public boolean finale() {
        return !aperta();
    }

    /** Come si chiama questo stato quando lo si scrive a un essere umano. */
    public String etichetta() {
        return switch (this) {
            case ATTIVO -> "attiva";
            case IN_CORSO -> "in corso";
            case COMPLETATO -> "completata";
            case ANNULLATO -> "annullata";
        };
    }
}
