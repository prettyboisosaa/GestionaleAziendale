package com.anitia.backend.utente;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * La chiave composta di {@link UtentePermesso}: utente più codice del
 * permesso. I nomi e i tipi dei campi devono combaciare con quelli annotati
 * {@code @Id} nell'entità.
 */
public class UtentePermessoId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long utenteId;
    private String permessoCodice;

    /** Richiesto da JPA. */
    public UtentePermessoId() {
    }

    public UtentePermessoId(Long utenteId, String permessoCodice) {
        this.utenteId = utenteId;
        this.permessoCodice = permessoCodice;
    }

    @Override
    public boolean equals(Object altro) {
        if (this == altro) {
            return true;
        }
        if (!(altro instanceof UtentePermessoId chiave)) {
            return false;
        }
        return Objects.equals(utenteId, chiave.utenteId)
                && Objects.equals(permessoCodice, chiave.permessoCodice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(utenteId, permessoCodice);
    }
}
