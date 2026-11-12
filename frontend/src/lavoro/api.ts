import { chiama, chiedi, messaggioErrore, postJson } from '../api/http'
import type { StatoLavoro } from '../stato/stati'

// Gli stati vivono in `stato/stati`, insieme alle loro etichette e ai loro
// colori: tenerli qui vorrebbe dire una seconda lista da aggiornare quando ne
// nasce uno. Si riesporta perché le pagine importano il tipo da questo modulo
// insieme a `RigaLavoro`.
export type { StatoLavoro }

export type RigaLavoro = {
  id: number
  codice: string
  nome: string
  descrizione: string | null
  dataInizio: string
  dataFinePrevista: string
  stato: StatoLavoro
  /** Quando è stato annullato; null se non lo è. */
  annullatoIl: string | null
  /** Null per una bozza senza responsabile: serve a sapere se chi guarda lo è. */
  responsabileId: number | null
  /** «Cognome Nome», oppure null se è una bozza senza responsabile. */
  responsabile: string | null
  /**
   * Il budget di ore. `null` vuol dire «non ancora fissato», che non è zero:
   * un budget a zero direbbe che le ore sono esaurite.
   *
   * Le ore arrivano come numeri JSON, non come stringhe: sono `BigDecimal`
   * sul server, e Jackson li serializza come numeri decimali.
   */
  monteOre: number | null
  /** La somma delle ore allocate alle task. Mai null. */
  oreAllocate: number
  /** La somma delle ore registrate dai dipendenti. Mai null. */
  oreConsumate: number
  /** `monteOre - oreAllocate`, oppure null se il monte ore non è fissato. */
  oreLibere: number | null
  /**
   * Quante task ha il lavoro, **annullate escluse**: il denominatore
   * dell'avanzamento scritto sulla scheda dell'elenco. Mai null — un lavoro
   * senza task ne ha zero.
   */
  taskTotali: number
  /** Quante di quelle task sono completate. */
  taskCompletate: number
  /**
   * Quante task ancora aperte sono di urgenza alta o critica: è il «2 priorità
   * alte» della scheda. Dice se dentro un lavoro tranquillo c'è qualcosa che
   * viene prima di tutto il resto.
   */
  taskUrgenti: number
  /** Quante task ancora aperte hanno la scadenza passata. */
  taskInRitardo: number
}

export type CandidatoResponsabile = {
  id: number
  nome: string
  cognome: string
}

export type NuovoLavoro = {
  nome: string
  descrizione: string | null
  dataInizio: string
  dataFinePrevista: string
  stato: StatoLavoro
  /** Null per una bozza senza responsabile. */
  responsabileId: number | null
  /**
   * Il budget di ore. Null per lasciarlo da fissare: il server risponde 403 a
   * chi lo invia senza il permesso `ORE_MONTE_GESTISCI`, quindi il campo si
   * mostra solo a chi ce l'ha.
   */
  monteOre: number | null
}

/**
 * I lavori visibili. Gli annullati restano fuori, perché l'elenco serve a
 * lavorare e un lavoro che non si farà sarebbe rumore; con
 * `includiAnnullati` rientrano, in fondo alla lista, per consultarli.
 */
export function elencoLavori(includiAnnullati = false): Promise<RigaLavoro[]> {
  const query = includiAnnullati ? '?includi_annullati=true' : ''
  return chiedi<RigaLavoro[]>('/api/lavori' + query)
}

/** Un singolo lavoro. 403 se non compete a chi chiede. */
export function dettaglioLavoro(id: number): Promise<RigaLavoro> {
  return chiedi<RigaLavoro>(`/api/lavori/${id}`)
}

/**
 * Gli indicatori di avanzamento di un lavoro.
 *
 * **Ogni percentuale può essere null**, e va trattata come tale: vuol dire che
 * il denominatore era zero, cioè che non c'era niente da misurare. Non è uno
 * zero travestito — un lavoro senza ore allocate non ha «scostamento zero» —
 * e l'interfaccia scrive «n/d».
 *
 * Le ore arrivano come **numeri** JSON: sono `BigDecimal` sul server e Jackson
 * li serializza come decimali.
 */
export type KpiLavoro = {
  /** Firmato: positivo sopra budget, negativo sotto. */
  scostamentoOre: number | null
  /** Ore consumate su allocate, in percentuale. */
  avanzamento: number | null
  consegneInTempo: number | null
  stagnazione: number | null
  oreAllocate: number
  oreConsumate: number
  /** Le task non annullate. */
  taskConsiderate: number
  taskCompletate: number
  /**
   * Su quante task è calcolato `consegneInTempo`. È minore di
   * `taskCompletate` quando ci sono task chiuse prima della `V21`, che non
   * hanno una data di completamento e restano fuori dal conto.
   */
  taskConDataCompletamento: number
  taskInCorso: number
  taskFerme: number
  /** Da quanti giorni di immobilità una task si considera ferma. */
  giorniDiStagnazione: number
}

