package com.anitia.backend.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * I dati per creare una task. Il lavoro non c'è: sta nell'indirizzo, perché
 * una task non esiste fuori da un lavoro.
 *
 * <p>La regola {@code scadenza >= dataInizio} sta nel servizio, come per le
 * date dei lavori: Bean Validation non esprime bene i confronti fra campi, e
 * lì il messaggio può spiegare cosa non va.
 */
public record RichiestaNuovaTask(

        @NotBlank(message = "Il titolo è obbligatorio")
        @Size(max = 200, message = "Il titolo è troppo lungo")
        String titolo,

        String descrizione,

        @NotNull(message = "La data di inizio è obbligatoria")
        LocalDate dataInizio,

        @NotNull(message = "La scadenza è obbligatoria")
        LocalDate scadenza,

        /** Facoltativa, ma se c'è deve essere positiva: lo vuole ck_task_stima. */
        @Positive(message = "La stima in ore deve essere maggiore di zero")
        BigDecimal stimaOre) {
}
