package com.anitia.backend.autenticazione;

import jakarta.validation.constraints.NotBlank;

/**
 * Le due password del cambio: quella attuale e quella nuova.
 *
 * <p><strong>Non c'è l'utente.</strong> Chi sta cambiando la password lo dice
 * la sessione, mai il corpo della richiesta: con un identificativo qui dentro,
 * chiunque potrebbe cambiare la password di qualcun altro mandando un numero
 * diverso.
 *
 * <p>Le regole della password nuova non stanno nelle annotazioni ma in
 * {@link RegolePassword}: un messaggio di validazione dichiarativa non sa
 * dire *quale* delle tre condizioni manca, e il codice d'errore
 * {@code WEAK_PASSWORD} che il frontend riconosce lo deve emettere il
 * servizio. Qui si pretende solo che i due campi ci siano.
 */
record RichiestaCambioPassword(

        @NotBlank(message = "Serve la password attuale")
        String oldPassword,

        @NotBlank(message = "Serve la password nuova")
        String newPassword) {
}
