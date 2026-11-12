package com.anitia.backend.report;

import java.time.LocalDate;
import java.util.List;

/**
 * La panoramica del report: quanto ha registrato ciascuno nel periodo.
 *
 * <p>Il periodo torna nella risposta e non è ridondante: la pagina può avere
 * cambiato le date mentre la chiamata era in volo, e un elenco senza il
 * proprio intervallo è un elenco di numeri senza unità di misura. È la stessa
 * ragione per cui {@code TempoDiUnaPersona} si porta dietro {@code da} e
 * {@code a}.
 */
public record ReportPersone(LocalDate da,
                            LocalDate a,
                            List<RiepilogoPersona> persone) {
}
