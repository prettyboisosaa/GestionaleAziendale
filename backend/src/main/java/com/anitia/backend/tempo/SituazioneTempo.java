package com.anitia.backend.tempo;

import java.util.List;

/**
 * Il tempo di chi sta guardando: quello che serve al dipendente, e niente di
 * più.
 *
 * <p>Non c'è una schermata del tempo e non ci deve essere — il senso del
 * cambiamento è che non ci sia più niente da compilare. Questo record
 * alimenta la barra in testata e l'avviso in cima alla dashboard, che sono i
 * due posti dove il dipendente passa comunque.
 */
public record SituazioneTempo(/** Null se non sta lavorando a niente adesso. */
                              SessioneInCorso inCorso,
                              /** Minuti chiusi di oggi, la sessione in corso esclusa. */
                              long minutiOggi,
                              /**
                               * Le chiusure d'ufficio da guardare. Restano qui
                               * finché non le conferma: sono l'unica cosa che
                               * il tracciamento automatico gli chiede.
                               */
                              List<RigaSessione> daConfermare) {
}
