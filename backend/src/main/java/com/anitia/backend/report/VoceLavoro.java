package com.anitia.backend.report;

import java.util.List;

/**
 * Un lavoro nella ripartizione del tempo di una persona, con dentro le sue
 * task.
 *
 * <p>Le task arrivano <strong>insieme al lavoro</strong> e non con una terza
 * chiamata: sono poche per lavoro, e il terzo livello del report deve aprirsi
 * senza attesa. Il caricamento pigro si ferma alla persona, dove i dati
 * crescono con il periodo.
 *
 * <p>{@code attivitaDirette} sono le ore registrate su questo lavoro
 * <em>senza</em> una task. Stanno qui e non nella sezione senza lavoro perché
 * appartengono al lavoro per davvero — una manutenzione per quel cliente è
 * tempo di quel cliente — e metterle fuori farebbe sembrare il lavoro più
 * leggero di quanto è stato.
 *
 * <p>{@code codice} e {@code nome} sono annullabili, e non per sbaglio: il
 * join su {@code lavoro} è esterno, e una riga può citare un lavoro che non
 * esiste più. La pagina mostra allora il solo identificativo invece di
 * scomparire.
 */
public record VoceLavoro(Long lavoroId,
                         String codice,
                         String nome,
                         long minuti,
                         long minutiNonConfermati,
                         List<VoceTask> task,
                         List<VoceAttivita> attivitaDirette) {
}
