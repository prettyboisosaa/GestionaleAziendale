package com.anitia.backend.report;

import com.anitia.backend.tempo.TipoAttivita;

/**
 * Tempo registrato senza una task: una riunione, una formazione, una
 * manutenzione.
 *
 * <p>Compare in due posti del report, e il posto cambia il significato:
 * <ul>
 *   <li>dentro un lavoro, quando la sessione cita il lavoro ma nessuna task
 *       (è il caso di {@code MANUTENZIONE_CLIENTE});
 *   <li>in fondo, nella sezione senza lavoro, quando non cita nemmeno quello.
 * </ul>
 *
 * <p>L'{@code etichetta} arriva dal backend invece di essere ricostruita in
 * pagina: {@link TipoAttivita#etichetta()} è già la traduzione ufficiale di
 * questi valori, e una seconda mappa nel frontend sarebbe una copia che
 * divergerà al primo valore nuovo.
 */
public record VoceAttivita(TipoAttivita tipo,
                           String etichetta,
                           long minuti) {

    static VoceAttivita da(TipoAttivita tipo, long minuti) {
        return new VoceAttivita(tipo, tipo.etichetta(), minuti);
    }
}
