package com.anitia.backend.sicurezza;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rilegge utente e permessi dal database a ogni richiesta.
 *
 * <p>Serve perche' Spring, di base, ricostruisce l'utente collegato
 * deserializzandolo dalla sessione: i permessi resterebbero la fotografia
 * scattata al momento del login. Disattivare una persona o cambiarle i ruoli
 * non avrebbe effetto fino al suo prossimo accesso.
 *
 * <p>Costa due query per richiesta (l'utente, piu' la vista
 * {@code permesso_effettivo}). Per un gestionale interno e' accettabile; se un
 * giorno pesasse, la via e' una cache a vita breve, non rinunciare al
 * controllo.
 *
 * <p>Non e' un {@code @Component} di proposito: un bean di tipo
 * {@code Filter} verrebbe registrato da Spring Boot anche fuori dalla catena
 * di Spring Security, e girerebbe due volte. Viene costruito a mano in
 * {@link ConfigurazioneSicurezza}.
 */
final class FiltroUtenteAggiornato extends OncePerRequestFilter {

    private final UserDetailsService dettagliUtente;
    private final SecurityContextHolderStrategy strategiaContesto =
            SecurityContextHolder.getContextHolderStrategy();

    FiltroUtenteAggiornato(UserDetailsService dettagliUtente) {
        this.dettagliUtente = dettagliUtente;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest richiesta, HttpServletResponse risposta,
                                    FilterChain catena) throws ServletException, IOException {

        Authentication autenticazione = strategiaContesto.getContext().getAuthentication();

        // Sulle richieste senza sessione, login compreso, non c'e' niente da
        // aggiornare: il contesto e' vuoto.
        if (autenticazione != null
                && autenticazione.isAuthenticated()
                && autenticazione.getPrincipal() instanceof UserDetails collegato
                && !aggiorna(collegato.getUsername())) {
            chiudiSessione(richiesta);
        }

        catena.doFilter(richiesta, risposta);
    }

    /**
     * Sostituisce l'utente nel contesto con quello appena letto dal database.
     * Restituisce falso se non puo' piu' stare collegato.
     *
     * <p>Il contesto aggiornato non viene risalvato in sessione: la copia che
     * sta li' e' comunque irrilevante, perche' a ogni richiesta si rilegge.
     */
    private boolean aggiorna(String email) {
        UserDetails aggiornato;
        try {
            aggiornato = dettagliUtente.loadUserByUsername(email);
        } catch (UsernameNotFoundException eccezione) {
            return false;
        }

        if (!aggiornato.isEnabled()) {
            return false;
        }

        SecurityContext contesto = strategiaContesto.createEmptyContext();
        contesto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                aggiornato, null, aggiornato.getAuthorities()));
        strategiaContesto.setContext(contesto);
        return true;
    }

    /**
     * Svuota il contesto e butta via la sessione, ma non scrive la risposta:
     * lasciando proseguire la catena, la richiesta arriva all'
     * {@code AuthorizationFilter} come se fosse anonima, ed e' quello a
     * fermarla con un 401. Si riusa la macchina che c'e' gia'.
     */
    private void chiudiSessione(HttpServletRequest richiesta) {
        strategiaContesto.clearContext();
        HttpSession sessione = richiesta.getSession(false);
        if (sessione != null) {
            sessione.invalidate();
        }
    }
}
