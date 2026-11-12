package com.anitia.backend.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anitia.backend.sicurezza.ConfigurazioneSicurezza;
import com.anitia.backend.tempo.TipoAttivita;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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

/**
 * Le autorizzazioni e il contratto delle rotte del report.
 *
 * <p>Il valore sta nei 403, e in particolare in uno: chi ha
 * {@code TEMPO_VISUALIZZA_LAVORO} <strong>non</strong> entra. È la differenza
 * voluta da {@code /api/tempo/utente/{id}}, che quel permesso accetta — lì si
 * guarda una persona per volta, e il limite «solo chi lavora ai miei lavori»
 * ha senso; qui si guarda l'azienda intera, e un report globale servito a chi
 * vede solo una fetta sarebbe un report con un denominatore sbagliato.
 */
@WebMvcTest(ReportController.class)
@Import(ConfigurazioneSicurezza.class)
class ReportControllerTest {

    /** Hash di "Password1!", lo stesso dei dati di sviluppo. */
    private static final String HASH =
            "{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq";

    private static final String EMAIL = "admin@gestionale.local";
    private static final String PERSONE = "/api/report/persone";
    private static final String PERIODO = "?da=2026-11-01&a=2026-11-30";
    private static final String TUTTI = "TEMPO_VISUALIZZA_TUTTI";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDetailsService dettagliUtente;

    @MockitoBean
    private ServizioReport servizio;

    @Test
    void laPanoramicaRispondeConLeOreDiCiascuno() throws Exception {
        given(servizio.persone(any(), any())).willReturn(new ReportPersone(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                List.of(new RiepilogoPersona(7L, "Mario", "Rossi", true, 9_000, 120),
                        new RiepilogoPersona(9L, "Luca", "Bianchi", false, 60, 0))));

        mockMvc.perform(get(PERSONE + PERIODO).session(sessioneCon(TUTTI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persone.length()").value(2))
                .andExpect(jsonPath("$.persone[0].cognome").value("Rossi"))
                .andExpect(jsonPath("$.persone[0].minutiTotali").value(9000))
                // Le non confermate sono dentro il totale e dichiarate a parte.
                .andExpect(jsonPath("$.persone[0].minutiNonConfermati").value(120))
                // Un disattivato compare perché ha ore nel periodo.
                .andExpect(jsonPath("$.persone[1].attivo").value(false));
    }

    @Test
    void ilDettaglioPortaLeTreFamiglieDiTempo() throws Exception {
        given(servizio.persona(any(), any(), any())).willReturn(new ReportPersona(
                7L, "Mario", "Rossi", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                600, 0,
                List.of(new VoceLavoro(3L, "P-2026-003", "Portale fornitori", 480, 0,
                        List.of(new VoceTask(11L, "Analisi requisiti", 420)),
                        List.of(VoceAttivita.da(TipoAttivita.MANUTENZIONE_CLIENTE, 60)))),
                List.of(VoceAttivita.da(TipoAttivita.RIUNIONE, 120))));

        mockMvc.perform(get(PERSONE + "/7" + PERIODO).session(sessioneCon(TUTTI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutiTotali").value(600))
                .andExpect(jsonPath("$.lavori[0].codice").value("P-2026-003"))
                .andExpect(jsonPath("$.lavori[0].task[0].titolo").value("Analisi requisiti"))
                // Le ore sul lavoro ma fuori da ogni task restano dentro il lavoro.
                .andExpect(jsonPath("$.lavori[0].attivitaDirette[0].minuti").value(60))
                // L'etichetta arriva dal backend, non si ricostruisce in pagina.
                .andExpect(jsonPath("$.senzaLavoro[0].etichetta").value(
                        TipoAttivita.RIUNIONE.etichetta()));
    }

    @Test
    void senzaSessioneRisponde401() throws Exception {
        mockMvc.perform(get(PERSONE + PERIODO)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PERSONE + "/7" + PERIODO)).andExpect(status().isUnauthorized());
    }

    @Test
    void senzaIlPermessoRisponde403() throws Exception {
        mockMvc.perform(get(PERSONE + PERIODO).session(sessioneCon("TASK_AGGIORNA_STATO")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PERSONE + "/7" + PERIODO).session(sessioneCon("TASK_AGGIORNA_STATO")))
                .andExpect(status().isForbidden());
    }

    /**
     * Il permesso sul lavoro non basta: è la prova che il report è globale e
     * non si affaccia su una fetta dell'azienda.
     */
    @Test
    void conIlSoloPermessoSulLavoroRisponde403() throws Exception {
        mockMvc.perform(get(PERSONE + PERIODO).session(sessioneCon("TEMPO_VISUALIZZA_LAVORO")))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        get(PERSONE + "/7" + PERIODO)
                                .session(sessioneCon("TEMPO_VISUALIZZA_LAVORO")))
                .andExpect(status().isForbidden());

        then(servizio).shouldHaveNoInteractions();
    }

    /**
     * Le date sono obbligatorie e non hanno un ripiego: un consuntivo senza
     * periodo non vuol dire niente, e un periodo scelto dal server sarebbe un
     * numero che chi legge crede di avere chiesto.
     */
    @Test
    void senzaLeDateRisponde400() throws Exception {
        mockMvc.perform(get(PERSONE).session(sessioneCon(TUTTI)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PERSONE + "?da=2026-11-01").session(sessioneCon(TUTTI)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PERSONE + "/7?a=2026-11-30").session(sessioneCon(TUTTI)))
                .andExpect(status().isBadRequest());

        then(servizio).shouldHaveNoInteractions();
    }

    @Test
    void unaDataIllegibileRisponde400() throws Exception {
        mockMvc.perform(get(PERSONE + "?da=novembre&a=2026-11-30").session(sessioneCon(TUTTI)))
                .andExpect(status().isBadRequest());
    }

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
}
