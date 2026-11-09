package com.anitia.backend.autenticazione;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.anitia.backend.sicurezza.UtenteCollegato;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Chi e' collegato e che cosa gli e' permesso fare. Il frontend usa
 * {@code permessi} per decidere quali voci di menu e quali pulsanti mostrare.
 */
public record UtenteCorrente(Long id, String email, Set<String> permessi,
                             String nome, String cognome) {

    /**
     * I permessi si leggono dal {@link UserDetails}, non dalle authority
     * dell'{@code Authentication}: quest'ultimo ne contiene anche di aggiunte
     * da Spring Security, come {@code FACTOR_PASSWORD}, che non sono permessi
     * del gestionale.
     */
    static UtenteCorrente da(UserDetails utente) {
        // L'id serve all'interfaccia per sapere se una task è sua: senza, le
        // resterebbero nome e cognome, e due omonimi basterebbero a mostrare
        // un controllo che il server poi rifiuta.
        Long id = utente instanceof UtenteCollegato collegato ? collegato.id : null;
        String nome = utente instanceof UtenteCollegato collegato ? collegato.nome : "";
        String cognome = utente instanceof UtenteCollegato collegato ? collegato.cognome : "";
        Set<String> permessi = utente.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                // TreeSet: ordine stabile nel JSON, piu' facile da leggere e
                // da verificare in un test.
                .collect(Collectors.toCollection(TreeSet::new));
        return new UtenteCorrente(id, utente.getUsername(), permessi, nome, cognome);
    }
}
