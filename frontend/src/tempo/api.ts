import { chiedi, putJson } from '../api/http'

/**
 * Il tempo lavorato: avvia, pausa, completa.
 *
 * Ha preso il posto della dichiarazione giornaliera. Lì gli indirizzi erano
 * indicizzati sulla data, perché la domanda era «la mia giornata di ieri»; qui
 * non c'è nessuna giornata da compilare, e gli indirizzi seguono le task — è
 * la task che si avvia, e il tempo viene dietro.
 *
 * Tutte le scritture rispondono con la **situazione intera** di chi chiede:
 * che cosa sta scorrendo, i minuti di oggi, le sessioni da confermare. È
 * quello che la barra in testata e la dashboard ridisegnano, e averlo nella
 * risposta evita una seconda chiamata dopo ogni clic.
 */

/** Da dove viene una sessione: misurata, dedotta, o importata dal vecchio modello. */
export type OrigineSessione = 'TIMER' | 'AUTOMATICA' | 'IMPORTATA'

/** Una sessione di lavoro su una task. */
export type RigaSessione = {
  id: number
  taskId: number
  task: string
  lavoroId: number
  codiceLavoro: string
  avviataIl: string
  /** Null se sta ancora scorrendo. */
  chiusaIl: string | null
  /**
   * La durata in **minuti**, non in ore: è la misura con cui la sessione è
   * stata registrata. Le ore sono un totale, e i totali li fa il server.
   */
  minuti: number
  origine: OrigineSessione
  /**
   * Falso solo per una chiusura d'ufficio che non è ancora stata guardata.
   * Quelle ore contano comunque nei conti del lavoro: non contarle farebbe
   * sparire lavoro vero. Qui il flag serve a chiederne conferma.
   */
  confermata: boolean
}

/** La task su cui il timer sta scorrendo adesso. */
export type SessioneInCorso = {
  id: number
  taskId: number
  task: string
  lavoroId: number
  codiceLavoro: string
  /**
   * L'istante di avvio. Il tempo che scorre lo conta il browser da qui: un
   * numero calcolato dal server sarebbe vecchio di un secondo appena
   * arrivato.
   */
  avviataIl: string
  minutiFinora: number
}

export type SituazioneTempo = {
  /** Null se adesso non sta lavorando a niente. */
  inCorso: SessioneInCorso | null
  /** Minuti già chiusi di oggi, la sessione in corso esclusa. */
  minutiOggi: number
  daConfermare: RigaSessione[]
}

export function mioTempo(): Promise<SituazioneTempo> {
  return chiedi<SituazioneTempo>('/api/mio-tempo')
}

/**
 * Comincia a lavorare su una task.
 *
 * Se un'altra stava scorrendo, il server **la chiude** senza chiedere niente:
 * passare da una task all'altra è un clic. Se la task era ancora da iniziare,
 * passa anche in corso.
 */
export function avvia(idLavoro: number, idTask: number): Promise<SituazioneTempo> {
  return chiedi<SituazioneTempo>(`/api/lavori/${idLavoro}/task/${idTask}/avvia`, {
    method: 'POST',
  })
}

/** Smette di lavorare. Non serve dire a quale task: ce n'è una sola aperta. */
export function pausa(): Promise<SituazioneTempo> {
  return chiedi<SituazioneTempo>('/api/mio-tempo/pausa', { method: 'POST' })
}

/**
 * Ha finito: chiude la sessione e poi la task.
 *
 * Dopo questa chiamata le ore allocate e non consumate tornano nel pool del
 * lavoro, quindi il riepilogo delle ore va ricaricato.
 */
export function completa(idLavoro: number, idTask: number): Promise<SituazioneTempo> {
  return chiedi<SituazioneTempo>(`/api/lavori/${idLavoro}/task/${idTask}/completa`, {
    method: 'POST',
  })
}

/** Accetta una chiusura d'ufficio così com'è. */
export function conferma(idSessione: number): Promise<SituazioneTempo> {
  return chiedi<SituazioneTempo>(`/api/mio-tempo/sessioni/${idSessione}/conferma`, {
    method: 'POST',
  })
}

/**
 * Corregge gli estremi di una propria sessione.
 *
 * Si corregge entro la giornata in cui si è lavorato; una chiusura d'ufficio
 * da confermare resta correggibile anche dopo, perché quel dato l'ha dedotto
 * il sistema e non la persona. Il server risponde 409 fuori da quella
 * finestra.
 */
export function correggi(
  idSessione: number,
  avviataIl: string,
  chiusaIl: string,
): Promise<SituazioneTempo> {
  return chiedi<SituazioneTempo>(
    `/api/mio-tempo/sessioni/${idSessione}`,
    putJson({ avviataIl, chiusaIl }),
  )
}

/** Questa sessione non andava registrata. La riga resta, fuori dai conti. */
export function annulla(idSessione: number): Promise<SituazioneTempo> {
  return chiedi<SituazioneTempo>(`/api/mio-tempo/sessioni/${idSessione}/annulla`, {
    method: 'POST',
  })
}

/** Minuti come «3h 20m», o «20m» se non c'è un'ora intera. */
export function durata(minuti: number): string {
  const ore = Math.floor(minuti / 60)
  const resto = minuti % 60
  if (ore === 0) {
    return `${resto}m`
  }
  return resto === 0 ? `${ore}h` : `${ore}h ${resto}m`
}
