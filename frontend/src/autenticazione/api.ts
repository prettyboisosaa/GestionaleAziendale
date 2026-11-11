import { chiama, chiedi, messaggioErrore, postJson } from '../api/http'

const BASE = '/api/autenticazione'

export type UtenteCorrente = {
  id: number
  email: string
  permessi: string[]
  nome: string
  cognome: string
}

export function login(email: string, password: string): Promise<UtenteCorrente> {
  return chiedi<UtenteCorrente>(`${BASE}/login`, postJson({ email, password }))
}

/**
 * Chi è collegato, oppure null se non c'è una sessione.
 *
 * Il 401 qui non è un errore: all'avvio dell'applicazione è la risposta
 * normale per chi non ha ancora fatto il login. Per questo non si usa
 * `chiedi`, che solleverebbe.
 */
export async function io(): Promise<UtenteCorrente | null> {
  const risposta = await chiama(`${BASE}/io`)

  if (risposta.status === 401) {
    return null
  }
  if (!risposta.ok) {
    throw new Error(await messaggioErrore(risposta))
  }

  return (await risposta.json()) as UtenteCorrente
}

/**
 * Cambia la propria password.
 *
 * L'utente non si manda: lo sa la sessione. Un identificativo nel corpo
 * vorrebbe dire che chiunque può cambiare la password di qualcun altro.
 *
 * Non usa `chiedi` perché il server risponde **204 senza corpo**: non c'è
 * niente da restituire, la sessione resta aperta e i permessi non cambiano.
 *
 * **L'errore conserva il codice in testa** — `WRONG_OLD_PASSWORD`,
 * `WEAK_PASSWORD`, `SAME_PASSWORD` — perché è quello che permette di mettere
 * il messaggio sotto il campo giusto. Chi lo riceve usa `codice()` per
 * smistarlo e `senzaCodice()` per mostrarlo; `testo()`, che fa le due cose
 * insieme, qui perderebbe l'informazione che serve.
 */
export async function cambiaPassword(
  oldPassword: string,
  newPassword: string,
): Promise<void> {
  const risposta = await chiama(
    `${BASE}/cambio-password`,
    postJson({ oldPassword, newPassword }),
  )

  if (!risposta.ok) {
    throw new Error(await messaggioErrore(risposta))
  }
}

/**
 * Chiude la sessione sul server. Non solleva: a chi voleva solo uscire non c'è
 * niente di utile da dire se la chiamata non va a buon fine, e il frontend
 * torna comunque alla schermata di accesso.
 */
export async function logout(): Promise<void> {
  try {
    await fetch(`${BASE}/logout`, { method: 'POST' })
  } catch {
    // niente da fare
  }
}
