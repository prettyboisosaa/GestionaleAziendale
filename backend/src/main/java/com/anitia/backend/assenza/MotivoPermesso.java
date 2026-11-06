package com.anitia.backend.assenza;

/**
 * I motivi ammessi per un permesso retribuito.
 *
 * <p>Un enum e non testo libero: sono le voci della tendina del form, e
 * lasciarle libere vorrebbe dire non poterle né contare né raggruppare.
 * Vivono dentro {@code dettagli}, la colonna JSON, perché riguardano una
 * tipologia su tre: una colonna dedicata sarebbe NULL due volte su tre.
 */
public enum MotivoPermesso {

    LUTTO,

    MATRIMONIO,

    LEGGE_104,

    DONAZIONE_SANGUE,

    VISITA_MEDICA,

    /** Richiede note obbligatorie: è l'unico motivo che non si spiega da sé. */
    ALTRO;

    public boolean richiedeNote() {
        return this == ALTRO;
    }

    public String etichetta() {
        return switch (this) {
            case LUTTO -> "Lutto";
            case MATRIMONIO -> "Matrimonio";
            case LEGGE_104 -> "Legge 104";
            case DONAZIONE_SANGUE -> "Donazione di sangue";
            case VISITA_MEDICA -> "Visita medica";
            case ALTRO -> "Altro";
        };
    }
}
