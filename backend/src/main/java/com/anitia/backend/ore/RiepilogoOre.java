package com.anitia.backend.ore;

import com.anitia.backend.lavoro.Lavoro;
import java.math.BigDecimal;

/**
 * La situazione delle ore di un lavoro: quante ce ne sono, quante sono
 * impegnate, quante sono state lavorate e quante restano da distribuire.
 *
 * <p>È quello che il frontend mostra in testa alla pagina del lavoro, ed è
 * anche la risposta di un'allocazione riuscita: dopo aver dato ore a una task,
 * la domanda immediata è «quante ne restano?», e averla già nella risposta
 * evita una seconda chiamata.
 */
public record RiepilogoOre(Long lavoroId,
                           String codice,
                           /** Null se il monte ore non è ancora stato fissato. */
                           BigDecimal monteOre,
                           BigDecimal oreAllocate,
                           BigDecimal oreConsumate,
                           /**
                            * {@code monteOre − oreAllocate}, oppure null se il monte ore non
                            * è fissato: in quel caso la domanda non ha risposta, e uno zero
                            * direbbe la cosa sbagliata — «non ne restano».
                            */
                           BigDecimal oreLibere) {

    static RiepilogoOre da(Lavoro lavoro, BigDecimal oreAllocate, BigDecimal oreConsumate) {
        BigDecimal monteOre = lavoro.getMonteOre();
        return new RiepilogoOre(lavoro.getId(), lavoro.getCodice(), monteOre,
                oreAllocate, oreConsumate,
                monteOre == null ? null : monteOre.subtract(oreAllocate));
    }
}
