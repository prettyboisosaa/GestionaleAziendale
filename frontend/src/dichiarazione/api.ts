import { chiama, chiedi, messaggioErrore, postJson } from '../api/http'

/**
 * La dichiarazione giornaliera: dove il dipendente registra le ore lavorate.
 *
 * Gli indirizzi sono indicizzati sulla **data** e non sull'id: è così che li
 * usa chi compila — «la mia giornata di ieri», non «la dichiarazione 417» — e
 * perché la giornata di ieri può non esistere ancora.
 *
 * Tutte le scritture rispondono con la **giornata intera** aggiornata, totale
 * e ore residue compresi: è quello che la pagina ridisegna, e averlo nella
 * risposta evita una seconda chiamata dopo ogni modifica.
 */

export type TipoGiornata = 'LAVORATA' | 'FERIE' | 'PERMESSO' | 'MALATTIA'

export type StatoDichiarazione = 'BOZZA' | 'INVIATA'

/** Una riga di dichiarazione: una task, le ore e cosa si è fatto. */
export type RigaVoce = {
  id: number
  taskId: number
  task: string
  /** Il codice del lavoro, per distinguere task omonime. */
  codiceLavoro: string
  lavoroId: number
  ore: number
  descrizione: string
}

/** Una task su cui si può registrare: aperta e assegnata a chi chiede. */
export type TaskDichiarabile = {
  taskId: number
  titolo: string
  lavoroId: number
  codiceLavoro: string
  nomeLavoro: string
}

export type Giornata = {
  data: string
  /**
   * Null quando per quel giorno non c'è ancora niente di scritto. È la
   * differenza fra «giornata vuota» e «giornata con zero ore»: la prima non
   * esiste nel database, e una lettura non la crea.
   */
  id: number | null
  tipoGiornata: TipoGiornata
  stato: StatoDichiarazione
  inviataIl: string | null
  oreTotali: number
  /** Il tetto giornaliero, dai parametri di sistema. */
  oreMassime: number
  /** `oreMassime - oreTotali`, mai negative. */
  oreResidue: number
  /**
   * Falso quando la finestra di correzione è scaduta o il giorno è futuro. Si
   * usa per mostrare la giornata in sola lettura invece di far scrivere
   * qualcosa che il server rifiuterebbe.
   */
  modificabile: boolean
  /**
   * Vero se per quel giorno c'è un'assenza approvata, ferie o permesso.
   *
   * È un **avviso**, non un divieto: registrare ore in un giorno di assenza
   * resta permesso, perché chi ha lavorato comunque deve poterlo dichiarare.
   */
  assenzaApprovata: boolean
  voci: RigaVoce[]
  taskDisponibili: TaskDichiarabile[]
}

/** Una giornata nell'elenco di un periodo: senza le voci, solo il totale. */
export type RigaGiorno = {
  id: number
  data: string
  tipoGiornata: TipoGiornata
  stato: StatoDichiarazione
  inviataIl: string | null
  oreTotali: number
}

export type NuovaVoce = {
  taskId: number
  ore: number
  descrizione: string
}

/**
 * La mia giornata. Un giorno senza dichiarazione risponde 200 con `id` nullo,
 * non 404: è una giornata da compilare, non un errore.
 */
export function giornata(data: string): Promise<Giornata> {
  return chiedi<Giornata>(`/api/dichiarazioni/${data}`)
}

export function mieGiornate(da: string, a: string): Promise<RigaGiorno[]> {
  return chiedi<RigaGiorno[]>(`/api/dichiarazioni?da=${da}&a=${a}`)
}

/** Le giornate di un'altra persona: serve il permesso sul team o su tutte. */
export function giornateDi(utenteId: number, da: string, a: string): Promise<RigaGiorno[]> {
  return chiedi<RigaGiorno[]>(`/api/dichiarazioni/utente/${utenteId}?da=${da}&a=${a}`)
}

/**
 * Registra ore su una task, creando la dichiarazione del giorno se è la prima
 * voce.
 *
 * Risponde 409 se la giornata sfonderebbe il tetto, e il messaggio dice quante
 * ore restano: va mostrato tale e quale.
 */
export function registraOre(data: string, dati: NuovaVoce): Promise<Giornata> {
  return chiedi<Giornata>(`/api/dichiarazioni/${data}/voci`, postJson(dati))
}

export function correggiVoce(
  data: string,
  idVoce: number,
  dati: NuovaVoce,
): Promise<Giornata> {
  return chiedi<Giornata>(`/api/dichiarazioni/${data}/voci/${idVoce}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(dati),
  })
}

export async function togliVoce(data: string, idVoce: number): Promise<Giornata> {
  const risposta = await chiama(`/api/dichiarazioni/${data}/voci/${idVoce}`, {
    method: 'DELETE',
  })
  if (!risposta.ok) {
    throw new Error(await messaggioErrore(risposta))
  }
  // La DELETE risponde con la giornata aggiornata, non con 204: il totale e le
  // ore residue sono cambiati, e la pagina li deve ridisegnare.
  return (await risposta.json()) as Giornata
}

/**
 * Ferie, permesso, malattia o giornata lavorata.
 *
 * Il server rifiuta con 409 il passaggio a un tipo non lavorato se ci sono ore
 * già registrate, invece di cancellarle in silenzio.
 */
export function cambiaTipoGiornata(
  data: string,
  tipoGiornata: TipoGiornata,
): Promise<Giornata> {
  return chiedi<Giornata>(`/api/dichiarazioni/${data}/tipo`, postJson({ tipoGiornata }))
}

/**
 * Consegna la giornata. Resta correggibile per i giorni lavorativi che dicono
 * i parametri di sistema: l'invio è una consegna, non una chiusura a chiave.
 */
export function inviaGiornata(data: string): Promise<Giornata> {
  return chiedi<Giornata>(`/api/dichiarazioni/${data}/invia`, { method: 'POST' })
}
