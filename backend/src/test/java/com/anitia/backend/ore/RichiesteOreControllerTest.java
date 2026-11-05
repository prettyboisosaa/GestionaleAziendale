package com.anitia.backend.ore;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anitia.backend.ore.ContestoRichiesta.TaskInSforamento;
import com.anitia.backend.sicurezza.ConfigurazioneSicurezza;
import java.math.BigDecimal;
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
 * Il contratto delle rotte delle richieste di ore.
 *
 * <p>Il valore sta nella <strong>separazione fra chi chiede e chi decide</strong>.
 * {@code ORE_RICHIEDI} apre una richiesta, {@code ORE_RICHIESTE_GESTISCI} la
 * decide, e il primo non implica il secondo: un PM che potesse approvare le
 * proprie richieste trasformerebbe tutta questa funzionalità in un giro
 * burocratico senza controllo. I 403 qui sotto sono la prova che non succede.
 *
 * <p>Il secondo punto è il contesto: la rotta che l'amministratore apre deve
 * portargli i numeri del lavoro — monte ore attuale e iniziale, allocate,
 * consumate, sforamenti, storico — perché è su quelli che decide.
 */
@WebMvcTest(RichiesteOreController.class)
@Import(ConfigurazioneSicurezza.class)
class RichiesteOreControllerTest {

    /** Hash di "Password1!", lo stesso dei dati di sviluppo. */
    private static final String HASH =
            "{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq";

    private static final String EMAIL = "marco.rossi@gestionale.local";
    private static final String SOTTO_LAVORO = "/api/lavori/2/richieste-ore";
    private static final String CODA = "/api/richieste-ore";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDetailsService dettagliUtente;

    @MockitoBean
    private ServizioRichiesteOre servizio;

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

    private static String corpoRichiesta(String taskId, String ore, String motivo) {
        return "{\"taskId\":" + taskId + ",\"oreRichieste\":" + ore
                + ",\"motivo\":" + motivo + "}";
    }

    private static RigaRichiestaOre inAttesa() {
        return new RigaRichiestaOre(2L, 2L, "LAV-002", "Migrazione ERP", 4L, "Mappatura dati",
                "Rossi Marco", new BigDecimal("40.00"), null,
                "Il pool del lavoro è esaurito.", StatoRichiesta.IN_ATTESA, null, null,
                LocalDateTime.of(2026, 11, 3, 9, 15));
    }

    private static RigaRichiestaOre parziale() {
        return new RigaRichiestaOre(2L, 2L, "LAV-002", "Migrazione ERP", 4L, "Mappatura dati",
                "Rossi Marco", new BigDecimal("40.00"), new BigDecimal("25.00"),
                "Il pool del lavoro è esaurito.", StatoRichiesta.APPROVATA_PARZIALE,
                "Amministratore Anna", LocalDateTime.of(2026, 11, 5, 12, 5),
                LocalDateTime.of(2026, 11, 3, 9, 15));
    }

    // --- aprire una richiesta -----------------------------------------------

