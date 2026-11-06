package com.anitia.backend.dichiarazione;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
 * Il contratto delle rotte della dichiarazione giornaliera.
 *
 * <p>Il valore sta in tre punti.
 *
 * <p>Primo: una GET su un giorno senza dichiarazione risponde 200 con
 * {@code id} nullo, non 404. È quello che permette al frontend di disegnare un
 * giorno vuoto senza trattare l'assenza come un errore — e il server non
 * crea niente per una lettura.
 *
 * <p>Secondo: {@code DICHIARAZIONI_COMPILA} serve per scrivere sulla propria
 * giornata, e i permessi di lettura del team o dell'azienda non lo
 * sostituiscono. Vedere le ore di qualcuno non è registrarle.
 *
 * <p>Terzo: il 409 del tetto giornaliero porta nel messaggio <strong>quante
 * ore restano</strong>. Senza quel numero, chi compila sa solo di aver
 * sbagliato.
 */
@WebMvcTest(DichiarazioniController.class)
@Import(ConfigurazioneSicurezza.class)
class DichiarazioniControllerTest {

    /** Hash di "Password1!", lo stesso dei dati di sviluppo. */
    private static final String HASH =
            "{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq";

    private static final String EMAIL = "giulia.verdi@gestionale.local";
    private static final String GIORNO = "/api/dichiarazioni/2026-11-02";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDetailsService dettagliUtente;

    @MockitoBean
    private ServizioDichiarazioni servizio;

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

    private static String corpoVoce(String taskId, String ore, String descrizione) {
        return "{\"taskId\":" + taskId + ",\"ore\":" + ore
                + ",\"descrizione\":" + descrizione + "}";
    }

    /** Una giornata vuota: nel database non c'è ancora niente. */
    private static Giornata vuota() {
        return new Giornata(LocalDate.of(2026, 11, 2), null, TipoGiornata.LAVORATA,
                StatoDichiarazione.BOZZA, null, BigDecimal.ZERO, new BigDecimal("8.00"),
                new BigDecimal("8.00"), true, false, List.of(),
                List.of(new TaskDichiarabile(2L, "Sviluppo area riservata", 1L, "LAV-001",
                        "Portale clienti")));
    }

    /** Una giornata con sei ore registrate: ne restano due. */
    private static Giornata conSeiOre() {
        return new Giornata(LocalDate.of(2026, 11, 2), 1L, TipoGiornata.LAVORATA,
                StatoDichiarazione.BOZZA, null, new BigDecimal("6.00"), new BigDecimal("8.00"),
                new BigDecimal("2.00"), true, false,
                List.of(new RigaVoce(1L, 2L, "Sviluppo area riservata", "LAV-001", 1L,
                        new BigDecimal("6.00"), "Pagina di login")),
                List.of());
    }

    // --- lettura ------------------------------------------------------------

