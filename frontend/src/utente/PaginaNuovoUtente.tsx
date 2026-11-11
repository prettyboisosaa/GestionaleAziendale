import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { creaUtente } from './api'
import { elencoRuoli } from '../ruolo/api'
import type { RigaRuolo } from '../ruolo/api'
import { testo } from '../api/http'

export default function PaginaNuovoUtente() {
  const naviga = useNavigate()

  const [ruoli, setRuoli] = useState<RigaRuolo[]>([])
  const [scelti, setScelti] = useState<number[]>([])
  const [nome, setNome] = useState('')
  const [cognome, setCognome] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errore, setErrore] = useState<string | null>(null)
  const [inCorso, setInCorso] = useState(false)

  useEffect(() => {
    let annullato = false

    elencoRuoli()
      .then((elenco) => {
        if (!annullato) {
          setRuoli(elenco)
        }
      })
      .catch((motivo: unknown) => {
        if (!annullato) {
          setErrore(testo(motivo))
        }
      })

    return () => {
      annullato = true
    }
  }, [])

  function alterna(id: number) {
    setScelti((precedenti) =>
      precedenti.includes(id) ? precedenti.filter((x) => x !== id) : [...precedenti, id],
    )
  }

  async function invia(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()

    // Il backend lo pretende comunque, ma dirlo qui evita un viaggio inutile:
    // un utente senza ruoli non avrebbe nessun permesso.
    if (scelti.length === 0) {
      setErrore('Scegli almeno un ruolo.')
      return
    }

    setErrore(null)
    setInCorso(true)
    try {
      await creaUtente({ nome, cognome, email, password, ruoli: scelti })
      naviga('/utenti')
    } catch (motivo) {
      setErrore(testo(motivo))
      setInCorso(false)
    }
  }

  return (
    <main className="pagina-larga pagina-modulo">
      <div className="testata">
        <h1>Nuovo utente</h1>
        <Link to="/utenti">Torna all'elenco</Link>
      </div>

      <form className="modulo" onSubmit={invia}>
        <div className="campo">
          <label htmlFor="nome">Nome</label>
          <input
            id="nome"
            required
            maxLength={100}
            value={nome}
            onChange={(evento) => setNome(evento.target.value)}
            disabled={inCorso}
          />
        </div>

        <div className="campo">
          <label htmlFor="cognome">Cognome</label>
          <input
            id="cognome"
            required
            maxLength={100}
            value={cognome}
            onChange={(evento) => setCognome(evento.target.value)}
            disabled={inCorso}
          />
        </div>

        <div className="campo">
          <label htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            required
            maxLength={255}
            value={email}
            onChange={(evento) => setEmail(evento.target.value)}
            disabled={inCorso}
          />
        </div>

        <div className="campo">
          <label htmlFor="password">Password iniziale</label>
          <input
            id="password"
            type="password"
            required
            minLength={10}
            autoComplete="new-password"
            value={password}
            onChange={(evento) => setPassword(evento.target.value)}
            disabled={inCorso}
          />
          <p className="nota">Almeno 10 caratteri. Comunicala alla persona.</p>
        </div>

        <fieldset className="campo">
          <legend>Ruoli</legend>
          {ruoli.map((ruolo) => (
            <label key={ruolo.id} className="scelta">
              <input
                type="checkbox"
                checked={scelti.includes(ruolo.id)}
                onChange={() => alterna(ruolo.id)}
                disabled={inCorso}
              />
              <span>
                {ruolo.nome}
                {ruolo.descrizione !== null && <em> — {ruolo.descrizione}</em>}
              </span>
            </label>
          ))}
        </fieldset>

        {errore !== null && (
          <p className="errore" role="alert">
            {errore}
          </p>
        )}

        <button type="submit" disabled={inCorso}>
          {inCorso ? 'Creo…' : 'Crea utente'}
        </button>
      </form>
    </main>
  )
}
