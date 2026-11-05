package com.anitia.backend.lavoro;

/**
 * Gli stati di un lavoro. I valori combaciano con il vincolo
 * {@code ck_lavoro_stato}, aggiornato dalla migrazione V5: tenerli in un enum
 * fa sì che siano scritti in un posto solo.
 *
 * <p>Aggiungere un valore qui non basta: il CHECK nel database va modificato
 * con una migrazione, altrimenti MySQL rifiuta il salvataggio.
 */
public enum StatoLavoro {

    /** In preparazione: il responsabile può ancora mancare. */
    BOZZA,

    ATTIVO,

    /** Concluso. */
    CHIUSO,

    /**
     * Annullato: non si farà. Prende il posto della cancellazione, che
     * distruggerebbe lo storico delle task e delle dichiarazioni.
     */
    ANNULLATO
}
