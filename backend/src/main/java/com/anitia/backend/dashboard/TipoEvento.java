package com.anitia.backend.dashboard;

/**
 * Che cosa rappresenta un evento del calendario.
 *
 * <p>Il frontend disegna in base a questo e non sa da quale tabella arrivi la
 * riga: è la ragione per cui gli eventi sono normalizzati in un formato solo.
 * Aggiungere una fonte nuova — riunioni, scadenze fiscali — vuol dire
 * aggiungere un valore qui e riempirlo nel servizio, senza toccare il
 * calendario.
 */
public enum TipoEvento {

    /** Una task assegnata a chi guarda. */
    TASK,

    /** Ferie o permesso, approvati o ancora in attesa. */
    ASSENZA,

    /** Una chiusura aziendale dalla tabella {@code festivita}. */
    FESTIVITA
}
