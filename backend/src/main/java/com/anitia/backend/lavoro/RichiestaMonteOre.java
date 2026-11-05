package com.anitia.backend.lavoro;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Il nuovo monte ore di un lavoro, inviato a
 * {@code POST /api/lavori/{id}/monte-ore}.
 *
 * <p>Il motivo è <strong>obbligatorio</strong>, al contrario di quasi tutti
 * gli altri campi facoltativi del gestionale. È il punto di questa
 * funzionalità: il budget di un lavoro non cambia senza che resti scritto
 * perché, altrimenti a fine progetto si trova un numero diverso da quello
 * preventivato e nessuno sa più spiegarlo.
 *
 * <p>La regola «mai sotto la somma delle ore già allocate» sta in
 * {@link ServizioLavori#cambiaMonteOre}, non qui: dipende dallo stato delle
 * task, che Bean Validation non può conoscere.
 */
public record RichiestaMonteOre(

        @NotNull(message = "Il monte ore è obbligatorio")
        @PositiveOrZero(message = "Il monte ore non può essere negativo")
        BigDecimal monteOre,

        @NotBlank(message = "Spiega perché il monte ore cambia")
        @Size(max = 500, message = "Il motivo è troppo lungo")
        String motivo) {
}
