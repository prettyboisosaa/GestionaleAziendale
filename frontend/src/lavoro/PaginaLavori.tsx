import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { annullaLavoro, elencoLavori, eliminaLavoro } from './api'
import type { RigaLavoro } from './api'
import { testo } from '../api/http'
import type { UtenteCorrente } from '../autenticazione/api'
import { ha } from '../autenticazione/permessi'

const ETICHETTE: Record<RigaLavoro['stato'], string> = {
  BOZZA: 'Bozza',
  ATTIVO: 'Attivo',
  CHIUSO: 'Chiuso',
  ANNULLATO: 'Annullato',
}

type Azione = {
  id: number
  tipo: 'annulla' | 'elimina'
}

/** Da «2027-01-01» a «01/01/2027». */
function data(iso: string): string {
  return new Date(iso).toLocaleDateString('it-IT')
}

export default function PaginaLavori({ utente }: { utente: UtenteCorrente }) {
  const [righe, setRighe] = useState<RigaLavoro[] | null>(null)
  const [errore, setErrore] = useState<string | null>(null)
  const [azione, setAzione] = useState<Azione | null>(null)
  const [inCorso, setInCorso] = useState(false)

  const puoModificare = ha(utente, 'LAVORI_MODIFICA_TUTTI')

  useEffect(() => {
    let annullato = false

    elencoLavori()
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

  async function esegui(scelta: Azione) {
    setInCorso(true)
    setErrore(null)
    try {
      if (scelta.tipo === 'annulla') {
        await annullaLavoro(scelta.id)
      } else {
        await eliminaLavoro(scelta.id)
      }
      // Ricarica dopo l'azione: lo stato è cambiato, o la riga non c'è più.
      setRighe(await elencoLavori())
    } catch (motivo) {
      // Il caso tipico è il 409 su una bozza che ha delle task: il messaggio
      // arriva dal server e spiega di annullare invece di cancellare.
      setErrore(testo(motivo))
    } finally {
      setAzione(null)
      setInCorso(false)
    }
  }

  return (
    <main className="pagina-larga">
      <div className="testata">
        <h1>Lavori</h1>
        {ha(utente, 'LAVORI_CREA') && (
          <Link to="/lavori/nuovo" className="azione">
            Nuovo lavoro
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

      {righe !== null && righe.length === 0 && (
        <p className="sommario">Nessun lavoro da mostrare.</p>
      )}

      {righe !== null && righe.length > 0 && (
        <div className="scheda-tabella">
          <table className="tabella">
            <thead>
              <tr>
                <th>Codice</th>
                <th>Nome</th>
                <th>Stato</th>
                <th>Responsabile</th>
                <th>Periodo</th>
                {puoModificare && <th>Azioni</th>}
              </tr>
            </thead>
            <tbody>
              {righe.map((riga) => (
                <tr key={riga.id}>
                  <td>
                    <Link to={`/lavori/${riga.id}`}>{riga.codice}</Link>
                  </td>
                  <td>{riga.nome}</td>
                  <td>{ETICHETTE[riga.stato]}</td>
                  <td>{riga.responsabile ?? 'Da assegnare'}</td>
                  <td>
                    {data(riga.dataInizio)} – {data(riga.dataFinePrevista)}
                  </td>

                  {puoModificare && (
                    <td className="azioni">
                      {azione !== null && azione.id === riga.id ? (
                        <span className="conferma">
                          {azione.tipo === 'annulla' ? 'Annullare?' : 'Cancellare?'}
                          <button
                            type="button"
                            onClick={() => void esegui(azione)}
                            disabled={inCorso}
                          >
                            Sì
                          </button>
                          <button
                            type="button"
                            onClick={() => setAzione(null)}
                            disabled={inCorso}
                          >
                            No
                          </button>
                        </span>
                      ) : (
                        <>
                          {(riga.stato === 'BOZZA' || riga.stato === 'ATTIVO') && (
                            <button
                              type="button"
                              onClick={() => setAzione({ id: riga.id, tipo: 'annulla' })}
                            >
                              Annulla
                            </button>
                          )}
                          {/* La cancellazione si offre solo sulle bozze. Se poi
                              quella bozza ha delle task, il server risponde 409
                              e il messaggio spiega di annullarla. */}
                          {riga.stato === 'BOZZA' && (
                            <button
                              type="button"
                              onClick={() => setAzione({ id: riga.id, tipo: 'elimina' })}
                            >
                              Elimina
                            </button>
                          )}
                        </>
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
