package com.anitia.backend.utente;

import com.anitia.backend.ruolo.Ruolo;
import com.anitia.backend.ruolo.RuoloRepository;
import com.anitia.backend.utente.UtenteRepository.ConteggioPermessi;
import com.anitia.backend.utente.UtenteRepository.RuoloAssegnato;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServizioUtenti {

    private static final String EMAIL_GIA_USATA = "Esiste già un utente con questa email.";

    private final UtenteRepository utenti;
    private final RuoloRepository ruoli;
    private final PasswordEncoder codificatorePassword;

    ServizioUtenti(UtenteRepository utenti, RuoloRepository ruoli,
                   PasswordEncoder codificatorePassword) {
        this.utenti = utenti;
        this.ruoli = ruoli;
        this.codificatorePassword = codificatorePassword;
    }

    /** Tutti gli utenti con i loro ruoli, in ordine di cognome. */
    @Transactional(readOnly = true)
    public List<RigaUtente> elenco() {
        Map<Long, List<String>> ruoliPerUtente = utenti.trovaRuoliAssegnati().stream()
                .collect(Collectors.groupingBy(RuoloAssegnato::getUtenteId,
                        Collectors.mapping(RuoloAssegnato::getNome, Collectors.toList())));

        Map<Long, Integer> permessiPerUtente = utenti.contaPermessiEffettivi().stream()
                .collect(Collectors.toMap(ConteggioPermessi::getUtenteId,
                        ConteggioPermessi::getQuanti));

        return utenti.findAllByOrderByCognomeAscNomeAsc().stream()
                .map(utente -> RigaUtente.da(utente,
                        ruoliPerUtente.getOrDefault(utente.getId(), List.of()),
                        permessiPerUtente.getOrDefault(utente.getId(), 0)))
                .toList();
    }

    /**
     * Crea l'utente e gli assegna i ruoli scelti.
     *
     * @param emailAutore chi sta creando, per registrarlo in
     *                    {@code utente_ruolo.assegnato_da_id}
     */
    @Transactional
    public RigaUtente crea(RichiestaNuovoUtente richiesta, String emailAutore) {
        if (utenti.existsByEmail(richiesta.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, EMAIL_GIA_USATA);
        }

        List<Ruolo> scelti = ruoli.findAllById(richiesta.ruoli());
        if (scelti.size() != richiesta.ruoli().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Uno dei ruoli indicati non esiste.");
        }

        // Il principal porta solo l'email, mentre la tabella vuole l'id: una
        // query in piu' su un'operazione rara, che costa meno di un
        // UserDetails su misura da mantenere.
        Long autoreId = utenti.findByEmail(emailAutore).map(Utente::getId).orElse(null);

        Utente creato;
        try {
            creato = utenti.saveAndFlush(Utente.nuovo(richiesta.nome(), richiesta.cognome(),
                    richiesta.email(), codificatorePassword.encode(richiesta.password())));
        } catch (DataIntegrityViolationException eccezione) {
            // Il vincolo uk_utente_email. Il controllo qui sopra da' un
            // messaggio migliore, ma non basta: fra quello e questo INSERT due
            // richieste possono incrociarsi, e la garanzia vera e' il vincolo.
            throw new ResponseStatusException(HttpStatus.CONFLICT, EMAIL_GIA_USATA);
        }

        for (Ruolo ruolo : scelti) {
            utenti.assegnaRuolo(creato.getId(), ruolo.getId(), autoreId);
        }

        // Il conteggio dei permessi si rilegge dalla vista: l'utente e' appena
        // nato e i suoi ruoli sono appena stati scritti.
        return RigaUtente.da(creato,
                scelti.stream().map(Ruolo::getNome).sorted().toList(),
                utenti.trovaCodiciPermesso(creato.getId()).size());
    }
}
