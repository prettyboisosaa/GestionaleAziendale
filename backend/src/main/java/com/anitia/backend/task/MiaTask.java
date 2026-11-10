package com.anitia.backend.task;

import com.anitia.backend.lavoro.Lavoro;
import com.anitia.backend.lavoro.StatoLavoro;
import com.anitia.backend.utente.Utente;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Una task come la vede la persona a cui è stata affidata.
 *
 * <p>È una cosa diversa da {@link RigaTask}, e la differenza è il punto di
 * vista: {@code RigaTask} descrive una task <em>dentro un lavoro</em> e porta
 * l'elenco di chi ci lavora, perché serve a chi distribuisce il lavoro. Qui
 * chi guarda è già uno degli assegnati — l'elenco dei colleghi non gli dice
 * niente di nuovo — e gli servono invece il lavoro a cui la task appartiene,
 * il nome del responsabile a cui chiedere, e la distanza dalla scadenza.
 *
 * <p>Tre campi sono <strong>calcolati</strong> e non salvati da nessuna parte:
 * {@code sforamento}, {@code inRitardo} e {@code giorniAllaScadenza}. È la
 * stessa scelta che il monte ore fa per lo sforamento — i totali non si
 * salvano mai — estesa al ritardo, che per di più dipende da «oggi»: un flag
 * su una colonna andrebbe riscritto ogni notte da qualcosa che in questo
 * progetto non esiste.
 */
public record MiaTask(Long taskId,
                      String titolo,
                      String descrizione,
                      LocalDate dataInizio,
                      LocalDate scadenza,
                      StatoTask stato,

                      /** Quante ore sono coperte dal budget del lavoro. Mai null. */
                      BigDecimal oreAllocate,

                      /**
                       * Le ore registrate sulla task da <em>tutti</em> gli
                       * assegnati: è il numero da confrontare con
                       * {@code oreAllocate} per sapere se la task sta
                       * finendo il budget.
                       */
                      BigDecimal oreConsumate,

                      /**
                       * Le ore che ho registrato io. Separata dalla
                       * precedente perché rispondono a due domande diverse:
                       * «quanto resta alla task» e «quanto ci ho messo».
                       */
                      BigDecimal mieOre,

                      /** Calcolato: {@code oreConsumate > oreAllocate}. */
                      boolean sforamento,

                      /**
                       * Vero se sto lavorando a questa task <em>adesso</em>:
                       * ho una sessione aperta su di essa.
                       *
                       * <p>Serve all'interfaccia per sapere se mostrare
                       * «avvia» o «pausa». Ce n'e' al massimo una vera in
                       * tutta la risposta: una persona ha una sola sessione
                       * aperta, e lo garantisce un vincolo di unicita'.
                       */
                      boolean inCorso,

                      /** Calcolato: scadenza passata e task ancora aperta. */
                      boolean inRitardo,

                      /** Giorni di calendario fino alla scadenza, negativi se è passata. */
                      long giorniAllaScadenza,

                      Long lavoroId,
                      String codiceLavoro,
                      String nomeLavoro,

                      /**
                       * Lo stato del lavoro, non della task: su un lavoro
                       * chiuso o annullato la task resta visibile ma non si
                       * tocca più, e l'interfaccia deve poterlo dire senza
                       * una seconda chiamata.
                       */
                      StatoLavoro statoLavoro,

                      /** «Cognome Nome» del responsabile del lavoro, null se non assegnato. */
                      String responsabile,

                      /**
                       * Quanto questa task viene prima delle altre.
                       *
                       * <p><strong>Può essere nulla</strong>: la {@code V19}
                       * ha aggiunto la colonna senza riempire le righe
                       * esistenti, quindi le task create prima non hanno
                       * un'urgenza e non se ne inventa una.
                       *
                       * <p>Serve alla card «Urgenti» della dashboard, che la
                       * conta, e alla lista, che la filtra: entrambe lavorano
                       * su questo elenco senza tornare al server.
                       */
                      TaskImportanza urgenzaTask) {

    /**
     * @param oreConsumate zero, non null, se nessuno ha ancora registrato ore
     * @param mieOre       zero, non null, se io non ne ho ancora registrate
     * @param oggi         passato da fuori, non letto qui: il confronto con la
     *                     scadenza deve usare la stessa data per tutte le
     *                     righe di una risposta, e un elenco che attraversa la
     *                     mezzanotte non deve risultare incoerente con sé
     *                     stesso
     */
    static MiaTask da(Task task, BigDecimal oreConsumate, BigDecimal mieOre,
                      boolean inCorso, LocalDate oggi) {
        Lavoro lavoro = task.getLavoro();
        Utente responsabile = lavoro.getResponsabile();
        BigDecimal oreAllocate = task.getOreAllocate();

        return new MiaTask(
                task.getId(),
                task.getTitolo(),
                task.getDescrizione(),
                task.getDataInizio(),
                task.getScadenza(),
                task.getStato(),
                oreAllocate,
                oreConsumate,
                mieOre,
                oreConsumate.compareTo(oreAllocate) > 0,
                inCorso,
                aperta(task) && task.getScadenza().isBefore(oggi),
                ChronoUnit.DAYS.between(oggi, task.getScadenza()),
                lavoro.getId(),
                lavoro.getCodice(),
                lavoro.getNome(),
                lavoro.getStato(),
                responsabile == null
                        ? null
                        : responsabile.getCognome() + " " + responsabile.getNome(),
                TaskImportanza.daStringa(task.getUrgenzaTask()));
    }

    /**
     * Una task chiusa o annullata non è «in ritardo»: il ritardo è una cosa
     * che si può ancora recuperare, e su quelle non c'è più niente da fare.
     */
    private static boolean aperta(Task task) {
        return task.getStato().aperta();
    }
}
