package com.anitia.backend.dichiarazione;

/**
 * Che tipo di giornata e' stata. I valori combaciano con il vincolo
 * {@code ck_dich_tipo} della migrazione V4.
 *
 * <p>Come per gli altri enum di stato del gestionale, aggiungere un valore
 * qui non basta: il CHECK nel database va modificato con una migrazione.
 */
public enum TipoGiornata {

    /** Si e' lavorato: e' l'unico tipo che ammette voci con ore su delle task. */
    LAVORATA,

    FERIE,

    PERMESSO,

    MALATTIA;

    /**
     * Vero se su questa giornata si possono registrare ore.
     *
     * <p>Un giorno di ferie con delle ore sopra sarebbe una contraddizione, e
     * il controllo sta qui invece che sparso nei servizi: basta aggiungere un
     * tipo per ricordarsi di rispondere anche a questa domanda.
     */
    public boolean ammetteOre() {
        return this == LAVORATA;
    }
}
