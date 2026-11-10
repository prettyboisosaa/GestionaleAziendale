package com.anitia.backend.tempo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anitia.backend.sicurezza.ConfigurazioneSicurezza;
import java.time.DayOfWeek;
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
 * Il contratto delle rotte del tempo.
 *
 * <p>Il valore sta in due punti. Il primo: registrare tempo <strong>non ha un
 * permesso suo</strong>. Sta su {@code TASK_AGGIORNA_STATO}, che è nel
 * pavimento e dice già «aggiornare lo stato delle task a cui è assegnato»:
 * avviare, mettere in pausa e completare sono quello. Questi test fissano la
 * scelta, così chi un domani aggiungesse un {@code TEMPO_REGISTRA} si trova
 * davanti la ragione per cui non c'è.
 *
 * <p>Il secondo: leggere il tempo di un'altra persona è un'altra cosa, e vuole
 * {@code TEMPO_VISUALIZZA_TUTTI} o {@code TEMPO_VISUALIZZA_LAVORO}. Avere il
 * pavimento non basta, ed è la linea che separa il dipendente dall'ammini-
 * stratore in questa funzionalità.
 *
 * <p>Il servizio è simulato. Le regole che dipendono dai dati — l'assegnazione
 * alla task, la chiusura al tetto, la finestra di correzione, il vincolo di
 * unicità — si verificano contro il database in {@link SessioniEOreTest}.
 */
@WebMvcTest(TempoController.class)
@Import(ConfigurazioneSicurezza.class)
class TempoControllerTest {

    /** Hash di "Password1!", lo stesso dei dati di sviluppo. */
    private static final String HASH =
            "{bcrypt}$2a$10$oC8srOGqrc3iS7pVZXUXNeozTt.rCaDmaXND7K0HWne.kKEjwp4aq";

    private static final String EMAIL = "admin@gestionale.local";
    private static final String MIO = "/api/mio-tempo";
    private static final String SETTIMANA = "/api/mio-tempo/settimana";
    private static final String AVVIA = "/api/lavori/1/task/2/avvia";
    private static final String COMPLETA = "/api/lavori/1/task/2/completa";
    private static final String SESSIONI = "/api/lavori/1/task/2/sessioni";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDetailsService dettagliUtente;

    @MockitoBean
    private ServizioTempo servizio;

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

    private static SituazioneTempo vuota() {
        return new SituazioneTempo(null, 0, List.of());
    }

    // --- il proprio tempo ----------------------------------------------------

