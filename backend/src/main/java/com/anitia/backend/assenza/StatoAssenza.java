package com.anitia.backend.assenza;

/**
 * Lo stato di una richiesta di assenza. I valori combaciano con il vincolo
 * {@code ck_assenza_stato} della migrazione V12.
 */
public enum StatoAssenza {

    IN_ATTESA,

    APPROVATA,

    RIFIUTATA,

    /** Ritirata dal dipendente prima che l'admin decidesse. */
    RITIRATA;

    /**
     * Vero se la richiesta è ancora aperta: solo in questo stato il
     * dipendente la modifica o la ritira, e solo in questo stato l'admin la
     * decide.
     */
    public boolean inAttesa() {
        return this == IN_ATTESA;
    }

    /**
     * Vero se la richiesta occupa il calendario, cioè se va contata nel
     * controllo di sovrapposizione: una rifiutata o ritirata non impegna
     * nessun giorno.
     */
    public boolean impegna() {
        return this == IN_ATTESA || this == APPROVATA;
    }

    public String etichetta() {
        return switch (this) {
            case IN_ATTESA -> "in attesa";
            case APPROVATA -> "approvata";
            case RIFIUTATA -> "rifiutata";
            case RITIRATA -> "ritirata";
        };
    }
}
