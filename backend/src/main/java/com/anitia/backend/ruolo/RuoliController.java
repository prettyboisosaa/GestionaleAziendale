package com.anitia.backend.ruolo;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/ruoli}: l'elenco dei ruoli assegnabili. */
@RestController
@RequestMapping("/api/ruoli")
class RuoliController {

    private final RuoloRepository ruoli;

    RuoliController(RuoloRepository ruoli) {
        this.ruoli = ruoli;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('RUOLI_ASSEGNA','RUOLI_GESTISCI')")
    @Transactional(readOnly = true)
    public List<RigaRuolo> elenco() {
        return ruoli.findAllByOrderByNomeAsc().stream().map(RigaRuolo::da).toList();
    }
}
