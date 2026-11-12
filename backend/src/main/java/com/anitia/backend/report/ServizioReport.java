package com.anitia.backend.report;

import com.anitia.backend.tempo.TipoAttivita;
import com.anitia.backend.utente.Utente;
import com.anitia.backend.utente.UtenteRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Su che cosa ha lavorato l'azienda, e in quale proporzione.
 *
 * <p>Risponde alla domanda che nessuna schermata sapeva fare: il calendario di
 * una persona mostra le sue sessioni una per una, e i KPI di un lavoro dicono
 * quante ore sono andate su quel progetto senza dire da chi. Qui il tempo si
 * guarda per <strong>persona</strong>, e la percentuale è sempre sul totale di
 * quella persona: su cento ore di Mario, quaranta sul lavoro A.
 *
 * <p>⚠️ <strong>Questo servizio non scrive.</strong> È la differenza voluta da
 * {@code ServizioTempo.diUnaPersona}, che prima di leggere chiama
 * {@code chiudiSeOltreIlTetto} e chiude d'ufficio le sessioni dimenticate
 * aperte. Lì ha senso: si sta guardando il tempo di qualcuno per occuparsene.
 * Un report invece si apre per contare, e un conto che modifica ciò che conta
 * — venti persone aperte di fila, venti scritture — non è un conto. Il prezzo
 * è dichiarato: una sessione lasciata aperta resta fuori dai totali finché il
 * suo proprietario non torna a guardare il proprio tempo. Il numero è
 * prudente, mai gonfiato.
 *
 * <p>Il permesso è {@code TEMPO_VISUALIZZA_TUTTI}, e <strong>non uno
 * nuovo</strong>: quel codice dice già «vedi il tempo registrato da tutti», e
 * {@code /api/tempo/utente/{id}} espone a chi lo ha le stesse sessioni con il
 * loro lavoro e la loro task dentro. Questo report ne è un'aggregazione, e un
 * permesso separato renderebbe esprimibili combinazioni senza senso — il
 * riassunto senza il dettaglio, o il contrario. Il controllo sta
 * nell'annotazione del controller e basta, perché qui non c'è nessun limite
 * che dipenda dai dati: il report è globale per definizione, e chi ha solo
 * {@code TEMPO_VISUALIZZA_LAVORO} resta fuori.
 */
@Service
@Transactional(readOnly = true)
public class ServizioReport {

    private final ReportRepository righe;
    private final UtenteRepository utenti;

    ServizioReport(ReportRepository righe, UtenteRepository utenti) {
        this.righe = righe;
        this.utenti = utenti;
    }

    /** Quanto ha registrato ciascuno nel periodo, dal più carico al più scarico. */
    public ReportPersone persone(LocalDate da, LocalDate a) {
        controllaIntervallo(da, a);

        List<RiepilogoPersona> persone = righe.perPersona(da.atStartOfDay(), finePeriodo(a))
                .stream()
                .map(riga -> new RiepilogoPersona(
                        riga.getUtenteId(),
                        riga.getNome(),
                        riga.getCognome(),
                        Boolean.TRUE.equals(riga.getAttivo()),
                        minuti(riga.getMinutiTotali()),
                        minuti(riga.getMinutiNonConfermati())))
                .toList();

        return new ReportPersone(da, a, persone);
    }

