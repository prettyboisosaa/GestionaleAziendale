import { useState } from 'react'
import type { FormEvent } from 'react'
import { login } from './api'
import { testo } from '../api/http'
import type { UtenteCorrente } from './api'

type Proprieta = {
  /** Riceve l'utente appena il backend ha verificato le credenziali. */
  onAccesso: (utente: UtenteCorrente) => void

  /**
   * Perché si è qui, quando non ci si è arrivati da soli: oggi l'unico caso è
   * la sessione scaduta per inattività. Senza, chi torna da una riunione si
   * ritrova alla schermata di accesso senza nessuna spiegazione e pensa di
   * aver perso il lavoro fatto.
   */
  avviso?: string
}

export default function PaginaLogin({ onAccesso, avviso }: Proprieta) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errore, setErrore] = useState<string | null>(null)
  const [inCorso, setInCorso] = useState(false)

  async function invia(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    setErrore(null)
    setInCorso(true)

    try {
      onAccesso(await login(email, password))
    } catch (motivo) {
      // `testo` toglie il codice in testa: il 429 del limite di tentativi
      // arriva come «TROPPI_TENTATIVI: Troppi tentativi…», e quel prefisso
      // serve al codice, non a chi legge.
      setErrore(testo(motivo))
      setInCorso(false)
    }
  }

  return (
    <main className="pagina">
      <div className="colonna">
        <p className="azienda">Anitia</p>
        <h1>Gestionale</h1>
        <p className="sommario">Allocazione di progetti e risorse.</p>

        {/* L'avviso sta fuori dal modulo e sopra, perché non parla di un
            campo: dice perché si è tornati qui. Sparisce al primo tentativo,
            quando al suo posto c'è un errore da leggere — `errore` e `avviso`
            non si mostrano mai insieme. */}
        {avviso !== undefined && errore === null && (
          <p className="avviso" role="status">
            {avviso}
          </p>
        )}

        <form onSubmit={invia}>
          <div className="campo">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              required
              autoComplete="username"
              value={email}
              onChange={(evento) => setEmail(evento.target.value)}
              disabled={inCorso}
            />
          </div>

          <div className="campo">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(evento) => setPassword(evento.target.value)}
              disabled={inCorso}
            />
          </div>

          {errore !== null && (
            <p className="errore" role="alert">
              {errore}
            </p>
          )}

          <button type="submit" disabled={inCorso}>
            {inCorso ? 'Verifico…' : 'Accedi'}
          </button>
        </form>
      </div>
    </main>
  )
}
