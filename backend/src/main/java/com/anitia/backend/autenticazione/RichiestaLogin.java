package com.anitia.backend.autenticazione;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Le credenziali inviate a {@code POST /api/autenticazione/login}. */
public record RichiestaLogin(

        @NotBlank(message = "L'email è obbligatoria")
        @Email(message = "L'email non è valida")
        String email,

        @NotBlank(message = "La password è obbligatoria")
        String password) {
}
