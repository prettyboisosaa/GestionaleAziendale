import { chiedi } from '../api/http'

/**
 * Il consuntivo del tempo: su cosa ha lavorato ciascuno, e in che proporzione.
 *
 * Due rotte e due livelli, come il backend: l'elenco delle persone, e la
 * ripartizione di una di loro. Le task arrivano già dentro i lavori, quindi il
 * terzo livello non chiede niente a nessuno.
 *
 * Il server manda **minuti**, mai percentuali. La percentuale è una lettura del
 * dato e si calcola qui, dove si disegna: una colonna di percentuali
 * arrotondate dal server sommerebbe a 99,8, e un consuntivo che non chiude non
 * convince nessuno. Vedi `quota`.
 */

/** Una persona nell'elenco, con quanto ha registrato nel periodo. */
export type RiepilogoPersona = {
  utenteId: number
  nome: string
  cognome: string
  /**
   * Un disattivato compare solo se ha ore nel periodo: il lavoro di chi non
   * c'è più è stato fatto per davvero. Il flag serve a dirlo in pagina, perché
   * «zero ore» significa due cose diverse per chi è in forza e per chi non lo
   * è.
   */
  attivo: boolean
  minutiTotali: number
  minutiNonConfermati: number
}

export type ReportPersone = {
  da: string
  a: string
  persone: RiepilogoPersona[]
}

export type VoceTask = {
  taskId: number
  titolo: string
  minuti: number
}

/** Tempo registrato senza task: una riunione, una manutenzione, una trasferta. */
export type VoceAttivita = {
  tipo: string
  /** Già tradotta dal backend, da `TipoAttivita.etichetta()`. */
  etichetta: string
  minuti: number
}

export type VoceLavoro = {
  lavoroId: number
  /** Nulli se il lavoro non esiste più: il join è esterno di proposito. */
  codice: string | null
  nome: string | null
  minuti: number
  minutiNonConfermati: number
  task: VoceTask[]
  /** Ore su questo lavoro ma fuori da ogni task. */
  attivitaDirette: VoceAttivita[]
}

export type ReportPersona = {
  utenteId: number
  nome: string
  cognome: string
  da: string
  a: string
  /** È la somma esatta delle righe qui sotto, non un conto a parte. */
  minutiTotali: number
  minutiNonConfermati: number
  lavori: VoceLavoro[]
  senzaLavoro: VoceAttivita[]
}

export function reportPersone(da: string, a: string): Promise<ReportPersone> {
  return chiedi<ReportPersone>(`/api/report/persone?${periodo(da, a)}`)
}

export function reportPersona(utenteId: number, da: string, a: string): Promise<ReportPersona> {
  return chiedi<ReportPersona>(`/api/report/persone/${utenteId}?${periodo(da, a)}`)
}

function periodo(da: string, a: string): string {
  return new URLSearchParams({ da, a }).toString()
}

/**
 * I minuti come li legge una persona: `450` diventa «7h 30m».
 *
 * Non in ore decimali — «7,5 h» — perché questo numero si confronta a mente
 * con una giornata, e nessuno pensa la propria giornata in centesimi di ora.
 * Sotto l'ora si scrivono i soli minuti, perché «0h 20m» è un modo goffo di
 * dire venti minuti.
 */
export function oreMinuti(minuti: number): string {
  if (minuti <= 0) {
    return '0m'
  }
  const ore = Math.floor(minuti / 60)
  const resto = minuti % 60
  if (ore === 0) {
    return `${resto}m`
  }
  return resto === 0 ? `${ore}h` : `${ore}h ${resto}m`
}

/**
 * La parte sul totale, fra 0 e 1, oppure `null` se il totale è zero.
 *
 * Il `null` è la risposta onesta alla divisione per zero: chi non ha
 * registrato niente non ha una ripartizione, e scrivere «0%» su ogni riga
 * suggerirebbe che le righe esistano. In pagina diventa «n/d», come fa già
 * `PannelloKpi`.
 *
 * Restituisce il **rapporto grezzo** e non una percentuale arrotondata:
 * l'arrotondamento è un fatto di presentazione e si fa all'ultimo momento, su
 * ciò che si scrive. Le larghezze delle barre usano questo numero, così i
 * segmenti riempiono sempre esattamente il totale.
 */
export function quota(parte: number, totale: number): number | null {
  return totale <= 0 ? null : parte / totale
}

/** La quota come la si scrive: un decimale, perché con gli interi si legge «99%». */
export function percentuale(parte: number, totale: number): string {
  const sua = quota(parte, totale)
  return sua === null ? 'n/d' : `${(sua * 100).toFixed(1)}%`
}
