package com.anitia.backend.assenza;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anitia.backend.sicurezza.ConfigurazioneSicurezza;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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

/**
 * Il contratto delle rotte delle assenze, che è una linea sola e netta:
 * <strong>chiedere e decidere sono due permessi diversi</strong>.
 *
 * <p>{@code ASSENZE_RICHIEDI} è un permesso di base, quindi ce l'hanno tutti:
 * chiedere ferie non è un privilegio. {@code ASSENZE_GESTISCI} no, ed è lui il
 * confine che conta — chi non lo ha non deve poter vedere le richieste di tutta
 * l'azienda né decidere su una di esse.
 *
 * <p>Le authority qui si iniettano a mano, quindi le combinazioni che un utente
 * reale non potrebbe avere — essere senza {@code ASSENZE_RICHIEDI} — restano
 * verificabili: è il contratto dell'annotazione, e vale anche quando il
 * pavimento lo soddisfa sempre.
 *
 * <p>Il servizio è simulato. Le regole che dipendono dai dati — la
 * sovrapposizione fra due richieste, lo stato da cui si può ritirare, il fatto
 * che una richiesta sia propria — si verificano contro il database.
 */
@WebMvcTest(AssenzeController.class)
@Import(ConfigurazioneSicurezza.class)
class AssenzeControllerTest {

    /** Hash di "Password1!", lo stesso dei dati di sviluppo. */
    private static final String HASH =
            "{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq";

    private static final String EMAIL = "admin@gestionale.local";
    private static final String MIE = "/api/mie-assenze";
    private static final String TUTTE = "/api/assenze";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDetailsService dettagliUtente;

    @MockitoBean
    private ServizioAssenze servizio;

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

    // --- le proprie richieste -----------------------------------------------

    @Test
    void senzaSessioneRisponde401() throws Exception {
        mockMvc.perform(get(MIE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void leProprieAssenzeConIlPermessoDiBaseRispondono200() throws Exception {
        given(servizio.mie(any())).willReturn(List.of());

        mockMvc.perform(get(MIE).session(sessioneCon("ASSENZE_RICHIEDI")))
                .andExpect(status().isOk());
    }

    /**
     * Decidere non è chiedere: chi gestisce le richieste degli altri non
     * eredita per questo la rotta delle proprie.
     */
    @Test
    void leProprieAssenzeConIlSoloPermessoDiGestioneRispondono403() throws Exception {
        mockMvc.perform(get(MIE).session(sessioneCon("ASSENZE_GESTISCI")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ritirareUnaRichiestaSenzaIlPermessoRisponde403() throws Exception {
        mockMvc.perform(post(MIE + "/1/ritira").session(sessioneCon("ASSENZE_GESTISCI")))
                .andExpect(status().isForbidden());
    }

    // --- la coda di chi decide ----------------------------------------------

    /**
     * Il controllo che conta: senza {@code ASSENZE_GESTISCI} non si vedono le
     * richieste di tutta l'azienda, e averne una propria non basta.
     */
    @Test
    void laCodaSenzaIlPermessoDiGestioneRisponde403() throws Exception {
        mockMvc.perform(get(TUTTE).session(sessioneCon("ASSENZE_RICHIEDI")))
                .andExpect(status().isForbidden());
    }

    @Test
    void laCodaConIlPermessoDiGestioneRisponde200() throws Exception {
        given(servizio.tutte(any(), any())).willReturn(List.of());

        mockMvc.perform(get(TUTTE).session(sessioneCon("ASSENZE_GESTISCI")))
                .andExpect(status().isOk());
    }

    @Test
    void rifiutareSenzaIlPermessoDiGestioneRisponde403() throws Exception {
        mockMvc.perform(post(TUTTE + "/1/rifiuta").session(sessioneCon("ASSENZE_RICHIEDI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\":\"non in questo periodo\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * Che il 403 arrivi dall'annotazione e non dal servizio: se la richiesta
     * passasse e fosse il servizio a fermarla, il permesso sulla rotta non
     * starebbe facendo il suo lavoro, e un domani basterebbe toglierlo dal
     * servizio per aprire la rotta a tutti.
     */
    @Test
    void approvareSenzaPermessoNonRaggiungeIlServizio() throws Exception {
        mockMvc.perform(post(TUTTE + "/1/approva").session(sessioneCon("ASSENZE_RICHIEDI")))
                .andExpect(status().isForbidden());

        then(servizio).shouldHaveNoInteractions();
    }

    // --- il confine fra le due metà -----------------------------------------

    /**
     * Chi ha entrambi i permessi raggiunge entrambe le metà. È il caso
     * dell'amministratore, e dice anche quello che il gestionale
     * <strong>non</strong> impedisce: approvare una richiesta propria. Il
     * vincolo richiedente ≠ decisore non c'è, né qui né nel servizio.
     */
    @Test
    void conEntrambiIPermessiSiRaggiungonoLeDueMeta() throws Exception {
        given(servizio.mie(any())).willReturn(List.of());
        given(servizio.tutte(any(), any())).willReturn(List.of());

        MockHttpSession sessione = sessioneCon("ASSENZE_RICHIEDI", "ASSENZE_GESTISCI");

        mockMvc.perform(get(MIE).session(sessione)).andExpect(status().isOk());
        mockMvc.perform(get(TUTTE).session(sessione)).andExpect(status().isOk());
    }

    @Test
    void laCodaFiltraPerStatoEPerPersona() throws Exception {
        given(servizio.tutte(eq(StatoAssenza.IN_ATTESA), eq(5L))).willReturn(List.of());

        // Il parametro si chiama utente_id, non utenteId: lo dichiara
        // @RequestParam(name = "utente_id") sul controller.
        mockMvc.perform(get(TUTTE + "?stato=IN_ATTESA&utente_id=5")
                        .session(sessioneCon("ASSENZE_GESTISCI")))
                .andExpect(status().isOk());

        then(servizio).should().tutte(eq(StatoAssenza.IN_ATTESA), eq(5L));
    }

    @Test
    void unoStatoInesistenteRisponde400() throws Exception {
        mockMvc.perform(get(TUTTE + "?stato=PIPPO").session(sessioneCon("ASSENZE_GESTISCI")))
                .andExpect(status().isBadRequest());
    }
}
