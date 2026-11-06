package com.anitia.backend.lavoro;

/**
 * Gli stati di un lavoro. I valori combaciano con il vincolo
 * {@code ck_lavoro_stato}, riscritto dalla migrazione V10: tenerli in un enum
 * fa sì che siano scritti in un posto solo.
 *
 * <p>Quattro nomi sono gli stessi di {@link com.anitia.backend.task.StatoTask}.
 * Il quinto, {@code BOZZA}, esiste solo qui: è lo stato in cui il
 * responsabile può ancora mancare ed è l'unico in cui il lavoro si cancella
 * davvero invece di essere annullato. Non ha un equivalente fra gli stati di
 * una task, che nasce già dentro un lavoro e quindi già completa.
 *
 * <p>Aggiungere un valore qui non basta: il CHECK nel database va modificato
 * con una migrazione, altrimenti MySQL rifiuta il salvataggio.
 */
public enum StatoLavoro {

    /** In preparazione: il responsabile può ancora mancare. */
    BOZZA,

    /** Creato e pronto, non ancora iniziato. */
    ATTIVO,

    IN_CORSO,

    /** Concluso. Si chiamava {@code CHIUSO} prima della V10. */
    COMPLETATO,

    /**
     * Annullato: non si farà. Prende il posto della cancellazione, che
     * distruggerebbe lo storico delle task e delle dichiarazioni.
     */
    ANNULLATO;

    /**
     * Vero se sul lavoro si può ancora registrare: è la condizione che
     * decide quali lavori compaiono nel selettore della dichiarazione.
     *
     * <p>Una bozza non c'è: non è ancora partita, e non ha nemmeno
     * necessariamente un responsabile.
     */
    public boolean selezionabile() {
        return this == ATTIVO || this == IN_CORSO;
    }

    /** Vero se il lavoro è finito o non si farà: stato definitivo. */
    public boolean chiuso() {
        return this == COMPLETATO || this == ANNULLATO;
    }

    /** Come si chiama questo stato quando lo si scrive a un essere umano. */
    public String etichetta() {
        return switch (this) {
            case BOZZA -> "in bozza";
            case ATTIVO -> "attivo";
            case IN_CORSO -> "in corso";
            case COMPLETATO -> "completato";
            case ANNULLATO -> "annullato";
        };
    }
}
