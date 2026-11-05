package com.anitia.backend.task;

/**
 * Perché una task compare in un giorno del calendario.
 *
 * <p>Una task occupa due giorni nel calendario di chi la deve fare, e sono due
 * cose diverse da sapere: il giorno in cui parte e il giorno in cui scade. Un
 * segno solo, senza dire quale dei due è, costringerebbe chi guarda a aprire
 * la task per capirlo.
 */
public enum TipoSegno {

    INIZIO,

    SCADENZA
}
