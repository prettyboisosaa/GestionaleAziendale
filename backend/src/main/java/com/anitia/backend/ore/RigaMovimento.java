package com.anitia.backend.ore;

import com.anitia.backend.task.Task;
import com.anitia.backend.utente.Utente;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un movimento di ore come lo vede il frontend.
 *
 * <p>Il titolo della task e il nome dell'autore arrivano già risolti: la
 * pagina dello storico li mostra come testo e non ha motivo di chiedere
 * un'altra rotta per tradurre due id.
 */
public record RigaMovimento(Long id,
                            TipoMovimento tipo,
                            /** Firmate: positive quando le ore entrano, negative quando escono. */
                            BigDecimal ore,
                            String motivo,
                            /** Nullo per i movimenti del solo monte ore. */
                            Long taskId,
                            /** Nullo per i movimenti del solo monte ore. */
                            String task,
                            /** La richiesta che lo ha causato, se ne nasce da una. */
                            Long richiestaId,
                            /** «Cognome Nome». */
                            String autore,
                            LocalDateTime creatoIl) {

    /**
     * Pubblico, al contrario di {@code RigaLavoro.da} e {@code RigaTask.da}
     * che restano nel loro package: lo storico delle ore si legge passando da
     * {@code ServizioLavori}, perché il permesso che lo protegge è quello di
     * vedere il lavoro.
     */
    public static RigaMovimento da(MovimentoOre movimento) {
        Task task = movimento.getTask();
        Utente autore = movimento.getAutore();
        return new RigaMovimento(
                movimento.getId(),
                movimento.getTipo(),
                movimento.getOre(),
                movimento.getMotivo(),
                task == null ? null : task.getId(),
                task == null ? null : task.getTitolo(),
                movimento.getRichiestaId(),
                autore.getCognome() + " " + autore.getNome(),
                movimento.getCreatoIl());
    }
}
