package com.anitia.backend.tempo;

import java.time.LocalDate;
import java.util.List;

/**
 * Il tempo di una persona in un intervallo, per chi ha il permesso di
 * vederlo.
 *
 * <p>È l'erede di {@code GET /api/dichiarazioni/utente/{id}}, che nessuna
 * pagina chiamava: esiste perché il permesso esiste, e perché il giorno in cui
 * servirà un prospetto non si dovrà inventare da zero.
 *
 * <p>{@code minutiNonConfermati} sta accanto al totale e non dentro: le ore
 * non confermate <strong>sono</strong> nel totale — escluderle farebbe sparire
 * lavoro vero — ma chi legge deve sapere quanta parte del numero nessuno ha
 * ancora guardato.
 */
public record TempoDiUnaPersona(Long utenteId,
                                LocalDate da,
                                LocalDate a,
                                long minutiTotali,
                                long minutiNonConfermati,
                                List<RigaSessione> sessioni) {
}
