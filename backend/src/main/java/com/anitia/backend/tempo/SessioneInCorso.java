package com.anitia.backend.tempo;

import com.anitia.backend.lavoro.Lavoro;
import com.anitia.backend.task.Task;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * La sessione che sta scorrendo adesso, per la barra in testata.
 *
 * <p>Porta {@code avviataIl} e <strong>non</strong> i minuti trascorsi: il
 * tempo che scorre lo conta il browser dall'istante di avvio, e un numero
 * calcolato dal server sarebbe vecchio di un secondo appena arrivato.
 * {@code minutiFinora} c'è comunque, per chi mostra la pagina senza far
 * scorrere niente.
 */
public record SessioneInCorso(Long id,
                              Long taskId,
                              String task,
                              Long lavoroId,
                              String codiceLavoro,
                              LocalDateTime avviataIl,
                              long minutiFinora) {

    static SessioneInCorso da(SessioneTask sessione, LocalDateTime adesso) {
        Task task = sessione.getTask();
        Lavoro lavoro = task.getLavoro();
        return new SessioneInCorso(sessione.getId(), task.getId(), task.getTitolo(),
                lavoro.getId(), lavoro.getCodice(), sessione.getAvviataIl(),
                Duration.between(sessione.getAvviataIl(), adesso).toMinutes());
    }
}
