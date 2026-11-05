import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { elencoUtenti } from './api'
import type { RigaUtente } from './api'
import { testo } from '../api/http'
import type { UtenteCorrente } from '../autenticazione/api'
import { ha } from '../autenticazione/permessi'

export default function PaginaUtenti({ utente }: { utente: UtenteCorrente }) {
  const [righe, setRighe] = useState<RigaUtente[] | null>(null)
  const [errore, setErrore] = useState<string | null>(null)

  const puoAssegnare = ha(utente, 'RUOLI_ASSEGNA')

  useEffect(() => {
    let annullato = false

    elencoUtenti()
      .then((elenco) => {
        if (!annullato) {
          setRighe(elenco)
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

  return (
    <main className="pagina-larga">
      <div className="testata">
        <h1>Utenti</h1>
        {ha(utente, 'UTENTI_CREA') && (
          <Link to="/utenti/nuovo" className="azione">
            Nuovo utente
          </Link>
        )}
      </div>

      {errore !== null && (
        <p className="errore" role="alert">
          {errore}
        </p>
      )}

      {errore === null && righe === null && (
        <p className="sommario" role="status">
          Carico l'elenco…
        </p>
      )}

      {righe !== null && (
        <div className="scheda-tabella">
          <table className="tabella">
            <thead>
              <tr>
                <th>Nome</th>
                <th>Email</th>
                <th>Ruoli</th>
                <th>Permessi</th>
                <th>Stato</th>
                {puoAssegnare && <th>Azioni</th>}
              </tr>
            </thead>
            <tbody>
              {righe.map((riga) => (
                <tr key={riga.id}>
                  <td>
                    {riga.cognome} {riga.nome}
                  </td>
                  <td>{riga.email}</td>
                  <td>{riga.ruoli.join(', ')}</td>
                  <td>{riga.permessi}</td>
                  <td>{riga.attivo ? 'Attivo' : 'Disattivato'}</td>
                  {puoAssegnare && (
                    <td className="azioni">
                      <Link to={`/utenti/${riga.id}/permessi`}>Permessi</Link>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  )
}
