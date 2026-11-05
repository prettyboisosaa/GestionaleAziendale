package com.anitia.backend.task;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Una task come la vede il frontend, con chi ci lavora e la sua situazione di ore. */
public record RigaTask(Long id,
                       String titolo,
                       String descrizione,
                       LocalDate dataInizio,
                       LocalDate scadenza,
                       /** Quante ore servono secondo il PM. Facoltativa. */
                       BigDecimal stimaOre,
                       /** Quante ore sono coperte dal budget del lavoro. Mai null. */
                       BigDecimal oreAllocate,
                       /** Quante ore sono state registrate dai dipendenti. Mai null. */
                       BigDecimal oreConsumate,
                       /**
                        * Vero quando le ore consumate hanno superato quelle allocate.
                        *
                        * <p>È <strong>calcolato</strong>, non un campo salvato: la verità sta
                        * nelle due somme, e un flag mantenuto in parallelo sarebbe una terza
                        * versione dei fatti da tenere in sincronia a ogni registrazione di
                        * ore. Il PM lo vede comparire da sé appena il dipendente registra la
                        * giornata che fa superare il budget.
                        */
                       boolean sforamento,
                       StatoTask stato,
                       List<Assegnatario> assegnatari) {

    /**
     * @param oreConsumate zero, non null, se nessuno ha ancora registrato ore
     *                     su questa task
     */
    static RigaTask da(Task task, BigDecimal oreConsumate, List<Assegnatario> assegnatari) {
        BigDecimal oreAllocate = task.getOreAllocate();
        return new RigaTask(task.getId(), task.getTitolo(), task.getDescrizione(),
                task.getDataInizio(), task.getScadenza(), task.getStimaOre(),
                oreAllocate, oreConsumate,
                oreConsumate.compareTo(oreAllocate) > 0,
                task.getStato(), assegnatari);
    }
}