    @Test
    void aperturaSenzaSessioneRisponde401() throws Exception {
        mockMvc.perform(post(SOTTO_LAVORO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRichiesta("4", "40.00", "\"Serve più tempo\"")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aperturaConIlPermessoRisponde201() throws Exception {
        given(servizio.apri(any(), any(), any())).willReturn(inAttesa());

        mockMvc.perform(post(SOTTO_LAVORO).session(sessioneCon("ORE_RICHIEDI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRichiesta("4", "40.00", "\"Serve più tempo\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stato").value("IN_ATTESA"))
                .andExpect(jsonPath("$.oreConcesse").value((Object) null));
    }

    /** Poter allocare ore non vuol dire poterne chiedere di nuove sui lavori altrui. */
    @Test
    void aperturaConIlSoloPermessoDiAllocareIPropriRisponde403() throws Exception {
        mockMvc.perform(post(SOTTO_LAVORO).session(sessioneCon("ORE_ALLOCA_PROPRI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRichiesta("4", "40.00", "\"Serve più tempo\"")))
                .andExpect(status().isForbidden());
    }

    /** La task è facoltativa: senza, le ore concesse restano nel pool. */
    @Test
    void aperturaSenzaTaskRisponde201() throws Exception {
        given(servizio.apri(any(), any(), any())).willReturn(inAttesa());

        mockMvc.perform(post(SOTTO_LAVORO).session(sessioneCon("ORE_RICHIEDI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRichiesta("null", "40.00", "\"Margine sul lavoro\"")))
                .andExpect(status().isCreated());
    }

    @Test
    void aperturaSenzaMotivoRisponde400() throws Exception {
        mockMvc.perform(post(SOTTO_LAVORO).session(sessioneCon("ORE_RICHIEDI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRichiesta("4", "40.00", "null")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aperturaDiZeroOreRisponde400() throws Exception {
        mockMvc.perform(post(SOTTO_LAVORO).session(sessioneCon("ORE_RICHIEDI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRichiesta("4", "0", "\"Niente\"")))
                .andExpect(status().isBadRequest());
    }

    /** Si chiedono ore solo per i lavori di cui si è responsabili. */
    @Test
    void aperturaSuUnLavoroAltruiRisponde403() throws Exception {
        given(servizio.apri(any(), any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Per chiedere ore sul lavoro LAV-001 devi esserne il responsabile."));

        mockMvc.perform(post("/api/lavori/1/richieste-ore").session(sessioneCon("ORE_RICHIEDI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRichiesta("2", "40.00", "\"Serve più tempo\"")))
                .andExpect(status().isForbidden());
    }

    // --- la coda di chi decide ----------------------------------------------

    @Test
    void codaSenzaIlPermessoDiGestioneRisponde403() throws Exception {
        mockMvc.perform(get(CODA + "/in-attesa").session(sessioneCon("ORE_RICHIEDI")))
                .andExpect(status().isForbidden());
    }

    @Test
    void codaConIlPermessoDiGestioneRisponde200() throws Exception {
        given(servizio.inAttesa()).willReturn(List.of(inAttesa()));

        mockMvc.perform(get(CODA + "/in-attesa")
                        .session(sessioneCon("ORE_RICHIESTE_GESTISCI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codiceLavoro").value("LAV-002"));
    }

    /** Il PM ritrova le proprie richieste: senza notifiche, è così che vede l'esito. */
    @Test
    void elencoProprioConIlPermessoDiRichiedereRisponde200() throws Exception {
        given(servizio.mie(any())).willReturn(List.of(parziale()));

        mockMvc.perform(get(CODA).session(sessioneCon("ORE_RICHIEDI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stato").value("APPROVATA_PARZIALE"))
                .andExpect(jsonPath("$[0].oreConcesse").value(25.00));
    }

    // --- il contesto della decisione ----------------------------------------

    /**
     * La schermata su cui l'amministratore decide: la fix al difetto del piano
     * iniziale, dove decideva senza sapere niente del lavoro.
     */
    @Test
    void contestoPortaINumeriDelLavoro() throws Exception {
        given(servizio.contesto(any(), any())).willReturn(new ContestoRichiesta(
                inAttesa(),
                new RiepilogoOre(2L, "LAV-002", new BigDecimal("100.00"),
                        new BigDecimal("100.00"), new BigDecimal("24.00"), BigDecimal.ZERO),
                new BigDecimal("100.00"),
                List.of(new TaskInSforamento(4L, "Mappatura dati", new BigDecimal("20.00"),
                        new BigDecimal("24.00"), new BigDecimal("4.00"))),
                List.of(inAttesa())));

        mockMvc.perform(get(CODA + "/2/contesto")
                        .session(sessioneCon("ORE_RICHIESTE_GESTISCI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ore.oreLibere").value(0))
                .andExpect(jsonPath("$.monteOreIniziale").value(100.00))
                .andExpect(jsonPath("$.sforamenti.length()").value(1))
                .andExpect(jsonPath("$.sforamenti[0].sforamento").value(4.00))
                .andExpect(jsonPath("$.storico.length()").value(1));
    }

    @Test
    void contestoSenzaIlPermessoDiGestioneRisponde403() throws Exception {
        mockMvc.perform(get(CODA + "/2/contesto").session(sessioneCon("ORE_RICHIEDI")))
                .andExpect(status().isForbidden());
    }

    // --- la decisione -------------------------------------------------------

    /**
     * Il test che conta: chi apre le richieste non le decide. Senza questo
     * confine, l'approvazione sarebbe una formalità che il PM si concede da sé.
     */
    @Test
    void decisioneConIlSoloPermessoDiRichiedereRisponde403() throws Exception {
        mockMvc.perform(post(CODA + "/2/gestisci").session(sessioneCon("ORE_RICHIEDI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oreConcesse\":40.00}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void decisioneParzialeRestituisceLoStatoDedotto() throws Exception {
        given(servizio.gestisci(any(), any(), any())).willReturn(parziale());

        mockMvc.perform(post(CODA + "/2/gestisci").session(sessioneCon("ORE_RICHIESTE_GESTISCI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oreConcesse\":25.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("APPROVATA_PARZIALE"))
                .andExpect(jsonPath("$.oreConcesse").value(25.00))
                .andExpect(jsonPath("$.gestitaDa").value("Amministratore Anna"));
    }

    /** Zero ore è un rifiuto, e va accettato dalla validazione. */
    @Test
    void rifiutoConZeroOreRisponde200() throws Exception {
        given(servizio.gestisci(any(), any(), any())).willReturn(parziale());

        mockMvc.perform(post(CODA + "/2/gestisci").session(sessioneCon("ORE_RICHIESTE_GESTISCI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oreConcesse\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    void decisioneConOreNegativeRisponde400() throws Exception {
        mockMvc.perform(post(CODA + "/2/gestisci").session(sessioneCon("ORE_RICHIESTE_GESTISCI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oreConcesse\":-5.00}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void decisioneSenzaNumeroRisponde400() throws Exception {
        mockMvc.perform(post(CODA + "/2/gestisci").session(sessioneCon("ORE_RICHIESTE_GESTISCI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oreConcesse\":null}"))
                .andExpect(status().isBadRequest());
    }

    /** Una richiesta già decisa non si decide una seconda volta. */
    @Test
    void secondaDecisioneSullaStessaRichiestaRisponde409() throws Exception {
        given(servizio.gestisci(any(), any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "La richiesta è già stata gestita il 2026-11-05T12:05: sono state "
                                + "concesse 25.00 ore."));

        mockMvc.perform(post(CODA + "/2/gestisci").session(sessioneCon("ORE_RICHIESTE_GESTISCI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oreConcesse\":10.00}"))
                .andExpect(status().isConflict());
    }
}
