package com.anitia.backend.sicurezza;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * Lo {@code UserDetails} del gestionale: come quello di Spring, più il flag
 * {@code attivo} letto da {@code utente.attivo}.
 *
 * <p>Perché non si usa {@code isEnabled()}, che esisterebbe già per questo:
 * {@code DaoAuthenticationProvider} lo controlla <strong>prima</strong> di
 * verificare la password. Un utente marcato disabilitato farebbe quindi
 * rispondere «utenza disattivata» a chi tira a indovinare un indirizzo,
 * rivelando quali account esistono. Il documento vuole l'ordine opposto:
 * prima le credenziali, e solo se sono giuste il 403 che spiega perché
 * l'accesso non passa.
 *
 * <p>Così {@code isEnabled()} resta sempre vero e la verifica di
 * {@link #attivo()} la fanno, dopo l'autenticazione,
 * {@code AutenticazioneController} per il login e
 * {@link FiltroUtenteAggiornato} per ogni richiesta successiva. Il flag viaggia
 * dentro il principale, quindi non costa nessuna query in più: il filtro
 * rilegge già l'utente dal database a ogni chiamata.
 */
public final class UtenteCollegato extends User {

    private final boolean attivo;
    public final Long id;
    public final String nome;
    public final String cognome;

    UtenteCollegato(Long id, String email, String passwordHash, boolean attivo,
                    Collection<? extends GrantedAuthority> permessi, String nome, String cognome) {
        // enabled = true sempre, per la ragione spiegata sopra.
        super(email, passwordHash, true, true, true, true, permessi);
        this.attivo = attivo;
        this.id = id;
        this.nome = nome;
        this.cognome = cognome;
    }

    /** Falso se l'utenza è chiusa: niente accesso e nessun permesso. */
    public boolean attivo() {
        return attivo;
    }

    /**
     * Vero se questo principale può ancora stare collegato.
     *
     * <p>Il ripiego su {@code isEnabled()} serve per uno {@code UserDetails}
     * che non venga da {@code ServizioDettagliUtente}: la domanda «può stare
     * collegato?» deve avere una risposta anche allora, e il flag di Spring è
     * la cosa più vicina.
     */
    public static boolean ammesso(org.springframework.security.core.userdetails.UserDetails chi) {
        return chi instanceof UtenteCollegato collegato ? collegato.attivo() : chi.isEnabled();
    }
}
