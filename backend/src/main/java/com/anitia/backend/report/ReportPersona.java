package com.anitia.backend.report;

import java.time.LocalDate;
import java.util.List;

/**
 * La ripartizione del tempo di una persona nel periodo: su che cosa ha
 * lavorato, e quanto su ciascuna cosa.
 *
 * <p><strong>Minuti, non percentuali.</strong> La percentuale è una lettura
 * del dato e non il dato: calcolarla qui vorrebbe dire arrotondarla qui, e una
 * colonna di percentuali arrotondate somma a 99,8 o a 100,2 — un report che
 * non chiude è un report di cui non si fida nessuno. La pagina la ricava dal
 * rapporto grezzo, quindi le barre riempiono sempre esattamente il totale.
 *
 * <p>{@code minutiTotali} è la <strong>somma delle righe</strong> qui dentro,
 * non un conto separato. È una garanzia, non un risparmio: il denominatore di
 * ogni percentuale è per costruzione la somma dei suoi numeratori, e nessun
 * filtro sfasato fra due query può far apparire del tempo che non si vede da
 * nessuna parte.
 *
 * <p>{@code senzaLavoro} chiude l'elenco: riunioni, formazione,
 * amministrazione. Non è un residuo da ignorare — è il contorno della
 * settimana, e vederlo crescere è un'informazione.
 */
public record ReportPersona(Long utenteId,
                            String nome,
                            String cognome,
                            LocalDate da,
                            LocalDate a,
                            long minutiTotali,
                            long minutiNonConfermati,
                            List<VoceLavoro> lavori,
                            List<VoceAttivita> senzaLavoro) {
}
