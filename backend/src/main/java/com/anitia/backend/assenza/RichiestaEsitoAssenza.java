package com.anitia.backend.assenza;

import jakarta.validation.constraints.Size;

/**
 * Il corpo di un'approvazione o di un rifiuto: solo la nota.
 *
 * <p>Facoltativa qui, perché la stessa forma serve a entrambe le rotte, ma
 * <strong>obbligatoria sul rifiuto</strong>: lo verifica
 * {@link ServizioAssenze}, e lo ribadisce il vincolo
 * {@code ck_assenza_nota_rifiuto}. Un rifiuto senza motivazione è il modo più
 * sicuro di far riaprire la stessa richiesta il giorno dopo.
 */
public record RichiestaEsitoAssenza(@Size(max = 500,
                                          message = "La nota non può superare i 500 caratteri.")
                                    String nota) {

    /** La nota ripulita, oppure null se non è stata scritta. */
    String notaPulita() {
        return nota == null || nota.isBlank() ? null : nota.trim();
    }
}
