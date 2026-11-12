package com.anitia.backend.lavoro;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anitia.backend.ore.RigaMovimento;
import com.anitia.backend.ore.TipoMovimento;
import com.anitia.backend.sicurezza.ConfigurazioneSicurezza;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * Le autorizzazioni e il contratto delle rotte dei lavori. Come per gli
 * utenti, il valore sta nei 403: provano che i permessi vengono applicati.
 */
@WebMvcTest(LavoriController.class)
@Import(ConfigurazioneSicurezza.class)
class LavoriControllerTest {

    /** Hash di "Password1!", lo stesso dei dati di sviluppo. */
    private static final String HASH =
            "{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq";

    private static final String EMAIL = "admin@gestionale.local";
    private static final String LAVORI = "/api/lavori";
    private static final String RESPONSABILI = "/api/lavori/responsabili";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDetailsService dettagliUtente;

    @MockitoBean
    private ServizioLavori servizio;

    /**
     * Non lo usa nessuna prova di questa classe, e serve comunque.
     *
     * <p>{@code @WebMvcTest} costruisce il controller vero, quindi ogni
     * collaboratore del suo costruttore deve esistere nel contesto: senza
     * questo finto, le trenta prove qui sotto non fallivano una per una —
     * cadeva il contesto, e con lui tutta la classe.
     *
     * <p>I KPI hanno la loro rotta e nessun test: l'elenco e il dettaglio
     * dei lavori non li toccano.
     */
    @MockitoBean
    private ServizioKpiLavoro kpi;

    /**
     * Una sessione con un utente collegato e i permessi indicati. Il finto
     * {@code UserDetailsService} deve restituire un'istanza nuova a ogni
     * chiamata, perché {@code ProviderManager} azzera le credenziali del
     * principal, e perché {@code FiltroUtenteAggiornato} rilegge l'utente a
     * ogni richiesta: sono quelle authority a contare.
     */
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

    private static String corpo(String stato, String dataFine, String responsabileId) {
        return "{\"nome\":\"Portale fornitori\",\"descrizione\":null,"
                + "\"dataInizio\":\"2027-01-01\",\"dataFinePrevista\":\"" + dataFine + "\","
                + "\"stato\":\"" + stato + "\",\"responsabileId\":" + responsabileId + "}";
    }

