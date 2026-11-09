package com.anitia.backend.utente;

import static org.assertj.core.api.Assertions.assertThat;

import com.anitia.backend.permesso.Permesso;
import com.anitia.backend.permesso.PermessoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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

    /** Il pavimento, come lo definisce la V14. */
    private static final String[] PERMESSI_DI_BASE = {
        "LAVORI_VISUALIZZA_PROPRI", "TASK_VISUALIZZA_ASSEGNATE", "TASK_AGGIORNA_STATO",
        "DICHIARAZIONI_COMPILA", "ASSENZE_RICHIEDI"};

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
     */
    @Test
    void unUtenteDisattivatoNonHaNemmenoIlPavimento() {
        long paolo = 7;
        assertThat(utenti.trovaCodiciPermesso(paolo)).isEmpty();
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
