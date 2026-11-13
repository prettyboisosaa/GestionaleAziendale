package com.anitia.backend.autenticazione;

import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Quante volte si può sbagliare la password attuale prima di dover aspettare.
 *
 * <p>Serve perché il cambio password è un <strong>oracolo</strong>: chi si
 * siede davanti a una sessione lasciata aperta può provare password a
 * raffica e scoprire quella vera. Il login ha un limite suo
 * ({@link LimiteTentativiAccesso}) e un errore identico per email sbagliata e
 * password sbagliata; qui l'utente è già noto, e la risposta dice esattamente
 * se la password era quella.
 *
 * <p>Si conta per <strong>id utente</strong>, che è la chiave giusta qui: chi
 * cambia la password è autenticato, quindi l'identità non è una stringa che
 * arriva da fuori ma la sessione in corso.
 *
 * <p>Il conteggio vero sta in {@link FinestraTentativi}, insieme a quello che
 * comporta tenerlo in memoria di processo.
 */
@Component
class LimiteTentativiPassword {

    /** Cinque sbagli: più di quanti ne faccia chi la password la sa. */
    private static final int TENTATIVI_CONCESSI = 5;

    /** Poi si aspetta un quarto d'ora. */
    private static final Duration ATTESA = Duration.ofMinutes(15);

    private final FinestraTentativi<Long> finestra =
            new FinestraTentativi<>(TENTATIVI_CONCESSI, ATTESA);

    /** Vero se questa persona ha esaurito i tentativi e deve ancora aspettare. */
    boolean bloccato(Long utenteId) {
        return finestra.bloccato(utenteId);
    }

    /** Quanto resta da aspettare, in minuti, almeno uno. */
    long minutiDiAttesa(Long utenteId) {
        return finestra.minutiDiAttesa(utenteId);
    }

    /** Ha sbagliato la password attuale. */
    void sbagliato(Long utenteId) {
        finestra.sbagliato(utenteId);
    }

    /** Ce l'ha fatta: il conto riparte da zero. */
    void riuscito(Long utenteId) {
        finestra.riuscito(utenteId);
    }
}
