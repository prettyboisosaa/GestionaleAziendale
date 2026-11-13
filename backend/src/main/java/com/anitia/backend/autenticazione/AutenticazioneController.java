package com.anitia.backend.autenticazione;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.anitia.backend.sicurezza.UtenteCollegato;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Accesso al gestionale.
 *
 * <ul>
 *   <li>{@code POST /api/autenticazione/login} — credenziali in JSON, in
 *       risposta l'utente con i suoi permessi e il cookie di sessione.
 *   <li>{@code GET  /api/autenticazione/io} — chi e' collegato adesso.
 *   <li>{@code POST /api/autenticazione/cambio-password} — la propria
 *       password, con quella attuale a dimostrare di esserne il padrone.
 *   <li>{@code POST /api/autenticazione/logout} — chiude la sessione e
 *       risponde 204. Non ha un metodo qui: lo gestisce il filtro configurato
 *       in {@code ConfigurazioneSicurezza}.
 * </ul>
 */
@RestController
@RequestMapping("/api/autenticazione")
class AutenticazioneController {

    private static final String CREDENZIALI_RIFIUTATE =
            "Email o password non corretti.";

    private static final String DISATTIVATO =
            "UTENTE_DISATTIVATO: Account disattivato. Contatta l'amministratore.";

    private final AuthenticationManager gestoreAutenticazione;
    private final SecurityContextRepository contestoSicurezza;
    private final ServizioPassword password;
    private final LimiteTentativiAccesso limiteTentativi;
    private final SecurityContextHolderStrategy strategiaContesto =
            SecurityContextHolder.getContextHolderStrategy();

    AutenticazioneController(AuthenticationManager gestoreAutenticazione,
                             SecurityContextRepository contestoSicurezza,
                             ServizioPassword password,
                             LimiteTentativiAccesso limiteTentativi) {
        this.gestoreAutenticazione = gestoreAutenticazione;
        this.contestoSicurezza = contestoSicurezza;
        this.password = password;
        this.limiteTentativi = limiteTentativi;
    }

    @PostMapping("/login")
    UtenteCorrente login(@Valid @RequestBody RichiestaLogin credenziali,
                         HttpServletRequest richiesta,
                         HttpServletResponse risposta) {

        // Il controllo sta PRIMA di authenticate, che e' l'unico posto in cui
        // serva: e' li' che si consuma il bcrypt, ed e' quello il lavoro che
        // chi tira a indovinare vuole farci fare.
        if (limiteTentativi.bloccato(credenziali.email())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "TROPPI_TENTATIVI: Troppi tentativi di accesso. Riprova fra "
                            + limiteTentativi.minutiDiAttesa(credenziali.email())
                            + " minuti.");
        }

        Authentication autenticazione;
        try {
            autenticazione = gestoreAutenticazione.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            credenziali.email(), credenziali.password()));
        } catch (AuthenticationException eccezione) {
            limiteTentativi.sbagliato(credenziali.email());
            // Lo stesso messaggio per password sbagliata ed email
            // inesistente: dalla risposta non si deve capire quali indirizzi
            // sono registrati.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, CREDENZIALI_RIFIUTATE);
        }

        // Le credenziali erano giuste: il conto riparte da zero, anche se
        // l'utenza risultasse chiusa due righe piu' sotto. Chi ha scritto la
        // password vera non e' chi stava tirando a indovinare, e lasciargli
        // addosso i tentativi di prima lo bloccherebbe al prossimo accesso
        // per colpa di qualcun altro.
        limiteTentativi.riuscito(credenziali.email());

        // Le credenziali sono giuste: solo adesso si guarda se l'utenza e'
        // aperta, e si puo' dire perche' l'accesso non passa. L'ordine e' la
        // ragione per cui ServizioDettagliUtente non marca l'utente come
        // disabilitato: Spring Security controllerebbe il flag prima della
        // password, e chi tira a indovinare un indirizzo scoprirebbe
        // dall'errore quali account esistono.
        if (!UtenteCollegato.ammesso((UserDetails) autenticazione.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, DISATTIVATO);
        }

        // Protezione dal session fixation: se il browser arrivava con una
        // sessione anonima, ne cambiamo l'identificativo. Il controllo serve
        // perche' changeSessionId solleva un'eccezione se la sessione non
        // esiste ancora, e in quel caso nascera' nuova qui sotto.
        if (richiesta.getSession(false) != null) {
            richiesta.changeSessionId();
        }

        SecurityContext contesto = strategiaContesto.createEmptyContext();
        contesto.setAuthentication(autenticazione);
        strategiaContesto.setContext(contesto);
        // E' questa chiamata a creare la sessione e ad accodare il cookie.
        contestoSicurezza.saveContext(contesto, richiesta, risposta);

        return UtenteCorrente.da((UserDetails) autenticazione.getPrincipal());
    }

    /**
     * Il cookie di sessione e' {@code HttpOnly}, quindi opaco a JavaScript: il
     * frontend, dopo un ricaricamento di pagina, non puo' sapere chi e' se non
     * chiedendolo qui.
     */
    @GetMapping("/io")
    UtenteCorrente io(@AuthenticationPrincipal UserDetails utente) {
        return UtenteCorrente.da(utente);
    }

    /**
     * Cambia la propria password.
     *
     * <p>Sta qui e non fra le rotte degli utenti perché non è
     * amministrazione: {@code /api/utenti} serve a chi gestisce le persone, e
     * questa è una cosa che ciascuno fa per sé. Non ha nessun
     * {@code @PreAuthorize}, e la catena di filtri pretende già che la
     * richiesta sia autenticata — è esattamente la condizione che serve, dato
     * che l'utenza su cui si scrive è quella della sessione.
     *
     * <p>Risponde <strong>204</strong>: non c'è niente da restituire. La
     * sessione resta aperta e i permessi non cambiano, quindi il frontend non
     * ha nulla da ridisegnare oltre al proprio modulo.
     */
    @PostMapping("/cambio-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cambioPassword(@Valid @RequestBody RichiestaCambioPassword richiesta,
                        @AuthenticationPrincipal UserDetails utente) {
        password.cambia(utente, richiesta);
    }
}
