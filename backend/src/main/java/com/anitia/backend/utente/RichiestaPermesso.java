package com.anitia.backend.utente;

import jakarta.validation.constraints.NotNull;

/**
 * Il corpo di {@code PUT /api/utenti/{id}/permessi/{codice}}: una sola
 * domanda, «questa persona deve poter fare questa cosa?».
 *
 * <p>Quale riga scrivere — una concessione, una negazione, o la cancellazione
 * di uno scostamento — lo decide il server. Così la logica sta in un posto
 * solo e l'interfaccia espone una casella e nient'altro.
 */
public record RichiestaPermesso(@NotNull(message = "Indica se il permesso è attivo")
                                Boolean effettivo) {
}
