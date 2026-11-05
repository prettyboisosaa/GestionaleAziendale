package com.anitia.backend.task;

/** Una persona assegnata a una task. */
public record Assegnatario(Long utenteId, String nome, String cognome) {
}
