package com.anitia.backend.assenza;

/**
 * Le tre tipologie di richiesta. I valori combaciano con il vincolo
 * {@code ck_assenza_tipo} della migrazione V12.
 *
 * <p>Ogni tipologia ha il suo form, e il tipo decide quali campi sono
 * obbligatori: è questo enum, e non il controller, a dire quali — così la
 * regola sta accanto al valore che la determina invece che sparsa nei
 * controlli del servizio.
 */
public enum TipoAssenza {

    /** Giornate intere, dal primo all'ultimo giorno. */
    FERIE,

    /** Un giorno solo, con una fascia oraria. */
    PERMESSO_ORARIO,

    /**
     * Giornata intera oppure fascia oraria, con un motivo fra quelli
     * previsti da {@link MotivoPermesso}.
     */
    PERMESSO_RETRIBUITO;

    /** Vero se la tipologia pretende una fascia oraria. */
    public boolean richiedeOrario() {
        return this == PERMESSO_ORARIO;
    }

    /** Vero se la tipologia non ammette una fascia oraria. */
    public boolean vietaOrario() {
        return this == FERIE;
    }

    /** Vero se la tipologia pretende un motivo in {@code dettagli}. */
    public boolean richiedeMotivo() {
        return this == PERMESSO_RETRIBUITO;
    }

    public String etichetta() {
        return switch (this) {
            case FERIE -> "Ferie";
            case PERMESSO_ORARIO -> "Permesso orario";
            case PERMESSO_RETRIBUITO -> "Permesso retribuito";
        };
    }
}
