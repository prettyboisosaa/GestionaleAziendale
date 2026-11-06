package com.anitia.backend.assenza;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Il corpo di una richiesta nuova o modificata.
 *
 * <p>Un record per tutte e tre le tipologie, con i campi di ciascuna
 * facoltativi qui dentro: è {@code tipo} a dire quali sono obbligatori, e
 * quella verifica la fa {@link ServizioAssenze}, non le annotazioni. Tre
 * record separati vorrebbero dire tre rotte, o un deserializzatore
 * polimorfico, per guadagnare dei {@code @NotNull} che comunque non
 * saprebbero esprimere «obbligatorio solo se il motivo è ALTRO».
 *
 * <p>I tre form del documento mandano tutti qui: quello delle ferie riempie
 * le date, quello del permesso orario le ore, quello del permesso retribuito
 * l'uno o l'altro più il motivo.
 */
public record RichiestaAssenza(@NotNull(message = "Scegli la tipologia di richiesta.")
                               TipoAssenza tipo,

                               @NotNull(message = "Indica il giorno di inizio.")
                               LocalDate dataInizio,

                               /**
                                * Per un giorno solo si può omettere: il
                                * servizio la fa coincidere con l'inizio,
                                * così il form del permesso orario non deve
                                * mandare due volte la stessa data.
                                */
                               LocalDate dataFine,

                               /** Richiesta per il permesso orario, vietata per le ferie. */
                               LocalTime oraInizio,

                               LocalTime oraFine,

                               /** Obbligatorio per il permesso retribuito. */
                               MotivoPermesso motivo,

                               @Size(max = 500, message = "Le note non possono superare i 500 caratteri.")
                               String note) {

    /** La data di fine effettiva: l'inizio, quando non è stata indicata. */
    LocalDate fineEffettiva() {
        return dataFine == null ? dataInizio : dataFine;
    }

    DettagliAssenza dettagli() {
        return new DettagliAssenza(motivo, note == null || note.isBlank() ? null : note.trim());
    }
}
