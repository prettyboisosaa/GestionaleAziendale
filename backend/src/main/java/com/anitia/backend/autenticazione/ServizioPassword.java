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
 * Le password: la propria, che si cambia, e quella di un altro, che si azzera.
 *
 * <p>Sono due operazioni diverse e qui stanno vicine di proposito, perché la
 * differenza è tutta in <strong>che cosa dimostra chi scrive</strong>:
 *
 * <ul>
 *   <li>{@link #cambia} — <strong>solo la propria</strong>. Chi sta cambiando
 *       lo dice la sessione e non la richiesta: non esiste un parametro per
 *       indicare un altro utente, quindi non esiste il caso da difendere. Si
 *       dimostra di essere il padrone dell'utenza esibendo la password
 *       attuale. Nessun permesso: cambiarsi la password non è una cosa che si
 *       concede, è parte dell'avere un accesso, e un permesso che si darebbe a
 *       tutti è un permesso che non esiste.
 *   <li>{@link #azzera} — <strong>quella di un altro</strong>, senza
 *       conoscerla. Qui non si dimostra niente sull'utenza toccata, e la
 *       garanzia è tutta nel permesso {@code UTENTI_RESET_PASSWORD}, che la
 *       {@code V22} tiene separato da {@code UTENTI_CREA} proprio perché
 *       entrare nell'identità di una persona che lavora non è la stessa cosa
 *       che aprire un'utenza nuova.
 * </ul>
 */
@Service
public class ServizioPassword {

    private static final Logger log = LoggerFactory.getLogger(ServizioPassword.class);

    private final UtenteRepository utenti;
    private final PasswordEncoder codificatorePassword;
    private final LimiteTentativiPassword limite;
    private final LimiteTentativiAccesso limiteAccesso;

    ServizioPassword(UtenteRepository utenti, PasswordEncoder codificatorePassword,
                     LimiteTentativiPassword limite, LimiteTentativiAccesso limiteAccesso) {
        this.utenti = utenti;
        this.codificatorePassword = codificatorePassword;
        this.limite = limite;
        this.limiteAccesso = limiteAccesso;
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

    /**
     * Azzera la password di un altro utente, senza conoscere quella attuale.
     *
     * <p>Esiste perché prima non c'era nessuna strada. Il gestionale non manda
     * email, non ha domande di sicurezza e non ha un «password dimenticata»:
     * chi dimenticava la propria restava fuori, e l'unico rimedio era una
     * {@code UPDATE} a mano sul database di produzione. Con diciassette
     * persone, la prima password dimenticata arriva nel giro di settimane.
     *
     * <p><strong>Non si può azzerare la propria.</strong> È il controllo meno
     * ovvio di questo metodo e il più importante: chi ha questo permesso ha
     * anche una sessione aperta, e permettergli di riscriversi la password
     * senza esibire quella attuale trasformerebbe una sessione lasciata
     * incustodita in un cambio di credenziali definitivo — il legittimo
     * padrone non entrerebbe più. Per la propria c'è {@link #cambia}, che la
     * password attuale la chiede.
     *
     * <p><strong>I contatori dei tentativi si azzerano.</strong> Chi ha
     * dimenticato la password ha quasi certamente provato a indovinarla
     * qualche volta prima di chiedere aiuto, e senza questa riga si
     * troverebbe bloccato proprio con la password nuova in mano.
     *
     * <p>Un'utenza disattivata si può azzerare: rimettere in piedi l'accesso
     * di chi rientra è una password nuova <em>e</em> una riattivazione, in
     * qualunque ordine le si faccia.
     *
     * @param emailAutore chi sta azzerando, per il log e per impedire che sia
     *                    la propria
     */
    @Transactional
    public void azzera(Long idUtente, RichiestaResetPassword richiesta, String emailAutore) {
        Utente bersaglio = utenti.findById(idUtente).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utente non trovato."));

        if (bersaglio.getEmail().equalsIgnoreCase(emailAutore)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "RESET_DI_SE_STESSI: la propria password si cambia da «Profilo», "
                            + "indicando quella attuale.");
        }

        if (!RegolePassword.valida(richiesta.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "WEAK_PASSWORD: la nuova password "
                            + RegolePassword.perche(richiesta.password()) + ".");
        }

        bersaglio.cambiaPassword(codificatorePassword.encode(richiesta.password()));

        limite.riuscito(bersaglio.getId());
        limiteAccesso.riuscito(bersaglio.getEmail());

        // Chi e su chi, per id: in un gestionale che non ha un registro degli
        // accessi, questa e' una delle poche righe che dicono che qualcuno e'
        // entrato nei panni di qualcun altro, e serve che resti.
        log.warn("Password dell'utente {} azzerata da {}",
                bersaglio.getId(), emailAutore);
    }
}
