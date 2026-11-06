package com.anitia.backend.utente;

import jakarta.validation.constraints.NotNull;

/**
 * Il corpo di {@code PATCH /api/utenti/{id}}: {@code {"attivo": false}} per
 * disattivare, {@code {"attivo": true}} per riattivare.
 *
 * <p>Un {@code Boolean} e non un {@code boolean}, con {@code @NotNull}: il
 * tipo primitivo prenderebbe {@code false} per un corpo che non nomina il
 * campo, e una richiesta vuota disattiverebbe l'utente senza averlo chiesto.
 */
public record RichiestaAttivazione(@NotNull(message = "Indica se l'utente deve essere attivo.")
                                   Boolean attivo) {
}
