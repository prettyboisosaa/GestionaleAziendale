import { useState } from 'react'
import type { FormEvent } from 'react'
import { login } from './api'
import type { UtenteCorrente } from './api'

type Proprieta = {
  /** Riceve l'utente appena il backend ha verificato le credenziali. */
  onAccesso: (utente: UtenteCorrente) => void
}

export default function PaginaLogin({ onAccesso }: Proprieta) {
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
      setErrore(motivo instanceof Error ? motivo.message : 'Accesso non riuscito. Riprova.')
      setInCorso(false)
    }
  }

  return (
    <main className="pagina">
      <div className="colonna">
        <p className="azienda">Anitia</p>
        <h1>Gestionale</h1>
        <p className="sommario">Allocazione di progetti e risorse.</p>

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
