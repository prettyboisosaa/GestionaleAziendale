package com.anitia.backend.assenza;

import com.anitia.backend.utente.Utente;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Un'assenza come la disegna un calendario: chi, che tipo, da quando a quando.
 *
 * <p>È una riga <strong>snella</strong>, e non {@link RigaAssenza} riusata,
 * per una ragione di costo misurabile. {@code RigaAssenza} porta
 * {@code giorniLavorativi} e {@code riepilogo}, che
 * {@code ServizioAssenze.giorniLavorativi} calcola iterando giorno per giorno
 * e chiamando {@code ServizioCalendario.eLavorativo}, il quale interroga la
 * tabella {@code festivita} <strong>una volta per giorno</strong>. Un mese di
 * assenze di dieci persone vorrebbe dire qualche centinaio di query per
 * riempire due campi che su un calendario nessuno legge: lì si guarda chi non
 * c'è, non quanti giorni lavorativi sono.
 *
 * <p>{@code dataFine} è <strong>compresa</strong>, come in tutto il resto di
 * questo gestionale: è il frontend ad aggiungere il giorno che FullCalendar
 * pretende per una fine esclusiva, come fa già {@code piuUnGiorno} per il
 * calendario della dashboard.
 */
public record RigaCalendarioAssenza(Long id,
                                    /** «Cognome Nome»: è metà del senso del calendario. */
                                    String richiedente,
                                    TipoAssenza tipo,
                                    String tipoEtichetta,
                                    LocalDate dataInizio,
                                    /** Compresa. */
                                    LocalDate dataFine,
                                    /** Null per le giornate intere. */
                                    LocalTime oraInizio,
                                    LocalTime oraFine,
                                    /**
                                     * Solo {@code IN_ATTESA} o {@code APPROVATA}: le
                                     * altre non sono assenze e non arrivano qui.
                                     * Serve a disegnare le prime tratteggiate e le
                                     * seconde piene.
                                     */
                                    StatoAssenza stato) {

    static RigaCalendarioAssenza da(Assenza assenza) {
        Utente chi = assenza.getUtente();
        return new RigaCalendarioAssenza(
                assenza.getId(),
                chi == null ? null : chi.getCognome() + " " + chi.getNome(),
                assenza.getTipo(),
                assenza.getTipo().etichetta(),
                assenza.getDataInizio(),
                assenza.getDataFine(),
                assenza.getOraInizio(),
                assenza.getOraFine(),
                assenza.getStato());
    }
}