    /**
     * Un lavoro appena creato: monte ore fissato, niente ancora allocato né
     * consumato, quindi tutte le 120 ore sono libere.
     */
    private static RigaLavoro riga() {
        return new RigaLavoro(4L, "LAV-004", "Portale fornitori", null,
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30),
                StatoLavoro.ATTIVO, null, 2L, "Bianchi Laura",
                new BigDecimal("120.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("120.00"), 0L, 0L, 0L, 0L);
    }

    // --- elenco -------------------------------------------------------------

    @Test
    void elencoSenzaSessioneRisponde401() throws Exception {
        mockMvc.perform(get(LAVORI))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elencoSenzaNessunPermessoDiVisualizzazioneRisponde403() throws Exception {
        mockMvc.perform(get(LAVORI).session(sessioneCon("ASSENZE_GESTISCI")))
                .andExpect(status().isForbidden());
    }

    @Test
    void elencoBastaIlPermessoSuiPropriLavori() throws Exception {
        given(servizio.elenco(any(), anyBoolean())).willReturn(List.of(riga()));

        mockMvc.perform(get(LAVORI).session(sessioneCon("LAVORI_VISUALIZZA_PROPRI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codice").value("LAV-004"))
                .andExpect(jsonPath("$[0].responsabile").value("Bianchi Laura"));
    }

    // --- candidati responsabile ---------------------------------------------

    @Test
    void responsabiliSenzaIlPermessoRisponde403() throws Exception {
        mockMvc.perform(get(RESPONSABILI).session(sessioneCon("LAVORI_CREA")))
                .andExpect(status().isForbidden());
    }

    @Test
    void responsabiliConIlPermessoRestituisceICandidati() throws Exception {
        given(servizio.responsabiliPossibili())
                .willReturn(List.of(new CandidatoResponsabile(2L, "Laura", "Bianchi")));

        mockMvc.perform(get(RESPONSABILI).session(sessioneCon("LAVORI_ASSEGNA_RESPONSABILE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cognome").value("Bianchi"));
    }

    // --- creazione ----------------------------------------------------------

    @Test
    void creazioneSenzaSessioneRisponde401() throws Exception {
        mockMvc.perform(post(LAVORI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("ATTIVO", "2027-06-30", "2")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creazioneSenzaIlPermessoRisponde403() throws Exception {
        mockMvc.perform(post(LAVORI).session(sessioneCon("LAVORI_VISUALIZZA_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("ATTIVO", "2027-06-30", "2")))
                .andExpect(status().isForbidden());
    }

    @Test
    void creazioneConIlPermessoRisponde201ConIlCodiceGenerato() throws Exception {
        given(servizio.crea(any(), any())).willReturn(riga());

        mockMvc.perform(post(LAVORI).session(sessioneCon("LAVORI_CREA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("ATTIVO", "2027-06-30", "2")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codice").value("LAV-004"))
                .andExpect(jsonPath("$.stato").value("ATTIVO"));
    }

    @Test
    void creazioneSenzaNomeRisponde400() throws Exception {
        mockMvc.perform(post(LAVORI).session(sessioneCon("LAVORI_CREA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"\",\"dataInizio\":\"2027-01-01\","
                                + "\"dataFinePrevista\":\"2027-06-30\",\"stato\":\"BOZZA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creazioneSenzaStatoRisponde400() throws Exception {
        mockMvc.perform(post(LAVORI).session(sessioneCon("LAVORI_CREA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Portale\",\"dataInizio\":\"2027-01-01\","
                                + "\"dataFinePrevista\":\"2027-06-30\"}"))
                .andExpect(status().isBadRequest());
    }

    /** Le regole che incrociano più campi le applica il servizio. */
    @Test
    void lavoroAttivoSenzaResponsabileRisponde400ConIlMotivo() throws Exception {
        given(servizio.crea(any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Un lavoro attivo deve avere un responsabile: scegline uno, "
                                + "oppure salvalo come bozza."));

        mockMvc.perform(post(LAVORI).session(sessioneCon("LAVORI_CREA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("ATTIVO", "2027-06-30", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Un lavoro attivo deve avere un responsabile: scegline uno, "
                                + "oppure salvalo come bozza."));
    }

    @Test
    void responsabileSenzaIlPermessoRichiestoRisponde400() throws Exception {
        given(servizio.crea(any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "L'utente scelto non può essere responsabile di un lavoro: "
                                + "non ne ha il permesso, oppure non è attivo."));

        mockMvc.perform(post(LAVORI).session(sessioneCon("LAVORI_CREA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("ATTIVO", "2027-06-30", "4")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dateInvertiteRisponde400() throws Exception {
        given(servizio.crea(any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La data di fine prevista non può precedere quella di inizio."));

        mockMvc.perform(post(LAVORI).session(sessioneCon("LAVORI_CREA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("BOZZA", "2026-01-01", "null")))
                .andExpect(status().isBadRequest());
    }

    // --- annullamento e cancellazione ---------------------------------------

    @Test
    void annullamentoSenzaNessunoDeiDuePermessiRisponde403() throws Exception {
        mockMvc.perform(post(LAVORI + "/1/annulla").session(sessioneCon("LAVORI_CREA")))
                .andExpect(status().isForbidden());
    }

    /**
     * Con il solo {@code LAVORI_MODIFICA_PROPRI} la richiesta passa
     * l'annotazione e arriva al servizio: è lui a sapere se chi chiede è il
     * responsabile di quel lavoro, e il 403 di chi non lo è nasce là.
     */
    @Test
    void annullamentoConIlSoloPermessoSuiPropriArrivaAlServizio() throws Exception {
        given(servizio.annulla(any(), eq(1L))).willThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Per annullare il lavoro LAV-001 devi esserne il "
                        + "responsabile, oppure avere il permesso su tutti i lavori."));

        mockMvc.perform(post(LAVORI + "/1/annulla").session(sessioneCon("LAVORI_MODIFICA_PROPRI")))
                .andExpect(status().isForbidden());

        then(servizio).should().annulla(any(), eq(1L));
    }

    @Test
    void annullamentoConIlPermessoRestituisceIlLavoroAnnullato() throws Exception {
        given(servizio.annulla(any(), eq(1L))).willReturn(new RigaLavoro(1L, "LAV-001", "Portale clienti",
                null, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 3, 31),
                StatoLavoro.ANNULLATO, LocalDateTime.of(2026, 11, 6, 12, 0), 2L, "Bianchi Laura",
                new BigDecimal("400.00"), new BigDecimal("136.00"), new BigDecimal("38.00"),
                new BigDecimal("264.00"), 8L, 3L, 0L, 0L));

        mockMvc.perform(post(LAVORI + "/1/annulla").session(sessioneCon("LAVORI_MODIFICA_TUTTI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("ANNULLATO"));
    }

    @Test
    void annullamentoDiUnLavoroChiusoRisponde409() throws Exception {
        given(servizio.annulla(any(), eq(1L))).willThrow(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "Il lavoro LAV-001 è concluso: un lavoro chiuso non si annulla."));

        mockMvc.perform(post(LAVORI + "/1/annulla").session(sessioneCon("LAVORI_MODIFICA_TUTTI")))
                .andExpect(status().isConflict());
    }

    @Test
    void cancellazioneSenzaIlPermessoRisponde403() throws Exception {
        mockMvc.perform(delete(LAVORI + "/3").session(sessioneCon("LAVORI_CREA")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancellazioneDiUnaBozzaSenzaTaskRisponde204() throws Exception {
        mockMvc.perform(delete(LAVORI + "/3").session(sessioneCon("LAVORI_MODIFICA_TUTTI")))
                .andExpect(status().isNoContent());
    }

    /** Un lavoro con task si annulla, non si cancella. */
    @Test
    void cancellazioneDiUnLavoroConTaskRisponde409() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                "Il lavoro LAV-001 ha 3 task e non può essere cancellato: annullalo, "
                        + "così lo storico delle assegnazioni e del tempo lavorato resta."))
                .given(servizio).elimina(1L);

        mockMvc.perform(delete(LAVORI + "/1").session(sessioneCon("LAVORI_MODIFICA_TUTTI")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Il lavoro LAV-001 ha 3 task e non può essere cancellato: annullalo, "
                                + "così lo storico delle assegnazioni e del tempo lavorato resta."));
    }

    @Test
    void cancellazioneDiUnLavoroInesistenteRisponde404() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Lavoro non trovato."))
                .given(servizio).elimina(99L);

        mockMvc.perform(delete(LAVORI + "/99").session(sessioneCon("LAVORI_MODIFICA_TUTTI")))
                .andExpect(status().isNotFound());
    }

    @Test
    void codiceGiaAssegnatoRisponde409() throws Exception {
        given(servizio.crea(any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "Il codice LAV-004 è appena "
                        + "stato assegnato a un altro lavoro. Riprova."));

        mockMvc.perform(post(LAVORI).session(sessioneCon("LAVORI_CREA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("BOZZA", "2027-06-30", "null")))
                .andExpect(status().isConflict());
    }

    // --- monte ore ----------------------------------------------------------
    //
    // Il valore di questi test sta nella separazione dei due poteri: creare un
    // lavoro e decidere quante ore costa non sono la stessa cosa. Chi ha
    // LAVORI_CREA ma non ORE_MONTE_GESTISCI non deve poter toccare il budget,
    // ne' dalla rotta dedicata ne' passando il campo alla creazione.

    @Test
    void monteOreSenzaSessioneRisponde401() throws Exception {
        mockMvc.perform(post(LAVORI + "/1/monte-ore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMonteOre("500.00", "\"Ampliamento di perimetro\"")))
                .andExpect(status().isUnauthorized());
    }

    /** Il PM distribuisce il budget fra le task, ma non lo decide. */
    @Test
    void monteOreSenzaIlPermessoRisponde403() throws Exception {
        mockMvc.perform(post(LAVORI + "/1/monte-ore")
                        .session(sessioneCon("LAVORI_MODIFICA_PROPRI", "ORE_ALLOCA_PROPRI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMonteOre("500.00", "\"Ampliamento di perimetro\"")))
                .andExpect(status().isForbidden());
    }

    @Test
    void monteOreConIlPermessoRestituisceLeOreLibereAggiornate() throws Exception {
        given(servizio.cambiaMonteOre(any(), any(), any(), any())).willReturn(
                new RigaLavoro(1L, "LAV-001", "Portale clienti", null,
                        LocalDate.of(2026, 9, 1), LocalDate.of(2027, 3, 31),
                        StatoLavoro.ATTIVO, null, 2L, "Bianchi Laura",
                        new BigDecimal("500.00"), new BigDecimal("136.00"),
                        new BigDecimal("38.00"), new BigDecimal("364.00"), 8L, 3L, 1L, 0L));

        mockMvc.perform(post(LAVORI + "/1/monte-ore").session(sessioneCon("ORE_MONTE_GESTISCI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMonteOre("500.00", "\"Ampliamento di perimetro\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monteOre").value(500.00))
                .andExpect(jsonPath("$.oreLibere").value(364.00));
    }

    /** Il motivo è obbligatorio: una variazione di budget senza spiegazione non serve a niente. */
    @Test
    void monteOreSenzaMotivoRisponde400() throws Exception {
        mockMvc.perform(post(LAVORI + "/1/monte-ore").session(sessioneCon("ORE_MONTE_GESTISCI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMonteOre("500.00", "null")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void monteOreNegativoRisponde400() throws Exception {
        mockMvc.perform(post(LAVORI + "/1/monte-ore").session(sessioneCon("ORE_MONTE_GESTISCI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMonteOre("-10.00", "\"Taglio\"")))
                .andExpect(status().isBadRequest());
    }

    /**
     * Il 409 del servizio arriva al client con la sua spiegazione: il
     * frontend la mostra tale e quale, quindi deve dire quante ore sono già
     * allocate.
     */
    @Test
    void monteOreSottoLeOreAllocateRisponde409() throws Exception {
        given(servizio.cambiaMonteOre(any(), any(), any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "Il lavoro LAV-001 ha già 136.00 ore allocate alle task: il monte ore "
                                + "non può scendere a 100.00. Chiudi o riduci delle task, "
                                + "poi riprova."));

        mockMvc.perform(post(LAVORI + "/1/monte-ore").session(sessioneCon("ORE_MONTE_GESTISCI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMonteOre("100.00", "\"Taglio di budget\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Il lavoro LAV-001 ha già 136.00 ore allocate alle task: il monte ore "
                                + "non può scendere a 100.00. Chiudi o riduci delle task, "
                                + "poi riprova."));
    }

    // --- storico dei movimenti ----------------------------------------------

    /** Per lo storico basta poter vedere il lavoro: non c'è un permesso dedicato. */
    @Test
    void movimentiConIlPermessoDiVisualizzazioneRisponde200() throws Exception {
        given(servizio.storicoMovimenti(any(), any())).willReturn(List.of(
                new RigaMovimento(1L, TipoMovimento.MONTE_INIZIALE, new BigDecimal("376.00"),
                        "Budget approvato in fase di offerta", null, null, null,
                        "Amministratore Anna", LocalDateTime.of(2026, 9, 1, 8, 30)),
                new RigaMovimento(3L, TipoMovimento.RILASCIO, new BigDecimal("-24.00"),
                        "Task chiusa con 16 ore: residuo restituito al pool", 1L,
                        "Analisi requisiti", null, "Bianchi Laura",
                        LocalDateTime.of(2026, 9, 30, 17, 0))));

        mockMvc.perform(get(LAVORI + "/1/movimenti")
                        .session(sessioneCon("LAVORI_VISUALIZZA_PROPRI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tipo").value("MONTE_INIZIALE"))
                // Il rilascio è negativo: le ore escono dall'allocazione.
                .andExpect(jsonPath("$[1].ore").value(-24.00))
                .andExpect(jsonPath("$[1].task").value("Analisi requisiti"));
    }

    @Test
    void movimentiSenzaIlPermessoRisponde403() throws Exception {
        mockMvc.perform(get(LAVORI + "/1/movimenti").session(sessioneCon("TASK_GESTISCI_PROPRI")))
                .andExpect(status().isForbidden());
    }

    private static String corpoMonteOre(String monteOre, String motivo) {
        return "{\"monteOre\":" + monteOre + ",\"motivo\":" + motivo + "}";
    }
}
