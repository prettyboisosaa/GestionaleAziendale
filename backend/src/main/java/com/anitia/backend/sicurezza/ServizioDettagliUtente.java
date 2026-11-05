package com.anitia.backend.sicurezza;

import com.anitia.backend.utente.Utente;
import com.anitia.backend.utente.UtenteRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carica l'utente e i suoi permessi dal database per la verifica al login.
 */
@Service
class ServizioDettagliUtente implements UserDetailsService {

    private final UtenteRepository utenti;

    ServizioDettagliUtente(UtenteRepository utenti) {
        this.utenti = utenti;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Utente utente = utenti.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Nessun utente con email " + email));

        return User.withUsername(utente.getEmail())
                .password(utente.getPasswordHash())
                // attivo = false nel database: niente accesso.
                .disabled(!utente.isAttivo())
                .authorities(utenti.trovaCodiciPermesso(utente.getId()).toArray(String[]::new))
                .build();
    }
}
