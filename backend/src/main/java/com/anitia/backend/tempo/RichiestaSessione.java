package com.anitia.backend.tempo;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Gli estremi di una sessione scritta a mano.
 *
 * <p>Due istanti e nient'altro, come {@link RichiestaCorrezione}: la durata
 * non si manda, la ricava il database dalla differenza. Mandare anche i minuti
 * vorrebbe dire poter mandare tre numeri in disaccordo fra loro, e poi
 * scegliere a quale credere.
 *
 * <p>La task non è qui: sta nell'indirizzo, perché è la task che si registra e
 * il tempo viene dietro — la stessa ragione per cui avviare e completare
 * stanno sotto {@code /api/lavori/{l}/task/{t}/} e non sotto
 * {@code /api/mio-tempo/}.
 *
 * <p>È un record a sé e non {@code RichiestaCorrezione} riusato: hanno la
 * stessa forma oggi, ma dicono due cose diverse — «questa sessione andava
 * così» e «questa sessione è esistita» — e un domani una delle due potrebbe
 * portare un campo che all'altra non serve.
 */
public record RichiestaSessione(@NotNull(message = "Serve l'ora di inizio")
                                LocalDateTime avviataIl,
                                @NotNull(message = "Serve l'ora di fine")
                                LocalDateTime chiusaIl) {
}
