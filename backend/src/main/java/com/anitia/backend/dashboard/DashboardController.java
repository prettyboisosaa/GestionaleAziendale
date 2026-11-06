package com.anitia.backend.dashboard;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La dashboard del dipendente: due letture, entrambe sulle proprie cose.
 *
 * <ul>
 *   <li>{@code GET /api/mia-dashboard} — i conteggi delle card e le task.
 *   <li>{@code GET /api/mio-calendario?da=…&a=…} — gli eventi dell'intervallo
 *       visibile.
 * </ul>
 *
 * <p>I nomi seguono {@code /api/mie-task} e {@code /api/mie-assenze}, che sono
 * già la convenzione del gestionale per «le mie cose»: il documento scriveva
 * {@code /api/me/dashboard}, ma una terza forma per la stessa idea avrebbe
 * costretto a ricordare quale rotta usa quale.
 *
 * <p>Il permesso è {@code TASK_VISUALIZZA_ASSEGNATE}, non uno nuovo. Il
 * documento ne prevedeva uno dedicato, {@code dashboard.view}, ma qui non
 * distinguerebbe niente: la pagina mostra le proprie task e le proprie
 * assenze, e chi può vedere le prime non ha nulla da guadagnare a vederle in
 * un'altra pagina. Un permesso che si concede sempre insieme a un altro è un
 * permesso che non esiste.
 */
@RestController
class DashboardController {

    private final ServizioDashboard servizio;

    DashboardController(ServizioDashboard servizio) {
        this.servizio = servizio;
    }

    @GetMapping("/api/mia-dashboard")
    @PreAuthorize("hasAuthority('TASK_VISUALIZZA_ASSEGNATE')")
    public RiepilogoDashboard riepilogo(@AuthenticationPrincipal UserDetails utente) {
        return servizio.riepilogo(utente);
    }

    /**
     * Gli eventi fra due date, estremi compresi.
     *
     * <p>Si chiede l'intervallo invece di tutto: il calendario ricarica a ogni
     * cambio di settimana o di mese, e una risposta che portasse l'anno intero
     * crescerebbe senza che nessuno se ne accorga.
     */
    @GetMapping("/api/mio-calendario")
    @PreAuthorize("hasAuthority('TASK_VISUALIZZA_ASSEGNATE')")
    public List<EventoCalendario> calendario(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate da,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate a,
            @AuthenticationPrincipal UserDetails utente) {
        return servizio.calendario(utente, da, a);
    }
}
