import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { annullaLavoro, elencoLavori, eliminaLavoro } from './api'
import type { RigaLavoro } from './api'
import { testo } from '../api/http'
import type { UtenteCorrente } from '../autenticazione/api'
import { ha } from '../autenticazione/permessi'
import { Pillola } from '../componenti/Stato'
import { chiusoLavoro, ETICHETTA_LAVORO, tinta } from '../stato/stati'

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
  /**
   * I lavori annullati non si vedono, finché non li si chiede. Il filtro lo
   * applica il server, non un `filter` qui: così la risposta non porta righe
   * che poi si buttano, e l'ordinamento — annullati in fondo — resta uno solo.
   */
  const [mostraAnnullati, setMostraAnnullati] = useState(false)

  const puoModificare = ha(utente, 'LAVORI_MODIFICA_TUTTI')

  useEffect(() => {
    let annullato = false

    elencoLavori(mostraAnnullati)
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
  }, [mostraAnnullati])

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
      // Un lavoro appena annullato sparisce dall'elenco, a meno che
      // l'interruttore non sia acceso: è esattamente l'effetto voluto.
      setRighe(await elencoLavori(mostraAnnullati))
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

      <div className="filtro">
        <label>
          <input
            type="checkbox"
            checked={mostraAnnullati}
            onChange={(evento) => {
              // L'elenco si svuota qui e non dentro l'effetto: senza, si
              // vedrebbe per un istante la lista vecchia come se fosse già
              // filtrata. Azzerarlo nell'evento che lo causa evita il doppio
              // render che un setState dentro l'effetto provocherebbe.
              setRighe(null)
              setErrore(null)
              setMostraAnnullati(evento.target.checked)
            }}
          />
          Mostra annullati
        </label>
      </div>

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
                <tr
                  key={riga.id}
                  className={riga.stato === 'ANNULLATO' ? 'riga-annullata' : undefined}
                >
                  <td>
                    <Link to={`/lavori/${riga.id}`}>{riga.codice}</Link>
                  </td>
                  <td>{riga.nome}</td>
                  <td>
                    <Pillola tinta={tinta(riga.stato)}>{ETICHETTA_LAVORO[riga.stato]}</Pillola>
                  </td>
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
                          {/* Un lavoro completato non si annulla, e uno già
                              annullato nemmeno: il server rifiuta entrambi, e
                              offrire il pulsante sarebbe una promessa falsa. */}
                          {!chiusoLavoro(riga.stato) && (
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
