package com.anitia.backend.ore;

/**
 * Il tipo di un movimento di ore. I valori combaciano con il vincolo
 * {@code ck_movimento_tipo} della migrazione V8.
 *
 * <p>Come per {@code StatoLavoro} e {@code StatoTask}, aggiungere un valore
 * qui non basta: il CHECK nel database va modificato con una migrazione,
 * altrimenti MySQL rifiuta il salvataggio.
 *
 * <p>Il tipo porta con sé un <strong>segno</strong>, e non è un dettaglio
 * estetico: {@code ck_movimento_segno} lo impone, perché un rilascio
 * registrato come positivo gonfierebbe il saldo del lavoro. Le ore sono
 * positive quando entrano e negative quando escono, così il saldo è una
 * somma e non una lettura caso per caso.
 */
public enum TipoMovimento {

    /**
     * Il monte ore fissato alla creazione del lavoro. Sempre positivo, e ce
     * n'è al massimo uno per lavoro: è da qui che si rilegge la stima
     * iniziale dopo che l'admin ha modificato il monte ore.
     */
    MONTE_INIZIALE,

    /**
     * Una variazione del monte ore deciso dall'admin, in aumento o in
     * diminuzione. Se nasce dall'approvazione di una richiesta, il movimento
     * la cita in {@code richiestaId}.
     */
    MODIFICA_MONTE,

    /** Ore che dal pool del lavoro passano a una task. Sempre positivo. */
    ALLOCAZIONE,

    /**
     * Ore che da una task chiusa o annullata tornano nel pool. Sempre
     * negativo, perché escono dall'allocazione.
     */
    RILASCIO
}
