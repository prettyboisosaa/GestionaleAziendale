package com.anitia.backend.tempo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Un'attività registrata a mano sul calendario: che genere di cosa era, su che
 * cosa, quando, e cosa si è fatto.
 *
 * <p>La durata non si manda, la ricava il database dalla differenza fra i due
 * istanti. Mandare anche i minuti vorrebbe dire poter mandare tre numeri in
 * disaccordo fra loro, e poi scegliere a quale credere.
 *
 * <p><strong>Qui la task sta nel corpo</strong>, al contrario di
 * {@link RichiestaSessione}, che la lascia all'indirizzo. La ragione è che
 * adesso può non esserci: una riunione o una manutenzione interna non stanno
 * sotto nessuna task, e un indirizzo non sa nominare una cosa facoltativa.
 *
 * <p>Sono due record e non uno con un campo in più perché dicono due cose
 * diverse — «questo tempo è di quella task» e «questo tempo è stato
 * quest'attività» — e perché la rotta per task resta quella che era: un
 * {@code tipoAttivita} obbligatorio nel suo corpo romperebbe chi la chiama
 * oggi per dirgli l'unica cosa che quella rotta già sapeva.
 *
 * <p>{@code taskId} e {@code lavoroId} sono facoltativi <em>qui</em>, ma non
 * lo sono sempre: che cosa serva lo decide {@code tipoAttivita}, e lo verifica
 * {@code ServizioTempo} leggendo {@link TipoAttivita#richiedeTask()} e
 * {@link TipoAttivita#richiedeLavoro()}. Una validazione dichiarativa non può
 * dipendere dal valore di un altro campo, e un record per ciascun tipo
 * sarebbero dieci record per un solo modulo.
 */
public record RichiestaAttivita(@NotNull(message = "Serve il tipo di attività")
                                TipoAttivita tipoAttivita,
                                /** Richiesto se il tipo di attività lo pretende. */
                                Long taskId,
                                /** Richiesto per la manutenzione cliente, ammesso altrove. */
                                Long lavoroId,
                                @Size(max = 500,
                                      message = "La descrizione non può superare i 500 caratteri")
                                String descrizione,
                                @NotNull(message = "Serve l'ora di inizio")
                                LocalDateTime avviataIl,
                                @NotNull(message = "Serve l'ora di fine")
                                LocalDateTime chiusaIl) {

    /**
     * L'attività che arriva dalla rotta per task, che nomina la task
     * nell'indirizzo e non sa niente di tipi.
     *
     * <p>Serve a non avere due strade dentro il servizio: quella rotta compone
     * una richiesta come questa e poi tutto prosegue uguale. Il tipo è
     * {@code LAVORO_TASK} perché è l'unica cosa che sapeva registrare, e la
     * descrizione è null perché non la riceveva.
     */
    static RichiestaAttivita suTask(Long taskId, LocalDateTime da, LocalDateTime a) {
        return new RichiestaAttivita(TipoAttivita.LAVORO_TASK, taskId, null, null, da, a);
    }
}
