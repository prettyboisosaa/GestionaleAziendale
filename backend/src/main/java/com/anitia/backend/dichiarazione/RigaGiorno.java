package com.anitia.backend.dichiarazione;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Una giornata nell'elenco di un periodo: quanto basta a disegnare una riga di
 * calendario, senza le voci.
 *
 * <p>Le voci non ci sono di proposito: un mese di dichiarazioni ne avrebbe
 * qualche decina, e per un elenco serve solo il totale. Chi apre la giornata
 * chiede {@link Giornata}, che le porta tutte.
 */
public record RigaGiorno(Long id,
                         LocalDate data,
                         TipoGiornata tipoGiornata,
                         StatoDichiarazione stato,
                         LocalDateTime inviataIl,
                         BigDecimal oreTotali) {

    static RigaGiorno da(Dichiarazione giorno, BigDecimal oreTotali) {
        return new RigaGiorno(giorno.getId(), giorno.getData(), giorno.getTipoGiornata(),
                giorno.getStato(), giorno.getInviataIl(), oreTotali);
    }
}
