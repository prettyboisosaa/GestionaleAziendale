package com.anitia.backend.ore;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La distribuzione del monte ore fra le task.
 *
 * <p>Sta sotto il lavoro nell'indirizzo per la stessa ragione delle task: le
 * ore di una task vengono dal pool del suo lavoro, e ogni rotta verifica che
 * la task appartenga davvero al lavoro indicato — altrimenti
 * {@code /api/lavori/3/task/1/ore} toccherebbe la task 1 anche se sta nel
 * lavoro 1.
 *
 * <p>Fissare il monte ore non è qui ma in {@code LavoriController}: è una
 * decisione sul lavoro, non sulle sue task, e il permesso è un altro.
 */
@RestController
@RequestMapping("/api/lavori/{idLavoro}")
class OreController {

    private final ServizioOre servizio;

    OreController(ServizioOre servizio) {
        this.servizio = servizio;
    }

    /**
     * La situazione delle ore del lavoro. Per leggerla basta poter vedere il
     * lavoro: non esiste un permesso dedicato di sola lettura sulle ore,
     * perché andrebbe concesso a tutti quelli che vedono i lavori.
     */
    @GetMapping("/ore")
    @PreAuthorize("hasAnyAuthority('LAVORI_VISUALIZZA_TUTTI','LAVORI_VISUALIZZA_PROPRI')")
    public RiepilogoOre riepilogo(@PathVariable Long idLavoro,
                                  @AuthenticationPrincipal UserDetails utente) {
        return servizio.riepilogo(utente, idLavoro);
    }

    /**
     * Alloca ore dal pool alla task. Restituisce la situazione del lavoro
     * dopo l'operazione, così chi chiama sa subito quante ore restano senza
     * una seconda chiamata.
     *
     * <p>Se il pool non basta la risposta è 409 e il messaggio dice quante ore
     * libere ci sono: è il numero con cui si precompila la richiesta
     * all'amministratore.
     */
    @PostMapping("/task/{idTask}/ore")
    @PreAuthorize("hasAnyAuthority('ORE_ALLOCA_TUTTI','ORE_ALLOCA_PROPRI')")
    public RiepilogoOre alloca(@PathVariable Long idLavoro,
                               @PathVariable Long idTask,
                               @Valid @RequestBody RichiestaAllocazione richiesta,
                               @AuthenticationPrincipal UserDetails utente) {
        return servizio.alloca(utente, idLavoro, idTask, richiesta.ore(), richiesta.motivo());
    }
}
