package com.anitia.backend.ore;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Le ore da allocare a una task, inviate a
 * {@code POST /api/lavori/{idLavoro}/task/{idTask}/ore}.
 *
 * <p>Come per {@code RichiestaMonteOre}, il motivo è obbligatorio: ogni
 * movimento di ore deve poter essere spiegato a distanza di mesi, quando
 * nessuno si ricorda più perché quella task aveva bisogno di venti ore in
 * più.
 *
 * <p>La capienza del pool non si verifica qui — dipende dalle altre task del
 * lavoro, che Bean Validation non vede. La verifica
 * {@link ServizioOre#alloca}, dentro la transazione che tiene bloccata la
 * riga del lavoro.
 */
public record RichiestaAllocazione(

        @NotNull(message = "Le ore sono obbligatorie")
        @Positive(message = "Le ore da allocare devono essere maggiori di zero")
        BigDecimal ore,

        @NotBlank(message = "Spiega perché queste ore servono")
        @Size(max = 500, message = "Il motivo è troppo lungo")
        String motivo) {
}
