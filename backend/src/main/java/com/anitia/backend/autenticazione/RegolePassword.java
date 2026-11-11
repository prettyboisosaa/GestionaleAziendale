package com.anitia.backend.autenticazione;

import java.util.regex.Pattern;

/**
 * Che cosa deve avere una password perché sia accettabile.
 *
 * <p>Una classe a sé e non un controllo dentro il servizio, perché la regola è
 * una e va detta in un posto: il frontend ne tiene una copia per disegnare la
 * sua spunta mentre si scrive, e due definizioni che vivono entrambe dentro un
 * modulo divergerebbero alla prima modifica. Questa è quella che decide — il
 * frontend mostra, il server stabilisce.
 *
 * <p>Il «carattere speciale» è inteso come carattere <strong>non
 * alfanumerico</strong>: chiedere un carattere alfanumerico oltre a una cifra
 * non vorrebbe dire niente, perché una cifra è già alfanumerica.
 */
final class RegolePassword {

    /** Otto caratteri: è il minimo, non una raccomandazione. */
    static final int LUNGHEZZA_MINIMA = 8;

    /**
     * Lunghezza, almeno una cifra, almeno un carattere non alfanumerico.
     *
     * <p>I due {@code (?=...)} sono asserzioni che non consumano niente:
     * guardano se da qualche parte nella stringa c'è quel carattere, e il
     * {@code .{8,}} finale conta la lunghezza. Scritta così la regola è una
     * sola espressione invece di tre scorrimenti, ma i messaggi d'errore
     * restano tre — vedi {@link #primoDifetto}.
     */
    private static final Pattern ACCETTABILE =
            Pattern.compile("^(?=.*\\d)(?=.*[^A-Za-z0-9]).{" + LUNGHEZZA_MINIMA + ",}$");

    private RegolePassword() {
    }

    /**
     * Se questa password si può usare. È <strong>questa</strong> la regola: il
     * resto di questa classe serve a spiegare un rifiuto, non a deciderlo.
     */
    static boolean valida(String password) {
        return password != null && ACCETTABILE.matcher(password).matches();
    }

    /**
     * Che cosa manca, in parole, per comporre il messaggio di un rifiuto.
     *
     * <p>Serve perché «password non valida» è un errore su cui non si può
     * agire: chi lo legge non sa che cosa aggiungere. Si dice il primo
     * difetto e non tutti, nell'ordine in cui si notano guardando il campo —
     * la lunghezza prima, che è l'unica che si vede a occhio.
     *
     * <p>Non decide niente, e va chiamata solo dopo che {@link #valida} ha
     * detto no: i tre controlli qui sotto sono le tre parti della stessa
     * espressione, ma se un giorno divergessero l'ultima riga evita di
     * rispondere con una frase a metà.
     */
    static String perche(String password) {
        if (password == null || password.length() < LUNGHEZZA_MINIMA) {
            return "deve avere almeno " + LUNGHEZZA_MINIMA + " caratteri";
        }
        if (!password.matches(".*\\d.*")) {
            return "deve contenere almeno un numero";
        }
        if (!password.matches(".*[^A-Za-z0-9].*")) {
            return "deve contenere almeno un carattere speciale, come ! @ #";
        }
        return "non rispetta le regole richieste";
    }
}
