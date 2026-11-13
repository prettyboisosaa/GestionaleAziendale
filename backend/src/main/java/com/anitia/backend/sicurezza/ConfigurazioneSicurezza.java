package com.anitia.backend.sicurezza;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractAuthenticationFilterConfigurer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Sicurezza del backend: autenticazione a sessione, con il cookie
 * {@code JSESSIONID}. Nessun token da gestire e nessun segreto di firma.
 *
 * <p>Il timeout e' configurato in {@code application.properties} e si rinnova
 * a ogni richiesta, come dice la riga {@code sessione.timeout_minuti} della
 * tabella {@code impostazione}: minuti di inattivita', non durata fissa.
 *
 * <p>{@code @EnableMethodSecurity} attiva i {@code @PreAuthorize} sui
 * controller. La catena di filtri si limita a pretendere che l'utente sia
 * autenticato: che cosa gli e' permesso fare lo decide il permesso dichiarato
 * sul singolo metodo. I permessi valutati sono quelli correnti, perche'
 * {@link FiltroUtenteAggiornato} li rilegge dal database a ogni richiesta.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ConfigurazioneSicurezza {

    /**
     * Le rotte di Swagger, aperte a chi non ha fatto l'accesso.
     *
     * <p>Sono tre e servono tutte e tre: {@code /v3/api-docs/**} e' il
     * documento OpenAPI (il pattern copre anche {@code /v3/api-docs} nudo e
     * {@code /v3/api-docs/swagger-config}, che la pagina chiede per prima),
     * {@code /swagger-ui/**} sono i file della pagina che lo sfoglia, e
     * {@code /swagger-ui.html} e' l'indirizzo che si digita, che reindirizza
     * dentro la cartella. Lasciarne fuori una qualunque si vede allo stesso
     * modo: la pagina si apre bianca o resta a «caricamento».
     *
     * <p>⚠️ Aperte **senza autenticazione**: l'elenco completo delle rotte,
     * dei corpi accettati e dei codici d'errore e' la mappa di come attaccare
     * il gestionale. Questa {@code permitAll()} resta perche' in sviluppo la
     * pagina deve aprirsi senza accesso; in produzione la risposta non e'
     * qui ma in {@code application-prod.properties}, che spegne springdoc del
     * tutto. Spento, queste tre rotte non esistono e la regola non concede
     * niente.
     */
    private static final String[] SWAGGER = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
    };

    /**
     * La salute dell'applicazione, aperta a chi non ha fatto l'accesso.
     *
     * <p>Deve esserlo: chi la interroga e' il motore dei container — la
     * {@code healthcheck} del compose, le probe di Kubernetes — che non ha
     * credenziali e non puo' averne. E' lo stesso motivo per cui prima le
     * probe erano su TCP: ogni {@code /api/**} risponde 401, che una probe
     * legge come un guasto, e il pod finiva per riavviarsi pur essendo sano.
     *
     * <p>Quello che si lascia vedere e' il minimo: {@code show-details=never}
     * in {@code application.properties} riduce la risposta a
     * {@code {"status":"UP"}}. Senza quella riga, questa permitAll
     * racconterebbe a chiunque quale database si usa e che cosa non sta
     * funzionando.
     *
     * <p>Solo {@code health}, e per estensione i suoi gruppi
     * {@code liveness}/{@code readiness}: il pattern non e'
     * {@code /actuator/**}, perche' quello aprirebbe anche gli endpoint che
     * un domani qualcuno aggiungesse a
     * {@code management.endpoints.web.exposure.include}.
     *
     * <p>Due voci e non una: la rotta nuda e i suoi gruppi. Il pattern
     * {@code /**} da solo coprirebbe gia' entrambe con PathPattern, ma
     * dipendere di quale matcher sia in uso per una regola di sicurezza e'
     * esattamente il genere di sottigliezza che si rompe in silenzio a un
     * aggiornamento.
     */
    private static final String[] SALUTE = {
            "/actuator/health",
            "/actuator/health/**",
    };

    @Bean
    SecurityFilterChain catenaFiltri(HttpSecurity http,
                                     SecurityContextRepository contestoSicurezza,
                                     UserDetailsService dettagliUtente) throws Exception {
        return http
                // I token CSRF sono disattivati di proposito, non per
                // dimenticanza: la difesa e' SameSite=Strict sul cookie di
                // sessione, che impedisce al browser di allegarlo alle
                // richieste che partono da un altro sito. Se un domani
                // servisse la doppia barriera, si riattiva qui con
                // CookieCsrfTokenRepository.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessione -> sessione
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // Lo stesso repository lo usa il controller del login per
                // salvare l'utente: se fossero due istanze diverse, il login
                // non avrebbe effetto.
                .securityContext(contesto -> contesto
                        .securityContextRepository(contestoSicurezza))
                .authorizeHttpRequests(richieste -> richieste
                        .requestMatchers(HttpMethod.POST, "/api/autenticazione/login").permitAll()
                        .requestMatchers("/error").permitAll()
                        // La documentazione dell'API e la pagina che la
                        // sfoglia. Le tre rotte stanno **prima** di
                        // anyRequest() e non dopo: le regole si valutano
                        // nell'ordine in cui sono scritte, e anyRequest()
                        // prende tutto quello che arriva fin li' — una
                        // permitAll() scritta sotto non verrebbe mai letta.
                        .requestMatchers(SWAGGER).permitAll()
                        // La salute, per chi sorveglia i container. Vedi SALUTE.
                        .requestMatchers(SALUTE).permitAll()
                        .anyRequest().authenticated())
                // Rilegge utente e permessi dal database. Va dopo il filtro
                // che carica il contesto dalla sessione e prima di quello che
                // decide se la richiesta passa.
                .addFilterBefore(new FiltroUtenteAggiornato(dettagliUtente),
                        AuthorizationFilter.class)
                // Senza questo, a chi non e' autenticato Spring risponde con
                // un redirect a una pagina di login HTML: per un'API e' il
                // comportamento sbagliato.
                .exceptionHandling(gestione -> gestione
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .logout(logout -> logout
                        .logoutUrl("/api/autenticazione/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(
                                new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .build();
    }

    /**
     * Riconosce il prefisso dell'hash nel database (ad esempio {@code {bcrypt}}),
     * come lo scrivono le migrazioni.
     */
    @Bean
    PasswordEncoder codificatorePassword() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Verifica email e password al login, e rifiuta gli utenti non attivi. */
    @Bean
    AuthenticationManager gestoreAutenticazione(UserDetailsService dettagliUtente,
                                                PasswordEncoder codificatorePassword) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(dettagliUtente);
        provider.setPasswordEncoder(codificatorePassword);
        return new ProviderManager(provider);
    }

    /**
     * Dove vive l'utente collegato: nella sessione HTTP, piu' un attributo
     * della richiesta per riusarlo entro la stessa richiesta. E' la coppia che
     * Spring Security userebbe da sola, dichiarata qui perche' serve anche al
     * controller del login.
     */
    @Bean
    SecurityContextRepository contestoSicurezza() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }
}
