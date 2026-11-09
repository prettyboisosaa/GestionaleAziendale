package com.anitia.backend.sicurezza;

import com.anitia.backend.utente.Utente;
import com.anitia.backend.utente.UtenteRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

        // Un UtenteCollegato e non un User: porta attivo accanto ai permessi,
        // invece di tradurlo nel flag enabled di Spring. La ragione sta nel
        // commento di quella classe — enabled viene controllato prima della
        // password, e farebbe capire dalla risposta quali indirizzi sono
        // registrati.
        return new UtenteCollegato(utente.getId(), utente.getEmail(), utente.getPasswordHash(),
                utente.isAttivo(),
                utenti.trovaCodiciPermesso(utente.getId()).stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList(), utente.getNome(), utente.getCognome());
    }
}
