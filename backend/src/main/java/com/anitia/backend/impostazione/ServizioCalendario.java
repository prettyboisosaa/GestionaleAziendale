package com.anitia.backend.impostazione;

import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Il calendario dell'azienda: quali giorni si lavora.
 *
 * <p>Un giorno è lavorativo se cade in un giorno della settimana previsto da
 * {@code calendario.giorni_lavorativi} e non è una festività. Serve al conto
 * dei giorni di una richiesta di assenza: chiedere ferie dal venerdì al lunedì
 * sono due giorni di ferie, non quattro.
 *
 * <p>Qui c'era anche {@code giorniLavorativiDopo}, che misurava in giorni
 * lavorativi la finestra entro cui si poteva correggere una dichiarazione già
 * inviata. È uscito con le dichiarazioni: il tempo si corregge entro la
 * giornata in cui è stato registrato, e «oggi» non ha bisogno di aritmetica
 * sul calendario.
 */
@Service
public class ServizioCalendario {

    private final FestivitaRepository festivita;
    private final ServizioImpostazioni impostazioni;

    ServizioCalendario(FestivitaRepository festivita, ServizioImpostazioni impostazioni) {
        this.festivita = festivita;
        this.impostazioni = impostazioni;
    }

    /** Vero se quel giorno si lavora: giorno della settimana previsto e non festivo. */
    @Transactional(readOnly = true)
    public boolean eLavorativo(LocalDate data) {
        return impostazioni.giorniLavorativi().contains(data.getDayOfWeek().getValue())
                && festivita.dateTra(data, data).isEmpty();
    }
}
