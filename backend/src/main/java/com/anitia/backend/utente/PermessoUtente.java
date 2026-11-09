package com.anitia.backend.utente;

/**
 * Un permesso del catalogo visto dalla parte di una persona.
 *
 * <p>Esce per <strong>tutto</strong> il catalogo, anche per i permessi che la
 * persona non ha: sono le caselle da spuntare. Il numero non si scrive qui —
 * cresce a ogni migrazione, e un commento che lo nomina invecchia da solo.
 *
 * <p>{@code dalRuolo} e {@code origine} sono separati di proposito: il primo
 * dice cosa direbbero i ruoli, il secondo cosa vale adesso e perché. È
 * l'informazione che permette all'interfaccia di spiegare la situazione invece
 * di mostrare una casella muta.
 *
 * <p>{@code base} viene prima dei tre perché non parla di questa persona ma
 * del permesso: è una proprietà del catalogo, e dice all'interfaccia che la
 * casella va mostrata spuntata e bloccata.
 */
public record PermessoUtente(String codice,
                             String categoria,
                             String descrizione,
                             /** Vero se è un permesso del pavimento: ce l'hanno tutti. */
                             boolean base,
                             /** Vero se un ruolo della persona concede questo permesso. */
                             boolean dalRuolo,
                             OriginePermesso origine,
                             /** Quello che vale adesso, ed è ciò che la casella mostra. */
                             boolean effettivo) {
}
