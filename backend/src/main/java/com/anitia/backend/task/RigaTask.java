package com.anitia.backend.task;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
                       /**
                        * Quanto questa task viene prima delle altre. <strong>Può
                        * essere nulla</strong>: la {@code V19} ha aggiunto la colonna
                        * senza riempire le righe esistenti, quindi le task create
                        * prima non hanno un'urgenza e non se ne può inventare una.
                        */
                       TaskImportanza urgenzaTask,
                       /**
                        * Quando la task e' stata completata, oppure null se non lo e'.
                        *
                        * <p>Puo' essere null <strong>anche su una task completata</strong>:
                        * la colonna e' arrivata con la {@code V21}, e le task chiuse
                        * prima non hanno una data. Chi disegna non deve dedurre la
                        * chiusura da questo campo — lo stato la dice — ma usarlo solo
                        * per scrivere <em>quando</em>, se c'e'.
                        */
                       LocalDateTime completatoIl,
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
                task.getStato(), TaskImportanza.daStringa(task.getUrgenzaTask()),
                task.getCompletatoIl(),
                assegnatari);
    }
}
