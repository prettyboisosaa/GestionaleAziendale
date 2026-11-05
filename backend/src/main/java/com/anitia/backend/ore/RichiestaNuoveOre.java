package com.anitia.backend.ore;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * I dati per aprire una richiesta di ore, inviati a
 * {@code POST /api/lavori/{idLavoro}/richieste-ore}.
 *
 * <p>Il motivo e' obbligatorio e lungo: e' l'unica cosa su cui
 * l'amministratore decide, insieme ai numeri del lavoro che la schermata gli
 * mostra. Una richiesta senza spiegazione lo costringerebbe a cercare il PM,
 * che e' esattamente la conversazione che questa funzionalita' sostituisce.
 */
public record RichiestaNuoveOre(

        /**
         * La task a cui destinare le ore concesse. Facoltativa: lasciandola
         * fuori, le ore restano nel pool del lavoro. Indicarla e' la garanzia
         * che non finiscano su un'altra task.
         */
        Long taskId,

        @NotNull(message = "Le ore sono obbligatorie")
        @Positive(message = "Le ore richieste devono essere maggiori di zero")
        BigDecimal oreRichieste,

        @NotBlank(message = "Spiega perche' queste ore servono")
        @Size(max = 500, message = "Il motivo e' troppo lungo")
        String motivo) {
}
