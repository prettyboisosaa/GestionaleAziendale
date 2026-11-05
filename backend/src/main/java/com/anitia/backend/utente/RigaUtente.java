package com.anitia.backend.utente;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Un utente come lo vede il frontend.
 *
 * <p>Non ha un campo per la password, nemmeno in forma di hash: è un record
 * separato dall'entità proprio perché quel dato non possa uscire dall'API per
 * distrazione.
 */
public record RigaUtente(Long id,
                         String nome,
                         String cognome,
                         String email,
                         boolean attivo,
                         LocalDateTime creatoIl,
                         List<String> ruoli,
                         /** Quanti permessi ha in tutto, ruoli e scostamenti insieme. */
                         int permessi) {

    static RigaUtente da(Utente utente, List<String> ruoli, int permessi) {
        return new RigaUtente(utente.getId(), utente.getNome(), utente.getCognome(),
                utente.getEmail(), utente.isAttivo(), utente.getCreatoIl(), ruoli, permessi);
    }
}
