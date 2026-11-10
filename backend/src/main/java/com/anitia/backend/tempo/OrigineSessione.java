package com.anitia.backend.tempo;

/**
 * Da dove viene una sessione di lavoro.
 *
 * <p>Serve a non confondere quello che una persona ha misurato con quello che
 * il sistema ha dedotto: sono due qualità di dato diverse, e l'interfaccia le
 * mostra in modo diverso.
 *
 * <p>Rispecchia {@code ck_sessione_origine}: aggiungere un valore qui vuole
 * una migrazione, altrimenti MySQL rifiuta la scrittura.
 */
public enum OrigineSessione {

    /** Il dipendente ha premuto avvia, e poi pausa o completa. */
    TIMER,

    /**
     * Chiusa d'ufficio perché era rimasta aperta oltre il tetto. L'ora di
     * fine è una convenzione, non una misura: per questo è l'unica origine
     * che può restare da confermare.
     */
    AUTOMATICA,

    /**
     * Convertita da una vecchia voce di dichiarazione dalla {@code V16}. Di
     * quelle righe era vera la durata, non l'ora del giorno.
     */
    IMPORTATA,

    /**
     * Scritta a mano sul calendario settimanale, da chi si è dimenticato di
     * premere avvia.
     *
     * <p>Non è {@code TIMER}, che vuol dire «misurata», e non è
     * {@code IMPORTATA}, che vuol dire «storia di un altro modello»: è un
     * ricordo dichiarato da una persona, una terza qualità di dato. La
     * distinzione serve a chi legge un consuntivo, che ha diritto di sapere
     * quanta parte di quelle ore è stata misurata — nella griglia il blocco è
     * tratteggiato come quello delle chiusure d'ufficio.
     *
     * <p>Nasce <strong>confermata</strong>: l'ha scritta la persona, e non c'è
     * niente da far confermare a nessuno.
     */
    MANUALE
}
