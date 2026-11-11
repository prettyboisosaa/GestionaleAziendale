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
     * La mia settimana, da lunedì a domenica.
     *
     * <p>{@code giorno} è un giorno <em>qualunque</em> della settimana
     * voluta, non il suo lunedì: il calendario in pagina manda il primo
     * giorno che ha in mano e il servizio risale alla settimana. Omesso, è
     * oggi.
     *
     * <p>Sta su {@code TASK_AGGIORNA_STATO} come le altre rotte di chi lavora,
     * e non su {@code TEMPO_VISUALIZZA_*}: quelli servono a leggere il tempo
     * <em>di un altro</em>, e guardare le proprie ore non è un privilegio
     * amministrativo. Per la stessa ragione non si riusa
     * {@code /api/tempo/utente/{id}}, che pure restituirebbe le stesse
     * sessioni — chiederebbe a un dipendente un permesso che non ha e non
     * deve avere.
     */
    @GetMapping("/api/mio-tempo/settimana")
    @PreAuthorize("hasAuthority('TASK_AGGIORNA_STATO')")
    public SettimanaDiTempo miaSettimana(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate giorno,
            @AuthenticationPrincipal UserDetails utente) {
        return servizio.miaSettimana(utente, giorno == null ? LocalDate.now() : giorno);
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

    /**
     * Registra a mano del lavoro già fatto, dal calendario settimanale.
     *
     * <p>Sta sotto {@code /api/lavori/{l}/task/{t}/} e non sotto
     * {@code /api/mio-tempo/} perché <strong>nomina una task</strong>, come
     * avviare e completare: è la task che si registra, e il tempo viene
     * dietro. {@code /api/mio-tempo/...} resta per ciò che riguarda la
     * sessione corrente, dove la task non serve dirla perché ce n'è una sola.
     *
     * <p>Nessun permesso nuovo, con lo stesso argomento di tutte le altre
     * rotte di chi lavora: {@code TASK_AGGIORNA_STATO} è nel pavimento e dice
     * già «aggiornare lo stato delle task a cui è assegnato». Il confine vero
     * — che si scrive solo sulle proprie task — lo verifica il servizio,
     * perché è un dato e un'annotazione non lo consulta.
     */
    @PostMapping("/api/lavori/{idLavoro}/task/{idTask}/sessioni")
    @PreAuthorize("hasAuthority('TASK_AGGIORNA_STATO')")
    public SituazioneTempo registraManuale(
            @PathVariable Long idLavoro,
            @PathVariable Long idTask,
            @Valid @RequestBody RichiestaSessione richiesta,
            @AuthenticationPrincipal UserDetails utente) {
        return servizio.registraManuale(utente, idLavoro, idTask,
                richiesta.avviataIl(), richiesta.chiusaIl());
    }

    /**
     * Registra a mano un'attività già fatta, dal calendario settimanale: una
     * task, oppure una riunione, una manutenzione, una trasferta.
     *
     * <p>Sta sotto {@code /api/mio-tempo/} e non sotto
     * {@code /api/lavori/{l}/task/{t}/} per una ragione precisa: la task
     * <strong>può non esserci</strong>, e un indirizzo non sa nominare una
     * cosa facoltativa. Quello che la rotta per task diceva nel percorso, qui
     * lo dice il corpo, e lo dice anche quando non c'è niente da dire.
     *
     * <p>La rotta per task resta: continua a valere per quello che sapeva
     * fare, e dentro il servizio le due strade confluiscono subito.
     *
     * <p>Nessun permesso nuovo, con lo stesso argomento di tutte le altre
     * rotte di chi lavora. Registrare un'attività che non è una task non è un
     * potere in più di registrarne una che lo è: è la stessa giornata,
     * raccontata meglio. Il confine sui dati — la task propria, il lavoro
     * visibile — lo verifica il servizio.
     */
    @PostMapping("/api/mio-tempo/sessioni")
    @PreAuthorize("hasAuthority('TASK_AGGIORNA_STATO')")
    public SituazioneTempo registraAttivita(
            @Valid @RequestBody RichiestaAttivita richiesta,
            @AuthenticationPrincipal UserDetails utente) {
        return servizio.registraAttivita(utente, richiesta);
    }

    /** Accetta una chiusura d'ufficio così com'è. */
    @PostMapping("/api/mio-tempo/sessioni/{id}/conferma")
    @PreAuthorize("hasAuthority('TASK_AGGIORNA_STATO')")
    public SituazioneTempo conferma(@PathVariable Long id,
                                    @AuthenticationPrincipal UserDetails utente) {
        return servizio.conferma(utente, id);
    }

    /**
     * Corregge una propria sessione — gli estremi, il tipo di attività, il
     * lavoro citato, la descrizione — entro la settimana, oppure sempre se è
     * una chiusura d'ufficio ancora da confermare.
     *
     * <p>La task non si cambia da qui: il perché sta su
     * {@link RichiestaCorrezione}.
     */
    @PutMapping("/api/mio-tempo/sessioni/{id}")
    @PreAuthorize("hasAuthority('TASK_AGGIORNA_STATO')")
    public SituazioneTempo correggi(@PathVariable Long id,
                                    @Valid @RequestBody RichiestaCorrezione richiesta,
                                    @AuthenticationPrincipal UserDetails utente) {
        return servizio.correggi(utente, id, richiesta);
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
