package com.anitia.backend.lavoro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Come è andato un lavoro <strong>nel tempo</strong>: una riga per settimana,
 * dall'inizio del lavoro a oggi.
 *
 * <p>Esiste perché i KPI rispondono a «a che punto siamo» e non a «con che
 * ritmo ci siamo arrivati». Un lavoro al 96% del monte ore può essere un
 * lavoro quasi finito o un lavoro che ha bruciato tutto nelle ultime due
 * settimane, e i due casi chiedono decisioni opposte. La differenza si vede
 * solo mettendo le ore sull'asse del tempo.
 *
 * <p><strong>Le ore sono quelle di ciascuna settimana, non il cumulato.</strong>
 * Il totale progressivo lo costruisce chi disegna, sommando: è una somma che
 * il grafico rifà comunque per collocare i punti, e mandarla dal server
 * vorrebbe dire due definizioni dello stesso numero.
 *
 * <p>La riga della settimana c'è anche quando in quella settimana non è
 * successo niente: una serie con i buchi disegnerebbe una linea che salta da
 * un lunedì all'altro come se le settimane vuote non fossero passate, e il
 * ritmo è proprio quello che qui si vuole leggere.
 */
public record AndamentoLavoro(
        /**
         * Il monte ore del lavoro, oppure null se non è ancora fissato.
         *
         * <p>È qui perché è il soffitto del grafico, e perché da lui nasce la
         * linea del piano: senza, chi disegna avrebbe i punti ma non saprebbe
         * rispetto a che cosa.
         */
        BigDecimal monteOre,
        /** Il primo giorno del lavoro: dove la linea del piano parte da zero. */
        LocalDate dataInizio,
        /** La fine prevista: dove la linea del piano arriva al monte ore. */
        LocalDate dataFinePrevista,
        List<SettimanaLavoro> settimane) {

    /**
     * Una settimana di lavoro.
     *
     * @param inizio         il lunedì della settimana
     * @param ore            le ore registrate in quella settimana, zero se nessuna
     * @param taskCompletate quante task si sono chiuse in quella settimana
     */
    public record SettimanaLavoro(LocalDate inizio,
                                  BigDecimal ore,
                                  long taskCompletate) {
    }
}
