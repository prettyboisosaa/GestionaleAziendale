package com.anitia.backend.tempo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Una sessione come andava registrata: gli estremi corretti, e che attività
 * era davvero.
 *
 * <p>La durata non si manda, la ricava il database dalla differenza. Mandare
 * anche i minuti vorrebbe dire poter mandare tre numeri in disaccordo fra
 * loro, e poi scegliere a quale credere.
 *
 * <p><strong>La task non c'è, di proposito.</strong> Una sessione è il tempo
 * passato su <em>quella</em> task, e spostarlo su un'altra non è correggere un
 * orario: è dire che si è lavorato a qualcos'altro. La strada per quello è
 * annullare e riscrivere, che dal calendario sono due clic. Il tipo di
 * attività invece si corregge — chi registra può sbagliare tendina, e quel
 * dato non è una misura.
 *
 * <p><strong>I tre campi nuovi sostituiscono, non aggiornano.</strong> Il PUT
 * riscrive tipo, lavoro e descrizione con quello che arriva, quindi il modulo
 * manda sempre i valori che ha in mano e una descrizione svuotata la
 * cancella. Un null che volesse dire «lascia com'era» renderebbe impossibile
 * togliere una descrizione scritta per sbaglio.
 */
public record RichiestaCorrezione(@NotNull(message = "Serve il tipo di attività")
                                  TipoAttivita tipoAttivita,
                                  /** Il lavoro citato, per un'attività senza task. */
                                  Long lavoroId,
                                  @Size(max = 500,
                                        message = "La descrizione non può superare i 500 caratteri")
                                  String descrizione,
                                  @NotNull(message = "Serve l'ora di inizio")
                                  LocalDateTime avviataIl,
                                  @NotNull(message = "Serve l'ora di fine")
                                  LocalDateTime chiusaIl) {
}
