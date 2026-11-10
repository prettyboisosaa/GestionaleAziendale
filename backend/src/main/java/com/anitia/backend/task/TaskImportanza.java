package com.anitia.backend.task;

public enum TaskImportanza {

    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;


    public String etichetta() {
        return switch (this) {
            case LOW -> "low";
            case MEDIUM -> "medium";
            case HIGH -> "high";
            case CRITICAL -> "critical";
        };
    }

    /**
     * Il livello scritto nella colonna {@code task.urgenza_task}, oppure
     * <strong>niente</strong> se là non c'è un livello.
     *
     * <p>Serve perché quella colonna è un {@code VARCHAR} senza {@code CHECK}:
     * il database non garantisce che contenga uno di questi quattro nomi. Ci
     * sono i {@code NULL} di tutte le task create prima della {@code V19}, che
     * ha aggiunto la colonna senza riempire le righe esistenti, e ci starebbe
     * qualunque altra stringa che un giorno finisse lì. Un
     * {@code valueOf} nudo farebbe cadere con un 500 la lettura di un intero
     * lavoro per una riga sola scritta male.
     *
     * <p>Non si ripiega su {@code MEDIUM}: inventare un livello vorrebbe dire
     * dire al PM che una task è «media» quando nessuno l'ha mai deciso. Si
     * risponde {@code null}, e chi disegna non mostra niente.
     *
     * <p>Sta qui e non nei record che la usano — {@code RigaTask} e
     * {@code MiaTask} — perché leggere i propri nomi è mestiere di questo
     * enum, e due copie della stessa conversione sono due occasioni di
     * correggerne una sola.
     */
    static TaskImportanza daStringa(String salvata) {
        if (salvata == null) {
            return null;
        }
        try {
            return TaskImportanza.valueOf(salvata);
        } catch (IllegalArgumentException sconosciuta) {
            return null;
        }
    }
}
