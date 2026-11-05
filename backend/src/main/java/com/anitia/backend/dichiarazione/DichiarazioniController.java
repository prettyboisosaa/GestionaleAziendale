package com.anitia.backend.dichiarazione;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La dichiarazione giornaliera: dove si registrano le ore lavorate.
 *
 * <p>Gli indirizzi sono indicizzati sulla <strong>data</strong> e non sull'id
 * della dichiarazione, perché è così che li usa chi compila: «la mia giornata
 * di ieri», non «la dichiarazione 417». E perché la giornata di ieri può non
 * esistere ancora — una GET su una data risponde con una giornata vuota, una
 * GET su un id inesistente dovrebbe dare 404.
 *
 * <p>Nessuna rotta accetta l'id di un'altra persona per le proprie
 * operazioni: si scrive solo sulla propria giornata, e chi sia lo dice la
 * sessione. Le dichiarazioni altrui si leggono, e solo con i permessi del
 * team o di tutta l'azienda.
 */
@RestController
@RequestMapping("/api/dichiarazioni")
class DichiarazioniController {

    private final ServizioDichiarazioni servizio;

    DichiarazioniController(ServizioDichiarazioni servizio) {
        this.servizio = servizio;
    }

    /**
     * La mia giornata, con le voci, il tetto di ore e l'elenco delle task su
     * cui posso scrivere.
     *
     * <p>Non crea niente: se per quel giorno non ho dichiarato nulla,
     * {@code id} è nullo.
     */
    @GetMapping("/{data}")
    @PreAuthorize("hasAuthority('DICHIARAZIONI_COMPILA')")
    public Giornata mia(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                        LocalDate data,
                        @AuthenticationPrincipal UserDetails utente) {
        return servizio.mia(utente, data);
    }

    /** Le mie giornate di un periodo, con il totale di ciascuna. */
    @GetMapping
    @PreAuthorize("hasAuthority('DICHIARAZIONI_COMPILA')")
    public List<RigaGiorno> mie(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate da,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate a,
            @AuthenticationPrincipal UserDetails utente) {
        return servizio.mieDelPeriodo(utente, da, a);
    }

    /**
     * Le giornate di un'altra persona. Chi ha solo il permesso sul team vede
     * i propri dipendenti, e il limite lo verifica il servizio: dipende da
     * {@code membro_team}, che un'annotazione non consulta.
     */
    @GetMapping("/utente/{utenteId}")
    @PreAuthorize("hasAnyAuthority('DICHIARAZIONI_VISUALIZZA_TUTTE',"
                + "'DICHIARAZIONI_VISUALIZZA_TEAM')")
    public List<RigaGiorno> diUnaPersona(
            @PathVariable Long utenteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate da,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate a,
            @AuthenticationPrincipal UserDetails utente) {
        return servizio.diUnaPersona(utente, utenteId, da, a);
    }

    /**
     * Registra ore su una task. Crea la dichiarazione del giorno se è la
     * prima voce.
     *
     * <p>Risponde 200 e non 201: quello che torna è la giornata intera
     * aggiornata — totale e ore residue compresi — non la voce appena creata,
     * perché è la giornata che il frontend deve ridisegnare.
     */
    @PostMapping("/{data}/voci")
    @PreAuthorize("hasAuthority('DICHIARAZIONI_COMPILA')")
    public Giornata registraOre(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                LocalDate data,
                                @Valid @RequestBody RichiestaVoce richiesta,
                                @AuthenticationPrincipal UserDetails utente) {
        return servizio.registraOre(utente, data, richiesta);
    }

    @PutMapping("/{data}/voci/{idVoce}")
    @PreAuthorize("hasAuthority('DICHIARAZIONI_COMPILA')")
    public Giornata correggiVoce(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                 LocalDate data,
                                 @PathVariable Long idVoce,
                                 @Valid @RequestBody RichiestaVoce richiesta,
                                 @AuthenticationPrincipal UserDetails utente) {
        return servizio.correggiVoce(utente, data, idVoce, richiesta);
    }

    @DeleteMapping("/{data}/voci/{idVoce}")
    @PreAuthorize("hasAuthority('DICHIARAZIONI_COMPILA')")
    public Giornata togliVoce(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                              LocalDate data,
                              @PathVariable Long idVoce,
                              @AuthenticationPrincipal UserDetails utente) {
        return servizio.togliVoce(utente, data, idVoce);
    }

    /** Ferie, permesso, malattia o giornata lavorata. */
    @PostMapping("/{data}/tipo")
    @PreAuthorize("hasAuthority('DICHIARAZIONI_COMPILA')")
    public Giornata cambiaTipo(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                               LocalDate data,
                               @Valid @RequestBody RichiestaTipoGiornata richiesta,
                               @AuthenticationPrincipal UserDetails utente) {
        return servizio.cambiaTipo(utente, data, richiesta.tipoGiornata());
    }

    /**
     * Consegna la giornata. Resta correggibile per i giorni lavorativi che
     * dice il parametro {@code dichiarazione.giorni_modifica}.
     */
    @PostMapping("/{data}/invia")
    @PreAuthorize("hasAuthority('DICHIARAZIONI_COMPILA')")
    public Giornata invia(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                          LocalDate data,
                          @AuthenticationPrincipal UserDetails utente) {
        return servizio.invia(utente, data);
    }
}
