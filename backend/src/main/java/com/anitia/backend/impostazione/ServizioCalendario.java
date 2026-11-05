package com.anitia.backend.impostazione;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Il calendario dell'azienda: quali giorni si lavora.
 *
 * <p>Esiste per una ragione sola, ma precisa: la finestra entro cui un
 * dipendente può correggere una dichiarazione già inviata è di
 * {@code dichiarazione.giorni_modifica} giorni <strong>lavorativi</strong>.
 * Contare i giorni di calendario darebbe la risposta sbagliata ogni venerdì —
 * una dichiarazione del venerdì scadrebbe il sabato, quando l'ufficio è
 * chiuso e nessuno può correggerla.
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

    /**
     * Quanti giorni lavorativi ci sono <strong>dopo</strong> {@code da} e fino
     * a {@code a} compreso.
     *
     * <p>{@code da} escluso di proposito: serve a misurare quanto tempo è
     * passato da una dichiarazione, e il giorno stesso della dichiarazione non
     * è «tempo passato». Con {@code giorni_modifica = 1}, il giorno della
     * dichiarazione vale 0 e il primo giorno lavorativo successivo vale 1,
     * quindi entrambi restano dentro la finestra.
     *
     * <p>Zero se {@code a} non è successivo a {@code da}.
     */
    @Transactional(readOnly = true)
    public int giorniLavorativiDopo(LocalDate da, LocalDate a) {
        if (!a.isAfter(da)) {
            return 0;
        }

        List<Integer> settimana = impostazioni.giorniLavorativi();
        // Una query sola per tutto l'intervallo, non una per giorno.
        Set<LocalDate> chiusure = Set.copyOf(festivita.dateTra(da.plusDays(1), a));

        int quanti = 0;
        for (LocalDate giorno = da.plusDays(1); !giorno.isAfter(a);
                giorno = giorno.plusDays(1)) {
            if (settimana.contains(giorno.getDayOfWeek().getValue())
                    && !chiusure.contains(giorno)) {
                quanti++;
            }
        }
        return quanti;
    }
}
