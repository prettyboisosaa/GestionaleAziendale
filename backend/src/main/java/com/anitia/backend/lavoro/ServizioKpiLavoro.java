package com.anitia.backend.lavoro;

import com.anitia.backend.task.TaskRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gli indicatori di avanzamento di un lavoro.
 *
 * <p><strong>Le quattro definizioni stanno qui e in nessun altro posto.</strong>
 * Sono formule discutibili — su quali task contare, cosa significa «ferma»,
 * cosa fare quando non c'è niente da dividere — e una definizione discutibile
 * sparsa in tre file diventa tre definizioni diverse. Chi vuole cambiarne una
 * cambia una riga di questa classe.
 *
 * <p><strong>Le ore vengono dalle stesse somme del resto dell'applicazione.</strong>
 * {@code sommaOreAllocate} e {@code sommaOreConsumate} sono i due metodi che
 * {@link ServizioLavori} usa per riempire {@code RigaLavoro}: i KPI devono
 * dire gli stessi numeri che la sezione «Monte ore» mostra due riquadri più in
 * là, e un secondo modo di sommare le ore sarebbe il modo sicuro di farli
 * litigare.
 *
 * <p>Non è la base scelta per caso: {@code ore_allocate} è il budget vero —
 * mai nullo, già aggiornato dalle richieste di ore approvate, ed è la stessa
 * base su cui il server decide se una task sfora. {@code stima_ore}, la stima
 * iniziale del PM, è nullable: su un lavoro dove metà delle task non ce l'ha,
 * uno scostamento calcolato su quella sarebbe un numero senza significato.
 */
@Service
public class ServizioKpiLavoro {

    /**
     * Da quanti giorni di immobilità una task in corso si considera ferma.
     *
     * <p>Cinque: una settimana lavorativa meno i due giorni in cui non si
     * lavora. Meno sarebbe un allarme a ogni fine settimana lunga, più
     * farebbe scoprire un lavoro arenato quando è troppo tardi.
     */
    private static final int GIORNI_DI_STAGNAZIONE = 5;

    /** Un decimale: la seconda cifra di una percentuale non ha mai deciso niente. */
    private static final int DECIMALI = 1;

    private static final BigDecimal CENTO = BigDecimal.valueOf(100);

    private final ServizioLavori lavori;
    private final TaskRepository task;

    ServizioKpiLavoro(ServizioLavori lavori, TaskRepository task) {
        this.lavori = lavori;
        this.task = task;
    }

    /**
     * I KPI di un lavoro che chi chiede ha il diritto di vedere.
     *
     * <p>Il controllo di visibilità passa da {@code lavoroVisibile}, lo stesso
     * del dettaglio: chi vede il lavoro vede i suoi numeri. Non c'è un
     * permesso separato per i KPI, perché non c'è niente qui che non si possa
     * già ricavare dalla pagina — sono le stesse ore e le stesse task, messe
     * in rapporto.
     */
    @Transactional(readOnly = true)
    public KpiLavoro diUnLavoro(UserDetails chiLoChiede, Long idLavoro) {
        Lavoro lavoro = lavori.lavoroVisibile(chiLoChiede, idLavoro);

        BigDecimal allocate = task.sommaOreAllocate(lavoro.getId());
        BigDecimal consumate = task.sommaOreConsumate(lavoro.getId());

        LocalDateTime limite = LocalDateTime.now().minusDays(GIORNI_DI_STAGNAZIONE);
        TaskRepository.ContiAvanzamento conti = task.contiAvanzamento(lavoro.getId(), limite);

        long considerate = quante(conti.getConsiderate());
        long completate = quante(conti.getCompletate());
        long conData = quante(conti.getConData());
        long inTempo = quante(conti.getInTempo());
        long inCorso = quante(conti.getInCorso());
        long ferme = quante(conti.getFerme());

        return new KpiLavoro(
                // Lo scostamento è la differenza sul budget, con il segno:
                // sopra è uno sforamento, sotto è un risparmio, e nasconderne
                // uno dei due renderebbe l'indicatore un allarme invece di una
                // misura.
                percentuale(consumate.subtract(allocate), allocate),
                percentuale(consumate, allocate),
                percentuale(BigDecimal.valueOf(inTempo), BigDecimal.valueOf(conData)),
                percentuale(BigDecimal.valueOf(ferme), BigDecimal.valueOf(inCorso)),
                allocate,
                consumate,
                considerate,
                completate,
                conData,
                inCorso,
                ferme,
                GIORNI_DI_STAGNAZIONE);
    }

    /**
     * Una percentuale, oppure <strong>niente</strong> se non c'è nulla da
     * dividere.
     *
     * <p>Il denominatore a zero non è un errore da nascondere con uno zero: un
     * lavoro senza ore allocate non ha «scostamento zero», e un lavoro senza
     * task completate non ha «zero consegne in tempo». In entrambi i casi la
     * risposta onesta è che non si sa, e la sola che non faccia prendere
     * decisioni su un numero inventato.
     */
    private static BigDecimal percentuale(BigDecimal numeratore, BigDecimal denominatore) {
        if (denominatore == null || denominatore.signum() == 0) {
            return null;
        }
        return numeratore.multiply(CENTO)
                .divide(denominatore, DECIMALI, RoundingMode.HALF_UP);
    }

    /**
     * Un conteggio di MySQL, zero se la {@code SUM} non ha trovato righe.
     *
     * <p>Su un lavoro senza task tutte le somme rispondono {@code NULL}, non
     * zero: lì «nessuna task» è davvero zero task, e questo è l'unico posto in
     * cui un null diventa uno zero a buon diritto.
     */
    private static long quante(Long conteggio) {
        return conteggio == null ? 0L : conteggio;
    }
}
