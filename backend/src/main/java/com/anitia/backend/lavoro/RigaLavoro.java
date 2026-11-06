package com.anitia.backend.lavoro;

import com.anitia.backend.utente.Utente;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Un lavoro come lo vede il frontend, con la situazione del suo monte ore. */
public record RigaLavoro(Long id,
                         String codice,
                         String nome,
                         String descrizione,
                         LocalDate dataInizio,
                         LocalDate dataFinePrevista,
                         StatoLavoro stato,
                         /** Quando e' stato annullato; null se non lo e'. */
                         LocalDateTime annullatoIl,
                         /** «Cognome Nome», oppure null se è una bozza senza responsabile. */
                         String responsabile,
                         /** Null se il monte ore non è ancora stato fissato. */
                         BigDecimal monteOre,
                         /** La somma delle ore allocate alle task. Mai null. */
                         BigDecimal oreAllocate,
                         /** La somma delle ore registrate dai dipendenti. Mai null. */
                         BigDecimal oreConsumate,
                         /**
                          * Quante ore restano da distribuire: {@code monteOre − oreAllocate}.
                          * Null quando il monte ore non è fissato, perché in quel caso la
                          * domanda non ha risposta — e uno zero direbbe la cosa sbagliata,
                          * cioè «non ne restano».
                          */
                         BigDecimal oreLibere) {

    /**
     * I totali si passano da fuori perché nessuno di loro è salvato sul
     * lavoro: li somma il database quando servono. L'elenco li chiede
     * raggruppati per tutti i lavori in una volta, invece di una query per
     * riga.
     *
     * @param oreAllocate  zero, non null, se il lavoro non ha task
     * @param oreConsumate zero, non null, se nessuno ha ancora registrato ore
     */
    static RigaLavoro da(Lavoro lavoro, BigDecimal oreAllocate, BigDecimal oreConsumate) {
        Utente responsabile = lavoro.getResponsabile();
        BigDecimal monteOre = lavoro.getMonteOre();
        return new RigaLavoro(
                lavoro.getId(),
                lavoro.getCodice(),
                lavoro.getNome(),
                lavoro.getDescrizione(),
                lavoro.getDataInizio(),
                lavoro.getDataFinePrevista(),
                lavoro.getStato(),
                lavoro.getAnnullatoIl(),
                responsabile == null
                        ? null
                        : responsabile.getCognome() + " " + responsabile.getNome(),
                monteOre,
                oreAllocate,
                oreConsumate,
                monteOre == null ? null : monteOre.subtract(oreAllocate));
    }
}
