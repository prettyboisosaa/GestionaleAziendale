package com.anitia.backend.autenticazione;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Quante volte si può sbagliare la password attuale prima di dover aspettare.
 *
 * <p>Serve perché il cambio password è un <strong>oracolo</strong>: chi si
 * siede davanti a una sessione lasciata aperta può provare password a
 * raffica e scoprire quella vera, senza che nessun tentativo di login
 * compaia da nessuna parte. Il login ha la lentezza di bcrypt a difenderlo e
 * un errore identico per email sbagliata e password sbagliata; qui l'utente è
 * già noto, e la risposta dice esattamente se la password era quella.
 *
 * <p><strong>Conta solo gli sbagli.</strong> Un cambio riuscito azzera il
 * contatore: chi conosce la propria password non deve incontrare questo
 * limite nemmeno cambiandola tre volte di seguito.
 *
 * <p>La memoria è <strong>in processo</strong>, e va detto che cosa comporta:
 * con due istanze del backend dietro un bilanciatore il limite vale per
 * istanza, e un riavvio lo azzera. È la scelta proporzionata a questo
 * gestionale, che gira su una macchina sola e non ha né Redis né una tabella
 * dei tentativi; il giorno in cui le istanze diventassero due, questa classe
 * è il solo posto da cambiare.
 *
 * <p>La mappa non si svuota da sé con un processo a tempo — in questo progetto
 * non ce n'è nessuno — ma una voce scaduta viene dimenticata appena la si
 * guarda, e le chiavi sono gli id degli utenti: non cresce oltre il numero di
 * persone che hanno sbagliato una password.
 */
@Component
class LimiteTentativiPassword {

    /** Cinque sbagli: più di quanti ne faccia chi la password la sa. */
    private static final int TENTATIVI_CONCESSI = 5;

    /** Poi si aspetta un quarto d'ora. */
    private static final Duration ATTESA = Duration.ofMinutes(15);

    private final Map<Long, Tentativi> perUtente = new ConcurrentHashMap<>();

    /** Vero se questa persona ha esaurito i tentativi e deve ancora aspettare. */
    boolean bloccato(Long utenteId) {
        Tentativi tentativi = perUtente.get(utenteId);
        if (tentativi == null) {
            return false;
        }
        if (tentativi.scaduti()) {
            // Scaduta: si dimentica adesso, invece di tenerla fino a un
            // ipotetico giro di pulizia che in questo progetto non esiste.
            perUtente.remove(utenteId);
            return false;
        }
        return tentativi.quanti() >= TENTATIVI_CONCESSI;
    }

    /** Quanto resta da aspettare, in minuti, almeno uno. */
    long minutiDiAttesa(Long utenteId) {
        Tentativi tentativi = perUtente.get(utenteId);
        if (tentativi == null) {
            return 0;
        }
        long minuti = ATTESA.minus(tentativi.daQuanto()).toMinutes();
        return Math.max(1, minuti);
    }

    /** Ha sbagliato la password attuale. */
    void sbagliato(Long utenteId) {
        perUtente.compute(utenteId, (id, tentativi) ->
                tentativi == null || tentativi.scaduti()
                        ? Tentativi.primo()
                        : tentativi.unAltro());
    }

    /** Ce l'ha fatta: il conto riparte da zero. */
    void riuscito(Long utenteId) {
        perUtente.remove(utenteId);
    }

    /**
     * Quanti sbagli e da quando.
     *
     * <p>L'istante è quello del <strong>primo</strong> sbaglio della serie e
     * non dell'ultimo: con l'ultimo, chi continua a provare rimanderebbe la
     * scadenza a ogni tentativo e resterebbe bloccato per sempre.
     */
    private record Tentativi(int quanti, Instant dal) {

        static Tentativi primo() {
            return new Tentativi(1, Instant.now());
        }

        Tentativi unAltro() {
            return new Tentativi(quanti + 1, dal);
        }

        Duration daQuanto() {
            return Duration.between(dal, Instant.now());
        }

        boolean scaduti() {
            return daQuanto().compareTo(ATTESA) >= 0;
        }
    }
}
