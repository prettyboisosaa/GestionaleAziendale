package com.anitia.backend.utente;

import com.anitia.backend.permesso.Permesso;
import com.anitia.backend.permesso.PermessoRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * I permessi di una singola persona: quali ha, da dove le arrivano, e come
 * cambiarli.
 *
 * <p>È un servizio a parte da {@code ServizioUtenti}: quello crea ed elenca
 * utenti, questo si occupa di autorizzazioni, e sono due ragioni di
 * cambiamento diverse.
 */
@Service
public class ServizioPermessiUtente {

    private final UtenteRepository utenti;
    private final PermessoRepository permessi;
    private final UtentePermessoRepository scostamenti;

    ServizioPermessiUtente(UtenteRepository utenti, PermessoRepository permessi,
                           UtentePermessoRepository scostamenti) {
        this.utenti = utenti;
        this.permessi = permessi;
        this.scostamenti = scostamenti;
    }

    /** Tutto il catalogo, con l'origine di ciascun permesso per questa persona. */
    @Transactional(readOnly = true)
    public List<PermessoUtente> permessiDi(Long utenteId) {
        Utente utente = trovaUtente(utenteId);

        Set<String> daiRuoli = new HashSet<>(utenti.trovaCodiciPermessoDaiRuoli(utenteId));
        Map<String, Boolean> perCodice = scostamenti.findByUtenteId(utenteId).stream()
                .collect(Collectors.toMap(UtentePermesso::getPermessoCodice,
                        UtentePermesso::isConcesso));

        return permessi.findAllByOrderByOrdineAsc().stream()
                .map(permesso -> descrivi(permesso, utente.isAttivo(),
                        daiRuoli.contains(permesso.getCodice()),
                        perCodice.get(permesso.getCodice())))
                .toList();
    }

    /**
     * Decide se questa persona può fare questa cosa, e scrive lo scostamento
     * che serve.
     *
     * <p>L'invariante è una sola: <strong>uno scostamento esiste solo quando
     * contraddice i ruoli</strong>. Se il ruolo già dice la cosa richiesta, la
     * riga va cancellata, non scritta. Questo copre anche il caso scomodo di un
     * permesso aggiunto a mano che più tardi il ruolo comincia a concedere da
     * sé: cancellando lo scostamento il risultato resta corretto.
     */
    @Transactional
    public PermessoUtente imposta(Long utenteId, String codice, boolean effettivo,
                                  String emailAutore) {
        vietaSuSeStesso(utenteId, emailAutore);

        Utente utente = trovaUtente(utenteId);
        Permesso permesso = permessi.findById(codice).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Il permesso " + codice + " non esiste."));

        // Qui e non prima: per sapere se è di base serve la riga del catalogo,
        // e un codice inesistente deve restare un 400. Qui e non dopo: il
        // calcolo di dalRuolo è una query che su un permesso di base non
        // serve a niente.
        //
        // Si rifiutano entrambe le direzioni, anche la concessione: quella
        // sarebbe già vera, e accettarla significherebbe avere due risposte
        // diverse per la stessa scrittura. 409 e non 403, perché chi chiede ha
        // RUOLI_ASSEGNA: non gli manca il permesso, è la richiesta che
        // contraddice lo stato del sistema.
        if (permesso.isBase()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Il permesso " + codice + " ce l'hanno tutti e non si tocca: è il "
                            + "minimo per poter usare il gestionale.");
        }

        boolean dalRuolo = utenti.trovaCodiciPermessoDaiRuoli(utenteId).contains(codice);
        Optional<UtentePermesso> esistente =
                scostamenti.findByUtenteIdAndPermessoCodice(utenteId, codice);

        if (effettivo == dalRuolo) {
            esistente.ifPresent(scostamenti::delete);
        } else {
            Utente autore = utenti.findByEmail(emailAutore).orElse(null);
            scostamenti.save(UtentePermesso.nuovo(utenteId, codice, effettivo, autore));
        }

        return descrivi(permesso, utente.isAttivo(), dalRuolo,
                effettivo == dalRuolo ? null : effettivo);
    }

    /**
     * Toglie lo scostamento: il permesso torna a dipendere solo dai ruoli.
     *
     * <p>Su un permesso di base non risponde 409 come {@link #imposta}, e
     * l'asimmetria è voluta: qui lo stato che il chiamante chiede — nessuno
     * scostamento — è esattamente quello che vale già. Una {@code DELETE} è
     * idempotente, e dopo la V14 nessuno scostamento su un codice di base può
     * esistere, quindi il metodo non trova niente e non fa niente.
     */
    @Transactional
    public void ripristina(Long utenteId, String codice, String emailAutore) {
        vietaSuSeStesso(utenteId, emailAutore);
        trovaUtente(utenteId);
        scostamenti.findByUtenteIdAndPermessoCodice(utenteId, codice)
                .ifPresent(scostamenti::delete);
    }

    private static PermessoUtente descrivi(Permesso permesso, boolean attivo, boolean dalRuolo,
                                           Boolean scostamento) {
        boolean effettivo;
        OriginePermesso origine;

        // Il pavimento vince su tutto, perché non è uno scostamento ma il
        // livello sotto: vince anche quando dalRuolo è falso, che dopo la V14
        // è il caso normale — i ruoli non protetti non nominano più i permessi
        // di base.
        //
        // Il filtro su attivo c'è perché la vista ce l'ha: un'utenza chiusa
        // non ha nessun permesso, e mostrare cinque caselle verdi sulla pagina
        // di una persona disattivata direbbe il contrario di quello che il
        // server poi risponde.
        if (permesso.isBase()) {
            return new PermessoUtente(permesso.getCodice(), permesso.getCategoria(),
                    permesso.getDescrizione(), true, dalRuolo, OriginePermesso.BASE, attivo);
        }

        if (scostamento == null) {
            effettivo = dalRuolo;
            origine = dalRuolo ? OriginePermesso.RUOLO : OriginePermesso.NESSUNO;
        } else if (scostamento) {
            effettivo = true;
            origine = OriginePermesso.AGGIUNTO;
        } else {
            effettivo = false;
            origine = OriginePermesso.TOGLIATO;
        }

        return new PermessoUtente(permesso.getCodice(), permesso.getCategoria(),
                permesso.getDescrizione(), false, dalRuolo, origine, effettivo);
    }

    /**
     * Nessuno modifica i propri permessi.
     *
     * <p>Senza questa regola chi ha {@code RUOLI_ASSEGNA} potrebbe concedersi
     * qualunque cosa, {@code IMPOSTAZIONI_GESTISCI} compreso: sarebbe
     * un'escalation di privilegi per costruzione, non per errore. Nei dati di
     * prova solo l'amministratore ha quel permesso, ma la regola va messa ora e
     * non quando i ruoli saranno molti.
     */
    private void vietaSuSeStesso(Long utenteId, String emailAutore) {
        utenti.findByEmail(emailAutore)
                .filter(autore -> autore.getId().equals(utenteId))
                .ifPresent(autore -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Non puoi modificare i tuoi permessi. Chiedi a un altro "
                                    + "amministratore.");
                });
    }

    private Utente trovaUtente(Long utenteId) {
        return utenti.findById(utenteId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Utente non trovato."));
    }
}
