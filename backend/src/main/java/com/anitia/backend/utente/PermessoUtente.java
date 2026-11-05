package com.anitia.backend.utente;

/**
 * Un permesso del catalogo visto dalla parte di una persona.
 *
 * <p>Esce per <strong>tutti</strong> i 22 permessi, anche quelli che la
 * persona non ha: sono le caselle da spuntare.
 *
 * <p>{@code dalRuolo} e {@code origine} sono separati di proposito: il primo
 * dice cosa direbbero i ruoli, il secondo cosa vale adesso e perché. È
 * l'informazione che permette all'interfaccia di spiegare la situazione invece
 * di mostrare una casella muta.
 */
public record PermessoUtente(String codice,
                             String categoria,
                             String descrizione,
                             /** Vero se un ruolo della persona concede questo permesso. */
                             boolean dalRuolo,
                             OriginePermesso origine,
                             /** Quello che vale adesso, ed è ciò che la casella mostra. */
                             boolean effettivo) {
}
