package com.anitia.backend.utente;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anitia.backend.sicurezza.ConfigurazioneSicurezza;
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
 * Le rotte che leggono e cambiano i permessi di una persona.
 *
 * <p>Il caso più importante della serie non è un 403 ma il 409 su se stessi:
 * è una regola di sicurezza, non una validazione.
 */
@WebMvcTest(UtentiController.class)
@Import(ConfigurazioneSicurezza.class)
class PermessiUtenteControllerTest {

    /** Hash di "Password1!", lo stesso dei dati di sviluppo. */
    private static final String HASH =
            "{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq";

    private static final String EMAIL = "admin@gestionale.local";
    private static final String PERMESSI = "/api/utenti/4/permessi";
    private static final String UNO = PERMESSI + "/LAVORI_VISUALIZZA_TUTTI";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDetailsService dettagliUtente;

    @MockitoBean
    private ServizioPermessiUtente servizio;

    /** Il controller lo richiede: le sue rotte hanno un test a parte. */
    @MockitoBean
    private ServizioUtenti servizioUtenti;

    /**
     * Una sessione con i permessi indicati. Istanza nuova a ogni chiamata del
     * mock, perché {@code ProviderManager} azzera le credenziali del principal
     * e perché {@code FiltroUtenteAggiornato} rilegge l'utente a ogni
     * richiesta: sono quelle authority a contare.
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

    private static PermessoUtente voce(OriginePermesso origine, boolean effettivo) {
        return new PermessoUtente("LAVORI_VISUALIZZA_TUTTI", "LAVORI", "Vedere tutti i lavori",
                origine == OriginePermesso.RUOLO || origine == OriginePermesso.TOGLIATO,
                origine, effettivo);
    }

    // --- lettura ------------------------------------------------------------

    @Test
    void letturaSenzaSessioneRisponde401() throws Exception {
        mockMvc.perform(get(PERMESSI))
                .andExpect(status().isUnauthorized());
    }

    /** Vedere gli utenti non basta: servono anche i due permessi insieme. */
    @Test
    void letturaConSoloUtentiVisualizzaRisponde403() throws Exception {
        mockMvc.perform(get(PERMESSI).session(sessioneCon("UTENTI_VISUALIZZA")))
                .andExpect(status().isForbidden());
    }

    @Test
    void letturaConIDuePermessiRestituisceIlCatalogoConLeOrigini() throws Exception {
        given(servizio.permessiDi(4L)).willReturn(List.of(
                voce(OriginePermesso.RUOLO, true),
                new PermessoUtente("UTENTI_CREA", "UTENTI", "Creare nuovi utenti",
                        false, OriginePermesso.NESSUNO, false)));

        mockMvc.perform(get(PERMESSI)
                        .session(sessioneCon("UTENTI_VISUALIZZA", "RUOLI_ASSEGNA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].codice").value("LAVORI_VISUALIZZA_TUTTI"))
                .andExpect(jsonPath("$[0].origine").value("RUOLO"))
                .andExpect(jsonPath("$[0].effettivo").value(true))
                // Escono anche i permessi che non ha: sono le caselle da spuntare.
                .andExpect(jsonPath("$[1].origine").value("NESSUNO"))
                .andExpect(jsonPath("$[1].effettivo").value(false));
    }

    // --- modifica -----------------------------------------------------------

    @Test
    void modificaSenzaSessioneRisponde401() throws Exception {
        mockMvc.perform(put(UNO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effettivo\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void modificaSenzaRuoliAssegnaRisponde403() throws Exception {
        mockMvc.perform(put(UNO).session(sessioneCon("UTENTI_VISUALIZZA", "UTENTI_MODIFICA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effettivo\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void concedereUnPermessoRestituisceLOrigineAggiunto() throws Exception {
        given(servizio.imposta(eq(4L), eq("LAVORI_VISUALIZZA_TUTTI"), eq(true), anyString()))
                .willReturn(voce(OriginePermesso.AGGIUNTO, true));

        mockMvc.perform(put(UNO).session(sessioneCon("RUOLI_ASSEGNA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effettivo\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origine").value("AGGIUNTO"))
                .andExpect(jsonPath("$.effettivo").value(true));
    }

    @Test
    void negareUnPermessoDelRuoloRestituisceLOrigineTogliato() throws Exception {
        given(servizio.imposta(eq(4L), eq("LAVORI_VISUALIZZA_TUTTI"), eq(false), anyString()))
                .willReturn(voce(OriginePermesso.TOGLIATO, false));

        mockMvc.perform(put(UNO).session(sessioneCon("RUOLI_ASSEGNA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effettivo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origine").value("TOGLIATO"))
                .andExpect(jsonPath("$.dalRuolo").value(true))
                .andExpect(jsonPath("$.effettivo").value(false));
    }

    @Test
    void corpoSenzaIlCampoRisponde400() throws Exception {
        mockMvc.perform(put(UNO).session(sessioneCon("RUOLI_ASSEGNA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void permessoInesistenteRisponde400() throws Exception {
        given(servizio.imposta(anyLong(), eq("NON_ESISTE"), anyBoolean(), anyString()))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Il permesso NON_ESISTE non esiste."));

        mockMvc.perform(put(PERMESSI + "/NON_ESISTE").session(sessioneCon("RUOLI_ASSEGNA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effettivo\":true}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * La regola che impedisce l'escalation: chi ha RUOLI_ASSEGNA non può
     * concedersi permessi da solo.
     */
    @Test
    void modificareIProprIPermessiRisponde409() throws Exception {
        given(servizio.imposta(anyLong(), anyString(), anyBoolean(), anyString()))
                .willThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                        "Non puoi modificare i tuoi permessi. Chiedi a un altro "
                                + "amministratore."));

        mockMvc.perform(put(UNO).session(sessioneCon("RUOLI_ASSEGNA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effettivo\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Non puoi modificare i tuoi permessi. Chiedi a un altro "
                                + "amministratore."));
    }

    // --- ripristino ---------------------------------------------------------

    @Test
    void ripristinoSenzaIlPermessoRisponde403() throws Exception {
        mockMvc.perform(delete(UNO).session(sessioneCon("UTENTI_VISUALIZZA")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ripristinoRisponde204() throws Exception {
        mockMvc.perform(delete(UNO).session(sessioneCon("RUOLI_ASSEGNA")))
                .andExpect(status().isNoContent());
    }

    @Test
    void ripristinoDiUnUtenteInesistenteRisponde404() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Utente non trovato."))
                .given(servizio).ripristina(eq(99L), any(), any());

        mockMvc.perform(delete("/api/utenti/99/permessi/UTENTI_CREA")
                        .session(sessioneCon("RUOLI_ASSEGNA")))
                .andExpect(status().isNotFound());
    }
}
