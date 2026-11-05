package com.anitia.backend.ore;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anitia.backend.sicurezza.ConfigurazioneSicurezza;
import java.math.BigDecimal;
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
 * Il contratto delle rotte dell'allocazione delle ore.
 *
 * <p>Il valore sta in due punti. Il primo: allocare ore richiede
 * {@code ORE_ALLOCA_*}, e non basta poter gestire le task — distribuire un
 * budget e creare attività sono due poteri diversi, e chi ha il secondo non
 * deve ereditare il primo.
 *
 * <p>Il secondo: il 409 di pool insufficiente deve arrivare al client
 * <strong>con il numero delle ore libere nel messaggio</strong>. È il dato con
 * cui l'interfaccia precompila la richiesta all'amministratore, e un 409 muto
 * costringerebbe il PM a fare la sottrazione a mano.
 *
 * <p>Il servizio è simulato: le regole che dipendono dai dati — essere il
 * responsabile, la capienza del pool, il lock sulla riga del lavoro — si
 * verificano contro il database.
 */
@WebMvcTest(OreController.class)
@Import(ConfigurazioneSicurezza.class)
class OreControllerTest {

    /** Hash di "Password1!", lo stesso dei dati di sviluppo. */
    private static final String HASH =
            "{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq";

    private static final String EMAIL = "admin@gestionale.local";
    private static final String ORE_TASK = "/api/lavori/1/task/2/ore";
    private static final String ORE_LAVORO = "/api/lavori/1/ore";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDetailsService dettagliUtente;

    @MockitoBean
    private ServizioOre servizio;

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

    private static String corpo(String ore, String motivo) {
        return "{\"ore\":" + ore + ",\"motivo\":" + motivo + "}";
    }

    /** LAV-001: 400 ore, 136 allocate, 38 consumate, 264 libere. */
    private static RiepilogoOre riepilogo() {
        return new RiepilogoOre(1L, "LAV-001", new BigDecimal("400.00"),
                new BigDecimal("136.00"), new BigDecimal("38.00"), new BigDecimal("264.00"));
    }

    // --- lettura del riepilogo ----------------------------------------------

    @Test
    void riepilogoSenzaSessioneRisponde401() throws Exception {
        mockMvc.perform(get(ORE_LAVORO))
                .andExpect(status().isUnauthorized());
    }

    /** Per leggere le ore basta poter vedere il lavoro. */
    @Test
    void riepilogoConIlPermessoDiVisualizzazioneRisponde200() throws Exception {
        given(servizio.riepilogo(any(), any())).willReturn(riepilogo());

        mockMvc.perform(get(ORE_LAVORO).session(sessioneCon("LAVORI_VISUALIZZA_PROPRI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monteOre").value(400.00))
                .andExpect(jsonPath("$.oreLibere").value(264.00));
    }

    @Test
    void riepilogoSenzaIlPermessoRisponde403() throws Exception {
        mockMvc.perform(get(ORE_LAVORO).session(sessioneCon("TASK_VISUALIZZA_ASSEGNATE")))
                .andExpect(status().isForbidden());
    }

    // --- allocazione --------------------------------------------------------

    @Test
    void allocazioneSenzaSessioneRisponde401() throws Exception {
        mockMvc.perform(post(ORE_TASK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("20.00", "\"Serve più tempo del previsto\"")))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Poter creare e assegnare task non vuol dire poter distribuire il
     * budget: sono due permessi distinti, e questo è il test che lo prova.
     */
    @Test
    void allocazioneConIlSoloPermessoSulleTaskRisponde403() throws Exception {
        mockMvc.perform(post(ORE_TASK).session(sessioneCon("TASK_GESTISCI_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("20.00", "\"Serve più tempo del previsto\"")))
                .andExpect(status().isForbidden());
    }

    @Test
    void allocazioneConIlPermessoRestituisceLeOreLibereRimaste() throws Exception {
        given(servizio.alloca(any(), any(), any(), any(), any())).willReturn(riepilogo());

        mockMvc.perform(post(ORE_TASK).session(sessioneCon("ORE_ALLOCA_PROPRI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("20.00", "\"Serve più tempo del previsto\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codice").value("LAV-001"))
                .andExpect(jsonPath("$.oreLibere").value(264.00));
    }

    @Test
    void allocazioneDiZeroOreRisponde400() throws Exception {
        mockMvc.perform(post(ORE_TASK).session(sessioneCon("ORE_ALLOCA_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("0", "\"Niente\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void allocazioneSenzaMotivoRisponde400() throws Exception {
        mockMvc.perform(post(ORE_TASK).session(sessioneCon("ORE_ALLOCA_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("20.00", "null")))
                .andExpect(status().isBadRequest());
    }

    /**
     * Il messaggio del 409 deve dire quante ore libere ci sono: è il numero
     * con cui il frontend precompila la richiesta all'amministratore.
     */
    @Test
    void allocazioneOltreIlPoolRisponde409ConLeOreLibere() throws Exception {
        given(servizio.alloca(any(), any(), any(), any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "Il lavoro LAV-002 ha solo 4.00 ore libere, e ne servono 40.00. "
                                + "Alloca quelle che restano e chiedi la differenza a un "
                                + "amministratore."));

        mockMvc.perform(post(ORE_TASK).session(sessioneCon("ORE_ALLOCA_PROPRI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("40.00", "\"Mappatura più lunga del previsto\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Il lavoro LAV-002 ha solo 4.00 ore libere, e ne servono 40.00. "
                                + "Alloca quelle che restano e chiedi la differenza a un "
                                + "amministratore."));
    }

    /** Allocare ore a una task che non consumerà più niente non ha senso. */
    @Test
    void allocazioneAUnaTaskChiusaRisponde409() throws Exception {
        given(servizio.alloca(any(), any(), any(), any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "La task «Analisi requisiti» non è più aperta: allocarle ore non "
                                + "servirebbe a niente."));

        mockMvc.perform(post(ORE_TASK).session(sessioneCon("ORE_ALLOCA_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("4.00", "\"Rifiniture\"")))
                .andExpect(status().isConflict());
    }

    /** Un lavoro senza monte ore non ha ore da distribuire. */
    @Test
    void allocazioneSuUnLavoroSenzaMonteOreRisponde409() throws Exception {
        given(servizio.alloca(any(), any(), any(), any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "Il lavoro LAV-003 non ha ancora un monte ore: deve fissarlo un "
                                + "amministratore prima che si possano distribuire le ore."));

        mockMvc.perform(post(ORE_TASK).session(sessioneCon("ORE_ALLOCA_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("4.00", "\"Avvio\"")))
                .andExpect(status().isConflict());
    }

    /**
     * La task deve appartenere al lavoro dell'indirizzo: con il solo id, un
     * indirizzo come /api/lavori/3/task/2/ore toccherebbe la task 2 anche se
     * sta nel lavoro 1.
     */
    @Test
    void allocazioneAUnaTaskDiUnAltroLavoroRisponde404() throws Exception {
        given(servizio.alloca(any(), any(), any(), any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Task non trovata in questo lavoro."));

        mockMvc.perform(post("/api/lavori/3/task/2/ore").session(sessioneCon("ORE_ALLOCA_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("4.00", "\"Prova\"")))
                .andExpect(status().isNotFound());
    }
}
