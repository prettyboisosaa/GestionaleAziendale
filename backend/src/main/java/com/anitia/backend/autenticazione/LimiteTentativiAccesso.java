package com.anitia.backend.autenticazione;

import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Quante volte si può sbagliare il login prima di dover aspettare.
 *
 * <p>Prima non c'era niente: {@code POST /api/autenticazione/login} accettava
 * tentativi a raffica e l'unico freno era la lentezza di bcrypt. Su una rete
 * aziendale non è lo scenario del furto di credenziali in massa, ma è
 * esattamente quello del collega che prova le password plausibili di un altro
 * da una postazione qualunque — e senza alcun limite nessuno se ne accorge,
 * perché il gestionale non ha un registro degli accessi.
 *
 * <p>Si conta per <strong>email</strong>, minuscola: al login un id utente non
 * c'è ancora, e per le email che non esistono nemmeno dopo. Che il conteggio
 * valga anche per un indirizzo inesistente è voluto: se contassimo solo gli
 * utenti veri, essere bloccati o no direbbe quali indirizzi sono registrati —
 * la stessa enumerazione che il messaggio d'errore unico evita con cura.
 *
 * <p><strong>Il rovescio della medaglia, detto chiaramente:</strong> chi
 * conosce l'email di un collega può tenerlo fuori dal gestionale sbagliando la
 * password dieci volte. Con le alternative — contare per indirizzo IP, che
 * dietro un NAT aziendale è lo stesso per tutti, o non contare affatto — è il
 * compromesso giusto per una rete interna di poche decine di persone, dove chi
 * resta fuori ha un amministratore a tre scrivanie di distanza. Su una
 * installazione esposta a Internet la scelta andrebbe rifatta.
 *
 * <p>Dieci tentativi e non cinque come sul cambio password: qui a sbagliare è
 * anche chi ha semplicemente dimenticato quale delle sue password ha usato, e
 * non ha modo di recuperarla da sé.
 */
@Component
class LimiteTentativiAccesso {

    private static final int TENTATIVI_CONCESSI = 10;

    private static final Duration ATTESA = Duration.ofMinutes(15);

    private final FinestraTentativi<String> finestra =
            new FinestraTentativi<>(TENTATIVI_CONCESSI, ATTESA);

    boolean bloccato(String email) {
        return finestra.bloccato(chiave(email));
    }

    long minutiDiAttesa(String email) {
        return finestra.minutiDiAttesa(chiave(email));
    }

    void sbagliato(String email) {
        finestra.sbagliato(chiave(email));
    }

    void riuscito(String email) {
        finestra.riuscito(chiave(email));
    }

    /**
     * L'email normalizzata.
     *
     * <p>Senza questo, {@code Mario.Rossi@…} e {@code mario.rossi@…} sarebbero
     * due contatori distinti e il limite si aggirerebbe cambiando una
     * maiuscola. {@link Locale#ROOT} e non il locale di sistema: in turco
     * {@code toLowerCase} manda la I in ı, e due indirizzi uguali
     * diventerebbero chiavi diverse a seconda di dove gira il server.
     */
    private static String chiave(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
