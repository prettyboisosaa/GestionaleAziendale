/**
 * Gli stati di lavori e task, con la loro etichetta e il loro colore.
 *
 * Un modulo solo perché le stesse quattro parole compaiono in cinque
 * schermate: l'elenco dei lavori, il dettaglio di un lavoro, «Le mie task»,
 * la giornata e il calendario. Cinque copie della stessa mappa sono cinque
 * occasioni di rinominare uno stato in quattro posti su cinque.
 *
 * I colori sono quelli del documento. Non stanno qui come valori: qui ci sono
 * i nomi delle classi CSS, e i valori stanno in `App.css` accanto agli altri
 * della tavolozza — un colore scritto in un file TypeScript è un colore che
 * nessuno trova quando cerca di cambiare il tema.
 */

export type StatoLavoro = 'BOZZA' | 'ATTIVO' | 'IN_CORSO' | 'COMPLETATO' | 'ANNULLATO'

export type StatoTask = 'ATTIVO' | 'IN_CORSO' | 'COMPLETATO' | 'ANNULLATO'

/**
 * Le etichette dei lavori. «Completato» e «Annullato» al maschile, come il
 * sostantivo «lavoro».
 */
export const ETICHETTA_LAVORO: Record<StatoLavoro, string> = {
  BOZZA: 'Bozza',
  ATTIVO: 'Attivo',
  IN_CORSO: 'In corso',
  COMPLETATO: 'Completato',
  ANNULLATO: 'Annullato',
}

/** Le etichette delle task, al femminile come il sostantivo «task». */
export const ETICHETTA_TASK: Record<StatoTask, string> = {
  ATTIVO: 'Attiva',
  IN_CORSO: 'In corso',
  COMPLETATO: 'Completata',
  ANNULLATO: 'Annullata',
}

/**
 * Il modificatore CSS del colore. `BOZZA` non ne ha uno proprio: usa quello
 * di `ATTIVO`, il grigio, perché il documento prevede quattro colori e una
 * bozza è la cosa più vicina a «creato, non ancora iniziato».
 */
export function tinta(stato: StatoLavoro | StatoTask): string {
  return stato === 'BOZZA' ? 'attivo' : stato.toLowerCase().replace('_', '-')
}

/**
 * I passaggi di stato che l'interfaccia propone per una task.
 *
 * `ATTIVO` non c'è: una task ci nasce, e tornarci indietro da `IN_CORSO` non
 * è un passaggio che il server accetti.
 */
export const PASSAGGI_TASK: StatoTask[] = ['IN_CORSO', 'COMPLETATO', 'ANNULLATO']

/** Vero se sulla task si può ancora lavorare. */
export function apertaTask(stato: StatoTask): boolean {
  return stato === 'ATTIVO' || stato === 'IN_CORSO'
}

/** Vero se il lavoro è finito o non si farà. */
export function chiusoLavoro(stato: StatoLavoro): boolean {
  return stato === 'COMPLETATO' || stato === 'ANNULLATO'
}
