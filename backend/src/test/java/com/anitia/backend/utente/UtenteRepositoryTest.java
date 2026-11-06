package com.anitia.backend.utente;

import static org.assertj.core.api.Assertions.assertThat;

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
 * <p>Si appoggia ai dati di {@code R__dati_sviluppo.sql}: Laura (2) ha in team
 * Giulia (4) e Luca (5); Marco (3) ha Luca (5) e Sara (6).
 */
@SpringBootTest
class UtenteRepositoryTest {

    private static final long LAURA = 2;
    private static final long MARCO = 3;
    private static final long GIULIA = 4;
    private static final long SARA = 6;

    @Autowired
    private UtenteRepository utenti;

    @Test
    void riconosceChiFaParteDelTeamDiUnResponsabile() {
        assertThat(utenti.eNelTeam(LAURA, GIULIA)).isTrue();
        assertThat(utenti.eNelTeam(MARCO, SARA)).isTrue();
    }

    @Test
    void escludeChiNonFaParteDelTeam() {
        assertThat(utenti.eNelTeam(LAURA, SARA)).isFalse();
        assertThat(utenti.eNelTeam(MARCO, GIULIA)).isFalse();
    }

    @Test
    void iPermessiDaiRuoliNonTengonoContoDegliScostamenti() {
        // Giulia ha il ruolo Dipendente: quattro permessi, e nessuno di questi
        // dipende dalla tabella utente_permesso.
        assertThat(utenti.trovaCodiciPermessoDaiRuoli(GIULIA))
                .containsExactlyInAnyOrder("LAVORI_VISUALIZZA_PROPRI",
                        "TASK_VISUALIZZA_ASSEGNATE", "TASK_AGGIORNA_STATO",
                        "DICHIARAZIONI_COMPILA", "ASSENZE_RICHIEDI");
    }

    @Test
    void laVistaDeiPermessiEffettiviEsisteEdEInterrogabile() {
        assertThat(utenti.trovaCodiciPermesso(GIULIA)).isNotEmpty();
        assertThat(utenti.contaPermessiEffettivi()).isNotEmpty();
    }
}