/**
 * I KPI del lavoro. Rotta a sé e non un campo del dettaglio: costano una query
 * aggregata in più, e chi apre l'elenco non li guarda.
 */
export function kpiLavoro(id: number): Promise<KpiLavoro> {
  return chiedi<KpiLavoro>(`/api/lavori/${id}/kpi`)
}

/**
 * Come è andato un lavoro **nel tempo**: una riga per settimana, dall'inizio
 * del lavoro a oggi.
 *
 * I KPI dicono a che punto è il lavoro; questa serie dice con che ritmo ci è
 * arrivato — ed è la differenza fra un lavoro quasi finito e uno che ha
 * bruciato il budget nelle ultime due settimane.
 *
 * **Le ore sono quelle di ciascuna settimana, non il totale progressivo**: il
 * cumulato lo costruisce il grafico, che deve comunque sommare per collocare
 * i punti.
 */
export type AndamentoLavoro = {
  /** Il soffitto del grafico, e da cui nasce la linea del piano. Null se non fissato. */
  monteOre: number | null
  dataInizio: string
  dataFinePrevista: string
  settimane: SettimanaLavoro[]
}

export type SettimanaLavoro = {
  /** Il lunedì della settimana, in ISO. */
  inizio: string
  /** Le ore registrate in quella settimana. Zero se nessuna. */
  ore: number
  /** Quante task si sono chiuse in quella settimana. */
  taskCompletate: number
}

/**
 * L'andamento settimanale. Rotta a sé come i KPI, e per la stessa ragione:
 * un errore su una serie di cinquanta righe non deve portare via i numeri in
 * cima alla pagina.
 */
export function andamentoLavoro(id: number): Promise<AndamentoLavoro> {
  return chiedi<AndamentoLavoro>(`/api/lavori/${id}/andamento`)
}

export function responsabiliPossibili(): Promise<CandidatoResponsabile[]> {
  return chiedi<CandidatoResponsabile[]>('/api/lavori/responsabili')
}

/**
 * Fissa o modifica il monte ore. Solo l'amministratore: il PM distribuisce il
 * budget fra le task ma non decide quanto è grande.
 *
 * Il motivo è obbligatorio, e il server lo registra in un movimento: è quello
 * che permette di rileggere il valore iniziale dopo la modifica.
 *
 * Risponde 409 se il nuovo valore è sotto le ore già allocate alle task, e il
 * messaggio dice quante sono. Va mostrato tale e quale.
 */
export function cambiaMonteOre(
  id: number,
  monteOre: number,
  motivo: string,
): Promise<RigaLavoro> {
  return chiedi<RigaLavoro>(`/api/lavori/${id}/monte-ore`, postJson({ monteOre, motivo }))
}

/** Il codice non si invia: lo genera il server. */
export function creaLavoro(dati: NuovoLavoro): Promise<RigaLavoro> {
  return chiedi<RigaLavoro>('/api/lavori', postJson(dati))
}

/**
 * Annulla il lavoro: la riga resta nel database con lo stato cambiato. È la
 * via per qualunque lavoro che abbia delle task, perché cancellarlo
 * porterebbe via anche le dichiarazioni già inviate.
 *
 * Annulla anche **tutte le sue task**, comprese quelle completate, e lo toglie
 * dagli elenchi e dai selettori della registrazione ore. Le ore già registrate
 * restano nello storico e nei report, perché sono lavoro svolto davvero.
 */
export function annullaLavoro(id: number): Promise<RigaLavoro> {
  return chiedi<RigaLavoro>(`/api/lavori/${id}/annulla`, { method: 'POST' })
}

/**
 * Cancella il lavoro. Il server lo consente solo su una bozza senza task, e
 * negli altri casi risponde 409 con la spiegazione: il frontend non deve
 * sapere quante task ci sono, gli basta mostrare quel messaggio.
 *
 * Non usa `chiedi` perché la risposta è 204 senza corpo, e leggerlo come JSON
 * fallirebbe.
 */
export async function eliminaLavoro(id: number): Promise<void> {
  const risposta = await chiama(`/api/lavori/${id}`, { method: 'DELETE' })
  if (!risposta.ok) {
    throw new Error(await messaggioErrore(risposta))
  }
}
