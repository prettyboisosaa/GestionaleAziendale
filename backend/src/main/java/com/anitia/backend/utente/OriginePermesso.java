package com.anitia.backend.utente;

/** Da dove viene — o perché manca — un permesso di una persona. */
public enum OriginePermesso {

    /** Glielo dà un suo ruolo, e nessuno è intervenuto. */
    RUOLO,

    /** Aggiunto a questa persona, oltre a quello che dicono i suoi ruoli. */
    AGGIUNTO,

    /** Tolto a questa persona, nonostante un suo ruolo lo conceda. */
    TOGLIATO,

    /** Non glielo dà nessun ruolo e non è stato aggiunto. */
    NESSUNO
}
