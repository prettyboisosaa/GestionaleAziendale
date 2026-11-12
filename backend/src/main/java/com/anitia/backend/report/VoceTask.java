package com.anitia.backend.report;

/**
 * Una task dentro la ripartizione di un lavoro: il terzo e ultimo livello del
 * report.
 *
 * <p>Non porta {@code minutiNonConfermati}, a differenza del lavoro e della
 * persona: a questa profondità il dettaglio è già quello delle singole
 * sessioni, e chi vuole sapere quali ore nessuno ha controllato ha
 * {@code /api/tempo/utente/{id}} che glielo dice sessione per sessione. Un
 * numero in più su ogni riga, letto da nessuno, è peso.
 */
public record VoceTask(Long taskId,
                       String titolo,
                       long minuti) {
}
