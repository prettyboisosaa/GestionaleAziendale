import { chiedi, postJson, putJson } from '../api/http'

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

/**
 * Da dove viene una sessione: misurata dal timer, dedotta dal tetto,
 * importata dal vecchio modello, o scritta a mano.
 *
 * Non è un dettaglio tecnico: dice **quanto quel dato è stato misurato**, e la
 * griglia settimanale ci si appoggia per tratteggiare i blocchi la cui ora del
 * giorno non l'ha scelta un orologio. Chi legge un consuntivo ha diritto di
 * sapere quanta parte di quelle ore è misura e quanta è ricordo.
 */
export type OrigineSessione = 'TIMER' | 'AUTOMATICA' | 'IMPORTATA' | 'MANUALE'

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

/**
 * Una settimana di lavoro, da lunedì a domenica, come la manda il server.
 *
 * Gli estremi arrivano da lì invece di essere calcolati qui: il lunedì di una
 * data dipende da dove si fa cominciare la settimana, e due definizioni —
 * una nel browser e una nel servizio — sposterebbero di un giorno il confine
 * fra una settimana e l'altra.
 */
export type SettimanaDiTempo = {
  /** Il lunedì, in ISO. */
  inizio: string
  /** La domenica, compresa. */
  fine: string
  /** La somma dei minuti delle sessioni non annullate. */
  minutiTotali: number
  /**
   * Vero se è la settimana corrente, cioè se qui si può ancora scrivere.
   *
   * Lo dice il server invece di lasciarlo dedurre a chi disegna la griglia: la
   * regola della finestra sta in `ServizioTempo`, e una seconda copia qui
   * prima o poi direbbe un'altra cosa. Una griglia che si apre per ricevere un
   * 409 ha mentito a chi l'ha cliccata.
   */
  modificabile: boolean
  sessioni: RigaSessione[]
}

export function mioTempo(): Promise<SituazioneTempo> {
  return chiedi<SituazioneTempo>('/api/mio-tempo')
}

/**
 * La mia settimana, da lunedì a domenica.
 *
 * `giorno` è un giorno **qualunque** della settimana voluta, non il suo
 * lunedì: il calendario manda il primo giorno che ha in mano e il server
 * risale alla settimana. Omesso, è quella di oggi.
 *
 * Non si usa `/api/tempo/utente/{id}`, che restituirebbe le stesse sessioni:
 * quella chiede `TEMPO_VISUALIZZA_TUTTI` o `_LAVORO`, permessi che un
 * dipendente non ha. Guardare le proprie ore sta sul pavimento.
 */
export function miaSettimana(giorno?: string): Promise<SettimanaDiTempo> {
  const query = giorno === undefined ? '' : `?giorno=${giorno}`
  return chiedi<SettimanaDiTempo>(`/api/mio-tempo/settimana${query}`)
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

/**
 * Registra a mano del lavoro già fatto: due istanti su una task.
 *
 * È la rete per chi si dimentica di premere avvia, non un ritorno alla
 * dichiarazione giornaliera: lì si compilava ogni sera una giornata intera,
 * qui si riempie un buco su un calendario che per il resto si riempie da sé.
 *
 * L'indirizzo nomina la task, come `avvia` e `completa`: è la task che si
 * registra, e il tempo viene dietro. Il server rifiuta un intervallo nel
 * futuro, fuori dalla settimana corrente, su una task non propria, o che si
 * accavalla a un'altra sessione.
 */
export function registraManuale(
  idLavoro: number,
  idTask: number,
  avviataIl: string,
  chiusaIl: string,
): Promise<SituazioneTempo> {
  return chiedi<SituazioneTempo>(
    `/api/lavori/${idLavoro}/task/${idTask}/sessioni`,
    postJson({ avviataIl, chiusaIl }),
  )
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
 * Si corregge entro la **settimana** in cui si è lavorato; una chiusura
 * d'ufficio da confermare resta correggibile anche dopo, perché quel dato
 * l'ha dedotto il sistema e non la persona. Il server risponde 409 fuori da
 * quella finestra, e 409 anche se gli orari nuovi finirebbero addosso a
 * un'altra sessione: le ore di una persona non si sovrappongono.
 *
 * La task non si cambia: la richiesta porta due istanti e nient'altro. Una
 * sessione è il tempo passato su *quella* task, e spostarlo su un'altra non è
 * correggere un orario — si annulla e si riscrive.
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

/**
 * Un istante nel formato che il server parla: `2026-11-10T09:30:00`, senza
 * fuso.
 *
 * Si compone a mano dai campi **locali** di proposito. `toISOString()`
 * convertirebbe in UTC, e in Italia un orario slitterebbe di un'ora o due —
 * un blocco disegnato alle 8 per del lavoro fatto alle 10, e peggio, ore
 * scritte nel giorno sbagliato. È la stessa trappola che `piuUnGiorno`, nella
 * dashboard, commenta per le date a giornata intera.
 *
 * Sta qui accanto a `durata` perché è il suo opposto: quella formatta per
 * l'occhio, questa per il filo.
 */
export function istanteLocale(quando: Date): string {
  const due = (numero: number) => String(numero).padStart(2, '0')
  return (
    `${quando.getFullYear()}-${due(quando.getMonth() + 1)}-${due(quando.getDate())}`
    + `T${due(quando.getHours())}:${due(quando.getMinutes())}:${due(quando.getSeconds())}`
  )
}
