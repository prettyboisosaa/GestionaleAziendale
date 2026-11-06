import { chiama, chiedi, messaggioErrore, postJson } from '../api/http'

import type { StatoTask } from '../stato/stati'

// Come per i lavori: gli stati stanno in `stato/stati` con le loro etichette e
// i loro colori, e si riesportano qui perché le pagine li importano insieme a
// `RigaTask`.
export type { StatoTask }

export type Assegnatario = {
  utenteId: number
  nome: string
  cognome: string
}

export type RigaTask = {
  id: number
  titolo: string
  descrizione: string | null
  dataInizio: string
  scadenza: string
  /**
   * Quante ore servono secondo il PM. Arriva come **numero**, non come
   * stringa: è un `BigDecimal` sul server e Jackson lo serializza come numero
   * decimale.
   */
  stimaOre: number | null
  /** Quante ore sono coperte dal budget del lavoro. Mai null. */
  oreAllocate: number
  /** Quante ore sono state registrate dai dipendenti. Mai null. */
  oreConsumate: number
  /**
   * Vero quando le ore consumate hanno superato quelle allocate.
   *
   * Lo calcola il server dalle due somme: non è un campo salvato e non va
   * ricalcolato qui, altrimenti due posti direbbero la stessa cosa e prima o
   * poi in modo diverso.
   */
  sforamento: boolean
  stato: StatoTask
  assegnatari: Assegnatario[]
}

export type NuovaTask = {
  titolo: string
  descrizione: string | null
  dataInizio: string
  scadenza: string
  stimaOre: number | null
}

/**
 * Le task del lavoro che si ha diritto di vedere. Per leggere basta poter
 * vedere il lavoro: quali task tornano dipende dai permessi sulle task, e lo
 * decide il server.
 */
export function taskDelLavoro(idLavoro: number): Promise<RigaTask[]> {
  return chiedi<RigaTask[]>(`/api/lavori/${idLavoro}/task`)
}

/** Chi si può assegnare: tutti gli attivi, o il proprio team. Lo filtra il server. */
export function assegnabili(idLavoro: number): Promise<Assegnatario[]> {
  return chiedi<Assegnatario[]>(`/api/lavori/${idLavoro}/task/assegnabili`)
}

export function creaTask(idLavoro: number, dati: NuovaTask): Promise<RigaTask> {
  return chiedi<RigaTask>(`/api/lavori/${idLavoro}/task`, postJson(dati))
}

/**
 * Cambia lo stato della task.
 *
 * Portandola a `COMPLETATO` o `ANNULLATO`, le ore stanziate e non consumate
 * tornano nel pool del lavoro: dopo questa chiamata il riepilogo delle ore va
 * ricaricato, perché le ore libere sono cambiate.
 *
 * Una task chiusa non si riapre, e il server risponde 409 spiegando perché.
 */
export function cambiaStatoTask(
  idLavoro: number,
  idTask: number,
  stato: StatoTask,
): Promise<RigaTask> {
  return chiedi<RigaTask>(
    `/api/lavori/${idLavoro}/task/${idTask}/stato`,
    postJson({ stato }),
  )
}

/** Risposte 204 senza corpo: non si può usare `chiedi`, che legge il JSON. */
export async function assegna(
  idLavoro: number,
  idTask: number,
  utenteId: number,
): Promise<void> {
  await senzaCorpo(`/api/lavori/${idLavoro}/task/${idTask}/assegnazioni`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ utenteId }),
  })
}

export async function rimuoviAssegnazione(
  idLavoro: number,
  idTask: number,
  utenteId: number,
): Promise<void> {
  await senzaCorpo(`/api/lavori/${idLavoro}/task/${idTask}/assegnazioni/${utenteId}`, {
    method: 'DELETE',
  })
}

async function senzaCorpo(url: string, opzioni: RequestInit): Promise<void> {
  const risposta = await chiama(url, opzioni)
  if (!risposta.ok) {
    throw new Error(await messaggioErrore(risposta))
  }
}
