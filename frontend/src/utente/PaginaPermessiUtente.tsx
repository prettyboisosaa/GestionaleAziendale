import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { impostaPermesso, permessiUtente, ripristinaPermesso } from './api'
import type { OriginePermesso, PermessoUtente } from './api'
import { testo } from '../api/http'

const ETICHETTE: Record<OriginePermesso, string> = {
  BASE: 'di base',
  RUOLO: 'dal ruolo',
  AGGIUNTO: 'aggiunto',
  TOGLIATO: 'togliato',
  NESSUNO: '',
}

/** Nell'ordine del catalogo, non alfabetico. */
function perCategoria(voci: PermessoUtente[]): [string, PermessoUtente[]][] {
  const gruppi = new Map<string, PermessoUtente[]>()
  for (const voce of voci) {
    const gruppo = gruppi.get(voce.categoria)
    if (gruppo === undefined) {
      gruppi.set(voce.categoria, [voce])
    } else {
      gruppo.push(voce)
    }
  }
  return [...gruppi]
}

export default function PaginaPermessiUtente() {
  const parametri = useParams()
  const id = Number(parametri.id)

  const [voci, setVoci] = useState<PermessoUtente[] | null>(null)
  const [errore, setErrore] = useState<string | null>(null)
  const [inCorso, setInCorso] = useState<string | null>(null)

  useEffect(() => {
    let annullato = false

    permessiUtente(id)
      .then((elenco) => {
        if (!annullato) {
          setVoci(elenco)
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
  }, [id])

  /**
   * Dopo ogni modifica si rilegge tutto l'elenco invece di aggiornare la riga
   * a mano. Costa una richiesta in più, ma la regola di quale scostamento vale
   * resta scritta in un posto solo, sul server: su una schermata di permessi
   * uno stato locale che divergesse sarebbe il bug peggiore possibile.
   */
  async function agisci(codice: string, azione: () => Promise<unknown>) {
    setInCorso(codice)
    setErrore(null)
    try {
      await azione()
      setVoci(await permessiUtente(id))
    } catch (motivo) {
      setErrore(testo(motivo))
    } finally {
      setInCorso(null)
    }
  }

  return (
    <main className="pagina-larga">
      <div className="testata">
        <h1>Permessi</h1>
        <Link to="/utenti">Torna agli utenti</Link>
      </div>

      <p className="sommario">
        La casella dice che cosa questa persona può fare adesso. Toccarla ha effetto
        immediato: non deve rifare l'accesso.
      </p>

      {errore !== null && (
        <p className="errore" role="alert">
          {errore}
        </p>
      )}

      {errore === null && voci === null && (
        <p className="sommario" role="status">
          Carico i permessi…
        </p>
      )}

      {voci !== null &&
        perCategoria(voci).map(([categoria, gruppo]) => (
          <section key={categoria} className="gruppo-permessi">
            <h2>{categoria}</h2>
            <div className="scheda-tabella">
              <table className="tabella">
                <tbody>
                  {gruppo.map((voce) => (
                    <tr key={voce.codice}>
                      <td className="cella-casella">
                        <input
                          type="checkbox"
                          aria-label={voce.descrizione}
                          checked={voce.effettivo}
                          /* I permessi di base non si toccano: il server
                             risponderebbe 409, e una casella che si può
                             premere per sentirsi dire no è una casella che
                             non andava mostrata attiva. */
                          disabled={inCorso !== null || voce.base}
                          onChange={() =>
                            void agisci(voce.codice, () =>
                              impostaPermesso(id, voce.codice, !voce.effettivo),
                            )
                          }
                        />
                      </td>
                      <td>
                        <span className="codice-permesso">{voce.codice}</span>
                        <span className="nota">{voce.descrizione}</span>
                      </td>
                      <td>
                        {voce.origine !== 'NESSUNO' && (
                          <span className={`origine origine-${voce.origine.toLowerCase()}`}>
                            {ETICHETTE[voce.origine]}
                          </span>
                        )}
                      </td>
                      <td className="azioni">
                        {(voce.origine === 'AGGIUNTO' || voce.origine === 'TOGLIATO') && (
                          <button
                            type="button"
                            disabled={inCorso !== null}
                            onClick={() =>
                              void agisci(voce.codice, () =>
                                ripristinaPermesso(id, voce.codice),
                              )
                            }
                          >
                            Ripristina
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        ))}
    </main>
  )
}
