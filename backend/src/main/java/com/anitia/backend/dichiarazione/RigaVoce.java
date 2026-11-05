package com.anitia.backend.dichiarazione;

import com.anitia.backend.task.Task;
import java.math.BigDecimal;

/** Una riga di dichiarazione come la vede il frontend. */
public record RigaVoce(Long id,
                       Long taskId,
                       String task,
                       /** Il codice del lavoro, per distinguere task omonime. */
                       String codiceLavoro,
                       Long lavoroId,
                       BigDecimal ore,
                       String descrizione) {

    static RigaVoce da(VoceDichiarazione voce) {
        Task task = voce.getTask();
        return new RigaVoce(voce.getId(), task.getId(), task.getTitolo(),
                task.getLavoro().getCodice(), task.getLavoro().getId(),
                voce.getOre(), voce.getDescrizione());
    }
}
