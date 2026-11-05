package com.anitia.backend.dichiarazione;

import jakarta.validation.constraints.NotNull;

/**
 * Il tipo di una giornata, inviato a
 * {@code POST /api/dichiarazioni/{data}/tipo}.
 *
 * <p>Passare a ferie, permesso o malattia cancella le voci di quel giorno: una
 * giornata non lavorata con delle ore sopra sarebbe una contraddizione, e
 * lasciarle le farebbe contare nei report. {@link ServizioDichiarazioni} lo
 * rifiuta se ci sono ore gia' scritte, invece di buttarle via di nascosto.
 */
public record RichiestaTipoGiornata(

        @NotNull(message = "Il tipo di giornata e' obbligatorio")
        TipoGiornata tipoGiornata) {
}
