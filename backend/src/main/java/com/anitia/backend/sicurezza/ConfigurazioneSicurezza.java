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
