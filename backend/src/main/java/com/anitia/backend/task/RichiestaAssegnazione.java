package com.anitia.backend.task;

import jakarta.validation.constraints.NotNull;

/** Chi assegnare a una task. */
public record RichiestaAssegnazione(@NotNull(message = "Scegli una persona") Long utenteId) {
}
