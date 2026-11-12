package com.anitia.backend.report;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Il consuntivo del tempo: su cosa ha lavorato ciascuno, e in che proporzione.
 *
 * <p><strong>Nessun permesso nuovo.</strong> Entrambe le rotte stanno su
 * {@code TEMPO_VISUALIZZA_TUTTI}, che dice già «vedi il tempo registrato da
 * tutti»: {@code /api/tempo/utente/{id}} espone a chi lo ha le stesse sessioni
 * con lavoro e task dentro, e questo report ne è il riassunto. Un permesso in
 * più costerebbe una migrazione e una riga nell'editor dei ruoli per
 * esprimere, in cambio, combinazioni che nessuno vuole — il totale senza il
 * dettaglio. È lo stesso ragionamento che {@code TempoController} fa per non
 * inventarsi un permesso sul timer.
 *
 * <p>A differenza di {@code /api/tempo/utente/{id}} qui non si accetta
 * {@code TEMPO_VISUALIZZA_LAVORO}: quel permesso autorizza a vedere il tempo
 * di chi lavora ai propri lavori, mentre questo report è <em>globale</em> per
 * costruzione — somma anche le ore andate sui lavori di cui chi guarda non sa
 * niente. Limitarlo vorrebbe dire ricalcolare ogni percentuale su un
 * denominatore parziale, cioè un report che risponde a una domanda diversa da
 * quella scritta sopra.
 *
 * <p><strong>Due rotte e non un albero unico.</strong> La panoramica deve
 * restare leggera anche su un anno, e la ripartizione completa di tutte le
 * persone sarebbe quasi sempre tempo speso per righe che nessuno apre. Si
 * scende di un livello alla volta: una chiamata per persona espansa. Le task
 * invece arrivano già dentro i lavori, perché sono poche e il terzo livello
 * deve aprirsi senza attesa.
 */
@RestController
@RequestMapping("/api/report")
public class ReportController {

    private final ServizioReport servizio;

    ReportController(ServizioReport servizio) {
        this.servizio = servizio;
    }

    /**
     * Quanto ha registrato ciascuno fra due date, estremi compresi.
     *
     * <p>Le date sono obbligatorie e non hanno un valore di ripiego: un
     * consuntivo senza periodo non vuol dire niente, e un periodo scelto dal
     * server sarebbe un numero che chi legge crede di avere chiesto. Se
     * mancano, Spring risponde 400.
     */
    @GetMapping("/persone")
    @PreAuthorize("hasAuthority('TEMPO_VISUALIZZA_TUTTI')")
    public ReportPersone persone(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate da,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate a) {
        return servizio.persone(da, a);
    }

    /** Su che cosa è andato il tempo di una persona, lavoro per lavoro. */
    @GetMapping("/persone/{utenteId}")
    @PreAuthorize("hasAuthority('TEMPO_VISUALIZZA_TUTTI')")
    public ReportPersona persona(
            @PathVariable Long utenteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate da,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate a) {
        return servizio.persona(utenteId, da, a);
    }
}
