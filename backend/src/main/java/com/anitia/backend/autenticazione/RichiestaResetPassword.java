package com.anitia.backend.autenticazione;

import jakarta.validation.constraints.NotBlank;

/**
 * La password nuova che un amministratore assegna a un altro utente.
 *
 * <p><strong>Un campo solo, e non due.</strong> Qui non c'è la password
 * attuale perché chi azzera non la conosce — è il motivo per cui questa
 * operazione esiste. Chi scrive non dimostra niente sull'utenza che tocca: la
 * garanzia è il permesso {@code UTENTI_RESET_PASSWORD}, non il corpo della
 * richiesta.
 *
 * <p>Non c'è nemmeno l'utente: quello sta nel percorso
 * ({@code PUT /api/utenti/{id}/password}), come in tutte le rotte di
 * amministrazione.
 *
 * <p>Pubblico, al contrario di {@link RichiestaCambioPassword}: questo lo
 * riceve {@code UtentiController}, che sta nel package degli utenti perché è
 * amministrazione. Le regole della password restano in
 * {@link RegolePassword} e non nelle annotazioni, per la stessa ragione
 * dell'altro record — una validazione dichiarativa non sa dire *quale* delle
 * tre condizioni manca.
 */
public record RichiestaResetPassword(

        @NotBlank(message = "Serve la nuova password")
        String password) {
}
