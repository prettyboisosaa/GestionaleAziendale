package com.anitia.backend.impostazione;

import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La lettura dei parametri di sistema dalla tabella {@code impostazione}.
 *
 * <p>Ogni lettura ha un <strong>fallback</strong>, e non è pigrizia: se
 * qualcuno cancella la riga {@code dichiarazione.ore_massime_giorno} o ci
 * scrive «otto», l'alternativa a un valore di ripiego sarebbe un'eccezione in
 * faccia a un dipendente che sta solo registrando la giornata. Il valore
 * sbagliato finisce nel log, dove un amministratore lo trova, e il gestionale
 * continua a funzionare con il default sensato.
 *
 * <p>Nessuna cache: sono letture di una riga su chiave primaria, e tenerle in
 * memoria vorrebbe dire inventare un modo di invalidarla quando un
 * amministratore cambia il parametro dall'interfaccia.
 */
@Service
public class ServizioImpostazioni {

    /** Le chiavi usate dal codice, scritte in un posto solo. */
    public static final String ORE_MASSIME_GIORNO = "dichiarazione.ore_massime_giorno";
    public static final String GIORNI_MODIFICA = "dichiarazione.giorni_modifica";
    public static final String GIORNI_LAVORATIVI = "calendario.giorni_lavorativi";

    private static final Logger log = LoggerFactory.getLogger(ServizioImpostazioni.class);

    private final ImpostazioneRepository impostazioni;

    ServizioImpostazioni(ImpostazioneRepository impostazioni) {
        this.impostazioni = impostazioni;
    }

    /** Il valore testuale, o il fallback se la riga manca. */
    @Transactional(readOnly = true)
    public String testo(String chiave, String fallback) {
        return impostazioni.findById(chiave)
                .map(Impostazione::getValore)
                .orElseGet(() -> {
                    log.warn("Parametro {} assente: uso il valore di ripiego {}", chiave,
                            fallback);
                    return fallback;
                });
    }

    /** Il valore come numero decimale, o il fallback se manca o non è un numero. */
    @Transactional(readOnly = true)
    public BigDecimal decimale(String chiave, BigDecimal fallback) {
        String valore = testo(chiave, fallback.toPlainString());
        try {
            return new BigDecimal(valore.trim());
        } catch (NumberFormatException eccezione) {
            log.warn("Parametro {} non è un numero ({}): uso il valore di ripiego {}", chiave,
                    valore, fallback);
            return fallback;
        }
    }

    /** Il valore come numero intero, o il fallback se manca o non è un numero. */
    @Transactional(readOnly = true)
    public int intero(String chiave, int fallback) {
        String valore = testo(chiave, String.valueOf(fallback));
        try {
            return Integer.parseInt(valore.trim());
        } catch (NumberFormatException eccezione) {
            log.warn("Parametro {} non è un intero ({}): uso il valore di ripiego {}", chiave,
                    valore, fallback);
            return fallback;
        }
    }

    /**
     * I giorni della settimana lavorativi, nella numerazione ISO: 1 = lunedì,
     * 7 = domenica. Il parametro li tiene come elenco separato da virgole.
     *
     * <p>Un valore illeggibile o vuoto fa ripiegare su lunedì–venerdì: è la
     * settimana dell'azienda, e un elenco vuoto renderebbe <em>ogni</em>
     * giorno non lavorativo, bloccando per sempre la finestra di modifica
     * delle dichiarazioni.
     */
    @Transactional(readOnly = true)
    public List<Integer> giorniLavorativi() {
        String valore = testo(GIORNI_LAVORATIVI, "1,2,3,4,5");
        try {
            List<Integer> giorni = List.of(valore.split(",")).stream()
                    .map(String::trim)
                    .filter(pezzo -> !pezzo.isEmpty())
                    .map(Integer::parseInt)
                    .filter(giorno -> giorno >= 1 && giorno <= 7)
                    .distinct()
                    .toList();
            if (giorni.isEmpty()) {
                log.warn("Parametro {} non elenca nessun giorno valido ({}): uso lunedì-venerdì",
                        GIORNI_LAVORATIVI, valore);
                return List.of(1, 2, 3, 4, 5);
            }
            return giorni;
        } catch (NumberFormatException eccezione) {
            log.warn("Parametro {} illeggibile ({}): uso lunedì-venerdì", GIORNI_LAVORATIVI,
                    valore);
            return List.of(1, 2, 3, 4, 5);
        }
    }
}
