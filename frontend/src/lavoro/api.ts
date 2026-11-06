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
