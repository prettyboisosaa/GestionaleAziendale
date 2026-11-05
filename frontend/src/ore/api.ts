import { chiedi, postJson } from '../api/http'

/**
 * Il monte ore: distribuzione fra le task, storico dei movimenti e richieste
 * di ore aggiuntive.
 *
 * Le ore arrivano come **numeri** JSON: sul server sono `BigDecimal`, e
 * Jackson li serializza come numeri decimali. Nessun totale si calcola qui —
 * ore libere, ore consumate e sforamenti li somma il database, perché avere
 * due posti che fanno lo stesso conto è il modo sicuro di farli divergere.
 */

export type TipoMovimento =
  | 'MONTE_INIZIALE'
  | 'MODIFICA_MONTE'
  | 'ALLOCAZIONE'
  | 'RILASCIO'

export type StatoRichiesta =
  | 'IN_ATTESA'
  | 'APPROVATA'
  | 'APPROVATA_PARZIALE'
  | 'RIFIUTATA'

/** La situazione delle ore di un lavoro. */
export type RiepilogoOre = {
  lavoroId: number
  codice: string
  /** Null se il monte ore non è ancora stato fissato. */
  monteOre: number | null
  oreAllocate: number
  oreConsumate: number
  /** `monteOre - oreAllocate`, null se il monte ore non è fissato. */
  oreLibere: number | null
}

/** Una variazione delle ore: chi, quando, quante e perché. */
export type RigaMovimento = {
  id: number
  tipo: TipoMovimento
  /** Firmate: positive quando le ore entrano, negative quando escono. */
  ore: number
  motivo: string | null
  taskId: number | null
  task: string | null
  /** La richiesta che lo ha causato, se ne nasce da una. */
  richiestaId: number | null
  autore: string
  creatoIl: string
}

export type RigaRichiestaOre = {
  id: number
  lavoroId: number
  codiceLavoro: string
  nomeLavoro: string
  /** Nulli se le ore concesse restano nel pool del lavoro. */
  taskId: number | null
  task: string | null
  richiedente: string
  oreRichieste: number
  /** Nulle finché è in attesa, zero se rifiutata. */
  oreConcesse: number | null
  motivo: string
  stato: StatoRichiesta
  gestitaDa: string | null
  gestitaIl: string | null
  creataIl: string
}

export type TaskInSforamento = {
  taskId: number
  titolo: string
  oreAllocate: number
  oreConsumate: number
  /** Di quante ore ha superato l'allocazione. */
  sforamento: number
}

/** Il quadro su cui l'amministratore decide una richiesta. */
export type ContestoRichiesta = {
  richiesta: RigaRichiestaOre
  ore: RiepilogoOre
  /** Il monte ore di partenza, dal movimento `MONTE_INIZIALE`. Null se mai fissato. */
  monteOreIniziale: number | null
  sforamenti: TaskInSforamento[]
  storico: RigaRichiestaOre[]
}

export type NuovaRichiestaOre = {
  /** Null per lasciare le ore concesse nel pool invece di mandarle a una task. */
  taskId: number | null
  oreRichieste: number
  motivo: string
}

// --- lettura ----------------------------------------------------------------

export function riepilogoOre(idLavoro: number): Promise<RiepilogoOre> {
  return chiedi<RiepilogoOre>(`/api/lavori/${idLavoro}/ore`)
}

/** Lo storico delle ore del lavoro, dal più vecchio. */
export function movimenti(idLavoro: number): Promise<RigaMovimento[]> {
  return chiedi<RigaMovimento[]>(`/api/lavori/${idLavoro}/movimenti`)
}

export function richiesteDelLavoro(idLavoro: number): Promise<RigaRichiestaOre[]> {
  return chiedi<RigaRichiestaOre[]>(`/api/lavori/${idLavoro}/richieste-ore`)
}

/** Le richieste che chi chiede ha diritto di vedere: tutte, o le proprie. */
export function mieRichieste(): Promise<RigaRichiestaOre[]> {
  return chiedi<RigaRichiestaOre[]>('/api/richieste-ore')
}

/** La coda di chi decide: le richieste in attesa, dalla più vecchia. */
export function richiesteInAttesa(): Promise<RigaRichiestaOre[]> {
  return chiedi<RigaRichiestaOre[]>('/api/richieste-ore/in-attesa')
}

export function contestoRichiesta(id: number): Promise<ContestoRichiesta> {
  return chiedi<ContestoRichiesta>(`/api/richieste-ore/${id}/contesto`)
}

// --- scrittura --------------------------------------------------------------

/**
 * Alloca ore dal pool del lavoro a una task.
 *
 * Risponde 409 se il pool non basta, e **il messaggio dice quante ore libere
 * ci sono**: è il numero con cui si precompila la richiesta
 * all'amministratore, quindi va mostrato tale e quale.
 */
export function allocaOre(
  idLavoro: number,
  idTask: number,
  ore: number,
  motivo: string,
): Promise<RiepilogoOre> {
  return chiedi<RiepilogoOre>(
    `/api/lavori/${idLavoro}/task/${idTask}/ore`,
    postJson({ ore, motivo }),
  )
}

export function apriRichiestaOre(
  idLavoro: number,
  dati: NuovaRichiestaOre,
): Promise<RigaRichiestaOre> {
  return chiedi<RigaRichiestaOre>(`/api/lavori/${idLavoro}/richieste-ore`, postJson(dati))
}

/**
 * La decisione: quante ore si concedono. Zero è un rifiuto, tutte quelle
 * chieste un'approvazione piena, una parte un'approvazione parziale — lo stato
 * lo deduce il server dal numero, non lo si invia.
 */
export function gestisciRichiestaOre(
  id: number,
  oreConcesse: number,
): Promise<RigaRichiestaOre> {
  return chiedi<RigaRichiestaOre>(
    `/api/richieste-ore/${id}/gestisci`,
    postJson({ oreConcesse }),
  )
}
