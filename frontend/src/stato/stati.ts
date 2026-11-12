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

/**
 * La tinta di un lavoro, per i blocchi del calendario settimanale.
 *
 * Una per lavoro e non una per task: la domanda a cui la settimana risponde a
 * colpo d'occhio è «su quali progetti è andata», e due task dello stesso
 * lavoro che fossero di due colori diversi la renderebbero illeggibile.
 *
 * Si ricava dall'**id**, non dall'ordine in cui le sessioni arrivano: così il
 * lavoro che questa settimana è verde è verde anche la prossima, e la memoria
 * visiva serve a qualcosa. Otto tinte che si ripetono — con più di otto lavori
 * nella stessa settimana due possono coincidere, e si accetta: dentro il
 * blocco c'è scritto il codice del lavoro.
 *
 * Come `tinta`, restituisce **il nome della classe** e non un colore: i valori
 * stanno in `App.css` accanto al resto della tavolozza.
 */
export function tintaLavoro(lavoroId: number): string {
  return `lavoro-${lavoroId % 8}`
}

/**
 * La tinta di una persona, per gli eventi del calendario delle assenze.
 *
 * Serve perché la domanda a cui quel calendario risponde è «**quante** persone
 * sono via la settimana del 14», e con tutti i rettangoli dello stesso viola
 * bisognava leggere i nomi uno per uno per saperlo: tre giorni della stessa
 * persona sembravano tre colleghi in ferie.
 *
 * Si ricava dal **nome** e non da un id perché la rotta del calendario non
 * manda l'id del richiedente — solo il nome visualizzato. È comunque stabile:
 * chi questo mese è verde è verde anche il mese prossimo, che è la proprietà
 * che serve. Il moltiplicatore 31 è quello dell'`hashCode` di Java, e sparge
 * su indici diversi anche nomi vicini come «Rossi Marco» e «Rossi Mario»;
 * il modulo tiene la somma lontana dall'interno non sicuro dei float.
 *
 * Otto tinte che si ripetono, come per i lavori: con più di otto persone via
 * nello stesso mese due possono coincidere, e si accetta — il nome resta
 * scritto dentro il rettangolo.
 *
 * Come `tinta` e `tintaLavoro`, restituisce **il nome della classe**: i valori
 * stanno in `App.css` accanto al resto della tavolozza.
 */
export function tintaPersona(nome: string): string {
  let somma = 0
  for (let i = 0; i < nome.length; i += 1) {
    somma = (somma * 31 + nome.charCodeAt(i)) % 1000003
  }
  return `persona-${somma % 8}`
}
