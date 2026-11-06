package com.anitia.backend.assenza;

import com.anitia.backend.utente.Utente;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Una richiesta di assenza come la vede il frontend.
 *
 * <p>Porta con sé le etichette degli enum e il riepilogo calcolato invece di
 * lasciarli al frontend: «3 giorni lavorativi» dipende dalle festività e dai
 * giorni lavorativi configurati nel gestionale, che il browser non conosce.
 * Ricostruirlo lì vorrebbe dire una seconda definizione di giorno lavorativo,
 * da tenere allineata a {@code ServizioCalendario}.
 */
public record RigaAssenza(Long id,
                          TipoAssenza tipo,
                          /** Come si chiama la tipologia, già pronta da stampare. */
                          String tipoEtichetta,
                          LocalDate dataInizio,
                          LocalDate dataFine,
                          /** Null per le giornate intere. */
                          LocalTime oraInizio,
                          LocalTime oraFine,
                          /** Null tranne che per il permesso retribuito. */
                          MotivoPermesso motivo,
                          String motivoEtichetta,
                          String note,
                          StatoAssenza stato,
                          String statoEtichetta,
                          /**
                           * «Cognome Nome» di chi l'ha chiesta. Riempito solo
                           * nell'elenco dell'admin: al dipendente, che vede
                           * soltanto le proprie, non dice niente.
                           */
                          String richiedente,
                          /** «Cognome Nome» dell'admin che ha deciso, se ha deciso. */
                          String gestitaDa,
                          LocalDateTime gestitaIl,
                          String notaEsito,
                          LocalDateTime creataIl,
                          /**
                           * Quanti giorni lavorativi copre, festività escluse.
                           * Null quando l'assenza è una fascia oraria, perché
                           * in quel caso la domanda non ha risposta in giorni.
                           */
                          Integer giorniLavorativi,
                          /**
                           * Quanti minuti dura la fascia oraria. Null per le
                           * giornate intere.
                           */
                          Integer minuti,
                          /** Il riepilogo già scritto: «3 giorni lavorativi», «2 h 30 min». */
                          String riepilogo) {

    /**
     * @param richiedente se includere il nome di chi l'ha chiesta: vero per
     *                    l'elenco dell'admin, falso per quello del dipendente
     * @param giorni      i giorni lavorativi contati da {@code ServizioCalendario},
     *                    o null se l'assenza è una fascia oraria
     */
    static RigaAssenza da(Assenza assenza, boolean richiedente, Integer giorni) {
        DettagliAssenza dettagli = assenza.getDettagli();
        MotivoPermesso motivo = dettagli == null ? null : dettagli.motivo();
        Integer minuti = minuti(assenza);

        return new RigaAssenza(
                assenza.getId(),
                assenza.getTipo(),
                assenza.getTipo().etichetta(),
                assenza.getDataInizio(),
                assenza.getDataFine(),
                assenza.getOraInizio(),
                assenza.getOraFine(),
                motivo,
                motivo == null ? null : motivo.etichetta(),
                dettagli == null ? null : dettagli.note(),
                assenza.getStato(),
                assenza.getStato().etichetta(),
                richiedente ? nome(assenza.getUtente()) : null,
                nome(assenza.getGestitaDa()),
                assenza.getGestitaIl(),
                assenza.getNotaEsito(),
                assenza.getCreataIl(),
                giorni,
                minuti,
                riepilogo(giorni, minuti));
    }

    /** I minuti della fascia oraria, null se l'assenza è a giornate intere. */
    private static Integer minuti(Assenza assenza) {
        if (assenza.getOraInizio() == null || assenza.getOraFine() == null) {
            return null;
        }
        return (int) java.time.Duration
                .between(assenza.getOraInizio(), assenza.getOraFine())
                .toMinutes();
    }

    /**
     * Il riepilogo in parole. Una fascia oraria si misura in ore e minuti,
     * una assenza a giornate in giorni lavorativi: sono due unità diverse, e
     * dirne una sola per tutte e due darebbe «0 giorni» a un permesso di due
     * ore.
     */
    private static String riepilogo(Integer giorni, Integer minuti) {
        if (minuti != null) {
            int ore = minuti / 60;
            int resto = minuti % 60;
            if (ore == 0) {
                return resto + " min";
            }
            return resto == 0 ? ore + " h" : ore + " h " + resto + " min";
        }
        if (giorni == null) {
            return null;
        }
        return giorni == 1 ? "1 giorno lavorativo" : giorni + " giorni lavorativi";
    }

    private static String nome(Utente utente) {
        return utente == null ? null : utente.getCognome() + " " + utente.getNome();
    }
}
