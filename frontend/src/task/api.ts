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
  /**
   * Quanto questa task viene prima delle altre.
   *
   * **Può essere nulla**, e non è un caso di confine da ignorare: la `V19` ha
   * aggiunto la colonna senza riempire le righe esistenti, quindi ogni task
   * creata prima non ha un'urgenza. Il server risponde `null` invece di
   * inventare un livello, e qui non si mostra niente — dire «media» di una
   * task su cui nessuno ha deciso sarebbe mettergli in bocca una parola.
   */
  urgenzaTask: UrgenzaTask | null
  /**
   * Quando la task è stata completata, oppure null se non lo è.
   *
   * **Può essere null anche su una task completata**: la colonna è arrivata
   * con la `V21`, e le task chiuse prima non hanno una data. Lo stato dice
   * *se* è chiusa; questo campo serve solo a scrivere *quando*, se c'è.
   */
  completatoIl: string | null
  assegnatari: Assegnatario[]
}

/**
 * Quanto una task viene prima delle altre.
 *
 * Il tipo sta qui e non in `stato/stati`: là vivono gli **stati** di lavori e
 * task, che compaiono in cinque schermate e portano i nomi delle classi CSS
 * dei loro colori. L'urgenza per ora riguarda un campo di un form, e la regola
 * del progetto è che i tipi propri di un dominio stiano nell'`api.ts` di quel
 * dominio insieme alle loro etichette — come `MotivoPermesso` in
 * `assenza/api.ts` o `TipoMovimento` in `ore/api.ts`.
 *
 * I valori sono in **inglese** perché rispecchiano `TaskImportanza` lato
 * server, e sul filo deve passare esattamente quello che quell'enum accetta.
 * È l'unico enum del progetto con valori non italiani: le etichette qui sotto
 * sono il punto dove le due lingue si incontrano, e non ce ne sono altri.
 */
export type UrgenzaTask = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

/** Al femminile, come le etichette di stato delle task: «questa task è alta». */
export const ETICHETTA_URGENZA: Record<UrgenzaTask, string> = {
  LOW: 'Bassa',
  MEDIUM: 'Media',
  HIGH: 'Alta',
  CRITICAL: 'Urgente',
}

/**
 * Il modificatore di colore della pillola, per livello.
 *
 * Come `TINTA_STATO` in `assenza/api.ts` e come `tinta()` in `stato/stati`:
 * qui c'è il **nome della classe**, i valori stanno in `App.css` accanto al
 * resto della tavolozza. Un colore scritto in un file TypeScript è un colore
 * che nessuno trova quando cerca di cambiare il tema.
 *
 * I primi due livelli sono volutamente spenti. La maggior parte delle task è
 * «media», e se ogni riga della tabella accendesse un colore la colonna
 * diventerebbe rumore invece di segnale: si notano le due che vengono prima.
 */
export const TINTA_URGENZA: Record<UrgenzaTask, string> = {
  LOW: 'urgenza-bassa',
  MEDIUM: 'urgenza-media',
  HIGH: 'urgenza-alta',
  CRITICAL: 'urgenza-urgente',
}

export type NuovaTask = {
  titolo: string
  descrizione: string | null
  dataInizio: string
  scadenza: string
  stimaOre: number | null
  /**
   * Non è facoltativa come la stima: la tendina parte compilata, quindi un
   * valore c'è sempre. Il nome combacia con la colonna `urgenza_task` e con
   * il campo dell'entità.
   */
  urgenzaTask: UrgenzaTask
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
