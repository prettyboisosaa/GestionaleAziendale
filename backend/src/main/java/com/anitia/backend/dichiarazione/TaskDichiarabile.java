package com.anitia.backend.dichiarazione;

import com.anitia.backend.task.TaskRepository.TaskAperta;

/**
 * Una task su cui chi chiede puo' registrare ore: aperta e assegnata a lui.
 *
 * <p>Arriva insieme alla giornata invece che da una rotta a parte, perche' e'
 * quello che serve a riempire la tendina: chiederla separatamente vorrebbe
 * dire due chiamate per disegnare una schermata.
 */
public record TaskDichiarabile(Long taskId,
                               String titolo,
                               Long lavoroId,
                               String codiceLavoro,
                               String nomeLavoro) {

    static TaskDichiarabile da(TaskAperta riga) {
        return new TaskDichiarabile(riga.getTaskId(), riga.getTitolo(), riga.getLavoroId(),
                riga.getCodiceLavoro(), riga.getNomeLavoro());
    }
}
