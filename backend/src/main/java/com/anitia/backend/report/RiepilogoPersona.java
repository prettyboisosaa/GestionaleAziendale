package com.anitia.backend.report;

/**
 * Una persona nella panoramica del report.
 *
 * <p>{@code attivo} viaggia perché un disattivato compare nell'elenco solo se
 * ha ore nel periodo — il suo lavoro passato esiste ancora — e chi legge deve
 * poter distinguerlo da chi è in forza: «zero ore» significa due cose molto
 * diverse nei due casi.
 *
 * <p>Nessuna percentuale qui dentro. In questa vista il denominatore sarebbe
 * il totale dell'azienda, che è un'altra domanda da quella che il report fa:
 * la ripartizione è <em>del tempo della persona</em>, e la si legge aprendo la
 * persona.
 */
public record RiepilogoPersona(Long utenteId,
                               String nome,
                               String cognome,
                               boolean attivo,
                               long minutiTotali,
                               long minutiNonConfermati) {
}
