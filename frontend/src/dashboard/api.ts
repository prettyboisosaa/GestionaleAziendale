import { chiedi } from '../api/http'
import type { StatoLavoro, StatoTask } from '../stato/stati'
import type { UrgenzaTask } from '../task/api'

/** Una task assegnata a chi guarda, come la restituisce il backend. */
export type MiaTask = {
  taskId: number
  titolo: string
  descrizione: string | null
  dataInizio: string
  scadenza: string
  stato: StatoTask
  oreAllocate: number
  oreConsumate: number
  /** Le ore registrate da me su questa task, non da tutti gli assegnatari. */
  mieOre: number
  sforamento: boolean
  /**
   * Vero se il timer sta scorrendo su questa task adesso. Al massimo una
   * riga della risposta lo ha: una persona ha una sola sessione aperta.
   */
  inCorso: boolean
  inRitardo: boolean
  /** Negativo se la scadenza è passata. */
  giorniAllaScadenza: number
  lavoroId: number
  codiceLavoro: string
  nomeLavoro: string
  statoLavoro: StatoLavoro
  responsabile: string | null
  /**
   * Quanto questa task viene prima delle altre. **Può essere nulla**: le task
   * create prima della `V19` non hanno un'urgenza, e il server risponde
   * `null` invece di inventarne una.
   */
  urgenzaTask: UrgenzaTask | null
}

export type ContatoriDashboard = {
  /**
   * Task aperte marcate «Urgente» dal PM.
   *
   * Ha preso il posto di `scadute`: una scadenza passata è un fatto che la
   * lista e il calendario già raccontano — «in ritardo di tre giorni», la
   * barra alle spalle di oggi — mentre l'urgenza, che è una decisione, non si
   * vedeva da nessuna parte.
   */
  critiche: number
  oggi: number
  settimana: number
  inCorso: number
}

export type RiepilogoDashboard = {
  contatori: ContatoriDashboard
  /**
   * Tutte le mie task, non solo le urgenti: i filtri della lista e il filtro
   * per giorno che nasce dal clic sul calendario lavorano su questo elenco
   * senza tornare al server.
   */
  task: MiaTask[]
  /** Gli id delle task che scadono in un giorno di assenza approvata. */
  taskInConflitto: number[]
}

export type TipoEvento = 'TASK' | 'ASSENZA' | 'FESTIVITA'

export type EventoCalendario = {
  id: string
  tipo: TipoEvento
  titolo: string
  inizio: string
  /**
   * L'ultimo giorno coperto, **compreso**. FullCalendar vuole invece una fine
   * esclusiva per gli eventi a giornata intera: il giorno in più lo aggiunge
   * il componente quando traduce, non il server.
   */
  fine: string
  /** Lo stato della task o dell'assenza; null per le festività. */
  stato: string | null
  conflitto: boolean
  url: string | null
}

export function miaDashboard(): Promise<RiepilogoDashboard> {
  return chiedi<RiepilogoDashboard>('/api/mia-dashboard')
}

/**
 * Gli eventi fra due date, estremi compresi. Si chiede solo l'intervallo
 * visibile: il calendario ricarica a ogni cambio di settimana o di mese, e una
 * risposta con l'anno intero crescerebbe senza che nessuno se ne accorga.
 */
export function mioCalendario(da: string, a: string): Promise<EventoCalendario[]> {
  return chiedi<EventoCalendario[]>(`/api/mio-calendario?da=${da}&a=${a}`)
}
