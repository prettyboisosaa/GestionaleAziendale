package com.anitia.backend.utente;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UtentePermessoRepository
        extends JpaRepository<UtentePermesso, UtentePermessoId> {

    List<UtentePermesso> findByUtenteId(Long utenteId);

    Optional<UtentePermesso> findByUtenteIdAndPermessoCodice(Long utenteId, String permessoCodice);
}
