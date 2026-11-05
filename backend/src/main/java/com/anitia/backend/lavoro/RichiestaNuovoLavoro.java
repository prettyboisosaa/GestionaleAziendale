package com.anitia.backend.lavoro;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * I dati per creare un lavoro, inviati a {@code POST /api/lavori}.
 *
 * <p>Il codice non c'è: lo genera il server. Le regole che incrociano più
 * campi — le date in ordine, il responsabile obbligatorio se il lavoro è
 * attivo — stanno in {@link ServizioLavori}, perché Bean Validation non le
 * esprime bene e lì il messaggio d'errore può spiegare il perché.
 */
public record RichiestaNuovoLavoro(

        @NotBlank(message = "Il nome è obbligatorio")
        @Size(max = 200, message = "Il nome è troppo lungo")
        String nome,

        @Size(max = 65535, message = "La descrizione è troppo lunga")
        String descrizione,

        @NotNull(message = "La data di inizio è obbligatoria")
        LocalDate dataInizio,

        @NotNull(message = "La data di fine prevista è obbligatoria")
        LocalDate dataFinePrevista,

        @NotNull(message = "Lo stato è obbligatorio")
        StatoLavoro stato,

        /**
         * Il budget di ore, che solo chi ha {@code ORE_MONTE_GESTISCI} può
         * fissare. Facoltativo: lasciarlo fuori vuol dire «non ancora
         * deciso», non «zero ore».
         */
        @PositiveOrZero(message = "Il monte ore non può essere negativo")
        BigDecimal monteOre,

        /** Facoltativo in bozza, obbligatorio se il lavoro è attivo. */
        Long responsabileId) {
}
