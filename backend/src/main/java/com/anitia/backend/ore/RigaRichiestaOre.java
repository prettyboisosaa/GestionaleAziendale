package com.anitia.backend.ore;

import com.anitia.backend.lavoro.Lavoro;
import com.anitia.backend.task.Task;
import com.anitia.backend.utente.Utente;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Una richiesta di ore come la vede il frontend. */
public record RigaRichiestaOre(Long id,
                               Long lavoroId,
                               String codiceLavoro,
                               String nomeLavoro,
                               /** Nulli se le ore concesse restano nel pool. */
                               Long taskId,
                               String task,
                               /** «Cognome Nome» del PM che l'ha aperta. */
                               String richiedente,
                               BigDecimal oreRichieste,
                               /** Nulle finche' e' in attesa, zero se rifiutata. */
                               BigDecimal oreConcesse,
                               String motivo,
                               StatoRichiesta stato,
                               /** «Cognome Nome» dell'amministratore, o null se in attesa. */
                               String gestitaDa,
                               LocalDateTime gestitaIl,
                               LocalDateTime creataIl) {

    public static RigaRichiestaOre da(RichiestaOre richiesta) {
        Lavoro lavoro = richiesta.getLavoro();
        Task task = richiesta.getTask();
        Utente richiedente = richiesta.getRichiedente();
        Utente gestita = richiesta.getGestitaDa();
        return new RigaRichiestaOre(
                richiesta.getId(),
                lavoro.getId(),
                lavoro.getCodice(),
                lavoro.getNome(),
                task == null ? null : task.getId(),
                task == null ? null : task.getTitolo(),
                richiedente.getCognome() + " " + richiedente.getNome(),
                richiesta.getOreRichieste(),
                richiesta.getOreConcesse(),
                richiesta.getMotivo(),
                richiesta.getStato(),
                gestita == null ? null : gestita.getCognome() + " " + gestita.getNome(),
                richiesta.getGestitaIl(),
                richiesta.getCreataIl());
    }
}
