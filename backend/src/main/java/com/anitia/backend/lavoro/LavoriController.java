package com.anitia.backend.lavoro;

import com.anitia.backend.ore.RigaMovimento;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gestione dei lavori. */
@RestController
@RequestMapping("/api/lavori")
class LavoriController {

    private final ServizioLavori servizio;
    private final ServizioKpiLavoro kpi;

    LavoriController(ServizioLavori servizio, ServizioKpiLavoro kpi) {
        this.servizio = servizio;
        this.kpi = kpi;
    }

    /**
     * Quali lavori si vedono dipende dal permesso, e il filtro lo applica il
     * servizio: con {@code LAVORI_VISUALIZZA_TUTTI} si vede tutto, con
     * {@code LAVORI_VISUALIZZA_PROPRI} solo i propri.
     *
     * <p>I lavori annullati restano fuori: l'elenco serve a lavorare, e un
     * lavoro che non si farà sarebbe rumore. Con
     * {@code ?includi_annullati=true} rientrano, in fondo alla lista, per
     * poterli consultare.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('LAVORI_VISUALIZZA_TUTTI','LAVORI_VISUALIZZA_PROPRI')")
    public List<RigaLavoro> elenco(@AuthenticationPrincipal UserDetails utente,
                                   @RequestParam(name = "includi_annullati",
                                                 defaultValue = "false")
                                   boolean includiAnnullati) {
        return servizio.elenco(utente, includiAnnullati);
    }

    /**
     * Un singolo lavoro. Chi non ha diritto di vederlo riceve 403, e il
     * controllo è lo stesso dell'elenco, estratto in {@code ServizioLavori}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('LAVORI_VISUALIZZA_TUTTI','LAVORI_VISUALIZZA_PROPRI')")
    public RigaLavoro dettaglio(@PathVariable Long id,
                                @AuthenticationPrincipal UserDetails utente) {
        return servizio.dettaglio(utente, id);
    }

    /**
     * Chi può fare il responsabile di un lavoro: serve a riempire il menu del
     * form di creazione. Sta sotto {@code /api/lavori} e non sotto
     * {@code /api/utenti} perché è una domanda sui lavori.
     */
    @GetMapping("/responsabili")
    @PreAuthorize("hasAuthority('LAVORI_ASSEGNA_RESPONSABILE')")
    public List<CandidatoResponsabile> responsabili() {
        return servizio.responsabiliPossibili();
    }

    /**
     * Il monte ore nel corpo è facoltativo, e il permesso per fissarlo è un
     * altro — {@code ORE_MONTE_GESTISCI}. Non si può dichiarare in
     * un'annotazione, perché dipende da quello che la richiesta contiene: il
     * controllo lo fa il servizio.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('LAVORI_CREA')")
    public RigaLavoro crea(@Valid @RequestBody RichiestaNuovoLavoro richiesta,
                           @AuthenticationPrincipal UserDetails utente) {
        return servizio.crea(utente, richiesta);
    }

    /**
     * Fissa o modifica il monte ore. Solo l'amministratore: il PM distribuisce
     * il budget fra le task, ma non decide quanto è grande.
     *
     * <p>Il servizio rifiuta di portarlo sotto le ore già allocate, e registra
     * un movimento che spiega la variazione — è così che il valore iniziale
     * resta leggibile.
     */
    @PostMapping("/{id}/monte-ore")
    @PreAuthorize("hasAuthority('ORE_MONTE_GESTISCI')")
    public RigaLavoro monteOre(@PathVariable Long id,
                               @Valid @RequestBody RichiestaMonteOre richiesta,
                               @AuthenticationPrincipal UserDetails utente) {
        return servizio.cambiaMonteOre(utente, id, richiesta.monteOre(), richiesta.motivo());
    }

    /**
     * Gli indicatori di avanzamento: scostamento sulle ore, avanzamento,
     * consegne in tempo, stagnazione.
     *
     * <p>Stesso permesso del dettaglio, e stesso controllo di visibilità nel
     * servizio: qui non c'è niente che non si possa già ricavare dalla pagina
     * del lavoro — sono le stesse ore e le stesse task, messe in rapporto —
     * quindi un permesso proprio sarebbe un permesso da concedere sempre
     * insieme a un altro.
     *
     * <p>Una rotta a sé e non un campo di {@code GET /api/lavori/{id}}: i
     * quattro numeri costano una query aggregata in più, e chi apre l'elenco
     * dei lavori non li guarda. Così il pannello dell'avanzamento carica per
     * conto suo e un suo errore non porta via il resto della pagina.
     */
    @GetMapping("/{id}/kpi")
    @PreAuthorize("hasAnyAuthority('LAVORI_VISUALIZZA_TUTTI','LAVORI_VISUALIZZA_PROPRI')")
    public KpiLavoro kpi(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails utente) {
        return kpi.diUnLavoro(utente, id);
    }

    /**
     * Lo storico delle ore del lavoro. Per leggerlo basta poter vedere il
     * lavoro: non esiste un permesso di sola lettura sulle ore, perché
     * andrebbe dato a tutti quelli che vedono i lavori.
     */
    @GetMapping("/{id}/movimenti")
    @PreAuthorize("hasAnyAuthority('LAVORI_VISUALIZZA_TUTTI','LAVORI_VISUALIZZA_PROPRI')")
    public List<RigaMovimento> movimenti(@PathVariable Long id,
                                         @AuthenticationPrincipal UserDetails utente) {
        return servizio.storicoMovimenti(utente, id);
    }

    /**
     * Annulla il lavoro: la riga resta, con lo stato cambiato. È la via per
     * qualunque lavoro che abbia delle task.
     *
     * <p>Due permessi: su qualunque lavoro con {@code LAVORI_MODIFICA_TUTTI},
     * sui propri con {@code LAVORI_MODIFICA_PROPRI}. Quale sia «il proprio» lo
     * decide il servizio, perché dipende da chi è il responsabile di quel
     * lavoro e un'annotazione non lo sa.
     */
    @PostMapping("/{id}/annulla")
    @PreAuthorize("hasAnyAuthority('LAVORI_MODIFICA_TUTTI','LAVORI_MODIFICA_PROPRI')")
    public RigaLavoro annulla(@PathVariable Long id,
                              @AuthenticationPrincipal UserDetails utente) {
        return servizio.annulla(utente, id);
    }

    /**
     * Cancella il lavoro, consentito solo su una bozza senza task. Risponde
     * 204 senza corpo: non c'è più niente da restituire.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('LAVORI_MODIFICA_TUTTI')")
    public void elimina(@PathVariable Long id) {
        servizio.elimina(id);
    }
}