    @Test
    void senzaSessioneRisponde401() throws Exception {
        mockMvc.perform(get(MIO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ilProprioTempoConIlPermessoDiBaseRisponde200() throws Exception {
        given(servizio.miaSituazione(any())).willReturn(vuota());

        mockMvc.perform(get(MIO).session(sessioneCon("TASK_AGGIORNA_STATO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inCorso").doesNotExist())
                .andExpect(jsonPath("$.minutiOggi").value(0));
    }

    /**
     * Vedere il tempo degli altri non è registrare il proprio: chi ha solo il
     * permesso di lettura non avvia niente.
     */
    @Test
    void ilProprioTempoConIlSoloPermessoDiLetturaRisponde403() throws Exception {
        mockMvc.perform(get(MIO).session(sessioneCon("TEMPO_VISUALIZZA_TUTTI")))
                .andExpect(status().isForbidden());
    }

    // --- la propria settimana ------------------------------------------------

    /**
     * La griglia della dashboard sta sul pavimento, come il resto di quello
     * che il dipendente fa col proprio tempo: guardare le proprie ore non è
     * un privilegio amministrativo.
     */
    @Test
    void laPropriaSettimanaConIlPermessoDiBaseRisponde200() throws Exception {
        given(servizio.miaSettimana(any(), any())).willReturn(new SettimanaDiTempo(
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15), 95, true,
                List.of(new RigaSessione(7L, 2L, "Revisione contratto", 1L, "LAV-001",
                        LocalDateTime.of(2026, 11, 9, 9, 0),
                        LocalDateTime.of(2026, 11, 9, 10, 35), 95,
                        OrigineSessione.TIMER, true))));

        mockMvc.perform(get(SETTIMANA).session(sessioneCon("TASK_AGGIORNA_STATO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inizio").value("2026-11-09"))
                .andExpect(jsonPath("$.fine").value("2026-11-15"))
                .andExpect(jsonPath("$.minutiTotali").value(95))
                .andExpect(jsonPath("$.sessioni[0].codiceLavoro").value("LAV-001"))
                .andExpect(jsonPath("$.sessioni[0].minuti").value(95))
                .andExpect(jsonPath("$.modificabile").value(true));
    }

    /**
     * Il giorno chiesto arriva al servizio così com'è, senza che il controller
     * provi a risalire al lunedì: la settimana la decide un posto solo.
     */
    @Test
    void laSettimanaDiUnGiornoPreciso() throws Exception {
        given(servizio.miaSettimana(any(), any())).willReturn(
                new SettimanaDiTempo(LocalDate.of(2026, 11, 2),
                        LocalDate.of(2026, 11, 8), 0, false, List.of()));

        mockMvc.perform(get(SETTIMANA).param("giorno", "2026-11-05")
                        .session(sessioneCon("TASK_AGGIORNA_STATO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inizio").value("2026-11-02"))
                // Una settimana chiusa lo dice, così la griglia resta inerte
                // invece di aprire un pannello che prenderebbe un 409.
                .andExpect(jsonPath("$.modificabile").value(false));

        then(servizio).should().miaSettimana(any(), eq(LocalDate.of(2026, 11, 5)));
    }

    /** Senza il parametro è la settimana di oggi: la dashboard non lo manda. */
    @Test
    void laSettimanaSenzaParametroEQuellaDiOggi() throws Exception {
        given(servizio.miaSettimana(any(), any())).willReturn(
                new SettimanaDiTempo(LocalDate.now().with(DayOfWeek.MONDAY),
                        LocalDate.now().with(DayOfWeek.MONDAY).plusDays(6), 0, true,
                        List.of()));

        mockMvc.perform(get(SETTIMANA).session(sessioneCon("TASK_AGGIORNA_STATO")))
                .andExpect(status().isOk());

        then(servizio).should().miaSettimana(any(), eq(LocalDate.now()));
    }

    /** Un giorno illeggibile è una richiesta malfatta, non una settimana vuota. */
    @Test
    void unGiornoNonValidoRisponde400() throws Exception {
        mockMvc.perform(get(SETTIMANA).param("giorno", "settimana-scorsa")
                        .session(sessioneCon("TASK_AGGIORNA_STATO")))
                .andExpect(status().isBadRequest());

        then(servizio).shouldHaveNoInteractions();
    }

    @Test
    void laPropriaSettimanaSenzaSessioneRisponde401() throws Exception {
        mockMvc.perform(get(SETTIMANA))
                .andExpect(status().isUnauthorized());
    }

    // --- avvia, pausa, completa ----------------------------------------------

    @Test
    void avviareSenzaIlPermessoDiBaseRisponde403() throws Exception {
        mockMvc.perform(post(AVVIA).session(sessioneCon("LAVORI_VISUALIZZA_TUTTI")))
                .andExpect(status().isForbidden());

        then(servizio).shouldHaveNoInteractions();
    }

    @Test
    void avviareRestituisceLaSituazioneConLaTaskInCorso() throws Exception {
        given(servizio.avvia(any(), eq(1L), eq(2L))).willReturn(new SituazioneTempo(
                new SessioneInCorso(7L, 2L, "Revisione contratto", 1L, "LAV-001",
                        LocalDateTime.of(2026, 11, 9, 9, 0), 0),
                0, List.of()));

        mockMvc.perform(post(AVVIA).session(sessioneCon("TASK_AGGIORNA_STATO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inCorso.taskId").value(2))
                .andExpect(jsonPath("$.inCorso.task").value("Revisione contratto"));
    }

    /**
     * Avviare una task a cui non si è assegnati: il 403 nasce nel servizio,
     * perché l'assegnazione è un dato e un'annotazione non la consulta.
     *
     * <p>Vale anche per chi potrebbe gestire quella task — un amministratore,
     * o il responsabile del lavoro. Il timer misura il tempo di chi lo avvia:
     * scriverlo su una task altrui vorrebbe dire mettere le proprie ore nel
     * consuntivo del lavoro di un collega. Chi ci lavora davvero si assegna
     * la task, cosa che il suo permesso di gestione gli consente.
     */
    @Test
    void avviareUnaTaskNonPropriaRisponde403() throws Exception {
        given(servizio.avvia(any(), anyLong(), anyLong())).willThrow(
                new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Non sei assegnato alla task «Preventivo Rossi»: puoi registrare "
                                + "tempo solo sulle task che ti sono state affidate."));

        mockMvc.perform(post(AVVIA).session(sessioneCon("TASK_AGGIORNA_STATO")))
                .andExpect(status().isForbidden());
    }

    /** Avviare la task che sta già scorrendo non è un passaggio: è un doppio clic. */
    @Test
    void avviareLaTaskGiaInCorsoRisponde409() throws Exception {
        given(servizio.avvia(any(), anyLong(), anyLong())).willThrow(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "Stai già lavorando a «Revisione contratto»."));

        mockMvc.perform(post(AVVIA).session(sessioneCon("TASK_AGGIORNA_STATO")))
                .andExpect(status().isConflict());
    }

    @Test
    void laPausaNonVuoleSapereQualeTask() throws Exception {
        given(servizio.pausa(any())).willReturn(vuota());

        mockMvc.perform(post(MIO + "/pausa").session(sessioneCon("TASK_AGGIORNA_STATO")))
                .andExpect(status().isOk());

        then(servizio).should().pausa(any());
    }

    @Test
    void laPausaSenzaNessunaTaskAvviataRisponde409() throws Exception {
        given(servizio.pausa(any())).willThrow(new ResponseStatusException(
                HttpStatus.CONFLICT, "Non hai nessuna task avviata."));

        mockMvc.perform(post(MIO + "/pausa").session(sessioneCon("TASK_AGGIORNA_STATO")))
                .andExpect(status().isConflict());
    }

    @Test
    void completarePassaLavoroETask() throws Exception {
        given(servizio.completa(any(), eq(1L), eq(2L))).willReturn(vuota());

        mockMvc.perform(post(COMPLETA).session(sessioneCon("TASK_AGGIORNA_STATO")))
                .andExpect(status().isOk());

        then(servizio).should().completa(any(), eq(1L), eq(2L));
    }

    // --- la sessione scritta a mano ------------------------------------------

    /**
     * Scrivere a mano non ha un permesso suo, come il resto di quello che il
     * dipendente fa col proprio tempo: è la stessa scelta che questo file
     * fissa per avvia, pausa e completa.
     */
    @Test
    void registrareAManoConIlPermessoDiBaseRisponde200() throws Exception {
        given(servizio.registraManuale(any(), eq(1L), eq(2L),
                eq(LocalDateTime.of(2026, 11, 9, 9, 0)),
                eq(LocalDateTime.of(2026, 11, 9, 13, 0)))).willReturn(vuota());

        mockMvc.perform(post(SESSIONI).session(sessioneCon("TASK_AGGIORNA_STATO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avviataIl\":\"2026-11-09T09:00:00\","
                                + "\"chiusaIl\":\"2026-11-09T13:00:00\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void registrareAManoSenzaIlPermessoDiBaseRisponde403() throws Exception {
        mockMvc.perform(post(SESSIONI).session(sessioneCon("TEMPO_VISUALIZZA_TUTTI"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avviataIl\":\"2026-11-09T09:00:00\","
                                + "\"chiusaIl\":\"2026-11-09T13:00:00\"}"))
                .andExpect(status().isForbidden());

        then(servizio).shouldHaveNoInteractions();
    }

    /** Un intervallo senza estremi non è una sessione da zero minuti. */
    @Test
    void registrareAManoSenzaGliEstremiRisponde400() throws Exception {
        mockMvc.perform(post(SESSIONI).session(sessioneCon("TASK_AGGIORNA_STATO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        then(servizio).shouldHaveNoInteractions();
    }

    /**
     * Su una task non propria non si scrive, nemmeno potendola gestire: il
     * 403 nasce nel servizio, perché l'assegnazione è un dato.
     */
    @Test
    void registrareAManoSuUnaTaskNonPropriaRisponde403() throws Exception {
        given(servizio.registraManuale(any(), anyLong(), anyLong(), any(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Non sei assegnato alla task «Preventivo Rossi»: puoi registrare "
                                + "tempo solo sulle task che ti sono state affidate."));

        mockMvc.perform(post(SESSIONI).session(sessioneCon("TASK_AGGIORNA_STATO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avviataIl\":\"2026-11-09T09:00:00\","
                                + "\"chiusaIl\":\"2026-11-09T13:00:00\"}"))
                .andExpect(status().isForbidden());
    }

    /** Del tempo nel futuro non è un ricordo: è un proposito. */
    @Test
    void registrareAManoNelFuturoRisponde400() throws Exception {
        given(servizio.registraManuale(any(), anyLong(), anyLong(), any(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "TEMPO_NEL_FUTURO: non si registra tempo che non è ancora passato."));

        mockMvc.perform(post(SESSIONI).session(sessioneCon("TASK_AGGIORNA_STATO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avviataIl\":\"2027-01-04T09:00:00\","
                                + "\"chiusaIl\":\"2027-01-04T13:00:00\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrareAManoInUnaSettimanaChiusaRisponde409() throws Exception {
        given(servizio.registraManuale(any(), anyLong(), anyLong(), any(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                        "Il tempo del 2026-11-02 non si scrive più: si sistema entro la "
                                + "settimana in cui si è lavorato."));

        mockMvc.perform(post(SESSIONI).session(sessioneCon("TASK_AGGIORNA_STATO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avviataIl\":\"2026-11-02T09:00:00\","
                                + "\"chiusaIl\":\"2026-11-02T13:00:00\"}"))
                .andExpect(status().isConflict());
    }

    /** Il conflitto nomina la task con cui si accavalla: altrimenti non si sa che spostare. */
    @Test
    void registrareAManoSopraUnAltraSessioneRisponde409() throws Exception {
        given(servizio.registraManuale(any(), anyLong(), anyLong(), any(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                        "In quelle ore hai già «Preventivo Rossi», dalle 09:00 alle 11:00. "
                                + "Le ore non si sovrappongono."));

        mockMvc.perform(post(SESSIONI).session(sessioneCon("TASK_AGGIORNA_STATO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avviataIl\":\"2026-11-09T10:00:00\","
                                + "\"chiusaIl\":\"2026-11-09T12:00:00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("Preventivo Rossi")));
    }

    // --- conferme e correzioni -----------------------------------------------

    @Test
    void confermareUnaChiusuraDUfficio() throws Exception {
        given(servizio.conferma(any(), eq(7L))).willReturn(vuota());

        mockMvc.perform(post(MIO + "/sessioni/7/conferma")
                        .session(sessioneCon("TASK_AGGIORNA_STATO")))
                .andExpect(status().isOk());

        then(servizio).should().conferma(any(), eq(7L));
    }

    @Test
    void correggereSenzaGliEstremiRisponde400() throws Exception {
        mockMvc.perform(put(MIO + "/sessioni/7").session(sessioneCon("TASK_AGGIORNA_STATO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Fuori dalla finestra il server rifiuta, e il messaggio dice qual è la
     * regola.
     *
     * <p>La finestra è la <strong>settimana</strong>, non più la giornata: da
     * quando la griglia settimanale permette di riempire un giorno che non è
     * stato registrato, un giorno di finestra mancherebbe lo scopo — di un
     * giovedì dimenticato ci si accorge il venerdì. Una settimana chiusa,
     * invece, resta chiusa: le ore entrano nel consuntivo di un lavoro, e un
     * consuntivo deve potersi fermare.
     */
    @Test
    void correggereIlTempoDiUnaSettimanaChiusaRisponde409() throws Exception {
        given(servizio.correggi(any(), anyLong(), any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "Il tempo del 2026-11-02 non si scrive più: si sistema entro la "
                                + "settimana in cui si è lavorato."));

        mockMvc.perform(put(MIO + "/sessioni/7").session(sessioneCon("TASK_AGGIORNA_STATO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avviataIl\":\"2026-11-02T09:00:00\","
                                + "\"chiusaIl\":\"2026-11-02T11:00:00\"}"))
                .andExpect(status().isConflict());
    }

    /**
     * Una correzione che porta la sessione addosso a un'altra viene respinta,
     * e il messaggio <strong>nomina</strong> quella in conflitto: un «si
     * sovrappone» senza dire a che cosa è un errore su cui non si può agire.
     */
    @Test
    void correggereSopraUnAltraSessioneRisponde409() throws Exception {
        given(servizio.correggi(any(), anyLong(), any(), any())).willThrow(
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "In quelle ore hai già «Preventivo Rossi», dalle 14:00 alle 16:00. "
                                + "Le ore non si sovrappongono."));

        mockMvc.perform(put(MIO + "/sessioni/7").session(sessioneCon("TASK_AGGIORNA_STATO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avviataIl\":\"2026-11-10T14:30:00\","
                                + "\"chiusaIl\":\"2026-11-10T15:30:00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("Preventivo Rossi")));
    }

    /** Il tempo di un altro giorno della settimana si corregge: è la finestra nuova. */
    @Test
    void correggereUnAltroGiornoDellaSettimanaRisponde200() throws Exception {
        given(servizio.correggi(any(), eq(7L),
                eq(LocalDateTime.of(2026, 11, 9, 9, 0)),
                eq(LocalDateTime.of(2026, 11, 9, 13, 0)))).willReturn(vuota());

        mockMvc.perform(put(MIO + "/sessioni/7").session(sessioneCon("TASK_AGGIORNA_STATO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avviataIl\":\"2026-11-09T09:00:00\","
                                + "\"chiusaIl\":\"2026-11-09T13:00:00\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void correggereConGliEstremiRisponde200() throws Exception {
        given(servizio.correggi(any(), eq(7L),
                eq(LocalDateTime.of(2026, 11, 9, 9, 0)),
                eq(LocalDateTime.of(2026, 11, 9, 11, 0)))).willReturn(vuota());

        mockMvc.perform(put(MIO + "/sessioni/7").session(sessioneCon("TASK_AGGIORNA_STATO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avviataIl\":\"2026-11-09T09:00:00\","
                                + "\"chiusaIl\":\"2026-11-09T11:00:00\"}"))
                .andExpect(status().isOk());
    }

    // --- il tempo degli altri ------------------------------------------------

    @Test
    void ilTempoDiUnAltroConIlSoloPavimentoRisponde403() throws Exception {
        mockMvc.perform(get("/api/tempo/utente/5?da=2026-11-01&a=2026-11-30")
                        .session(sessioneCon("TASK_AGGIORNA_STATO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ilTempoDiUnAltroConIlPermessoSuTuttiRisponde200() throws Exception {
        given(servizio.diUnaPersona(any(), eq(5L), any(), any())).willReturn(
                new TempoDiUnaPersona(5L, java.time.LocalDate.of(2026, 11, 1),
                        java.time.LocalDate.of(2026, 11, 30), 480, 120, List.of()));

        mockMvc.perform(get("/api/tempo/utente/5?da=2026-11-01&a=2026-11-30")
                        .session(sessioneCon("TEMPO_VISUALIZZA_TUTTI")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutiTotali").value(480))
                .andExpect(jsonPath("$.minutiNonConfermati").value(120));
    }

    /** Il permesso sul lavoro apre la rotta; chi si può vedere lo decide il servizio. */
    @Test
    void ilTempoDiUnAltroConIlPermessoSulLavoroRisponde200() throws Exception {
        given(servizio.diUnaPersona(any(), eq(5L), any(), any())).willReturn(
                new TempoDiUnaPersona(5L, java.time.LocalDate.of(2026, 11, 1),
                        java.time.LocalDate.of(2026, 11, 30), 0, 0, List.of()));

        mockMvc.perform(get("/api/tempo/utente/5?da=2026-11-01&a=2026-11-30")
                        .session(sessioneCon("TEMPO_VISUALIZZA_LAVORO")))
                .andExpect(status().isOk());
    }

    @Test
    void leConfermeInAttesaVoglionoIlPermessoSuTutti() throws Exception {
        mockMvc.perform(get("/api/tempo/da-confermare")
                        .session(sessioneCon("TEMPO_VISUALIZZA_LAVORO")))
                .andExpect(status().isForbidden());
    }
}
