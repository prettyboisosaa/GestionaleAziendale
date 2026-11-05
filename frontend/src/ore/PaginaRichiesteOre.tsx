import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router'
import {
  contestoRichiesta,
  gestisciRichiestaOre,
  mieRichieste,
  richiesteInAttesa,
} from './api'
import type { ContestoRichiesta, RigaRichiestaOre, StatoRichiesta } from './api'
import { testo } from '../api/http'
import type { UtenteCorrente } from '../autenticazione/api'
import { ha } from '../autenticazione/permessi'

const STATI: Record<StatoRichiesta, string> = {
  IN_ATTESA: 'In attesa',
  APPROVATA: 'Approvata',
  APPROVATA_PARZIALE: 'Approvata in parte',
  RIFIUTATA: 'Rifiutata',
}

function pastiglia(stato: StatoRichiesta): string {
  if (stato === 'IN_ATTESA') {
    return 'origine origine-attesa'
  }
  if (stato === 'RIFIUTATA') {
    return 'origine origine-sforamento'
  }
  return 'origine origine-aggiunto'
}

function data(iso: string): string {
  return new Date(iso).toLocaleDateString('it-IT')
}

function ore(valore: number | null): string {
  if (valore === null) {
    return '—'
  }
  return valore.toLocaleString('it-IT', { maximumFractionDigits: 2 })
}

/**
 * Le richieste di ore.
 *
 * Due letture della stessa pagina. Chi le **decide** vede la coda delle
 * richieste in attesa e, aprendone una, il quadro completo del lavoro su cui
 * deve decidere: monte ore attuale e iniziale, ore allocate, ore consumate,
 * task in sforamento, storico delle richieste precedenti. Era il difetto del
 * piano iniziale — l'amministratore decideva senza contesto.
 *
 * Chi le **apre** vede le proprie con l'esito: il gestionale non ha notifiche,
 * quindi è qui che un PM scopre quante ore gli sono state concesse.
 */
