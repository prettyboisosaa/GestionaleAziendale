package com.anitia.backend.autenticazione;

import com.anitia.backend.ruolo.Ruolo;
import com.anitia.backend.ruolo.RuoloRepository;
import com.anitia.backend.utente.Utente;
import com.anitia.backend.utente.UtenteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea il primo amministratore su un database che non ha nessun utente.
 *
 * <h2>Perche' esiste</h2>
 *
 * <p>In sviluppo gli utenti arrivano da {@code db/dev/R__dati_sviluppo.sql},
 * che il profilo {@code prod} esclude di proposito — quel file crea sette
 * utenti con la stessa password scritta in chiaro nel sorgente. Le migrazioni
 * versionate seminano ruoli, permessi e impostazioni, ma <strong>nessun
 * utente</strong>, e l'applicazione non ha registrazione pubblica: l'unica
 * rotta aperta e' {@code POST /api/autenticazione/login}.
 *
 * <p>Il risultato, su un database di produzione vuoto, era un sistema
 * migrato correttamente e inaccessibile a tutti — compreso chi l'ha
 * installato. Questa classe e' la porta d'ingresso, e si chiude da se' appena
 * entrati.
 *
 * <h2>Quando agisce</h2>
 *
 * <p>Solo se la tabella {@code utente} e' <strong>vuota</strong>. Non «se non
 * esiste questa email», che rimetterebbe in piedi l'amministratore dopo che
 * qualcuno l'ha disattivato di proposito, ne' «a ogni avvio», che
 * riscriverebbe una password cambiata. Un database con dentro anche un solo
 * utente non viene toccato, e viene detto nel log.
 *
 * <p>Se il database e' vuoto e la configurazione non c'e', l'avvio
 * <strong>non</strong> fallisce: il backend parte e lascia scritto nel log
 * che cosa impostare. Fallire sarebbe peggio, perche' la stessa immagine
 * viene avviata anche contro database gia' popolati, dove queste variabili
 * non servono.
 *
 * <h2>Come si configura</h2>
 *
 * <pre>
 * GESTIONALE_AMMINISTRATOREINIZIALE_EMAIL=nome.cognome@azienda.it
 * GESTIONALE_AMMINISTRATOREINIZIALE_PASSWORD=...
 * GESTIONALE_AMMINISTRATOREINIZIALE_NOME=Nome        (facoltativo)
 * GESTIONALE_AMMINISTRATOREINIZIALE_COGNOME=Cognome  (facoltativo)
 * </pre>
 *
 * <p>La password passa dalle stesse {@link RegolePassword} che valgono per
 * tutti: il primo accesso del sistema non e' il posto dove fare un'eccezione.
 * Se non le rispetta, l'utente non viene creato e il log dice perche'.
 *
 * <p><strong>La password sta in chiaro nell'ambiente del processo</strong>, e
 * va trattata come un segreto usa e getta: serve per il primo accesso, dopo
 * il quale la si cambia da {@code /profilo} e si toglie la variabile. Non
 * viene mai scritta nel log.
 */
@Component
@ConfigurationProperties(prefix = "gestionale.amministratore-iniziale")
public class AmministratoreIniziale implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AmministratoreIniziale.class);

    private final UtenteRepository utenti;
    private final RuoloRepository ruoli;
    private final PasswordEncoder codificatorePassword;

    private String email;
    private String password;
    private String nome = "Amministratore";
    private String cognome = "Iniziale";

    AmministratoreIniziale(UtenteRepository utenti, RuoloRepository ruoli,
                           PasswordEncoder codificatorePassword) {
        this.utenti = utenti;
        this.ruoli = ruoli;
        this.codificatorePassword = codificatorePassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments argomenti) {
        if (utenti.count() > 0) {
            // Il caso normale a ogni riavvio: non si dice niente di allarmante,
            // ma si dice, perche' chi legge il log durante un'installazione
            // deve poter distinguere «non serviva» da «non ha funzionato».
            log.debug("Utenti già presenti: nessun amministratore iniziale da creare.");
            return;
        }

        if (!configurato()) {
            log.warn("""
                    Database senza nessun utente e amministratore iniziale non configurato: \
                    NESSUNO PUÒ ACCEDERE. L'applicazione non ha registrazione pubblica, \
                    quindi il primo utente va creato da qui. Imposta \
                    GESTIONALE_AMMINISTRATOREINIZIALE_EMAIL e \
                    GESTIONALE_AMMINISTRATOREINIZIALE_PASSWORD e riavvia.""");
            return;
        }

        if (!RegolePassword.valida(password)) {
            log.error("""
                    Amministratore iniziale NON creato: la password {}. \
                    Correggi GESTIONALE_AMMINISTRATOREINIZIALE_PASSWORD e riavvia.""",
                    RegolePassword.perche(password));
            return;
        }

        Ruolo protetto = ruoli.findFirstByProtettoTrueOrderByIdAsc().orElse(null);
        if (protetto == null) {
            // Non puo' succedere con le migrazioni applicate (la V1 crea
            // «Amministratore» protetto), ma creare un utente senza permessi
            // sarebbe un modo silenzioso di non risolvere niente: meglio
            // nessun utente e un messaggio che lo spiega.
            log.error("""
                    Amministratore iniziale NON creato: non esiste nessun ruolo protetto. \
                    Le migrazioni di db/migration sono state applicate?""");
            return;
        }

        Utente creato = utenti.saveAndFlush(Utente.nuovo(
                nome.strip(), cognome.strip(), email.strip(),
                codificatorePassword.encode(password)));
        // `assegnato_da_id` resta NULL: nessuno gliel'ha assegnato, e scrivere
        // il suo stesso id direbbe che se l'e' dato da se'. La colonna e'
        // nullable proprio per questo caso.
        utenti.assegnaRuolo(creato.getId(), protetto.getId(), null);

        log.warn("""
                Creato l'amministratore iniziale {} con il ruolo «{}». \
                CAMBIA LA PASSWORD AL PRIMO ACCESSO da /profilo, poi togli \
                GESTIONALE_AMMINISTRATOREINIZIALE_PASSWORD dall'ambiente.""",
                creato.getEmail(), protetto.getNome());
    }

    /** Vero se ci sono entrambe le cose che servono, non in bianco. */
    private boolean configurato() {
        return email != null && !email.isBlank()
                && password != null && !password.isBlank();
    }

    // I setter servono al binder di @ConfigurationProperties.

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public void setCognome(String cognome) {
        this.cognome = cognome;
    }
}
