package com.anitia.backend.ore;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tutto quello che serve all'amministratore per decidere su una richiesta di
 * ore, in una risposta sola.
 *
 * <p>Risolve il difetto più serio del piano iniziale: l'amministratore
 * decideva <strong>senza contesto</strong>. Vedeva «Bianchi chiede 40 ore» e
 * non sapeva se quel lavoro ne avesse già consumate il doppio del previsto, se
 * ci fossero ore libere che il PM non aveva notato, o se fosse la quarta
 * richiesta in due mesi. Con quei dati sotto gli occhi la decisione è
 * informata; senza, è un atto di fiducia.
 *
 * <p>Il monte ore iniziale sta accanto a quello attuale per la ragione per cui
 * i movimenti esistono: vedere che un lavoro è partito da 376 ore ed è
 * arrivato a 400 dice qualcosa che il solo 400 non dice.
 */
public record ContestoRichiesta(RigaRichiestaOre richiesta,
                                /** La situazione delle ore del lavoro, adesso. */
                                RiepilogoOre ore,
                                /**
                                 * Il monte ore con cui il lavoro è partito, dal movimento
                                 * {@code MONTE_INIZIALE}. Nullo per i lavori nati senza
                                 * budget o prima della migrazione V8 — e nullo non è zero.
                                 */
                                BigDecimal monteOreIniziale,
                                /**
                                 * Le task del lavoro che hanno consumato più ore di quante
                                 * ne hanno allocate. È il segnale che dice se la richiesta
                                 * nasce da un problema reale.
                                 */
                                List<TaskInSforamento> sforamenti,
                                /**
                                 * Lo storico delle richieste di questo lavoro, dalla più
                                 * recente. La richiesta in esame compresa.
                                 */
                                List<RigaRichiestaOre> storico) {

    /** Una task che ha sforato, con il conto di quanto. */
    public record TaskInSforamento(Long taskId,
                                   String titolo,
                                   BigDecimal oreAllocate,
                                   BigDecimal oreConsumate,
                                   /** Di quante ore ha superato l'allocazione. */
                                   BigDecimal sforamento) {
    }
}
