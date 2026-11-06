package com.anitia.backend.dashboard;

import java.time.LocalDate;

/**
 * Un evento del calendario, qualunque sia la sua provenienza.
 *
 * <p>Task, assenze e festività escono tutte con questa forma. Il frontend non
 * conosce le tabelle sottostanti, quindi una fonte nuova si aggiunge senza
 * toccare il calendario: è il motivo per cui il formato è uno solo invece di
 * tre liste separate.
 *
 * <p><strong>{@link #fine} è inclusiva</strong>: è l'ultimo giorno coperto.
 * FullCalendar, per gli eventi a giornata intera, vuole invece una fine
 * <em>esclusiva</em>, e il giorno in più lo aggiunge il frontend quando
 * traduce. La conversione sta da quella parte di proposito: è una convenzione
 * della libreria di disegno, non un fatto del dominio, e un'API che restituisce
 * «finisce il 7» per un'assenza che finisce il 6 sarebbe una trappola per
 * chiunque la legga.
 */
public record EventoCalendario(
        /**
         * Identificatore stabile, con il prefisso della fonte:
         * {@code task-142}, {@code assenza-7}, {@code festivita-2026-12-25}.
         * Gli id delle tre tabelle si sovrapporrebbero, e FullCalendar
         * pretende chiavi uniche.
         */
        String id,

        TipoEvento tipo,

        String titolo,

        LocalDate inizio,

        /** L'ultimo giorno coperto, compreso. Uguale a {@link #inizio} per gli eventi di un giorno. */
        LocalDate fine,

        /**
         * Lo stato della task o dell'assenza, già come stringa: serve al
         * frontend per scegliere il colore e per nascondere le task
         * completate. Nullo per le festività, che non hanno stati.
         */
        String stato,

        /**
         * Vero quando la task scade in un giorno in cui chi guarda è in ferie
         * o in permesso approvato. È l'avviso che il documento chiede: il
         * collegamento fra i due moduli, che da soli non si parlerebbero.
         */
        boolean conflitto,

        /**
         * Dove porta un clic sull'evento. Per una task è la pagina del suo
         * lavoro; per assenze e festività è nullo, perché non c'è niente da
         * aprire.
         */
        String url) {

    static EventoCalendario task(Long taskId, String titolo, LocalDate inizio, LocalDate fine,
                                 String stato, boolean conflitto, Long lavoroId) {
        return new EventoCalendario("task-" + taskId, TipoEvento.TASK, titolo, inizio, fine,
                stato, conflitto, "/lavori/" + lavoroId);
    }

    static EventoCalendario assenza(Long assenzaId, String titolo, LocalDate inizio,
                                    LocalDate fine, String stato) {
        return new EventoCalendario("assenza-" + assenzaId, TipoEvento.ASSENZA, titolo, inizio,
                fine, stato, false, null);
    }

    static EventoCalendario festivita(LocalDate giorno, String descrizione) {
        return new EventoCalendario("festivita-" + giorno, TipoEvento.FESTIVITA, descrizione,
                giorno, giorno, null, false, null);
    }
}
