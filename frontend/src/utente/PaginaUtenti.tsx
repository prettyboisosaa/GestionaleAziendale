import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { cambiaAttivazione, elencoUtenti } from './api'
import type { RigaUtente } from './api'
import { testo } from '../api/http'
import type { UtenteCorrente } from '../autenticazione/api'
import { ha } from '../autenticazione/permessi'
import { Pillola } from '../componenti/Stato'

/** Da «2027-01-01T08:00:00» a «01/01/2027». */
function data(iso: string): string {
  return new Date(iso).toLocaleDateString('it-IT')
}

export default function PaginaUtenti({ utente }: { utente: UtenteCorrente }) {
  const [righe, setRighe] = useState<RigaUtente[] | null>(null)
  const [errore, setErrore] = useState<string | null>(null)
  /** L'id su cui è in corso il cambio di stato, per spegnere il suo pulsante. */
  const [inCorso, setInCorso] = useState<number | null>(null)

  const puoAssegnare = ha(utente, 'RUOLI_ASSEGNA')
  const puoDisattivare = ha(utente, 'UTENTI_DISATTIVA')

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

  async function cambia(riga: RigaUtente) {
    setInCorso(riga.id)
    setErrore(null)
    try {
      const aggiornato = await cambiaAttivazione(riga.id, !riga.attivo)
      // Si sostituisce la riga invece di ricaricare tutto: il server
      // restituisce l'utente aggiornato, e una seconda chiamata per leggere
      // quello che si ha già in mano sarebbe sprecata.
      setRighe((attuali) =>
        attuali === null
          ? null
          : attuali.map((una) => (una.id === aggiornato.id ? aggiornato : una)),
      )
    } catch (motivo) {
      // Il caso tipico è il 409 su se stessi: il messaggio arriva dal server
      // e dice di chiedere a un altro amministratore.
      setErrore(testo(motivo))
    } finally {
      setInCorso(null)
    }
  }

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
                {(puoAssegnare || puoDisattivare) && <th>Azioni</th>}
              </tr>
            </thead>
            <tbody>
              {/* Gli utenti disattivati restano nell'elenco: il loro nome
                  compare nello storico di lavori, task e registrazioni, e
                  nasconderli renderebbe quello storico illeggibile. */}
              {righe.map((riga) => (
                <tr key={riga.id} className={riga.attivo ? undefined : 'riga-annullata'}>
                  <td>
                    {riga.cognome} {riga.nome}
                  </td>
                  <td>{riga.email}</td>
                  <td>{riga.ruoli.join(', ')}</td>
                  <td>{riga.permessi}</td>
                  <td>
                    {riga.attivo ? (
                      <Pillola tinta="completato">Attivo</Pillola>
                    ) : (
                      <Pillola tinta="attivo">Disattivato</Pillola>
                    )}
                    {riga.disattivatoIl !== null && !riga.attivo && (
                      <span className="nota"> dal {data(riga.disattivatoIl)}</span>
                    )}
                  </td>
                  {(puoAssegnare || puoDisattivare) && (
                    <td className="azioni">
                      {puoAssegnare && (
                        <Link to={`/utenti/${riga.id}/permessi`}>Permessi</Link>
                      )}
                      {/* Lo stesso pulsante nei due versi: l'interruttore è
                          quello che rende la riattivazione ovvia a chi ha
                          appena disattivato qualcuno per sbaglio.

                          Non compare sulla propria riga: il server lo rifiuta
                          con un 409, e offrirlo sarebbe una promessa falsa. */}
                      {puoDisattivare && riga.email !== utente.email && (
                        <button
                          type="button"
                          onClick={() => void cambia(riga)}
                          disabled={inCorso === riga.id}
                        >
                          {riga.attivo ? 'Disattiva' : 'Riattiva'}
                        </button>
                      )}
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
