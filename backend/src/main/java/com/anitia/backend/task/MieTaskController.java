package com.anitia.backend.task;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le task affidate a chi sta chiedendo, di ogni lavoro.
 *
 * <p>Sta fuori da {@link TaskController}, che è mappato su
 * {@code /api/lavori/{idLavoro}/task}, perché parte da un'altra domanda: non
 * «quali task ha questo lavoro» ma «che cosa devo fare io». La prima si legge
 * un lavoro per volta, e per un dipendente assegnato su quattro progetti
 * significherebbe quattro chiamate e nessun elenco.
 *
 * <p><strong>Nessun parametro, di proposito.</strong> La rotta restituisce
 * solo le righe di chi chiede, e il filtro è l'id ricavato dalla sessione: se
 * accettasse un {@code utenteId} servirebbe difenderlo, e vedere il lavoro di
 * un altro è già un'altra rotta con i suoi permessi
 * ({@code /api/tempo/utente/{id}}).
 *
 * <p>Il permesso è {@code TASK_VISUALIZZA_ASSEGNATE}, che il catalogo porta
 * dalla V1 e che finora nessuna rotta usava.
 */
@RestController
@RequestMapping("/api/mie-task")
class MieTaskController {

    private final ServizioTask servizio;

    MieTaskController(ServizioTask servizio) {
        this.servizio = servizio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TASK_VISUALIZZA_ASSEGNATE')")
    public List<MiaTask> mie(@AuthenticationPrincipal UserDetails utente) {
        return servizio.mie(utente);
    }
}
