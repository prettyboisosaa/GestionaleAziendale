package com.anitia.backend.tempo;

import com.anitia.backend.lavoro.Lavoro;
import com.anitia.backend.task.Task;
import java.time.LocalDateTime;

/**
 * Una sessione come la vede chi la legge: la sua, o quella di una persona di
 * cui si ha il permesso di vedere il tempo.
 *
 * <p>{@code minuti} esce in minuti e non in ore, e non è una svista: è la
 * misura in cui la sessione è stata registrata, e convertirla qui vorrebbe
 * dire arrotondare due volte — una qui e una nella somma. Le ore sono un
 * totale, e i totali si fanno sui minuti.
 */
public record RigaSessione(Long id,
                           Long taskId,
                           String task,
                           Long lavoroId,
                           String codiceLavoro,
                           LocalDateTime avviataIl,
                           /** Null se sta ancora scorrendo. */
                           LocalDateTime chiusaIl,
                           long minuti,
                           OrigineSessione origine,
                           /** Falso solo per una chiusura d'ufficio non ancora guardata. */
                           boolean confermata) {

    static RigaSessione da(SessioneTask sessione) {
        Task task = sessione.getTask();
        Lavoro lavoro = task.getLavoro();
        return new RigaSessione(sessione.getId(), task.getId(), task.getTitolo(),
                lavoro.getId(), lavoro.getCodice(), sessione.getAvviataIl(),
                sessione.getChiusaIl(), sessione.durataMinuti(), sessione.getOrigine(),
                sessione.isConfermata());
    }
}
