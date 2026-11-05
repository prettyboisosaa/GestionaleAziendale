const ERRORE_GENERICO = 'Richiesta non riuscita. Riprova.'
const SERVER_MUTO = 'Server non raggiungibile. Controlla che il backend sia avviato.'

/** Il corpo degli errori del backend, in formato RFC 9457. */
type Problema = {
  detail?: string
}

/**
 * Esegue la richiesta traducendo la caduta della rete in un messaggio
 * leggibile.
 *
 * L'autenticazione sta nel cookie di sessione: non c'è niente da allegare a
 * mano, perché `fetch` usa `credentials: 'same-origin'` per default e, passando
 * dal proxy di Vite, tutto è sulla stessa origine.
 */
export async function chiama(url: string, opzioni?: RequestInit): Promise<Response> {
  try {
    return await fetch(url, opzioni)
  } catch {
    // fetch solleva solo se la richiesta non arriva a destinazione: qui il
    // backend è spento o il proxy punta all'indirizzo sbagliato.
    throw new Error(SERVER_MUTO)
  }
}

/** La richiesta con il corpo JSON già letto. Solleva sugli stati di errore. */
export async function chiedi<T>(url: string, opzioni?: RequestInit): Promise<T> {
  const risposta = await chiama(url, opzioni)
  if (!risposta.ok) {
    throw new Error(await messaggioErrore(risposta))
  }
  return (await risposta.json()) as T
}

/** Le opzioni per una POST con corpo JSON. */
export function postJson(dati: unknown): RequestInit {
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(dati),
  }
}

export async function messaggioErrore(risposta: Response): Promise<string> {
  // 400 e 403 li descrive Spring, in inglese: «Invalid request content.»,
  // «Forbidden». Non vanno mostrati a chi usa il gestionale. Gli altri stati
  // portano un `detail` scritto da noi, in italiano e con la formulazione
  // scelta di proposito: quello si mostra tale e quale.
  if (risposta.status === 400) {
    return 'Controlla i dati inseriti.'
  }
  if (risposta.status === 403) {
    return 'Non hai il permesso per questa operazione.'
  }

  try {
    const problema = (await risposta.json()) as Problema
    return problema.detail ?? ERRORE_GENERICO
  } catch {
    return ERRORE_GENERICO
  }
}

/** Il testo di un errore sollevato dalle funzioni di questo modulo. */
export function testo(motivo: unknown): string {
  return motivo instanceof Error ? motivo.message : ERRORE_GENERICO
}
