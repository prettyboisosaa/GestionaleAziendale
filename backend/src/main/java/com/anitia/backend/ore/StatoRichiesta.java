package com.anitia.backend.ore;

/**
 * Lo stato di una richiesta di ore. I valori combaciano con il vincolo
 * {@code ck_richiesta_stato} della migrazione V8.
 *
 * <p>Tre esiti invece di due, e la distinzione conta: l'approvazione parziale
 * e' il caso che il piano iniziale non gestiva. Quando il pool non basta ma
 * non e' vuoto, il PM alloca le ore libere e chiede solo la differenza; e
 * l'amministratore, a sua volta, puo' concederne una parte. Sapere che una
 * richiesta e' stata accolta <em>a meta'</em> e' diverso dal vederla
 * approvata, perche' le ore che mancano restano un problema aperto.
 *
 * <p>I vincoli {@code ck_richiesta_rifiuto} e {@code ck_richiesta_totale} del
 * database impediscono che l'etichetta menta sui numeri: rifiutata vuol dire
 * zero ore concesse, approvata vuol dire tutte quelle chieste.
 */
public enum StatoRichiesta {

    /** Sul tavolo dell'amministratore. */
    IN_ATTESA,

    /** Concesse tutte le ore chieste. */
    APPROVATA,

    /** Concesse meno ore di quelle chieste, ma piu' di zero. */
    APPROVATA_PARZIALE,

    /** Nessuna ora concessa. */
    RIFIUTATA
}
