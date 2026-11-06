package com.anitia.backend.assenza;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le rotte di ferie e permessi.
 *
 * <p>Due gruppi sotto lo stesso controller, distinti dal prefisso e dal
 * permesso:
 *
 * <ul>
 *   <li>{@code /api/mie-assenze…} — le proprie, con
 *       {@code ASSENZE_RICHIEDI}. Il nome segue {@code /api/mie-task}, che è
 *       già la convenzione del gestionale per «le mie cose».
 *   <li>{@code /api/assenze…} — tutte, con {@code ASSENZE_GESTISCI}.
 * </ul>
 *
 * <p>Sono nello stesso file perché sono due facce della stessa cosa e
 * chiamano lo stesso servizio: separarle in due controller farebbe sembrare
 * che siano due funzionalità.
 */
@RestController
class AssenzeController {

    private final ServizioAssenze servizio;

    AssenzeController(ServizioAssenze servizio) {
        this.servizio = servizio;
    }

    // --- le mie -------------------------------------------------------------

    @GetMapping("/api/mie-assenze")
    @PreAuthorize("hasAuthority('ASSENZE_RICHIEDI')")
    public List<RigaAssenza> mie(@AuthenticationPrincipal UserDetails utente) {
        return servizio.mie(utente);
    }

    /**
     * Una richiesta nuova. {@code tipo} decide quali campi sono obbligatori,
     * e il servizio risponde {@code 422 CAMPI_MANCANTI} quando ne manca uno.
     */
    @PostMapping("/api/mie-assenze")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ASSENZE_RICHIEDI')")
    public RigaAssenza chiedi(@Valid @RequestBody RichiestaAssenza richiesta,
                              @AuthenticationPrincipal UserDetails utente) {
        return servizio.chiedi(utente, richiesta);
    }

    /** Modifica, solo finché è in attesa. */
    @PutMapping("/api/mie-assenze/{id}")
    @PreAuthorize("hasAuthority('ASSENZE_RICHIEDI')")
    public RigaAssenza modifica(@PathVariable Long id,
                                @Valid @RequestBody RichiestaAssenza richiesta,
                                @AuthenticationPrincipal UserDetails utente) {
        return servizio.modifica(utente, id, richiesta);
    }

    /** Ritiro, solo finché è in attesa. */
    @PostMapping("/api/mie-assenze/{id}/ritira")
    @PreAuthorize("hasAuthority('ASSENZE_RICHIEDI')")
    public RigaAssenza ritira(@PathVariable Long id,
                              @AuthenticationPrincipal UserDetails utente) {
        return servizio.ritira(utente, id);
    }

    // --- tutte, per l'amministratore ----------------------------------------

    /**
     * Tutte le richieste, con i due filtri facoltativi. Le richieste in
     * attesa arrivano in cima all'elenco.
     */
    @GetMapping("/api/assenze")
    @PreAuthorize("hasAuthority('ASSENZE_GESTISCI')")
    public List<RigaAssenza> tutte(@RequestParam(required = false) StatoAssenza stato,
                                   @RequestParam(name = "utente_id", required = false)
                                   Long utenteId) {
        return servizio.tutte(stato, utenteId);
    }

    @PostMapping("/api/assenze/{id}/approva")
    @PreAuthorize("hasAuthority('ASSENZE_GESTISCI')")
    public RigaAssenza approva(@PathVariable Long id,
                               @Valid @RequestBody(required = false)
                               RichiestaEsitoAssenza esito,
                               @AuthenticationPrincipal UserDetails utente) {
        return servizio.approva(utente, id, esito);
    }

    /**
     * Rifiuto. La nota è obbligatoria: senza, il servizio risponde
     * {@code 422 NOTA_OBBLIGATORIA}.
     */
    @PostMapping("/api/assenze/{id}/rifiuta")
    @PreAuthorize("hasAuthority('ASSENZE_GESTISCI')")
    public RigaAssenza rifiuta(@PathVariable Long id,
                               @Valid @RequestBody(required = false)
                               RichiestaEsitoAssenza esito,
                               @AuthenticationPrincipal UserDetails utente) {
        return servizio.rifiuta(utente, id, esito);
    }
}