export default function PaginaRichiesteOre({ utente }: { utente: UtenteCorrente }) {
  const puoGestire = ha(utente, 'ORE_RICHIESTE_GESTISCI')

  const [elenco, setElenco] = useState<RigaRichiestaOre[] | null>(null)
  const [aperta, setAperta] = useState<ContestoRichiesta | null>(null)
  const [concesse, setConcesse] = useState('')
  const [errore, setErrore] = useState<string | null>(null)
  const [inCorso, setInCorso] = useState(false)

  const ricarica = useCallback(async () => {
    setElenco(puoGestire ? await richiesteInAttesa() : await mieRichieste())
  }, [puoGestire])

  // Il primo caricamento chiama l'API direttamente invece di passare da
  // `ricarica`: una funzione che scrive stato, invocata dentro un effect,
  // innesca un secondo render prima che il primo sia finito. `ricarica` resta
  // per i gestori degli eventi.
  useEffect(() => {
    let annullato = false

    const chiamata = puoGestire ? richiesteInAttesa() : mieRichieste()
    chiamata
      .then((righe) => {
        if (!annullato) {
          setElenco(righe)
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
  }, [puoGestire])

  async function apri(id: number) {
    setErrore(null)
    setInCorso(true)
    try {
      const contesto = await contestoRichiesta(id)
      setAperta(contesto)
      // Precompilato con quanto è stato chiesto: approvare per intero è il
      // caso più frequente, e ridurre è un gesto in meno che togliere.
      setConcesse(String(contesto.richiesta.oreRichieste))
    } catch (motivo) {
      setErrore(testo(motivo))
    } finally {
      setInCorso(false)
    }
  }

  async function decidi(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (aperta === null) {
      return
    }

    setInCorso(true)
    setErrore(null)
    try {
      await gestisciRichiestaOre(aperta.richiesta.id, Number(concesse))
      setAperta(null)
      setConcesse('')
      await ricarica()
    } catch (motivo) {
      setErrore(testo(motivo))
    } finally {
      setInCorso(false)
    }
  }

  async function rifiuta() {
    if (aperta === null) {
      return
    }

    setInCorso(true)
    setErrore(null)
    try {
      await gestisciRichiestaOre(aperta.richiesta.id, 0)
      setAperta(null)
      setConcesse('')
      await ricarica()
    } catch (motivo) {
      setErrore(testo(motivo))
    } finally {
      setInCorso(false)
    }
  }

  return (
    <main className="pagina-larga">
      <div className="testata">
        <h1>{puoGestire ? 'Richieste di ore da decidere' : 'Le mie richieste di ore'}</h1>
        <Link to="/lavori">Vai ai lavori</Link>
      </div>

      {errore !== null && (
        <p className="errore" role="alert">
          {errore}
        </p>
      )}

      {elenco === null && errore === null && (
        <p className="sommario" role="status">
          Carico le richieste…
        </p>
      )}

      {elenco !== null && elenco.length === 0 && (
        <p className="sommario">
          {puoGestire
            ? 'Nessuna richiesta in attesa.'
            : 'Non hai aperto nessuna richiesta di ore.'}
        </p>
      )}

      {elenco !== null && elenco.length > 0 && (
        <div className="scheda-tabella">
          <table className="tabella">
            <thead>
              <tr>
                <th>Aperta il</th>
                <th>Lavoro</th>
                <th>Task</th>
                <th>Chi</th>
                <th>Chieste</th>
                <th>Concesse</th>
                <th>Stato</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {elenco.map((richiesta) => (
                <tr key={richiesta.id}>
                  <td>{data(richiesta.creataIl)}</td>
                  <td>
                    <Link to={`/lavori/${richiesta.lavoroId}`}>
                      {richiesta.codiceLavoro}
                    </Link>
                    <span className="nota">{richiesta.nomeLavoro}</span>
                  </td>
                  <td>{richiesta.task ?? '— pool del lavoro'}</td>
                  <td>{richiesta.richiedente}</td>
                  <td>{ore(richiesta.oreRichieste)}</td>
                  <td>{ore(richiesta.oreConcesse)}</td>
                  <td>
                    <span className={pastiglia(richiesta.stato)}>
                      {STATI[richiesta.stato]}
                    </span>
                  </td>
                  <td>
                    {puoGestire && richiesta.stato === 'IN_ATTESA' && (
                      <button
                        type="button"
                        className="minuscolo"
                        disabled={inCorso}
                        onClick={() => void apri(richiesta.id)}
                      >
                        Esamina
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Il quadro su cui si decide. Tutto quello che serve è qui: senza
          questi numeri, concedere ore sarebbe un atto di fiducia. */}
      {aperta !== null && (
        <section className="scheda-ore">
          <div className="testata">
            <h2>
              {aperta.richiesta.codiceLavoro} · {aperta.richiesta.nomeLavoro}
            </h2>
            <button
              type="button"
              className="secondario"
              disabled={inCorso}
              onClick={() => setAperta(null)}
            >
              Chiudi
            </button>
          </div>

          <p>
            <strong>{aperta.richiesta.richiedente}</strong> chiede{' '}
            {ore(aperta.richiesta.oreRichieste)} ore
            {aperta.richiesta.task === null
              ? ' per il pool del lavoro'
              : ` per la task «${aperta.richiesta.task}»`}
            .
          </p>
          <p className="nota">{aperta.richiesta.motivo}</p>

          <div className="cifre">
            <p className="cifra">
              <span className="cifra-etichetta">Monte ore</span>
              <span className="cifra-valore">{ore(aperta.ore.monteOre)}</span>
            </p>
            <p className="cifra">
              <span className="cifra-etichetta">Iniziale</span>
              <span className="cifra-valore">{ore(aperta.monteOreIniziale)}</span>
            </p>
            <p className="cifra">
              <span className="cifra-etichetta">Allocate</span>
              <span className="cifra-valore">{ore(aperta.ore.oreAllocate)}</span>
            </p>
            <p className="cifra">
              <span className="cifra-etichetta">Consumate</span>
              <span className="cifra-valore">{ore(aperta.ore.oreConsumate)}</span>
            </p>
            <p className="cifra cifra-forte">
              <span className="cifra-etichetta">Libere</span>
              <span className="cifra-valore">{ore(aperta.ore.oreLibere)}</span>
            </p>
          </div>

          {aperta.monteOreIniziale !== null &&
            aperta.ore.monteOre !== null &&
            aperta.ore.monteOre !== aperta.monteOreIniziale && (
              <p className="nota">
                Il monte ore è già passato da {ore(aperta.monteOreIniziale)} a{' '}
                {ore(aperta.ore.monteOre)} ore.
              </p>
            )}

          {/* Le task in sforamento dicono se la richiesta nasce da un
              problema reale o da una previsione. */}
          {aperta.sforamenti.length === 0 ? (
            <p className="sommario">Nessuna task di questo lavoro ha sforato.</p>
          ) : (
            <div className="scheda-tabella">
              <table className="tabella">
                <thead>
                  <tr>
                    <th>Task in sforamento</th>
                    <th>Allocate</th>
                    <th>Consumate</th>
                    <th>Sfora di</th>
                  </tr>
                </thead>
                <tbody>
                  {aperta.sforamenti.map((task) => (
                    <tr key={task.taskId}>
                      <td>
                        <span className="codice-permesso">{task.titolo}</span>
                      </td>
                      <td>{ore(task.oreAllocate)}</td>
                      <td>{ore(task.oreConsumate)}</td>
                      <td>
                        <span className="origine origine-sforamento">
                          {ore(task.sforamento)}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {aperta.storico.length > 1 && (
            <div className="scheda-tabella">
              <table className="tabella">
                <thead>
                  <tr>
                    <th>Richieste precedenti</th>
                    <th>Chieste</th>
                    <th>Concesse</th>
                    <th>Stato</th>
                  </tr>
                </thead>
                <tbody>
                  {aperta.storico
                    .filter((passata) => passata.id !== aperta.richiesta.id)
                    .map((passata) => (
                      <tr key={passata.id}>
                        <td>
                          {data(passata.creataIl)}
                          <span className="nota">{passata.motivo}</span>
                        </td>
                        <td>{ore(passata.oreRichieste)}</td>
                        <td>{ore(passata.oreConcesse)}</td>
                        <td>
                          <span className={pastiglia(passata.stato)}>
                            {STATI[passata.stato]}
                          </span>
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          )}

          {/* La decisione è un numero solo: zero rifiuta, tutto approva, una
              parte approva parzialmente. Lo stato lo deduce il server. */}
          <form className="modulo" onSubmit={decidi}>
            <div className="campo">
              <label htmlFor="concesse">Ore da concedere</label>
              <input
                id="concesse"
                type="number"
                min="0"
                step="0.25"
                required
                value={concesse}
                onChange={(evento) => setConcesse(evento.target.value)}
                disabled={inCorso}
              />
              <p className="nota">
                Concedendone meno di {ore(aperta.richiesta.oreRichieste)}, la richiesta
                risulta approvata in parte.
                {aperta.richiesta.task !== null &&
                  ` Le ore vanno direttamente alla task «${aperta.richiesta.task}».`}
              </p>
            </div>
            <div className="azioni">
              <button type="submit" disabled={inCorso}>
                {inCorso ? 'Salvo…' : 'Concedi'}
              </button>
              <button
                type="button"
                className="secondario"
                disabled={inCorso}
                onClick={() => void rifiuta()}
              >
                Rifiuta
              </button>
            </div>
          </form>
        </section>
      )}
    </main>
  )
}
