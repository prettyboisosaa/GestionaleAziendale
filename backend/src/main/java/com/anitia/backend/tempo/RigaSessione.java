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
 *
 * <p><strong>Quattro campi sono nullable</strong> da quando una sessione può
 * non avere una task: {@code taskId}, {@code task}, {@code lavoroId},
 * {@code codiceLavoro}. Chi disegna una riga deve guardare
 * {@code tipoAttivita}, che invece c'è sempre — senza task è la sola cosa che
 * dica di quelle ore che cosa sono state.
 */
public record RigaSessione(Long id,
                           /** Null per un'attività che non è una task. */
                           Long taskId,
                           /** Il titolo della task, null se non c'è una task. */
                           String task,
                           /** Il lavoro della task, o quello citato dall'attività. */
                           Long lavoroId,
                           String codiceLavoro,
                           /** Che genere di attività è. Non è mai null. */
                           TipoAttivita tipoAttivita,
                           /** Cosa si è fatto, a parole. Null se nessuno l'ha scritto. */
                           String descrizione,
                           LocalDateTime avviataIl,
                           /** Null se sta ancora scorrendo. */
                           LocalDateTime chiusaIl,
                           long minuti,
                           OrigineSessione origine,
                           /** Falso solo per una chiusura d'ufficio non ancora guardata. */
                           boolean confermata) {

    static RigaSessione da(SessioneTask sessione) {
        Task task = sessione.getTask();
        Lavoro lavoro = sessione.lavoroCitato();
        return new RigaSessione(sessione.getId(),
                task == null ? null : task.getId(),
                task == null ? null : task.getTitolo(),
                lavoro == null ? null : lavoro.getId(),
                lavoro == null ? null : lavoro.getCodice(),
                sessione.getTipoAttivita(), sessione.getDescrizione(),
                sessione.getAvviataIl(), sessione.getChiusaIl(),
                sessione.durataMinuti(), sessione.getOrigine(),
                sessione.isConfermata());
    }
}
