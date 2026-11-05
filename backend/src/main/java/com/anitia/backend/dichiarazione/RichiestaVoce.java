package com.anitia.backend.dichiarazione;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Le ore lavorate su una task in un giorno, inviate a
 * {@code POST /api/dichiarazioni/{data}/voci}.
 *
 * <p>La descrizione e' obbligatoria: il piano del monte ore chiede che ogni
 * registrazione dica <em>che cosa</em> si e' fatto, altrimenti a fine
 * progetto si sa quante ore sono andate su una task ma non in che cosa sono
 * finite.
 *
 * <p>Il {@code @DecimalMax} e' il limite della colonna {@code DECIMAL(4,2)},
 * non il tetto giornaliero: quello e' un parametro di sistema e vale sulla
 * somma della giornata, non su una voce sola. Lo verifica
 * {@link ServizioDichiarazioni}.
 */
public record RichiestaVoce(

        @NotNull(message = "Scegli la task")
        Long taskId,

        @NotNull(message = "Le ore sono obbligatorie")
        @Positive(message = "Le ore devono essere maggiori di zero")
        @DecimalMax(value = "99.99", message = "Troppe ore per una sola voce")
        BigDecimal ore,

        @NotBlank(message = "Descrivi che cosa hai fatto")
        @Size(max = 1000, message = "La descrizione e' troppo lunga")
        String descrizione) {
}
