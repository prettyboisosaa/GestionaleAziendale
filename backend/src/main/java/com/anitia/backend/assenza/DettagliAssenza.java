package com.anitia.backend.assenza;

/**
 * I campi propri di una tipologia, quelli che non sono comuni a tutte e tre.
 *
 * <p>Finisce nella colonna JSON {@code richiesta_assenza.dettagli}: le date e
 * gli orari sono colonne perché ci si filtra sopra, questi no. Tenerli come
 * colonne vorrebbe dire due colonne NULL su tre righe, e una tabella che
 * cresce di una colonna a ogni tipologia nuova.
 *
 * <p>Entrambi i campi possono essere nulli: le ferie e il permesso orario non
 * hanno motivo, e le note sono facoltative tranne quando il motivo è
 * {@code ALTRO}. È {@link ServizioAssenze} a verificarlo in base al tipo,
 * perché è una regola che il database, con un JSON dentro, non può esprimere.
 */
public record DettagliAssenza(MotivoPermesso motivo, String note) {

    static final DettagliAssenza VUOTI = new DettagliAssenza(null, null);
}
