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

/**
 * Le opzioni per una richiesta con corpo JSON. Una funzione sola per i tre
 * metodi, perché cambia solo il verbo: tre copie delle stesse tre righe sono
 * tre occasioni di sbagliarne una.
 */
function conCorpo(metodo: string, dati: unknown): RequestInit {
  return {
    method: metodo,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(dati),
  }
}

export function postJson(dati: unknown): RequestInit {
  return conCorpo('POST', dati)
}

export function putJson(dati: unknown): RequestInit {
  return conCorpo('PUT', dati)
}

export function patchJson(dati: unknown): RequestInit {
  return conCorpo('PATCH', dati)
}

export async function messaggioErrore(risposta: Response): Promise<string> {
  // 400 lo descrive Spring, in inglese: «Invalid request content.». Non va
  // mostrato a chi usa il gestionale. Gli altri stati portano un `detail`
  // scritto da noi, in italiano e con la formulazione scelta di proposito:
  // quello si mostra tale e quale.
  if (risposta.status === 400) {
    return 'Controlla i dati inseriti.'
  }

  const detail = await dettaglio(risposta)

  // Il 403 del login porta un `detail` nostro (UTENTE_DISATTIVATO) e va
  // mostrato: è l'unico 403 che spieghi qualcosa a chi lo legge. Gli altri
  // sono il rifiuto secco di `@PreAuthorize`, che Spring descrive in inglese.
  if (risposta.status === 403) {
    return detail === null ? 'Non hai il permesso per questa operazione.' : detail
  }

  return detail === null ? ERRORE_GENERICO : detail
}

/** Il `detail` del corpo RFC 9457, oppure null se non c'è. */
async function dettaglio(risposta: Response): Promise<string | null> {
  try {
    const problema = (await risposta.json()) as Problema
    return problema.detail === undefined ? null : problema.detail
  } catch {
    return null
  }
}

/**
 * Il codice in testa a un messaggio del backend, se c'è.
 *
 * I messaggi delle regole introdotte con ferie, permessi e stati cominciano
 * con un codice in maiuscolo seguito da due punti — `TASK_NON_SELEZIONABILE:
 * la task …` — così il frontend può riconoscere il caso senza interpretare il
 * testo italiano, che resta leggibile per chi lo legge.
 */
export function codice(messaggio: string): string | null {
  const trovato = /^([A-Z][A-Z_]+):/.exec(messaggio)
  return trovato === null ? null : trovato[1]
}

/** Il messaggio senza il codice in testa, pronto da mostrare. */
export function senzaCodice(messaggio: string): string {
  return messaggio.replace(/^[A-Z][A-Z_]+:\s*/, '')
}

/** Il testo di un errore sollevato dalle funzioni di questo modulo. */
export function testo(motivo: unknown): string {
  return senzaCodice(motivo instanceof Error ? motivo.message : ERRORE_GENERICO)
}
