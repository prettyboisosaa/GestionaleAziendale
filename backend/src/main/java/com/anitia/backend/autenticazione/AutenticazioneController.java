package com.anitia.backend.autenticazione;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Accesso al gestionale.
 *
 * <ul>
 *   <li>{@code POST /api/autenticazione/login} — credenziali in JSON, in
 *       risposta l'utente con i suoi permessi e il cookie di sessione.
 *   <li>{@code GET  /api/autenticazione/io} — chi e' collegato adesso.
 *   <li>{@code POST /api/autenticazione/logout} — chiude la sessione e
 *       risponde 204. Non ha un metodo qui: lo gestisce il filtro configurato
 *       in {@code ConfigurazioneSicurezza}.
 * </ul>
 */
@RestController
@RequestMapping("/api/autenticazione")
class AutenticazioneController {

    private static final String CREDENZIALI_RIFIUTATE =
            "Email o password non corretti, oppure utenza non attiva.";

    private final AuthenticationManager gestoreAutenticazione;
    private final SecurityContextRepository contestoSicurezza;
    private final SecurityContextHolderStrategy strategiaContesto =
            SecurityContextHolder.getContextHolderStrategy();

    AutenticazioneController(AuthenticationManager gestoreAutenticazione,
                             SecurityContextRepository contestoSicurezza) {
        this.gestoreAutenticazione = gestoreAutenticazione;
        this.contestoSicurezza = contestoSicurezza;
    }

    @PostMapping("/login")
    UtenteCorrente login(@Valid @RequestBody RichiestaLogin credenziali,
                         HttpServletRequest richiesta,
                         HttpServletResponse risposta) {

        Authentication autenticazione;
        try {
            autenticazione = gestoreAutenticazione.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            credenziali.email(), credenziali.password()));
        } catch (AuthenticationException eccezione) {
            // Lo stesso messaggio per password sbagliata, email inesistente e
            // utenza disattivata: dalla risposta non si deve capire quali
            // indirizzi sono registrati.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, CREDENZIALI_RIFIUTATE);
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
}
