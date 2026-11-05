package com.anitia.backend.task;

/**
 * Una task che cade in un giorno del calendario di chi la deve fare.
 *
 * <p>Il minimo che serve a una cella di calendario, e non una task intera: la
 * cella mostra una pastiglia su cui si clicca, non una scheda. I dettagli
 * stanno in <em>Le mie task</em>, e {@code taskId} con {@code lavoroId} sono
 * quanto basta per arrivarci.
 *
 * <p>La data non è qui: il giorno è quello della cella che contiene il segno,
 * e ripeterla dentro vorrebbe dire poterla far mentire.
 */
public record SegnoTask(Long taskId,
                        String titolo,
                        Long lavoroId,
                        String codiceLavoro,
                        StatoTask stato,
                        TipoSegno segno) {

    static SegnoTask da(Task task, TipoSegno segno) {
        return new SegnoTask(task.getId(), task.getTitolo(), task.getLavoro().getId(),
                task.getLavoro().getCodice(), task.getStato(), segno);
    }
}
