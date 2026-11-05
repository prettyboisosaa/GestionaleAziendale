package com.anitia.backend.lavoro;

import com.anitia.backend.utente.Utente;

/** Un utente che può fare il responsabile di un lavoro. */
public record CandidatoResponsabile(Long id, String nome, String cognome) {

    static CandidatoResponsabile da(Utente utente) {
        return new CandidatoResponsabile(utente.getId(), utente.getNome(), utente.getCognome());
    }
}
