package com.anitia.backend.dashboard;

/**
 * I quattro numeri delle card in cima alla dashboard.
 *
 * <p>Rispondono alla domanda che il documento mette come primo principio:
 * «cosa devo fare oggi e cosa mi sta per scappare?». Per questo contano solo
 * le task <strong>aperte</strong>: una task completata la settimana scorsa non
 * è né in ritardo né in scadenza, e contarla gonfierebbe i numeri proprio dove
 * devono essere credibili.
 */
public record ContatoriDashboard(
        /**
         * Task aperte che il PM ha marcato {@code CRITICAL}.
         *
         * <p>Ha preso il posto di «scadute». Le due domande non sono la stessa
         * — una scadenza passata è un fatto, l'urgenza è una decisione — e
         * quella che manca a chi apre la dashboard è la seconda: il ritardo si
         * legge già nella lista, dove ogni riga dice «in ritardo di tre
         * giorni», e nel calendario, dove la barra è alle spalle di oggi.
         * L'urgenza invece non si vedeva da nessuna parte.
         *
         * <p>Conta solo le task <strong>aperte</strong>, come gli altri tre:
         * una task urgente ma completata non è più niente da fare.
         */
        long critiche,

        /** Task aperte che scadono oggi. */
        long oggi,

        /**
         * Task aperte che scadono da oggi alla domenica di questa settimana,
         * oggi compreso.
         *
         * <p>La settimana finisce la domenica e non fra sette giorni: il
         * calendario si apre sulla vista settimanale, e un conteggio «prossimi
         * sette giorni» mostrerebbe un numero che nella griglia accanto non si
         * ritrova. Comprende le task di oggi, quindi questo numero non è mai
         * minore di {@link #oggi}.
         */
        long settimana,

        /** Task nello stato {@code IN_CORSO}, qualunque sia la scadenza. */
        long inCorso) {
}
