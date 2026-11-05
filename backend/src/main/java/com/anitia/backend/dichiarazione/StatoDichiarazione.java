package com.anitia.backend.dichiarazione;

/**
 * Lo stato di una dichiarazione giornaliera. I valori combaciano con il
 * vincolo {@code ck_dich_stato} della migrazione V4.
 */
public enum StatoDichiarazione {

    /** Il dipendente la sta ancora compilando: nessuno la vede nei report. */
    BOZZA,

    /**
     * Consegnata. Resta modificabile per i giorni lavorativi che dice il
     * parametro {@code dichiarazione.giorni_modifica}: correggere una
     * dimenticanza del giorno prima e' normale, riscrivere il mese scorso no.
     */
    INVIATA
}
