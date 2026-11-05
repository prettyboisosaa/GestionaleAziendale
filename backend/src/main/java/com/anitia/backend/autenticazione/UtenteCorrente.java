package com.anitia.backend.autenticazione;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Chi e' collegato e che cosa gli e' permesso fare. Il frontend usa
 * {@code permessi} per decidere quali voci di menu e quali pulsanti mostrare.
 */
public record UtenteCorrente(String email, Set<String> permessi) {

    /**
     * I permessi si leggono dal {@link UserDetails}, non dalle authority
     * dell'{@code Authentication}: quest'ultimo ne contiene anche di aggiunte
     * da Spring Security, come {@code FACTOR_PASSWORD}, che non sono permessi
     * del gestionale.
     */
    static UtenteCorrente da(UserDetails utente) {
        Set<String> permessi = utente.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                // TreeSet: ordine stabile nel JSON, piu' facile da leggere e
                // da verificare in un test.
                .collect(Collectors.toCollection(TreeSet::new));
        return new UtenteCorrente(utente.getUsername(), permessi);
    }
}
