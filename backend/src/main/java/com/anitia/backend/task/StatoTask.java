package com.anitia.backend.task;

/**
 * Gli stati di una task. I valori combaciano con il vincolo
 * {@code ck_task_stato} della migrazione V3.
 *
 * <p>Come per {@code StatoLavoro}, aggiungere un valore qui non basta: il
 * CHECK nel database va modificato con una migrazione, altrimenti MySQL
 * rifiuta il salvataggio.
 */
public enum StatoTask {

    DA_FARE,

    IN_CORSO,

    COMPLETATA,

    /** Annullata: non si farà, ma la riga e le sue assegnazioni restano. */
    ANNULLATA
}
