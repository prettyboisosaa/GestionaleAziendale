package com.anitia.backend.permesso;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermessoRepository extends JpaRepository<Permesso, String> {

    /** Il catalogo nell'ordine deciso dalle migrazioni, non alfabetico. */
    List<Permesso> findAllByOrderByOrdineAsc();
}
