package com.anitia.backend.tempo;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Gli estremi corretti di una sessione.
 *
 * <p>Due istanti e nient'altro: la durata non si manda, la ricava il database
 * dalla differenza. Mandare anche i minuti vorrebbe dire poter mandare tre
 * numeri in disaccordo fra loro, e poi scegliere a quale credere.
 */
public record RichiestaCorrezione(@NotNull(message = "Serve l'ora di inizio")
                                  LocalDateTime avviataIl,
                                  @NotNull(message = "Serve l'ora di fine")
                                  LocalDateTime chiusaIl) {
}
