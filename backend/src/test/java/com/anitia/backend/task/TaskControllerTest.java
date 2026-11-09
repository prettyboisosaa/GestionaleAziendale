package com.anitia.backend.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anitia.backend.sicurezza.ConfigurazioneSicurezza;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * Il contratto delle rotte delle task.
 *
 * <p>Il valore sta nei 403 della creazione: sono la prova che la prima fix è
 * stata interpretata come concordato — aprire un lavoro non è gestirne le task.
 *
 * <p>Nota che il servizio è simulato, quindi qui si verifica che i permessi
 * dichiarati sulle rotte funzionino e che gli errori del servizio arrivino al
 * client col codice giusto. Le regole che dipendono dai dati — essere il
 * responsabile, il team — sono verificate end-to-end contro il database.
 */
@WebMvcTest(TaskController.class)
@Import(ConfigurazioneSicurezza.class)
class TaskControllerTest {

    /** Hash di "Password1!", lo stesso dei dati di sviluppo. */
    private static final String HASH =
            "{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq";

    private static final String EMAIL = "admin@gestionale.local";
    private static final String TASK = "/api/lavori/1/task";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDetailsService dettagliUtente;

    @MockitoBean
    private ServizioTask servizio;

    /** Istanza nuova a ogni chiamata del mock: vedi gli altri test. */
    private MockHttpSession sessioneCon(String... permessi) {
        given(dettagliUtente.loadUserByUsername(EMAIL)).willAnswer(invocazione ->
                User.withUsername(EMAIL).password(HASH).authorities(permessi).build());

        UserDetails collegato = User.withUsername(EMAIL).password(HASH)
                .authorities(permessi).build();
        SecurityContext contesto = SecurityContextHolder.createEmptyContext();
        contesto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                collegato, null, collegato.getAuthorities()));

        MockHttpSession sessione = new MockHttpSession();
        sessione.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contesto);
        return sessione;
    }

    private static String corpoTask(String titolo, String inizio, String scadenza, String stima) {
        return "{\"titolo\":\"" + titolo + "\",\"descrizione\":null,"
                + "\"dataInizio\":\"" + inizio + "\",\"scadenza\":\"" + scadenza + "\","
                + "\"stimaOre\":" + stima + "}";
    }

    /** Stimate 8 ore, allocate 8, nessuna ancora consumata: niente sforamento. */
    private static RigaTask riga() {
        return new RigaTask(7L, "Analisi requisiti", null,
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1),
                new BigDecimal("8.00"), new BigDecimal("8.00"), BigDecimal.ZERO, false,
                StatoTask.ATTIVO,
                List.of(new Assegnatario(4L, "Giulia", "Verdi")));
    }

    // --- lettura ------------------------------------------------------------

    @Test
    void letturaSenzaSessioneRisponde401() throws Exception {
        mockMvc.perform(get(TASK))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void letturaSenzaNessunPermessoSuiLavoriRisponde403() throws Exception {
        mockMvc.perform(get(TASK).session(sessioneCon("ASSENZE_GESTISCI")))
                .andExpect(status().isForbidden());
    }

    /** Per leggere basta poter vedere il lavoro: è la prima fix. */
    @Test
    void perLeggereBastaIlPermessoSuiPropriLavori() throws Exception {
        given(servizio.delLavoro(any(), anyLong())).willReturn(List.of(riga()));

        mockMvc.perform(get(TASK).session(sessioneCon("LAVORI_VISUALIZZA_PROPRI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].titolo").value("Analisi requisiti"))
                .andExpect(jsonPath("$[0].assegnatari[0].cognome").value("Verdi"));
    }

    // --- creazione ----------------------------------------------------------

    /**
     * Il cuore della prima fix: vedere il lavoro non basta per aggiungerci
     * task. Chi ha solo i permessi di visualizzazione riceve 403.
     */
    @Test
    void creazioneConSoliPermessiDiVisualizzazioneRisponde403() throws Exception {
        mockMvc.perform(post(TASK)
                        .session(sessioneCon("LAVORI_VISUALIZZA_TUTTI",
                                "TASK_VISUALIZZA_ASSEGNATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoTask("Nuova", "2027-01-01", "2027-02-01", "8")))
                .andExpect(status().isForbidden());
    }

    @Test
    void creazioneConGestisciTuttiRisponde201() throws Exception {
        given(servizio.crea(any(), anyLong(), any())).willReturn(riga());

        mockMvc.perform(post(TASK).session(sessioneCon("TASK_GESTISCI_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoTask("Nuova", "2027-01-01", "2027-02-01", "8")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.stato").value("ATTIVO"));
    }

    /** Con _PROPRI su un lavoro di cui non si è responsabile: 403 dal servizio. */
    @Test
    void creazioneConGestisciPropriSuUnLavoroAltruiRisponde403() throws Exception {
        given(servizio.crea(any(), anyLong(), any())).willThrow(
                new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Per gestire le task del lavoro LAV-001 devi esserne il responsabile, "
                                + "oppure avere il permesso su tutti i lavori."));

        mockMvc.perform(post(TASK).session(sessioneCon("TASK_GESTISCI_PROPRI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoTask("Nuova", "2027-01-01", "2027-02-01", "8")))
                .andExpect(status().isForbidden());
    }

    @Test
    void creazioneSenzaTitoloRisponde400() throws Exception {
        mockMvc.perform(post(TASK).session(sessioneCon("TASK_GESTISCI_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoTask("", "2027-01-01", "2027-02-01", "8")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creazioneConStimaNegativaRisponde400() throws Exception {
        mockMvc.perform(post(TASK).session(sessioneCon("TASK_GESTISCI_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoTask("Nuova", "2027-01-01", "2027-02-01", "-3")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void scadenzaPrimaDellInizioRisponde400() throws Exception {
        given(servizio.crea(any(), anyLong(), any())).willThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La scadenza non può precedere la data di inizio."));

        mockMvc.perform(post(TASK).session(sessioneCon("TASK_GESTISCI_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoTask("Nuova", "2027-02-01", "2027-01-01", "8")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creazioneInUnLavoroNonPiuInCorsoRisponde409() throws Exception {
        given(servizio.crea(any(), anyLong(), any())).willThrow(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "Il lavoro LAV-003 non è più in corso: non accetta nuove task."));

        mockMvc.perform(post(TASK).session(sessioneCon("TASK_GESTISCI_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoTask("Nuova", "2027-01-01", "2027-02-01", "8")))
                .andExpect(status().isConflict());
    }

    // --- chi si può assegnare -----------------------------------------------

    @Test
    void candidatiSenzaIPermessiDiGestioneRisponde403() throws Exception {
        mockMvc.perform(get(TASK + "/assegnabili")
                        .session(sessioneCon("LAVORI_VISUALIZZA_TUTTI")))
                .andExpect(status().isForbidden());
    }

    @Test
    void candidatiConIPermessiDiGestioneRestituisceLElenco() throws Exception {
        given(servizio.assegnabili(any(), anyLong()))
                .willReturn(List.of(new Assegnatario(4L, "Giulia", "Verdi")));

        mockMvc.perform(get(TASK + "/assegnabili").session(sessioneCon("TASK_GESTISCI_PROPRI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cognome").value("Verdi"));
    }

    // --- assegnazioni -------------------------------------------------------

    @Test
    void assegnazioneSenzaIPermessiDiGestioneRisponde403() throws Exception {
        mockMvc.perform(post(TASK + "/1/assegnazioni")
                        .session(sessioneCon("LAVORI_VISUALIZZA_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"utenteId\":4}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void assegnazioneRiuscitaRisponde204() throws Exception {
        mockMvc.perform(post(TASK + "/1/assegnazioni")
                        .session(sessioneCon("TASK_GESTISCI_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"utenteId\":4}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void assegnareDueVolteLaStessaPersonaRisponde409() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                "Verdi Giulia è già assegnata a questa task."))
                .given(servizio).assegna(any(), anyLong(), anyLong(), anyLong());

        mockMvc.perform(post(TASK + "/1/assegnazioni")
                        .session(sessioneCon("TASK_GESTISCI_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"utenteId\":4}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Verdi Giulia è già assegnata a questa task."));
    }

    /**
     * Una task è assegnabile a chiunque sia attivo: non c'è nessun elenco di
     * sottoposti da consultare, e con {@code TASK_GESTISCI_PROPRI} si assegna
     * a una persona qualunque, purché nel proprio lavoro.
     */
    @Test
    void assegnareAUnaPersonaQualunqueRiesce() throws Exception {
        mockMvc.perform(post(TASK + "/1/assegnazioni")
                        .session(sessioneCon("TASK_GESTISCI_PROPRI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"utenteId\":6}"))
                .andExpect(status().isNoContent());

        then(servizio).should().assegna(any(), eq(1L), eq(1L), eq(6L));
    }

    /**
     * Il confine che resta: chi ha solo {@code TASK_GESTISCI_PROPRI} assegna
     * nei lavori di cui è responsabile, e il servizio lo verifica.
     */
    @Test
    void assegnareInUnLavoroNonProprioRisponde403() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Per gestire le task del lavoro LAV-001 devi esserne il responsabile, "
                        + "oppure avere il permesso su tutti i lavori."))
                .given(servizio).assegna(any(), anyLong(), anyLong(), anyLong());

        mockMvc.perform(post(TASK + "/1/assegnazioni")
                        .session(sessioneCon("TASK_GESTISCI_PROPRI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"utenteId\":6}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void assegnazioneSenzaUtenteIdRisponde400() throws Exception {
        mockMvc.perform(post(TASK + "/1/assegnazioni")
                        .session(sessioneCon("TASK_GESTISCI_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    /** Una task di un altro lavoro non si raggiunge cambiando l'indirizzo. */
    @Test
    void taskCheNonAppartieneAlLavoroRisponde404() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Task non trovata in questo lavoro."))
                .given(servizio).assegna(any(), anyLong(), anyLong(), anyLong());

        mockMvc.perform(post("/api/lavori/3/task/1/assegnazioni")
                        .session(sessioneCon("TASK_GESTISCI_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"utenteId\":4}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rimozioneRisponde204() throws Exception {
        mockMvc.perform(delete(TASK + "/1/assegnazioni/4")
                        .session(sessioneCon("TASK_GESTISCI_TUTTI")))
                .andExpect(status().isNoContent());
    }

    @Test
    void rimozioneSenzaIPermessiDiGestioneRisponde403() throws Exception {
        mockMvc.perform(delete(TASK + "/1/assegnazioni/4")
                        .session(sessioneCon("TASK_VISUALIZZA_ASSEGNATE")))
                .andExpect(status().isForbidden());
    }

    // --- cambio di stato ----------------------------------------------------
    //
    // La rotta che prima non c'era: TASK_AGGIORNA_STATO era nel catalogo dei
    // permessi dalla prima migrazione, ma nessun indirizzo lo usava, e
    // Task.cambiaStato non aveva chiamanti. È il momento in cui le ore
    // residue di una task tornano nel pool del lavoro.

    @Test
    void cambioStatoSenzaSessioneRisponde401() throws Exception {
        mockMvc.perform(post(TASK + "/1/stato")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stato\":\"IN_CORSO\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** Chi ci lavora può aggiornare lo stato: è il senso di TASK_AGGIORNA_STATO. */
    @Test
    void cambioStatoConIlPermessoDellAssegnatarioRisponde200() throws Exception {
        given(servizio.cambiaStato(any(), any(), any(), any())).willReturn(riga());

        mockMvc.perform(post(TASK + "/1/stato").session(sessioneCon("TASK_AGGIORNA_STATO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stato\":\"IN_CORSO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titolo").value("Analisi requisiti"));
    }

    @Test
    void cambioStatoSenzaNessunoDeiTrePermessiRisponde403() throws Exception {
        mockMvc.perform(post(TASK + "/1/stato").session(sessioneCon("TASK_VISUALIZZA_ASSEGNATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stato\":\"IN_CORSO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cambioStatoSenzaStatoRisponde400() throws Exception {
        mockMvc.perform(post(TASK + "/1/stato").session(sessioneCon("TASK_GESTISCI_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stato\":null}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Il 403 di chi ha TASK_AGGIORNA_STATO ma non è assegnato alla task: il
     * limite è nella descrizione del permesso, e lo applica il servizio
     * perché dipende dai dati.
     */
    @Test
    void cambioStatoDiUnaTaskNonPropriaRisponde403() throws Exception {
        given(servizio.cambiaStato(any(), any(), any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Puoi cambiare lo stato solo delle task a cui sei assegnato, oppure "
                                + "di quelle dei lavori di cui sei responsabile."));

        mockMvc.perform(post(TASK + "/1/stato").session(sessioneCon("TASK_AGGIORNA_STATO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stato\":\"COMPLETATO\"}"))
                .andExpect(status().isForbidden());
    }

    /** Una task chiusa non si riapre: le sue ore sono già tornate nel pool. */
    @Test
    void riaperturaDiUnaTaskCompletataRisponde409() throws Exception {
        given(servizio.cambiaStato(any(), any(), any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "La task «Analisi requisiti» è completata e non si riapre: le sue ore "
                                + "residue sono già tornate nel pool del lavoro. Crea una task "
                                + "nuova."));

        mockMvc.perform(post(TASK + "/1/stato").session(sessioneCon("TASK_GESTISCI_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stato\":\"IN_CORSO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "La task «Analisi requisiti» è completata e non si riapre: le sue ore "
                                + "residue sono già tornate nel pool del lavoro. Crea una task "
                                + "nuova."));
    }
}
