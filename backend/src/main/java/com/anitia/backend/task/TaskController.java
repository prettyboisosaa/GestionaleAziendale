package com.anitia.backend.task;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le task di un lavoro.
 *
 * <p>Stanno sotto il lavoro nell'indirizzo perché non esistono senza:
 * {@code task.lavoro_id} è {@code NOT NULL}. Ogni rotta verifica che la task
 * appartenga davvero al lavoro indicato.
 *
 * <p>I {@code @PreAuthorize} qui fanno il primo filtro — hai almeno uno dei
 * permessi? — e il servizio rifinisce la parte che dipende dai dati: chi ha
 * solo {@code TASK_GESTISCI_PROPRI} può agire soltanto sui lavori di cui è
 * responsabile, e quello in un'annotazione non si esprime.
 */
@RestController
@RequestMapping("/api/lavori/{idLavoro}/task")
class TaskController {

    private final ServizioTask servizio;

    TaskController(ServizioTask servizio) {
        this.servizio = servizio;
    }

    /**
     * Per leggere basta poter vedere il lavoro: quali task si vedono poi
     * dipende dai permessi sulle task, e lo decide il servizio. È la prima
     * fix concordata — aprire un lavoro sì, aggiungerci task no.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('LAVORI_VISUALIZZA_TUTTI','LAVORI_VISUALIZZA_PROPRI')")
    public List<RigaTask> elenco(@PathVariable Long idLavoro,
                                 @AuthenticationPrincipal UserDetails utente) {
        return servizio.delLavoro(utente, idLavoro);
    }

    /** Chi si può assegnare: serve a riempire il menu dell'assegnazione. */
    @GetMapping("/assegnabili")
    @PreAuthorize("hasAnyAuthority('TASK_GESTISCI_TUTTI','TASK_GESTISCI_PROPRI')")
    public List<Assegnatario> assegnabili(@PathVariable Long idLavoro,
                                          @AuthenticationPrincipal UserDetails utente) {
        return servizio.assegnabili(utente, idLavoro);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('TASK_GESTISCI_TUTTI','TASK_GESTISCI_PROPRI')")
    public RigaTask crea(@PathVariable Long idLavoro,
                         @Valid @RequestBody RichiestaNuovaTask richiesta,
                         @AuthenticationPrincipal UserDetails utente) {
        return servizio.crea(utente, idLavoro, richiesta);
    }

    /**
     * Cambia lo stato della task. Chiudendola o annullandola, le ore
     * stanziate e non consumate tornano nel pool del lavoro.
     *
     * <p>Tre permessi, perché tre figure diverse hanno motivo di farlo: chi
     * gestisce le task di ogni lavoro, il responsabile dei propri, e — con
     * {@code TASK_AGGIORNA_STATO} — chi ci lavora. Quell'ultimo caso vale solo
     * per le task a cui si è assegnati, e il limite lo verifica il servizio:
     * in un'annotazione non si esprime.
     */
    @PostMapping("/{idTask}/stato")
    @PreAuthorize("hasAnyAuthority('TASK_AGGIORNA_STATO','TASK_GESTISCI_TUTTI',"
                + "'TASK_GESTISCI_PROPRI')")
    public RigaTask cambiaStato(@PathVariable Long idLavoro,
                               @PathVariable Long idTask,
                               @Valid @RequestBody RichiestaStatoTask richiesta,
                               @AuthenticationPrincipal UserDetails utente) {
        return servizio.cambiaStato(utente, idLavoro, idTask, richiesta.stato());
    }

    @PostMapping("/{idTask}/assegnazioni")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('TASK_GESTISCI_TUTTI','TASK_GESTISCI_PROPRI')")
    public void assegna(@PathVariable Long idLavoro,
                        @PathVariable Long idTask,
                        @Valid @RequestBody RichiestaAssegnazione richiesta,
                        @AuthenticationPrincipal UserDetails utente) {
        servizio.assegna(utente, idLavoro, idTask, richiesta.utenteId());
    }

    @DeleteMapping("/{idTask}/assegnazioni/{utenteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('TASK_GESTISCI_TUTTI','TASK_GESTISCI_PROPRI')")
    public void rimuoviAssegnazione(@PathVariable Long idLavoro,
                                    @PathVariable Long idTask,
                                    @PathVariable Long utenteId,
                                    @AuthenticationPrincipal UserDetails utente) {
        servizio.rimuoviAssegnazione(utente, idLavoro, idTask, utenteId);
    }
}
