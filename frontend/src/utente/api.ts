import { chiama, chiedi, messaggioErrore, patchJson, postJson } from '../api/http'

export type RigaUtente = {
  id: number
  nome: string
  cognome: string
  email: string
  attivo: boolean
  /** Quando è stato disattivato; null se è attivo. */
  disattivatoIl: string | null
  creatoIl: string
  ruoli: string[]
  /** Quanti permessi ha in tutto, ruoli e scostamenti insieme. */
  permessi: number
}

export type NuovoUtente = {
  nome: string
  cognome: string
  email: string
  password: string
  ruoli: number[]
}

/** Da dove viene — o perché manca — un permesso di una persona. */
export type OriginePermesso = 'RUOLO' | 'AGGIUNTO' | 'TOGLIATO' | 'NESSUNO'

export type PermessoUtente = {
  codice: string
  categoria: string
  descrizione: string
  /** Vero se un ruolo della persona concede questo permesso. */
  dalRuolo: boolean
  origine: OriginePermesso
  effettivo: boolean
}

export function elencoUtenti(): Promise<RigaUtente[]> {
  return chiedi<RigaUtente[]>('/api/utenti')
}

export function creaUtente(dati: NuovoUtente): Promise<RigaUtente> {
  return chiedi<RigaUtente>('/api/utenti', postJson(dati))
}

/**
 * Disattiva o riattiva un utente.
 *
 * La riga non si cancella mai: il nome di chi ha creato un lavoro o registrato
 * delle ore compare nello storico, e cancellarlo lo renderebbe illeggibile. Un
 * utente disattivato resta nell'elenco, col suo badge, non entra più nei
 * selettori di assegnazione e perde subito le sessioni aperte.
 *
 * Il server risponde 409 a chi prova a cambiare lo stato della propria utenza.
 */
export function cambiaAttivazione(id: number, attivo: boolean): Promise<RigaUtente> {
  return chiedi<RigaUtente>(`/api/utenti/${id}`, patchJson({ attivo }))
}

/** Tutto il catalogo, con l'origine di ciascun permesso per questa persona. */
export function permessiUtente(id: number): Promise<PermessoUtente[]> {
  return chiedi<PermessoUtente[]>(`/api/utenti/${id}/permessi`)
}

/**
 * Dice se la persona deve poter fare questa cosa. Quale riga scrivere — una
 * concessione, una negazione o la cancellazione di uno scostamento — lo decide
 * il server: qui si manda solo il sì o il no.
 */
export function impostaPermesso(
  id: number,
  codice: string,
  effettivo: boolean,
): Promise<PermessoUtente> {
  return chiedi<PermessoUtente>(`/api/utenti/${id}/permessi/${codice}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ effettivo }),
  })
}

/**
 * Toglie lo scostamento: il permesso torna a dipendere dai ruoli. Non usa
 * `chiedi` perché la risposta è 204 senza corpo.
 */
export async function ripristinaPermesso(id: number, codice: string): Promise<void> {
  const risposta = await chiama(`/api/utenti/${id}/permessi/${codice}`, { method: 'DELETE' })
  if (!risposta.ok) {
    throw new Error(await messaggioErrore(risposta))
  }
}