    @Test
    void giornataSenzaSessioneRisponde401() throws Exception {
        mockMvc.perform(get(GIORNO))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Un giorno senza dichiarazione non è un errore: risponde 200 con id
     * nullo, e il frontend disegna una giornata da compilare.
     */
    @Test
    void giornataSenzaDichiarazioneRisponde200ConIdNullo() throws Exception {
        given(servizio.mia(any(), any())).willReturn(vuota());

        mockMvc.perform(get(GIORNO).session(sessioneCon("DICHIARAZIONI_COMPILA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((Object) null))
                .andExpect(jsonPath("$.oreResidue").value(8.00))
                .andExpect(jsonPath("$.taskDisponibili.length()").value(1));
    }

    @Test
    void giornataSenzaIlPermessoRisponde403() throws Exception {
        mockMvc.perform(get(GIORNO).session(sessioneCon("TASK_VISUALIZZA_ASSEGNATE")))
                .andExpect(status().isForbidden());
    }

    /** Una data che non è una data deve dare 400, non 500. */
    @Test
    void giornataConDataMalformataRisponde400() throws Exception {
        mockMvc.perform(get("/api/dichiarazioni/ieri")
                        .session(sessioneCon("DICHIARAZIONI_COMPILA")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elencoDelPeriodoRisponde200() throws Exception {
        given(servizio.mieDelPeriodo(any(), any(), any())).willReturn(List.of(
                new RigaGiorno(1L, LocalDate.of(2026, 11, 2), TipoGiornata.LAVORATA,
                        StatoDichiarazione.INVIATA, null, new BigDecimal("8.00"))));

        mockMvc.perform(get("/api/dichiarazioni?da=2026-11-01&a=2026-11-30")
                        .session(sessioneCon("DICHIARAZIONI_COMPILA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].oreTotali").value(8.00));
    }

    /** Le dichiarazioni altrui hanno i loro permessi, diversi da quello di compilare. */
    @Test
    void dichiarazioniDiUnAltroConIlSoloPermessoDiCompilareRisponde403() throws Exception {
        mockMvc.perform(get("/api/dichiarazioni/utente/5?da=2026-11-01&a=2026-11-30")
                        .session(sessioneCon("DICHIARAZIONI_COMPILA")))
                .andExpect(status().isForbidden());
    }

    @Test
    void dichiarazioniDiUnAltroConIlPermessoSulTeamRisponde200() throws Exception {
        given(servizio.diUnaPersona(any(), any(), any(), any())).willReturn(List.of());

        mockMvc.perform(get("/api/dichiarazioni/utente/5?da=2026-11-01&a=2026-11-30")
                        .session(sessioneCon("DICHIARAZIONI_VISUALIZZA_TEAM")))
                .andExpect(status().isOk());
    }

    // --- registrazione delle ore --------------------------------------------

    @Test
    void registrazioneSenzaIlPermessoDiCompilareRisponde403() throws Exception {
        mockMvc.perform(post(GIORNO + "/voci")
                        .session(sessioneCon("DICHIARAZIONI_VISUALIZZA_TUTTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoVoce("2", "6.00", "\"Pagina di login\"")))
                .andExpect(status().isForbidden());
    }

    /** Risponde con la giornata intera, non con la voce: è quello che il frontend ridisegna. */
    @Test
    void registrazioneRestituisceLaGiornataAggiornata() throws Exception {
        given(servizio.registraOre(any(), any(), any())).willReturn(conSeiOre());

        mockMvc.perform(post(GIORNO + "/voci").session(sessioneCon("DICHIARAZIONI_COMPILA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoVoce("2", "6.00", "\"Pagina di login\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oreTotali").value(6.00))
                .andExpect(jsonPath("$.oreResidue").value(2.00))
                .andExpect(jsonPath("$.voci.length()").value(1));
    }

    /** La descrizione è obbligatoria: il piano chiede che ogni ora dica cosa si è fatto. */
    @Test
    void registrazioneSenzaDescrizioneRisponde400() throws Exception {
        mockMvc.perform(post(GIORNO + "/voci").session(sessioneCon("DICHIARAZIONI_COMPILA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoVoce("2", "6.00", "null")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrazioneDiZeroOreRisponde400() throws Exception {
        mockMvc.perform(post(GIORNO + "/voci").session(sessioneCon("DICHIARAZIONI_COMPILA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoVoce("2", "0", "\"Niente\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrazioneSenzaTaskRisponde400() throws Exception {
        mockMvc.perform(post(GIORNO + "/voci").session(sessioneCon("DICHIARAZIONI_COMPILA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoVoce("null", "6.00", "\"Qualcosa\"")))
                .andExpect(status().isBadRequest());
    }

    /** Il messaggio deve dire quante ore restano, non solo che non ci stanno. */
    @Test
    void registrazioneOltreIlTettoRisponde409ConLeOreResidue() throws Exception {
        given(servizio.registraOre(any(), any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "Il 2026-11-02 hai già registrato 6.00 ore sulle 8.00 della giornata: "
                                + "puoi aggiungerne ancora 2.00, non 3.00."));

        mockMvc.perform(post(GIORNO + "/voci").session(sessioneCon("DICHIARAZIONI_COMPILA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoVoce("2", "3.00", "\"Altro\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Il 2026-11-02 hai già registrato 6.00 ore sulle 8.00 della giornata: "
                                + "puoi aggiungerne ancora 2.00, non 3.00."));
    }

    /** Si registra solo sulle task a cui si è assegnati. */
    @Test
    void registrazioneSuUnaTaskNonAssegnataRisponde403() throws Exception {
        given(servizio.registraOre(any(), any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Non sei assegnato alla task «Mappatura dati»: chiedi al responsabile "
                                + "del lavoro di assegnartela."));

        mockMvc.perform(post(GIORNO + "/voci").session(sessioneCon("DICHIARAZIONI_COMPILA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoVoce("4", "4.00", "\"Mappatura\"")))
                .andExpect(status().isForbidden());
    }

    /** Si registra solo sulle task aperte. */
    @Test
    void registrazioneSuUnaTaskChiusaRisponde409() throws Exception {
        given(servizio.registraOre(any(), any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "La task «Analisi requisiti» non è più aperta: non accetta "
                                + "registrazioni di ore."));

        mockMvc.perform(post(GIORNO + "/voci").session(sessioneCon("DICHIARAZIONI_COMPILA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoVoce("1", "4.00", "\"Requisiti\"")))
                .andExpect(status().isConflict());
    }

    @Test
    void registrazioneSuUnaGiornataFuturaRisponde400() throws Exception {
        given(servizio.registraOre(any(), any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Il 2027-01-01 non è ancora arrivato: non si può dichiarare una "
                                + "giornata futura."));

        mockMvc.perform(post("/api/dichiarazioni/2027-01-01/voci")
                        .session(sessioneCon("DICHIARAZIONI_COMPILA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoVoce("2", "4.00", "\"Qualcosa\"")))
                .andExpect(status().isBadRequest());
    }

    // --- correzione e rimozione ---------------------------------------------

    @Test
    void rimozioneDiUnaVoceRestituisceLaGiornataAggiornata() throws Exception {
        given(servizio.togliVoce(any(), any(), any())).willReturn(vuota());

        mockMvc.perform(delete(GIORNO + "/voci/1").session(sessioneCon("DICHIARAZIONI_COMPILA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oreTotali").value(0));
    }

    @Test
    void rimozioneDiUnaVoceInesistenteRisponde404() throws Exception {
        given(servizio.togliVoce(any(), any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Voce non trovata in questa giornata."));

        mockMvc.perform(delete(GIORNO + "/voci/99")
                        .session(sessioneCon("DICHIARAZIONI_COMPILA")))
                .andExpect(status().isNotFound());
    }

    /** Passata la finestra, una dichiarazione inviata non si corregge più. */
    @Test
    void correzioneOltreLaFinestraRisponde409() throws Exception {
        given(servizio.togliVoce(any(), any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "La giornata del 2026-11-02 è stata inviata e il termine per "
                                + "correggerla è passato. Chiedi a un amministratore."));

        mockMvc.perform(delete(GIORNO + "/voci/1").session(sessioneCon("DICHIARAZIONI_COMPILA")))
                .andExpect(status().isConflict());
    }

    // --- tipo di giornata e invio -------------------------------------------

    @Test
    void cambioTipoRisponde200() throws Exception {
        given(servizio.cambiaTipo(any(), any(), any())).willReturn(vuota());

        mockMvc.perform(post(GIORNO + "/tipo").session(sessioneCon("DICHIARAZIONI_COMPILA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoGiornata\":\"FERIE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void cambioTipoSenzaTipoRisponde400() throws Exception {
        mockMvc.perform(post(GIORNO + "/tipo").session(sessioneCon("DICHIARAZIONI_COMPILA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoGiornata\":null}"))
                .andExpect(status().isBadRequest());
    }

    /** Le ore già scritte non si buttano via di nascosto. */
    @Test
    void ferieConOreGiaRegistrateRisponde409() throws Exception {
        given(servizio.cambiaTipo(any(), any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "Il 2026-11-02 ha 8.00 ore registrate: togli prima le voci, poi "
                                + "dichiara la giornata come ferie."));

        mockMvc.perform(post(GIORNO + "/tipo").session(sessioneCon("DICHIARAZIONI_COMPILA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoGiornata\":\"FERIE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invioRisponde200() throws Exception {
        given(servizio.invia(any(), any())).willReturn(conSeiOre());

        mockMvc.perform(post(GIORNO + "/invia").session(sessioneCon("DICHIARAZIONI_COMPILA")))
                .andExpect(status().isOk());
    }

    @Test
    void invioDiUnaGiornataSenzaOreRisponde409() throws Exception {
        given(servizio.invia(any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "La giornata del 2026-11-02 non ha nessuna ora registrata: aggiungi "
                                + "almeno una voce, oppure dichiarala come ferie, permesso o "
                                + "malattia."));

        mockMvc.perform(post(GIORNO + "/invia").session(sessioneCon("DICHIARAZIONI_COMPILA")))
                .andExpect(status().isConflict());
    }
}
