package com.anitia.backend.tempo;

import java.time.LocalDate;
import java.util.List;

/**
 * Una settimana di lavoro, da lunedì a domenica, come la vede chi l'ha
 * lavorata.
 *
 * <p>Nasce per la griglia a fasce orarie della dashboard, che è il primo posto
 * in cui il dipendente vede <em>dove</em> sono cadute le sue ore nella giornata
 * e non solo quante sono state. Fino a qui di tutto il tempo misurato dalla
 * V15 si vedeva un totale — «oggi hai registrato 3h 20m» — e la collocazione
 * nel giorno, che il dato contiene, non la leggeva nessuno.
 *
 * <p>Gli estremi li manda il server invece di lasciarli calcolare al
 * chiamante: il lunedì di una data dipende da quando comincia la settimana, e
 * due definizioni diverse — una qui e una nel browser — sposterebbero di un
 * giorno il confine fra una settimana e l'altra.
 *
 * <p>{@code minutiTotali} è una somma, quindi non si salva da nessuna parte:
 * la fa questo record ogni volta dai minuti delle sessioni, come ogni altro
 * totale del gestionale.
 */
public record SettimanaDiTempo(/** Il lunedì della settimana. */
                               LocalDate inizio,
                               /** La domenica, compresa. */
                               LocalDate fine,
                               /** La somma dei minuti delle sessioni non annullate. */
                               long minutiTotali,
                               /**
                                * Vero se è la settimana corrente, cioè se qui
                                * si può ancora scrivere.
                                *
                                * <p>Lo dice il server invece di lasciarlo
                                * dedurre a chi disegna la griglia: la regola
                                * della finestra sta in
                                * {@code ServizioTempo}, e una seconda copia
                                * nel browser prima o poi direbbe un'altra
                                * cosa. Una griglia che si apre per ricevere un
                                * 409 ha mentito a chi l'ha cliccata.
                                */
                               boolean modificabile,
                               List<RigaSessione> sessioni) {
}
