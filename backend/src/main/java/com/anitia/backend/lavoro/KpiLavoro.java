package com.anitia.backend.lavoro;

import java.math.BigDecimal;

/**
 * I quattro indicatori di avanzamento di un lavoro, con le basi su cui sono
 * calcolati.
 *
 * <p><strong>Ogni percentuale può essere null</strong>, e non è un caso di
 * confine da ignorare: vuol dire che il denominatore era zero, cioè che non
 * c'era niente da misurare. Un lavoro senza ore allocate non è «in anticipo
 * del 100%», e un lavoro senza task completate non ha «zero consegne in
 * tempo»: non si sa, e l'interfaccia scrive «n/d». Riempire quei buchi con
 * uno zero farebbe prendere decisioni su numeri inventati.
 *
 * <p><strong>Le basi viaggiano con i numeri.</strong> «Consegne in tempo
 * 100%» ha un peso molto diverso se è calcolato su una task o su quaranta, e
 * senza il denominatore chi legge non può saperlo. Per lo stesso motivo c'è
 * {@code taskCompletate} accanto a {@code taskConDataCompletamento}: la
 * differenza fra i due è il numero di task chiuse prima della {@code V21}, che
 * non hanno una data di completamento e restano fuori dal conto.
 */
public record KpiLavoro(

        /**
         * Di quanto le ore consumate si scostano da quelle allocate, in
         * percentuale. <strong>Negativo è valido</strong>: vuol dire sotto
         * budget, ed è un'informazione buona come quella opposta.
         */
        BigDecimal scostamentoOre,

        /** Quanto del budget è stato consumato, in percentuale delle ore. */
        BigDecimal avanzamento,

        /** Quante consegne sono arrivate entro la scadenza, in percentuale. */
        BigDecimal consegneInTempo,

        /** Quante task in corso sono ferme, in percentuale. */
        BigDecimal stagnazione,

        // --- le basi -------------------------------------------------------

        BigDecimal oreAllocate,
        BigDecimal oreConsumate,
        /** Le task non annullate: il denominatore dell'avanzamento per conteggio. */
        long taskConsiderate,
        long taskCompletate,
        /** Su quante task è calcolato {@code consegneInTempo}. */
        long taskConDataCompletamento,
        long taskInCorso,
        long taskFerme,
        /** Da quanti giorni di immobilità una task si considera ferma. */
        int giorniDiStagnazione) {
}
