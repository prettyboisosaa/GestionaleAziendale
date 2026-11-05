package com.anitia.backend.utente;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** I dati per creare un utente, inviati a {@code POST /api/utenti}. */
public record RichiestaNuovoUtente(

        @NotBlank(message = "Il nome è obbligatorio")
        @Size(max = 100, message = "Il nome è troppo lungo")
        String nome,

        @NotBlank(message = "Il cognome è obbligatorio")
        @Size(max = 100, message = "Il cognome è troppo lungo")
        String cognome,

        @NotBlank(message = "L'email è obbligatoria")
        @Email(message = "L'email non è valida")
        @Size(max = 255, message = "L'email è troppo lunga")
        String email,

        @NotBlank(message = "La password è obbligatoria")
        @Size(min = 10, max = 100, message = "La password deve avere almeno 10 caratteri")
        String password,

        /*
         * Almeno un ruolo: un utente senza ruoli non ha nessun permesso e non
         * può fare niente nel gestionale, quindi crearlo così è un errore, non
         * una scelta.
         */
        @NotEmpty(message = "Scegli almeno un ruolo")
        Set<Long> ruoli) {
}
