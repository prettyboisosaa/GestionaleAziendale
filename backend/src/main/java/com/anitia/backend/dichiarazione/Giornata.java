package com.anitia.backend.dichiarazione;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Una giornata come la vede il dipendente: che giorno è stato, quante ore ha
 * registrato, quante ne può ancora registrare e su quali task.
 *
 * <p>Porta tutto quello che serve a disegnare la schermata, compreso l'elenco
 * delle task su cui si può scrivere e il tetto giornaliero: senza, il
 * frontend dovrebbe fare tre chiamate e duplicare il calcolo delle ore
 * residue — che è esattamente il genere di conto che deve stare in un posto
 * solo.
 *
 * <p>{@code id} è <strong>nullo</strong> quando per quel giorno non c'è ancora
 * nessuna dichiarazione. È la differenza fra «giornata vuota» e «giornata con
 * zero ore»: la prima non esiste ancora nel database, e la si crea solo quando
 * il dipendente scrive la prima voce. Una GET non deve lasciare righe dietro
 * di sé.
 */
public record Giornata(LocalDate data,
                       /** Null se per quel giorno non c'è ancora niente di scritto. */
                       Long id,
                       TipoGiornata tipoGiornata,
                       StatoDichiarazione stato,
                       LocalDateTime inviataIl,
                       BigDecimal oreTotali,
                       /** Il tetto, dal parametro {@code dichiarazione.ore_massime_giorno}. */
                       BigDecimal oreMassime,
                       /** {@code oreMassime − oreTotali}, mai negative. */
                       BigDecimal oreResidue,
                       /**
                        * Falso quando la finestra di correzione è scaduta, o quando il
                        * giorno è nel futuro. Il frontend lo usa per mostrare la giornata
                        * in sola lettura invece di far scrivere qualcosa che il server
                        * rifiuterebbe.
                        */
                       boolean modificabile,
                       List<RigaVoce> voci,
                       List<TaskDichiarabile> taskDisponibili) {

    /** Una giornata che nel database non c'è ancora: nessuna voce, nessun totale. */
    static Giornata vuota(LocalDate data, BigDecimal oreMassime, boolean modificabile,
                          List<TaskDichiarabile> taskDisponibili) {
        return new Giornata(data, null, TipoGiornata.LAVORATA, StatoDichiarazione.BOZZA, null,
                BigDecimal.ZERO, oreMassime, oreMassime, modificabile, List.of(),
                taskDisponibili);
    }

    static Giornata da(Dichiarazione dichiarazione, BigDecimal oreMassime, boolean modificabile,
                       List<TaskDichiarabile> taskDisponibili) {
        BigDecimal totali = dichiarazione.oreTotali();
        BigDecimal residue = oreMassime.subtract(totali);
        return new Giornata(
                dichiarazione.getData(),
                dichiarazione.getId(),
                dichiarazione.getTipoGiornata(),
                dichiarazione.getStato(),
                dichiarazione.getInviataIl(),
                totali,
                oreMassime,
                // Mai negative: le ore registrate non possono superare il
                // tetto, ma se il parametro venisse abbassato dopo il fatto
                // una residua negativa non direbbe niente di utile.
                residue.signum() < 0 ? BigDecimal.ZERO : residue,
                modificabile,
                dichiarazione.getVoci().stream().map(RigaVoce::da).toList(),
                taskDisponibili);
    }
}
