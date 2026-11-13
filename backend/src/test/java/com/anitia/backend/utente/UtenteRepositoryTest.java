package com.anitia.backend.utente;

import static org.assertj.core.api.Assertions.assertThat;

import com.anitia.backend.permesso.Permesso;
import com.anitia.backend.permesso.PermessoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le query native di {@link UtenteRepository}, eseguite contro il database
 * vero con i dati di sviluppo.
 *
 * <p>Esistono per una ragione precisa: una query nativa può compilare
 * benissimo e poi rompersi a tempo di esecuzione perché il tipo che MySQL
 * restituisce non è quello dichiarato in Java. È accaduto con
 * {@code SELECT COUNT(*) > 0}, che MySQL dà come {@code BIGINT} e che Spring
 * Data non sa convertire in {@code Boolean}. Nessun test con il servizio
 * simulato poteva accorgersene, perché il repository non veniva mai eseguito.
 *
 * <p>Qui vive anche il controllo sul pavimento dei permessi, per lo stesso
 * motivo: la {@code UPDATE} della V14 che marca {@code base = TRUE} non
 * fallisce se un codice è scritto male, non trova righe e lascia il pavimento
 * vuoto in silenzio.
 */
@SpringBootTest
class UtenteRepositoryTest {

    private static final long LAURA = 2;
    private static final long GIULIA = 4;

    /**
     * Il pavimento, come lo definiscono la V14 e la V17.
     *
     * <p>Quattro e non cinque: DICHIARAZIONI_COMPILA e' uscito con la giornata
     * da compilare. Il tempo si registra avviando una task, e quello lo
     * autorizza gia' TASK_AGGIORNA_STATO.
     */
    private static final String[] PERMESSI_DI_BASE = {
        "LAVORI_VISUALIZZA_PROPRI", "TASK_VISUALIZZA_ASSEGNATE", "TASK_AGGIORNA_STATO",
        "ASSENZE_RICHIEDI"};

    @Autowired
    private UtenteRepository utenti;

    @Autowired
    private PermessoRepository permessi;

    /**
     * Il pavimento è esattamente quello che la V14 dichiara: né uno in più,
     * perché sarebbe un permesso regalato a tutti, né uno in meno, perché
     * sarebbe un codice scritto male che nessun errore SQL segnala.
     */
    @Test
    void ilPavimentoEEsattamenteQuelloDichiarato() {
        assertThat(permessi.findAll().stream()
                .filter(Permesso::isBase)
                .map(Permesso::getCodice))
                .containsExactlyInAnyOrder(PERMESSI_DI_BASE);
    }

    /**
     * Il ruolo Dipendente è un'etichetta e non concede niente: dopo la V14 i
     * suoi permessi sono usciti da {@code ruolo_permesso}, perché il pavimento
     * li dà già a tutti.
     *
     * <p>È la coppia di asserzioni che pianta la differenza fra i due livelli:
     * dai ruoli non arriva niente, e quello che Giulia può fare arriva per
     * intero dal pavimento.
     */
    @Test
    void aGiuliaIPermessiArrivanoDalPavimentoENonDalRuolo() {
        assertThat(utenti.trovaCodiciPermessoDaiRuoli(GIULIA)).isEmpty();
        assertThat(utenti.trovaCodiciPermesso(GIULIA))
                .containsExactlyInAnyOrder(PERMESSI_DI_BASE);
    }

    /**
     * Il pavimento arriva anche a chi ha dei ruoli, e si somma a quello che i
     * ruoli aggiungono: Laura è Project Manager, quindi ha più del minimo, ma
     * il minimo ce l'ha tutto.
     */
    @Test
    void ancheUnProjectManagerHaIlPavimento() {
        assertThat(utenti.trovaCodiciPermesso(LAURA))
                .contains(PERMESSI_DI_BASE)
                .hasSizeGreaterThan(PERMESSI_DI_BASE.length);
    }

    /**
     * Un'utenza chiusa non ha nessun permesso, pavimento compreso: il filtro
     * {@code attivo = TRUE} è ripetuto in tutti e tre i rami della vista
     * proprio perché è questa la proprietà su cui si appoggia il resto.
     *
     * <p><strong>L'utente se lo crea il test.</strong> Prima qui c'era
     * {@code paolo = 7}, il disattivato di prova del seed, e questo test
     * falliva su qualunque macchina dove qualcuno avesse riattivato Paolo
     * dall'interfaccia: {@code R__dati_sviluppo.sql} è una migrazione
     * ripetibile, e Flyway la rilancia quando cambia il <em>file</em>, non
     * quando cambiano i <em>dati</em>. Passava sul CI, dove il database nasce
     * vuoto a ogni corsa, e falliva in locale — cioè era verde esattamente
     * dove nessuno lo guardava.
     *
     * <p>È la stessa regola che {@code ilRimpiazzoDiENelTeam} qui sotto mette
     * per iscritto a proposito dei lavori: un test che asserisce su righe che
     * l'applicazione può modificare è vero o falso a seconda della giornata.
     * Creando e chiudendo un'utenza propria, l'asserzione dipende solo da
     * quello che il test ha fatto.
     *
     * <p>Le due asserzioni vanno in coppia: la prima dimostra che
     * quell'utenza <em>aveva</em> qualcosa da perdere — un utente senza
     * nessun ruolo riceve comunque il pavimento — così la seconda non può
     * essere vera per il motivo sbagliato.
     *
     * <p>{@code @Transactional} e non una cancellazione in coda: il test
     * scrive su un database condiviso con gli altri, e il rollback è l'unico
     * modo di non lasciare niente dietro nemmeno quando l'asserzione fallisce.
     */
    @Test
    @Transactional
    void unUtenteDisattivatoNonHaNemmenoIlPavimento() {
        Utente chiuso = utenti.saveAndFlush(Utente.nuovo(
                "Utenza", "DiProva", "utenza.diprova@test.local", "{noop}non-serve"));

        assertThat(utenti.trovaCodiciPermesso(chiuso.getId()))
                .containsExactlyInAnyOrder(PERMESSI_DI_BASE);

        chiuso.disattiva();
        utenti.saveAndFlush(chiuso);

        assertThat(utenti.trovaCodiciPermesso(chiuso.getId())).isEmpty();
    }

    /**
     * Il rimpiazzo di {@code eNelTeam}.
     *
     * <p>Si interrogano due id che non esistono, non Laura e Giulia: i
     * {@code lavoro} e le {@code task} di questo database sono dati veri,
     * creati dall'applicazione, e un'asserzione sul loro contenuto sarebbe
     * vera o falsa a seconda di che cosa c'è dentro oggi. Con id inesistenti
     * la risposta è stabile e la query viene eseguita comunque, che è quello
     * che serve: a questa classe interessa cogliere il {@code COUNT(*)} che
     * MySQL restituisce come {@code BIGINT}, non il contenuto delle tabelle.
     */
    @Test
    void chiNonHaTaskNeiLavoriDiUnAltroNonRisulta() {
        long inesistente = -1;
        assertThat(utenti.eNeiLavoriDi(inesistente, inesistente)).isFalse();
    }

    @Test
    void laVistaDeiPermessiEffettiviEsisteEdEInterrogabile() {
        assertThat(utenti.trovaCodiciPermesso(GIULIA)).isNotEmpty();
        assertThat(utenti.contaPermessiEffettivi()).isNotEmpty();
    }
}
