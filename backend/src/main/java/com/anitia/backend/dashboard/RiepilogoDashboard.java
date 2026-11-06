package com.anitia.backend.dashboard;

import com.anitia.backend.task.MiaTask;
import java.util.List;

/**
 * Tutto quello che serve a disegnare la dashboard, in una risposta sola.
 *
 * <p>Una chiamata e non quattro, come chiede il documento: i conteggi delle
 * card e le task che li compongono sono gli stessi dati visti in due modi, e
 * chiederli separatamente vorrebbe dire anche poterli vedere in disaccordo —
 * una card che dice «3 oggi» sopra una lista che ne mostra due.
 *
 * <p>Le task escono <strong>tutte</strong>, non solo le urgenti: i filtri
 * della lista («Tutte / Oggi / Scadute») e il filtro per giorno che nasce dal
 * clic sul calendario lavorano su questo stesso elenco, senza tornare al
 * server. Sono le task di una persona, qualche decina al massimo.
 */
public record RiepilogoDashboard(ContatoriDashboard contatori,

                                 List<MiaTask> task,

                                 /**
                                  * Gli id delle task che scadono in un giorno
                                  * di assenza approvata. La lista ci mette un
                                  * avviso accanto.
                                  *
                                  * <p>Sono qui e non dentro {@link MiaTask}
                                  * perché quel record lo restituisce anche
                                  * {@code /api/mie-task}, che di ferie non sa
                                  * nulla: il conflitto è una domanda della
                                  * dashboard, e resta dove la si fa.
                                  */
                                 List<Long> taskInConflitto) {
}
