package com.anitia.backend.ruolo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuoloRepository extends JpaRepository<Ruolo, Long> {

    List<Ruolo> findAllByOrderByNomeAsc();

    /**
     * Il ruolo protetto, cioe' quello che ha sempre tutti i permessi (vedi la
     * {@code V1}). Serve all'amministratore iniziale, che deve poter fare
     * tutto: si cerca per {@code protetto} e non per id o per nome perche' i
     * ruoli sono dati — si rinominano dall'interfaccia, e un id scritto nel
     * codice e' una dipendenza silenziosa dai numeri della migrazione.
     */
    Optional<Ruolo> findFirstByProtettoTrueOrderByIdAsc();
}
