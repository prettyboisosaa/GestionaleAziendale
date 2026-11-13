package com.anitia.backend.utente;

import com.anitia.backend.autenticazione.RichiestaResetPassword;
import com.anitia.backend.autenticazione.ServizioPassword;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestione degli utenti.
 *
 * <p>Qui vivono i controlli di autorizzazione veri. La catena di filtri
 * pretende soltanto che l'utente sia autenticato: a decidere che cosa gli e'
 * permesso fare sono i {@code @PreAuthorize} di questi metodi, che leggono i
 * permessi correnti perche' vengono riletti dal database a ogni richiesta.
 */
@RestController
@RequestMapping("/api/utenti")
class UtentiController {

    private final ServizioUtenti servizio;
    private final ServizioPermessiUtente servizioPermessi;
    private final ServizioPassword password;

    UtentiController(ServizioUtenti servizio, ServizioPermessiUtente servizioPermessi,
                     ServizioPassword password) {
        this.servizio = servizio;
        this.servizioPermessi = servizioPermessi;
        this.password = password;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('UTENTI_VISUALIZZA')")
    public List<RigaUtente> elenco() {
        return servizio.elenco();
    }

    /**
     * Servono due permessi: creare un utente significa assegnargli dei ruoli,
     * e {@link RichiestaNuovoUtente} ne pretende almeno uno.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('UTENTI_CREA') and hasAuthority('RUOLI_ASSEGNA')")
    public RigaUtente crea(@Valid @RequestBody RichiestaNuovoUtente richiesta,
                           Authentication autenticazione) {
        return servizio.crea(richiesta, autenticazione.getName());
    }

    /**
     * Disattiva o riattiva un utente. Il corpo è
     * {@code {"attivo": false}} oppure {@code {"attivo": true}}.
     *
     * <p>{@code PATCH} e non {@code PUT}: cambia un campo solo e lascia gli
     * altri dove sono. {@code UTENTI_DISATTIVA} è un permesso a sé, distinto
     * da {@code UTENTI_MODIFICA}, perché chiudere un'utenza non è modificare
     * un dato: toglie l'accesso al gestionale.
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('UTENTI_DISATTIVA')")
    public RigaUtente cambiaAttivazione(@PathVariable Long id,
                                        @Valid @RequestBody RichiestaAttivazione richiesta,
                                        Authentication autenticazione) {
        return servizio.cambiaAttivazione(id, richiesta.attivo(), autenticazione.getName());
    }

    /**
     * Azzera la password di questa persona e basta: non la comunica a
     * nessuno, perché il gestionale non manda email. Chi la assegna la
     * consegna a voce, e chi la riceve la cambia da «Profilo».
     *
     * <p>Sta qui, fra le rotte di amministrazione, e non accanto al cambio
     * password in {@code /api/autenticazione}: quella è una cosa che ciascuno
     * fa per sé, questa è una cosa che si fa a qualcun altro. La regola e il
     * perché del permesso separato stanno in {@code ServizioPassword.azzera}.
     *
     * <p>Risponde <strong>204</strong>: non c'è niente da restituire, e men
     * che meno la password.
     */
    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('UTENTI_RESET_PASSWORD')")
    public void azzeraPassword(@PathVariable Long id,
                               @Valid @RequestBody RichiestaResetPassword richiesta,
                               Authentication autenticazione) {
        password.azzera(id, richiesta, autenticazione.getName());
    }

    /**
     * Tutto il catalogo dei permessi, con l'origine di ciascuno per questa
     * persona: serve a disegnare la schermata, quindi escono anche quelli che
     * non ha.
     */
    @GetMapping("/{id}/permessi")
    @PreAuthorize("hasAuthority('UTENTI_VISUALIZZA') and hasAuthority('RUOLI_ASSEGNA')")
    public List<PermessoUtente> permessi(@PathVariable Long id) {
        return servizioPermessi.permessiDi(id);
    }

    /**
     * Alza o abbassa un permesso di questa persona. L'effetto è immediato:
     * {@code FiltroUtenteAggiornato} rilegge i permessi a ogni richiesta,
     * quindi non serve che lei rifaccia l'accesso.
     */
    @PutMapping("/{id}/permessi/{codice}")
    @PreAuthorize("hasAuthority('RUOLI_ASSEGNA')")
    public PermessoUtente impostaPermesso(@PathVariable Long id,
                                          @PathVariable String codice,
                                          @Valid @RequestBody RichiestaPermesso richiesta,
                                          Authentication autenticazione) {
        return servizioPermessi.imposta(id, codice, richiesta.effettivo(),
                autenticazione.getName());
    }

    /** Toglie lo scostamento: il permesso torna a dipendere dai ruoli. */
    @DeleteMapping("/{id}/permessi/{codice}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RUOLI_ASSEGNA')")
    public void ripristinaPermesso(@PathVariable Long id,
                                   @PathVariable String codice,
                                   Authentication autenticazione) {
        servizioPermessi.ripristina(id, codice, autenticazione.getName());
    }
}
