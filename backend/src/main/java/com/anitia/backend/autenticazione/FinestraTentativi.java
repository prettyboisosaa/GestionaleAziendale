package com.anitia.backend.autenticazione;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quanti sbagli sono concessi, per chiave, prima di dover aspettare.
 *
 * <p>È il meccanismo, non la politica: quante volte e per quanto lo decide chi
 * costruisce l'istanza, e che cosa sia la chiave lo decide l'uso —
 * {@link LimiteTentativiPassword} conta per id utente, perché lì chi cambia la
 * password è già autenticato; {@link LimiteTentativiAccesso} conta per email,
 * perché al login un id ancora non c'è.
 *
 * <p>Era codice dentro {@code LimiteTentativiPassword}. È uscito di lì quando
 * è servito lo stesso conteggio sul login: copiarlo avrebbe significato due
 * copie della stessa logica di scadenza, che è il genere di cosa che diverge
 * alla prima correzione.
 *
 * <p><strong>Conta solo gli sbagli.</strong> Un tentativo riuscito azzera il
 * contatore: chi conosce la propria password non deve incontrare questo limite
 * nemmeno sbagliando a digitare qualche volta prima.
 *
 * <p>La memoria è <strong>in processo</strong>, e va detto che cosa comporta:
 * con due istanze del backend dietro un bilanciatore il limite vale per
 * istanza, e un riavvio lo azzera. È la scelta proporzionata a questo
 * gestionale, che gira su una macchina sola e non ha né Redis né una tabella
 * dei tentativi.
 *
 * <p>La mappa non si svuota da sé con un processo a tempo — in questo progetto
 * non ce n'è nessuno — ma una voce scaduta viene dimenticata appena la si
 * guarda. Per una chiave che arriva da fuori, come l'email del login, questo
 * non basta a limitarne la crescita: ci pensa {@link #TETTO_CHIAVI}.
 */
class FinestraTentativi<K> {

    /**
     * Oltre questo numero di chiavi in memoria, la mappa si svuota.
     *
     * <p>Serve solo dove la chiave la sceglie chi chiama da fuori: al login si
     * conta per email, e nessuno impedisce di inventarne un milione diverse
     * per far crescere la mappa finché il processo non finisce la memoria.
     * Svuotare è la risposta grossolana ma giusta — perdere i contatori
     * significa concedere qualche tentativo in più a chi in quel momento stava
     * già tirando a indovinare, mentre tenerli significa cadere.
     *
     * <p>Il numero è largo rispetto a qualunque uso legittimo: in un
     * gestionale da qualche decina di persone non ci si arriva per sbaglio.
     */
    private static final int TETTO_CHIAVI = 10_000;

    private final int tentativiConcessi;
    private final Duration attesa;
    private final Map<K, Tentativi> perChiave = new ConcurrentHashMap<>();

    FinestraTentativi(int tentativiConcessi, Duration attesa) {
        this.tentativiConcessi = tentativiConcessi;
        this.attesa = attesa;
    }

    /** Vero se questa chiave ha esaurito i tentativi e deve ancora aspettare. */
    boolean bloccato(K chiave) {
        Tentativi tentativi = perChiave.get(chiave);
        if (tentativi == null) {
            return false;
        }
        if (tentativi.scaduti(attesa)) {
            // Scaduta: si dimentica adesso, invece di tenerla fino a un
            // ipotetico giro di pulizia che in questo progetto non esiste.
            perChiave.remove(chiave);
            return false;
        }
        return tentativi.quanti() >= tentativiConcessi;
    }

    /** Quanto resta da aspettare, in minuti, almeno uno. */
    long minutiDiAttesa(K chiave) {
        Tentativi tentativi = perChiave.get(chiave);
        if (tentativi == null) {
            return 0;
        }
        long minuti = attesa.minus(tentativi.daQuanto()).toMinutes();
        return Math.max(1, minuti);
    }

    /** Ha sbagliato. */
    void sbagliato(K chiave) {
        if (perChiave.size() >= TETTO_CHIAVI && !perChiave.containsKey(chiave)) {
            perChiave.clear();
        }
        perChiave.compute(chiave, (ignorata, tentativi) ->
                tentativi == null || tentativi.scaduti(attesa)
                        ? Tentativi.primo()
                        : tentativi.unAltro());
    }

    /** Ce l'ha fatta: il conto riparte da zero. */
    void riuscito(K chiave) {
        perChiave.remove(chiave);
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

        boolean scaduti(Duration attesa) {
            return daQuanto().compareTo(attesa) >= 0;
        }
    }
}
