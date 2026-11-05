package com.anitia.backend.ore;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le richieste di ore aggiuntive.
 *
 * <p>Gli indirizzi stanno su due radici diverse, di proposito. Aprire una
 * richiesta è un'azione <em>su un lavoro</em>, e sta sotto
 * {@code /api/lavori/{id}/richieste-ore} come le task e le allocazioni.
 * Deciderle è un lavoro a sé — l'amministratore apre una coda che attraversa
 * tutti i lavori — e sta sotto {@code /api/richieste-ore}.
 */
@RestController
class RichiesteOreController {

    private final ServizioRichiesteOre servizio;

    RichiesteOreController(ServizioRichiesteOre servizio) {
        this.servizio = servizio;
    }

    // --- sotto il lavoro: chiedere -----------------------------------------

    /**
     * Apre una richiesta. Il servizio pretende che chi la apre sia il
     * responsabile del lavoro: una richiesta su un lavoro che non si gestisce
     * arriverebbe all'amministratore senza nessuno che ne risponda.
     */
    @PostMapping("/api/lavori/{idLavoro}/richieste-ore")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ORE_RICHIEDI','ORE_ALLOCA_TUTTI','ORE_RICHIESTE_GESTISCI')")
    public RigaRichiestaOre apri(@PathVariable Long idLavoro,
                                 @Valid @RequestBody RichiestaNuoveOre richiesta,
                                 @AuthenticationPrincipal UserDetails utente) {
        return servizio.apri(utente, idLavoro, richiesta);
    }

    /** Lo storico delle richieste di un lavoro: basta poter vedere il lavoro. */
    @GetMapping("/api/lavori/{idLavoro}/richieste-ore")
    @PreAuthorize("hasAnyAuthority('LAVORI_VISUALIZZA_TUTTI','LAVORI_VISUALIZZA_PROPRI')")
    public List<RigaRichiestaOre> delLavoro(@PathVariable Long idLavoro,
                                            @AuthenticationPrincipal UserDetails utente) {
        return servizio.delLavoro(utente, idLavoro);
    }

    // --- la coda: decidere --------------------------------------------------

    /**
     * Le richieste che chi chiede ha diritto di vedere: tutte, oppure quelle
     * che ha aperto lui.
     *
     * <p>È da qui che un PM scopre com'è finita la sua richiesta: il
     * gestionale non ha notifiche, quindi l'esito si va a vedere.
     */
    @GetMapping("/api/richieste-ore")
    @PreAuthorize("hasAnyAuthority('ORE_RICHIEDI','ORE_RICHIESTE_GESTISCI',"
                + "'LAVORI_VISUALIZZA_TUTTI')")
    public List<RigaRichiestaOre> mie(@AuthenticationPrincipal UserDetails utente) {
        return servizio.mie(utente);
    }

    /**
     * La coda di chi decide: le richieste in attesa, dalla più vecchia.
     *
     * <p>In mancanza di notifiche è questo l'unico posto in cui una richiesta
     * si fa notare, e per questo l'ordine è quello dell'attesa.
     */
    @GetMapping("/api/richieste-ore/in-attesa")
    @PreAuthorize("hasAuthority('ORE_RICHIESTE_GESTISCI')")
    public List<RigaRichiestaOre> inAttesa() {
        return servizio.inAttesa();
    }

    /**
     * Il quadro su cui decidere: la richiesta, la situazione delle ore del
     * lavoro, il monte ore iniziale, le task in sforamento e lo storico delle
     * richieste precedenti.
     *
     * <p>È la risposta al difetto del piano iniziale — l'amministratore
     * decideva senza contesto.
     */
    @GetMapping("/api/richieste-ore/{id}/contesto")
    @PreAuthorize("hasAuthority('ORE_RICHIESTE_GESTISCI')")
    public ContestoRichiesta contesto(@PathVariable Long id,
                                      @AuthenticationPrincipal UserDetails utente) {
        return servizio.contesto(utente, id);
    }

    /**
     * La decisione: quante ore si concedono. Zero è un rifiuto, tutte quelle
     * chieste un'approvazione piena, una parte un'approvazione parziale — lo
     * stato lo deduce il servizio dal numero.
     *
     * <p>Concedere ore <strong>alza il monte ore</strong> del lavoro con un
     * movimento collegato alla richiesta, e se la richiesta era legata a una
     * task le alloca direttamente là.
     */
    @PostMapping("/api/richieste-ore/{id}/gestisci")
    @PreAuthorize("hasAuthority('ORE_RICHIESTE_GESTISCI')")
    public RigaRichiestaOre gestisci(@PathVariable Long id,
                                     @Valid @RequestBody RichiestaEsito esito,
                                     @AuthenticationPrincipal UserDetails utente) {
        return servizio.gestisci(utente, id, esito.oreConcesse());
    }
}
