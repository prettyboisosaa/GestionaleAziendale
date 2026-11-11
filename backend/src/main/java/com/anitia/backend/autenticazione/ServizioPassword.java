package com.anitia.backend.autenticazione;

import com.anitia.backend.utente.Utente;
import com.anitia.backend.utente.UtenteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Il cambio della propria password.
 *
 * <p><strong>Solo la propria.</strong> Chi sta cambiando lo dice la sessione e
 * non la richiesta: non esiste un parametro per indicare un altro utente,
 * quindi non esiste il caso da difendere. Un amministratore che debba
 * rimettere in piedi l'accesso di qualcuno non passa da qui — quella è un'altra
 * operazione, con un altro permesso, e non c'è.
 *
 * <p><strong>Nessun permesso.</strong> Cambiarsi la password non è una cosa
 * che si concede: è parte dell'avere un accesso. Un permesso che si darebbe a
 * tutti è un permesso che non esiste, come dice già
 * {@code TempoController} per le rotte di chi lavora.
 */
@Service
public class ServizioPassword {

    private static final Logger log = LoggerFactory.getLogger(ServizioPassword.class);

    private final UtenteRepository utenti;
    private final PasswordEncoder codificatorePassword;
    private final LimiteTentativiPassword limite;

    ServizioPassword(UtenteRepository utenti, PasswordEncoder codificatorePassword,
                     LimiteTentativiPassword limite) {
        this.utenti = utenti;
        this.codificatorePassword = codificatorePassword;
        this.limite = limite;
    }

    /**
     * Cambia la password di chi lo chiede.
     *
     * <p>I controlli sono quattro, e <strong>l'ordine è il messaggio</strong>:
     * prima si verifica che chi scrive sia davvero il padrone dell'utenza,
     * poi che la password nuova stia in piedi, poi che sia un'altra. Dire «è
     * troppo corta» a chi ha sbagliato la password attuale sarebbe raccontargli
     * di un campo che non è il suo problema — e, peggio, gli confermerebbe che
     * la vecchia era giusta.
     *
     * <p><strong>Nessun 401, in nessun caso.</strong> La password attuale
     * sbagliata risponde 400 con {@code WRONG_OLD_PASSWORD}: un 401 verrebbe
     * letto dal frontend come sessione scaduta, e chi ha solo battuto male un
     * tasto si troverebbe alla schermata di accesso con il modulo perduto.
     */
    @Transactional
    public void cambia(UserDetails chiLoChiede, RichiestaCambioPassword richiesta) {
        Utente io = utenti.findByEmail(chiLoChiede.getUsername()).orElseThrow(
                () -> new IllegalStateException(
                        "Utente collegato non trovato: " + chiLoChiede.getUsername()));

        if (limite.bloccato(io.getId())) {
            // 429 e non 400: non c'è niente da correggere nel modulo, c'è da
            // aspettare. È l'unico caso in cui la risposta non parla dei campi.
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "TROPPI_TENTATIVI: troppi tentativi con la password attuale "
                            + "sbagliata. Riprova fra "
                            + limite.minutiDiAttesa(io.getId()) + " minuti.");
        }

        if (!codificatorePassword.matches(richiesta.oldPassword(), io.getPasswordHash())) {
            limite.sbagliato(io.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "WRONG_OLD_PASSWORD: la password attuale non è corretta.");
        }

        // Da qui in avanti chi scrive ha dimostrato di essere il padrone
        // dell'utenza: gli sbagli che restano sono sul modulo, non sull'identità,
        // e non contano come tentativi.
        limite.riuscito(io.getId());

        if (!RegolePassword.valida(richiesta.newPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "WEAK_PASSWORD: la nuova password "
                            + RegolePassword.perche(richiesta.newPassword()) + ".");
        }

        // Si confrontano le due in chiaro, non i due hash: bcrypt sala ogni
        // volta in modo diverso, quindi la stessa password dà hash diversi e
        // un confronto fra stringhe cifrate direbbe sempre «sono diverse».
        if (richiesta.newPassword().equals(richiesta.oldPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "SAME_PASSWORD: la nuova password è identica a quella attuale.");
        }

        io.cambiaPassword(codificatorePassword.encode(richiesta.newPassword()));

        // L'evento resta scritto; le password no, in nessuna forma e nemmeno
        // la loro lunghezza. Dell'utente si registra l'id e non l'email: nel
        // log di un gestionale un indirizzo è un dato personale che non serve
        // a ricostruire l'accaduto.
        log.info("Password cambiata dall'utente {}", io.getId());
    }
}
