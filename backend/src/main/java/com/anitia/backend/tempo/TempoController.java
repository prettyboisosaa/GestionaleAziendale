package com.anitia.backend.tempo;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Il tempo lavorato: avvia, pausa, completa.
 *
 * <p><strong>Nessun permesso nuovo.</strong> Le rotte di chi lavora stanno su
 * {@code TASK_AGGIORNA_STATO}, che è nel pavimento e dice già «aggiornare lo
 * stato delle task a cui è assegnato»: avviare, mettere in pausa e completare
 * <em>sono</em> quello. Un permesso che si concede sempre insieme a un altro è
 * un permesso che non esiste — è la stessa ragione per cui la dashboard non ne
 * ha uno proprio.
 *
 * <p>Il confine vero non è nell'annotazione ma nei dati: si registra tempo solo
 * sulle task a cui si è assegnati, e quello lo verifica il servizio. Nemmeno
 * un amministratore può avviare il timer su una task di qualcun altro:
 * gestire una task e averci lavorato sono due cose diverse.
 *
 * <p>Tutte le scritture rispondono con la <strong>situazione intera</strong> di
 * chi chiede — cosa sta scorrendo, i minuti di oggi, cosa resta da confermare —
 * perché è quello che la barra in testata e la dashboard ridisegnano, e averlo
 * nella risposta evita una seconda chiamata dopo ogni clic.
 */
@RestController
public class TempoController {

    private final ServizioTempo servizio;

    TempoController(ServizioTempo servizio) {
        this.servizio = servizio;
    }

    /** Che cosa sto facendo adesso, quanto ho fatto oggi, cosa devo confermare. */
    @GetMapping("/api/mio-tempo")
    @PreAuthorize("hasAuthority('TASK_AGGIORNA_STATO')")
    public SituazioneTempo mio(@AuthenticationPrincipal UserDetails utente) {
        return servizio.miaSituazione(utente);
    }

    /**
     * Comincia a lavorare su una task. Se un'altra stava scorrendo, la chiude.
     */
    @PostMapping("/api/lavori/{idLavoro}/task/{idTask}/avvia")
    @PreAuthorize("hasAuthority('TASK_AGGIORNA_STATO')")
    public SituazioneTempo avvia(@PathVariable Long idLavoro,
                                 @PathVariable Long idTask,
                                 @AuthenticationPrincipal UserDetails utente) {
        return servizio.avvia(utente, idLavoro, idTask);
    }

    /**
     * Smette di lavorare. Non serve dire a quale task: è quella che sta
     * scorrendo, e ce n'è una sola.
     */
    @PostMapping("/api/mio-tempo/pausa")
    @PreAuthorize("hasAuthority('TASK_AGGIORNA_STATO')")
    public SituazioneTempo pausa(@AuthenticationPrincipal UserDetails utente) {
        return servizio.pausa(utente);
    }

    /** Ha finito: chiude la sessione e poi la task. */
    @PostMapping("/api/lavori/{idLavoro}/task/{idTask}/completa")
    @PreAuthorize("hasAuthority('TASK_AGGIORNA_STATO')")
    public SituazioneTempo completa(@PathVariable Long idLavoro,
                                    @PathVariable Long idTask,
                                    @AuthenticationPrincipal UserDetails utente) {
        return servizio.completa(utente, idLavoro, idTask);
    }

    /** Accetta una chiusura d'ufficio così com'è. */
    @PostMapping("/api/mio-tempo/sessioni/{id}/conferma")
    @PreAuthorize("hasAuthority('TASK_AGGIORNA_STATO')")
    public SituazioneTempo conferma(@PathVariable Long id,
                                    @AuthenticationPrincipal UserDetails utente) {
        return servizio.conferma(utente, id);
    }

    /**
     * Corregge gli estremi di una propria sessione: entro la giornata, oppure
     * sempre se è una chiusura d'ufficio ancora da confermare.
     */
    @PutMapping("/api/mio-tempo/sessioni/{id}")
    @PreAuthorize("hasAuthority('TASK_AGGIORNA_STATO')")
    public SituazioneTempo correggi(@PathVariable Long id,
                                    @Valid @RequestBody RichiestaCorrezione richiesta,
                                    @AuthenticationPrincipal UserDetails utente) {
        return servizio.correggi(utente, id, richiesta.avviataIl(), richiesta.chiusaIl());
    }

    /**
     * Annulla una propria sessione. Non la cancella: la riga resta, fuori dai
     * conti, come ogni cosa in questo gestionale.
     */
    @PostMapping("/api/mio-tempo/sessioni/{id}/annulla")
    @PreAuthorize("hasAuthority('TASK_AGGIORNA_STATO')")
    public SituazioneTempo annulla(@PathVariable Long id,
                                   @AuthenticationPrincipal UserDetails utente) {
        return servizio.annulla(utente, id);
    }

    /**
     * Il tempo di un'altra persona. Chi ha solo il permesso sul lavoro vede
     * chi ha task nei lavori di cui è responsabile, e il limite lo verifica il
     * servizio: dipende dalle assegnazioni, che un'annotazione non consulta.
     */
    @GetMapping("/api/tempo/utente/{utenteId}")
    @PreAuthorize("hasAnyAuthority('TEMPO_VISUALIZZA_TUTTI','TEMPO_VISUALIZZA_LAVORO')")
    public TempoDiUnaPersona diUnaPersona(
            @PathVariable Long utenteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate da,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate a,
            @AuthenticationPrincipal UserDetails utente) {
        return servizio.diUnaPersona(utente, utenteId, da, a);
    }

    /**
     * Chi ha chiusure d'ufficio in attesa, e quante.
     *
     * <p>Serve a sapere a chi chiedere: l'amministratore non può correggerle
     * lui — le ore sono di chi le ha lavorate — quindi la sola cosa utile è
     * l'elenco di chi deve guardarle.
     */
    @GetMapping("/api/tempo/da-confermare")
    @PreAuthorize("hasAuthority('TEMPO_VISUALIZZA_TUTTI')")
    public List<SessioneTaskRepository.DaConfermare> daConfermare() {
        return servizio.daConfermare();
    }
}
