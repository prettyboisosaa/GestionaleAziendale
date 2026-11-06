package com.anitia.backend.autenticazione;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anitia.backend.sicurezza.ConfigurazioneSicurezza;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Il contratto dell'accesso. Il database non serve: l'unica dipendenza
 * esterna, il caricamento dell'utente, e' simulata.
 */
@WebMvcTest(AutenticazioneController.class)
@Import(ConfigurazioneSicurezza.class)
class AutenticazioneControllerTest {

    /** Hash di "Password1!", lo stesso dei dati di sviluppo. */
    private static final String HASH =
            "{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq";

    private static final String EMAIL = "admin@gestionale.local";

    private static final String LOGIN = "/api/autenticazione/login";
    private static final String IO = "/api/autenticazione/io";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDetailsService dettagliUtente;

    private static UserDetails utente(boolean attivo) {
        return User.withUsername(EMAIL).password(HASH).disabled(!attivo)
                .authorities("LAVORI_CREA", "UTENTI_VISUALIZZA").build();
    }

    /**
     * Il finto servizio deve restituire un'istanza <em>nuova</em> a ogni
     * chiamata, non sempre la stessa: {@code ProviderManager} cancella le
     * credenziali dal principal dopo un accesso riuscito, e {@code User}
     * implementa {@code CredentialsContainer}, quindi la password di
     * un'istanza condivisa verrebbe azzerata e gli accessi successivi
     * fallirebbero. In produzione non accade, perche'
     * {@code ServizioDettagliUtente} costruisce un User nuovo ogni volta.
     */
    private void utenteNelDatabase(boolean attivo) {
        given(dettagliUtente.loadUserByUsername(EMAIL))
                .willAnswer(invocazione -> utente(attivo));
    }

    private static String corpo(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    /** Fa il login e restituisce la sessione aperta. */
    private MockHttpSession accedi() throws Exception {
        return (MockHttpSession) mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(EMAIL, "Password1!")))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    @Test
    void conCredenzialiCorretteApreLaSessioneEElencaIPermessi() throws Exception {
        utenteNelDatabase(true);

        MockHttpSession sessione = (MockHttpSession) mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(EMAIL, "Password1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                // In ordine alfabetico, come li espone UtenteCorrente. Due e
                // non tre: FACTOR_PASSWORD, che Spring Security aggiunge
                // all'Authentication, non e' un permesso del gestionale.
                .andExpect(jsonPath("$.permessi.length()").value(2))
                .andExpect(jsonPath("$.permessi[0]").value("LAVORI_CREA"))
                .andExpect(jsonPath("$.permessi[1]").value("UTENTI_VISUALIZZA"))
                // La password, anche in hash, non deve mai uscire dall'API.
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn()
                .getRequest()
                .getSession(false);

        // L'autenticazione finisce davvero nella sessione: senza questo
        // controllo il login potrebbe rispondere 200 senza tenere collegato
        // nessuno, con la risposta giusta e l'effetto assente.
        assertThat(sessione).isNotNull();
        SecurityContext contesto = (SecurityContext) sessione
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(contesto).isNotNull();
        assertThat(contesto.getAuthentication().getName()).isEqualTo(EMAIL);
    }

    @Test
    void conPasswordSbagliataRisponde401() throws Exception {
        utenteNelDatabase(true);

        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(EMAIL, "sbagliata")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail")
                        .value("Email o password non corretti."));
    }

    @Test
    void conEmailInesistenteRispondeComeConLaPasswordSbagliata() throws Exception {
        given(dettagliUtente.loadUserByUsername("nessuno@gestionale.local"))
                .willThrow(new UsernameNotFoundException("non trovato"));

        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("nessuno@gestionale.local", "Password1!")))
                .andExpect(status().isUnauthorized())
                // Lo stesso messaggio: dalla risposta non si capisce se
                // l'indirizzo e' registrato.
                .andExpect(jsonPath("$.detail")
                        .value("Email o password non corretti."));
    }

    @Test
    void utenteDisattivatoNonEntraNemmenoConLaPasswordGiusta() throws Exception {
        utenteNelDatabase(false);

        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(EMAIL, "Password1!")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conCampiNonValidiRisponde400() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("non-una-email", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ioSenzaSessioneRisponde401() throws Exception {
        mockMvc.perform(get(IO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ioConLaSessioneDelLoginRestituisceLUtente() throws Exception {
        utenteNelDatabase(true);

        mockMvc.perform(get(IO).session(accedi()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.permessi.length()").value(2));
    }

    /**
     * La ragione per cui si e' passati alle sessioni: disattivare un utente
     * ha effetto subito, senza aspettare che rifaccia il login.
     */
    @Test
    void utenteDisattivatoMentreEraCollegatoVieneRifiutatoAllaRichiestaDopo() throws Exception {
        utenteNelDatabase(true);
        MockHttpSession sessione = accedi();

        // Nel frattempo un amministratore lo disattiva.
        utenteNelDatabase(false);

        mockMvc.perform(get(IO).session(sessione))
                .andExpect(status().isUnauthorized());
    }

    /** Stessa logica per i permessi: un ruolo modificato vale subito. */
    @Test
    void iPermessiModificatiValgonoDallaRichiestaDopoSenzaNuovoAccesso() throws Exception {
        utenteNelDatabase(true);
        MockHttpSession sessione = accedi();

        given(dettagliUtente.loadUserByUsername(EMAIL)).willAnswer(invocazione ->
                User.withUsername(EMAIL).password(HASH).authorities("LAVORI_CREA").build());

        mockMvc.perform(get(IO).session(sessione))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permessi.length()").value(1))
                .andExpect(jsonPath("$.permessi[0]").value("LAVORI_CREA"));
    }
}
