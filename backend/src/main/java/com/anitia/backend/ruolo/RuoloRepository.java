package com.anitia.backend.ruolo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuoloRepository extends JpaRepository<Ruolo, Long> {

    List<Ruolo> findAllByOrderByNomeAsc();
}
