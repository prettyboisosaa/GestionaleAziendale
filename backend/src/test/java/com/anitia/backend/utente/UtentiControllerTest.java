package com.anitia.backend.utente;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anitia.backend.sicurezza.ConfigurazioneSicurezza;
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
 * Le autorizzazioni sulle rotte degli utenti. Il valore di questi test sta nei
 * 403: verificano che il catalogo dei permessi venga davvero applicato, e non
 * solo dichiarato nel database.
 */
@WebMvcTest(UtentiController.class)
@Import(ConfigurazioneSicurezza.class)
class UtentiControllerTest {

    /** Hash di "Password1!", lo stesso dei dati di sviluppo. */
    private static final String HASH =
            "{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq";

    private static final String EMAIL = "admin@gestionale.local";
    private static final String UTENTI = "/api/utenti";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDetailsService dettagliUtente;

    @MockitoBean
    private ServizioUtenti servizio;

    /** Il controller lo richiede: le sue rotte hanno un test a parte. */
    @MockitoBean
    private ServizioPermessiUtente servizioPermessi;

    /**
     * Una sessione con un utente collegato e i permessi indicati.
     *
     * <p>Costruisce l'attributo che il login scrive in sessione, e insegna al
     * finto {@code UserDetailsService} a restituire gli stessi permessi:
     * servono entrambi, perche' {@code FiltroUtenteAggiornato} rilegge
     * l'utente a ogni richiesta e sono le authority <em>rilette</em> quelle
     * che {@code @PreAuthorize} valuta.
     *
     * <p>Istanza nuova a ogni chiamata del mock, non condivisa:
     * {@code ProviderManager} azzera le credenziali del principal dopo un
     * accesso riuscito.
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

    private static String corpo(String email, String password, String ruoli) {
        return "{\"nome\":\"Mario\",\"cognome\":\"Rossi\",\"email\":\"" + email
                + "\",\"password\":\"" + password + "\",\"ruoli\":" + ruoli + "}";
    }

    private static RigaUtente riga() {
        return new RigaUtente(8L, "Mario", "Rossi", "mario.rossi@gestionale.local",
                true, null, LocalDateTime.now(), List.of("Dipendente"), 4);
    }

    // --- elenco -------------------------------------------------------------

    @Test
    void elencoSenzaSessioneRisponde401() throws Exception {
        mockMvc.perform(get(UTENTI))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elencoSenzaIlPermessoRisponde403() throws Exception {
        mockMvc.perform(get(UTENTI).session(sessioneCon("DICHIARAZIONI_COMPILA")))
                .andExpect(status().isForbidden());
    }

    @Test
    void elencoConIlPermessoRestituisceGliUtenti() throws Exception {
        given(servizio.elenco()).willReturn(List.of(riga()));

        mockMvc.perform(get(UTENTI).session(sessioneCon("UTENTI_VISUALIZZA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("mario.rossi@gestionale.local"))
                .andExpect(jsonPath("$[0].ruoli[0]").value("Dipendente"));
    }

    // --- creazione ----------------------------------------------------------

    @Test
    void creazioneSenzaSessioneRisponde401() throws Exception {
        mockMvc.perform(post(UTENTI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("mario.rossi@gestionale.local", "Password1!", "[3]")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creazioneConSoloUtentiVisualizzaRisponde403() throws Exception {
        mockMvc.perform(post(UTENTI).session(sessioneCon("UTENTI_VISUALIZZA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("mario.rossi@gestionale.local", "Password1!", "[3]")))
                .andExpect(status().isForbidden());
    }

    /** Creare senza poter assegnare ruoli non ha senso: serve anche RUOLI_ASSEGNA. */
    @Test
    void creazioneSenzaRuoliAssegnaRisponde403() throws Exception {
        mockMvc.perform(post(UTENTI).session(sessioneCon("UTENTI_CREA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("mario.rossi@gestionale.local", "Password1!", "[3]")))
                .andExpect(status().isForbidden());
    }

    @Test
    void creazioneConIDuePermessiRisponde201ESenzaLaPassword() throws Exception {
        given(servizio.crea(any(), any())).willReturn(riga());

        mockMvc.perform(post(UTENTI).session(sessioneCon("UTENTI_CREA", "RUOLI_ASSEGNA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("mario.rossi@gestionale.local", "Password1!", "[3]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.email").value("mario.rossi@gestionale.local"))
                // Nemmeno in forma di hash: il record non ha quel campo.
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void creazioneConEmailGiaUsataRisponde409() throws Exception {
        given(servizio.crea(any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "Esiste già un utente con questa email."));

        mockMvc.perform(post(UTENTI).session(sessioneCon("UTENTI_CREA", "RUOLI_ASSEGNA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("admin@gestionale.local", "Password1!", "[3]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esiste già un utente con questa email."));
    }

    @Test
    void creazioneSenzaNessunRuoloRisponde400() throws Exception {
        mockMvc.perform(post(UTENTI).session(sessioneCon("UTENTI_CREA", "RUOLI_ASSEGNA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("mario.rossi@gestionale.local", "Password1!", "[]")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creazioneConPasswordCortaRisponde400() throws Exception {
        mockMvc.perform(post(UTENTI).session(sessioneCon("UTENTI_CREA", "RUOLI_ASSEGNA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("mario.rossi@gestionale.local", "corta", "[3]")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creazioneConEmailNonValidaRisponde400() throws Exception {
        mockMvc.perform(post(UTENTI).session(sessioneCon("UTENTI_CREA", "RUOLI_ASSEGNA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("non-una-email", "Password1!", "[3]")))
                .andExpect(status().isBadRequest());
    }
}
