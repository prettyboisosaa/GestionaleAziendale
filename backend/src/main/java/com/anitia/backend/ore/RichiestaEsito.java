package com.anitia.backend.ore;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * La decisione dell'amministratore su una richiesta di ore, inviata a
 * {@code POST /api/richieste-ore/{id}/gestisci}.
 *
 * <p>Un numero solo, non uno stato: zero ore e' un rifiuto, tutte quelle
 * chieste sono un'approvazione, una parte e' un'approvazione parziale. Far
 * scegliere l'etichetta a chi chiama vorrebbe dire poterla far mentire sui
 * numeri, e {@code RichiestaOre.gestisci} la deduce invece di accettarla.
 */
public record RichiestaEsito(

        @NotNull(message = "Indica quante ore concedi: zero per rifiutare")
        @PositiveOrZero(message = "Le ore concesse non possono essere negative")
        BigDecimal oreConcesse) {
}