    /**
     * La ripartizione del tempo di una persona: lavori, task dentro i lavori,
     * e in fondo le attività che non appartengono a nessun lavoro.
     *
     * <p>Una sola query, e l'albero si costruisce qui. Le righe arrivano
     * piatte e si smistano in tre famiglie, che si riconoscono da quali
     * colonne sono nulle; la mappa che le raccoglie è una
     * {@link LinkedHashMap} perché la query le consegna già dalla più
     * corposa alla più leggera, e quell'ordine è esattamente quello in cui si
     * vogliono leggere.
     */
    public ReportPersona persona(Long utenteId, LocalDate da, LocalDate a) {
        controllaIntervallo(da, a);

        Utente chi = utenti.findById(utenteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Questa persona non esiste."));

        // L'ordine di inserimento conta: la query ordina per minuti
        // decrescenti, e queste mappe lo conservano fino alla risposta.
        Map<Long, Accumulo> perLavoro = new LinkedHashMap<>();
        Map<TipoAttivita, Long> senzaLavoro = new LinkedHashMap<>();
        long totali = 0;
        long nonConfermati = 0;

        for (ReportRepository.RigaDettaglio riga : righe.dettaglioDiUnaPersona(
                utenteId, da.atStartOfDay(), finePeriodo(a))) {
            long suoi = minuti(riga.getMinuti());
            totali += suoi;
            nonConfermati += minuti(riga.getMinutiNonConfermati());

            if (riga.getLavoroId() == null) {
                // Nessun lavoro: resta solo il tipo di attività a descriverla.
                senzaLavoro.merge(TipoAttivita.valueOf(riga.getTipoAttivita()), suoi, Long::sum);
                continue;
            }

            Accumulo lavoro = perLavoro.computeIfAbsent(riga.getLavoroId(),
                    id -> new Accumulo(id, riga.getLavoroCodice(), riga.getLavoroNome()));
            lavoro.minuti += suoi;
            lavoro.nonConfermati += minuti(riga.getMinutiNonConfermati());

            if (riga.getTaskId() != null) {
                lavoro.task.add(new VoceTask(riga.getTaskId(), riga.getTaskTitolo(), suoi));
            } else {
                // Tempo su questo lavoro ma fuori da ogni task: una
                // manutenzione, un sopralluogo.
                lavoro.dirette.merge(TipoAttivita.valueOf(riga.getTipoAttivita()), suoi, Long::sum);
            }
        }

        return new ReportPersona(chi.getId(), chi.getNome(), chi.getCognome(), da, a,
                totali, nonConfermati,
                perLavoro.values().stream().map(Accumulo::finito).toList(),
                attivita(senzaLavoro));
    }

    /**
     * L'intervallo è a estremi <strong>inclusi</strong>, quindi la fine è la
     * mezzanotte del giorno dopo: una sessione avviata alle 23:59 dell'ultimo
     * giorno è dentro. Stessa conversione di {@code ServizioTempo}, perché due
     * schermate che leggono «dal 1 al 31» in modo diverso sarebbero
     * indifendibili.
     */
    private static LocalDateTime finePeriodo(LocalDate a) {
        return a.plusDays(1).atStartOfDay();
    }

    private static void controllaIntervallo(LocalDate da, LocalDate a) {
        if (a.isBefore(da)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "L'intervallo finisce prima di cominciare.");
        }
    }

    /**
     * Zero invece di niente. Le {@code SUM} hanno già il loro
     * {@code COALESCE}, ma una proiezione nativa non è un contratto che il
     * compilatore verifica: un NULL che arrivasse qui diventerebbe una
     * {@code NullPointerException} dentro un'aggregazione, cioè l'errore più
     * difficile da leggere fra tutti quelli possibili.
     */
    private static long minuti(Long valore) {
        return valore == null ? 0 : valore;
    }

    private static List<VoceAttivita> attivita(Map<TipoAttivita, Long> minuti) {
        return minuti.entrySet().stream()
                .map(voce -> VoceAttivita.da(voce.getKey(), voce.getValue()))
                .sorted(Comparator.comparingLong(VoceAttivita::minuti).reversed())
                .toList();
    }

    /**
     * Un lavoro mentre lo si sta sommando. Mutabile e privato di proposito: il
     * record che esce è immutabile, e questo vive solo per la durata del ciclo
     * che lo riempie.
     */
    private static final class Accumulo {
        private final Long id;
        private final String codice;
        private final String nome;
        private final List<VoceTask> task = new ArrayList<>();
        private final Map<TipoAttivita, Long> dirette = new LinkedHashMap<>();
        private long minuti;
        private long nonConfermati;

        private Accumulo(Long id, String codice, String nome) {
            this.id = id;
            this.codice = codice;
            this.nome = nome;
        }

        private VoceLavoro finito() {
            return new VoceLavoro(id, codice, nome, minuti, nonConfermati,
                    List.copyOf(task), attivita(dirette));
        }
    }
}
