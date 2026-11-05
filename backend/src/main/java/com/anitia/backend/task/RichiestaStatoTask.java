package com.anitia.backend.task;

import jakarta.validation.constraints.NotNull;

/**
 * Il nuovo stato di una task, inviato a
 * {@code POST /api/lavori/{idLavoro}/task/{idTask}/stato}.
 *
 * <p>Quali passaggi siano ammessi non si dichiara qui: dipende dallo stato in
 * cui la task si trova adesso, e lo decide
 * {@link ServizioTask#cambiaStato}. Lì il messaggio può anche spiegare perché
 * una task completata non torna indietro.
 */
public record RichiestaStatoTask(

        @NotNull(message = "Lo stato è obbligatorio")
        StatoTask stato) {
}
