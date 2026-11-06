import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import {
  cambiaTipoGiornata,
  giornata as caricaGiornata,
  inviaGiornata,
  registraOre,
  togliVoce,
} from './api'
import type { Giornata, TipoGiornata } from './api'
import { testo } from '../api/http'

const TIPI: Record<TipoGiornata, string> = {
  LAVORATA: 'Lavorata',
  FERIE: 'Ferie',
  PERMESSO: 'Permesso',
  MALATTIA: 'Malattia',
}

/** La data di oggi in formato ISO, nel fuso locale. */
function oggi(): string {
  const adesso = new Date()
  const fuso = adesso.getTimezoneOffset() * 60_000
  return new Date(adesso.getTime() - fuso).toISOString().slice(0, 10)
}

function ore(valore: number): string {
  return valore.toLocaleString('it-IT', { maximumFractionDigits: 2 })
}

function dataLunga(iso: string): string {
  return new Date(iso).toLocaleDateString('it-IT', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

/**
 * La giornata del dipendente: su quali task ha lavorato, per quante ore e che
 * cosa ha fatto.
 *
 * La pagina è indicizzata sulla **data**, non sulla dichiarazione: la giornata
 * di oggi può non esistere ancora nel database, e il server risponde con una
 * giornata vuota (`id` nullo) invece di un 404. Si crea alla prima voce.
 *
 * Nessun totale si calcola qui: ore registrate e ore residue arrivano dal
 * server a ogni risposta, perché il tetto giornaliero è un parametro di
 * sistema e il conto deve stare in un posto solo.
 */
export default function PaginaGiornata() {
  const [data, setData] = useState(oggi())
  const [giorno, setGiorno] = useState<Giornata | null>(null)
  const [errore, setErrore] = useState<string | null>(null)
  const [inCorso, setInCorso] = useState(false)

  const [taskId, setTaskId] = useState('')
  const [oreVoce, setOreVoce] = useState('')
  const [descrizione, setDescrizione] = useState('')

  // L'errore si azzera quando si cambia giorno, cioè nel gestore del campo
  // data: farlo qui dentro vorrebbe dire scrivere stato in modo sincrono in un
  // effect, e innescare un render in più prima che il primo sia finito.
  useEffect(() => {
    let annullato = false

    caricaGiornata(data)
      .then((giorno) => {
        if (!annullato) {
          setGiorno(giorno)
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
  }, [data])

  /**
   * Esegue l'azione e usa la giornata che il server restituisce: tutte le
   * scritture rispondono con la giornata intera aggiornata, quindi non serve
   * una seconda chiamata per ridisegnare la pagina.
   */
  async function agisci(azione: () => Promise<Giornata>) {
    setInCorso(true)
    setErrore(null)
    try {
      setGiorno(await azione())
      return true
    } catch (motivo) {
      setErrore(testo(motivo))
      return false
    } finally {
      setInCorso(false)
    }
  }

  async function inviaVoce(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    const riuscita = await agisci(() =>
      registraOre(data, {
        taskId: Number(taskId),
        ore: Number(oreVoce),
        descrizione,
      }),
    )
    if (riuscita) {
      setTaskId('')
      setOreVoce('')
      setDescrizione('')
    }
  }

  const scrivibile =
    giorno !== null && giorno.modificabile && giorno.tipoGiornata === 'LAVORATA'
  const pieno = giorno !== null && giorno.oreResidue <= 0

  return (
    <main className="pagina-larga">
      <div className="testata">
        <h1>La mia giornata</h1>
        <div className="campo campo-inline">
          <label htmlFor="giorno">Giorno</label>
          <input
            id="giorno"
            type="date"
            max={oggi()}
            value={data}
            onChange={(evento) => {
              setErrore(null)
              setData(evento.target.value)
            }}
            disabled={inCorso}
          />
        </div>
      </div>

      {errore !== null && (
        <p className="errore" role="alert">
          {errore}
        </p>
      )}

      {giorno === null && errore === null && (
        <p className="sommario" role="status">
          Carico la giornata…
        </p>
      )}

      {giorno !== null && (
        <>
          <section className="scheda-ore">
            <div className="testata">
              <h2>{dataLunga(giorno.data)}</h2>
              <span className="origine origine-ruolo">
                {giorno.stato === 'INVIATA' ? 'Inviata' : 'Bozza'}
              </span>
            </div>

            <div className="cifre">
              <p className="cifra cifra-forte">
                <span className="cifra-etichetta">Registrate</span>
                <span className="cifra-valore">{ore(giorno.oreTotali)}</span>
              </p>
              <p className="cifra">
                <span className="cifra-etichetta">Massimo</span>
                <span className="cifra-valore">{ore(giorno.oreMassime)}</span>
              </p>
              <p className="cifra">
                <span className="cifra-etichetta">Ancora disponibili</span>
                <span className="cifra-valore">{ore(giorno.oreResidue)}</span>
              </p>
            </div>

            {/* Il tipo di giornata. Passare a ferie con delle ore registrate
                viene rifiutato dal server invece di cancellarle: sono il
                lavoro di una giornata, e toccherà a chi compila decidere di
                buttarle. */}
            <fieldset className="campo">
              <legend>Tipo di giornata</legend>
              <div className="scelte-riga">
                {(Object.keys(TIPI) as TipoGiornata[]).map((tipo) => (
                  <label key={tipo} className="scelta">
                    <input
                      type="radio"
                      name="tipoGiornata"
                      checked={giorno.tipoGiornata === tipo}
                      disabled={inCorso || !giorno.modificabile}
                      onChange={() => void agisci(() => cambiaTipoGiornata(data, tipo))}
                    />
                    <span>{TIPI[tipo]}</span>
                  </label>
                ))}
              </div>
            </fieldset>

            {!giorno.modificabile && (
              <p className="sommario">
                Questa giornata non è più modificabile: il termine per correggerla è
                passato. Per una rettifica serve un amministratore.
              </p>
            )}

            {/* Un avviso e non un blocco: chi ha un'assenza approvata e ha
                lavorato comunque deve poterlo dichiarare. Serve a far notare
                la contraddizione prima del salvataggio, non a impedirlo. */}
            {giorno.assenzaApprovata && (
              <p className="avviso" role="status">
                Per questo giorno hai un'assenza approvata. Se hai lavorato
                comunque puoi registrare le ore: controlla solo che sia giusto.
              </p>
            )}
          </section>

          {giorno.tipoGiornata !== 'LAVORATA' ? (
            <p className="sommario">
              Giornata dichiarata come {TIPI[giorno.tipoGiornata].toLowerCase()}: non ci
              sono ore da registrare.
            </p>
          ) : (
            <section className="gruppo-permessi">
              <div className="testata">
                <h2>Ore registrate</h2>
                {giorno.stato === 'BOZZA' && giorno.oreTotali > 0 && (
                  <button
                    type="button"
                    disabled={inCorso}
                    onClick={() => void agisci(() => inviaGiornata(data))}
                  >
                    {inCorso ? 'Invio…' : 'Invia la giornata'}
                  </button>
                )}
              </div>

              {giorno.voci.length === 0 ? (
                <p className="sommario">
                  Nessuna ora registrata per questo giorno.
                </p>
              ) : (
                <div className="scheda-tabella">
                  <table className="tabella">
                    <thead>
                      <tr>
                        <th>Task</th>
                        <th>Lavoro</th>
                        <th>Ore</th>
                        <th>Che cosa hai fatto</th>
                        <th />
                      </tr>
                    </thead>
                    <tbody>
                      {giorno.voci.map((voce) => (
                        <tr key={voce.id}>
                          <td>
                            <span className="codice-permesso">{voce.task}</span>
                          </td>
                          <td>{voce.codiceLavoro}</td>
                          <td>{ore(voce.ore)}</td>
                          <td>
                            <span className="nota">{voce.descrizione}</span>
                          </td>
                          <td>
                            {giorno.modificabile && (
                              <button
                                type="button"
                                className="togli"
                                aria-label={`Togli le ore su ${voce.task}`}
                                disabled={inCorso}
                                onClick={() => void agisci(() => togliVoce(data, voce.id))}
                              >
                                ×
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {scrivibile && giorno.taskDisponibili.length === 0 && (
                <p className="sommario">
                  Non hai task aperte assegnate: le ore si registrano solo sulle task a
                  cui sei assegnato. Chiedi al responsabile di un lavoro di assegnartene
                  una.
                </p>
              )}

              {scrivibile && pieno && (
                <p className="sommario">
                  Hai raggiunto il massimo di {ore(giorno.oreMassime)} ore per questa
                  giornata. Per aggiungerne altre, correggi prima una voce.
                </p>
              )}

              {scrivibile && !pieno && giorno.taskDisponibili.length > 0 && (
                <form className="modulo" onSubmit={inviaVoce}>
                  <div className="campo">
                    <label htmlFor="task">Task</label>
                    <select
                      id="task"
                      required
                      value={taskId}
                      onChange={(evento) => setTaskId(evento.target.value)}
                      disabled={inCorso}
                    >
                      <option value="">Scegli…</option>
                      {giorno.taskDisponibili.map((disponibile) => (
                        <option key={disponibile.taskId} value={disponibile.taskId}>
                          {disponibile.codiceLavoro} · {disponibile.titolo}
                        </option>
                      ))}
                    </select>
                    <p className="nota">
                      Solo le task aperte a cui sei assegnato.
                    </p>
                  </div>
                  <div className="campo">
                    <label htmlFor="oreVoce">Ore</label>
                    <input
                      id="oreVoce"
                      type="number"
                      min="0.25"
                      step="0.25"
                      max={giorno.oreResidue}
                      required
                      value={oreVoce}
                      onChange={(evento) => setOreVoce(evento.target.value)}
                      disabled={inCorso}
                    />
                    <p className="nota">
                      Ne restano {ore(giorno.oreResidue)} per questa giornata.
                    </p>
                  </div>
                  <div className="campo">
                    <label htmlFor="descrizioneVoce">Che cosa hai fatto</label>
                    <textarea
                      id="descrizioneVoce"
                      rows={2}
                      required
                      maxLength={1000}
                      value={descrizione}
                      onChange={(evento) => setDescrizione(evento.target.value)}
                      disabled={inCorso}
                    />
                    <p className="nota">
                      Obbligatoria: a fine lavoro serve sapere non solo quante ore sono
                      andate su una task, ma in che cosa.
                    </p>
                  </div>
                  <button type="submit" disabled={inCorso}>
                    {inCorso ? 'Registro…' : 'Registra le ore'}
                  </button>
                </form>
              )}
            </section>
          )}
        </>
      )}
    </main>
  )
}
