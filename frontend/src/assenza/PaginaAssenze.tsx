import { useEffect, useState } from 'react'
import {
  approvaAssenza,
  ETICHETTA_STATO,
  rifiutaAssenza,
  TINTA_STATO,
  tutteLeAssenze,
} from './api'
import type { RigaAssenza, StatoAssenza } from './api'
import { testo } from '../api/http'
import { Pillola } from '../componenti/Stato'

const STATI: StatoAssenza[] = ['IN_ATTESA', 'APPROVATA', 'RIFIUTATA', 'RITIRATA']

function data(iso: string): string {
  return new Date(iso).toLocaleDateString('it-IT')
}

function ora(iso: string): string {
  return iso.slice(0, 5)
}

function periodo(riga: RigaAssenza): string {
  if (riga.oraInizio !== null && riga.oraFine !== null) {
    return `${data(riga.dataInizio)}, ${ora(riga.oraInizio)}–${ora(riga.oraFine)}`
  }
  if (riga.dataInizio === riga.dataFine) {
    return data(riga.dataInizio)
  }
  return `${data(riga.dataInizio)} – ${data(riga.dataFine)}`
}

/** La riga su cui si sta scrivendo la nota del rifiuto. */
type Rifiuto = {
  id: number
  nota: string
}

export default function PaginaAssenze() {
  const [righe, setRighe] = useState<RigaAssenza[] | null>(null)
  const [errore, setErrore] = useState<string | null>(null)
  const [filtro, setFiltro] = useState<StatoAssenza | ''>('')
  const [rifiuto, setRifiuto] = useState<Rifiuto | null>(null)
  const [inCorso, setInCorso] = useState(false)

  useEffect(() => {
    let annullato = false

    tutteLeAssenze(filtro === '' ? null : filtro, null)
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
  }, [filtro])

  async function decidi(azione: () => Promise<RigaAssenza>) {
    setInCorso(true)
    setErrore(null)
    try {
      const aggiornata = await azione()
      // Si sostituisce la riga invece di ricaricare: il server restituisce la
      // richiesta decisa, e rileggerla sarebbe una chiamata sprecata. Con un
      // filtro acceso la riga può non appartenere più all'elenco, e resta
      // visibile fino al prossimo caricamento: è il comportamento meno
      // sorprendente, perché la riga che si è appena toccata non sparisce
      // sotto il dito.
      setRighe((attuali) =>
        attuali === null
          ? null
          : attuali.map((una) => (una.id === aggiornata.id ? aggiornata : una)),
      )
      setRifiuto(null)
    } catch (motivo) {
      setErrore(testo(motivo))
    } finally {
      setInCorso(false)
    }
  }

  return (
    <main className="pagina-larga">
      <div className="testata">
        <h1>Richieste di assenza</h1>
      </div>

      {errore !== null && (
        <p className="errore" role="alert">
          {errore}
        </p>
      )}

      <div className="filtro">
        <label>
          Stato
          <select
            value={filtro}
            onChange={(evento) => {
              // Svuotare l'elenco qui, e non dentro l'effetto, evita il doppio
              // render: è l'evento che causa il ricaricamento.
              setRighe(null)
              setErrore(null)
              setFiltro(evento.target.value as StatoAssenza | '')
            }}
          >
            <option value="">Tutti</option>
            {STATI.map((uno) => (
              <option key={uno} value={uno}>
                {ETICHETTA_STATO[uno]}
              </option>
            ))}
          </select>
        </label>
      </div>

      {errore === null && righe === null && (
        <p className="sommario" role="status">
          Carico le richieste…
        </p>
      )}

      {righe !== null && righe.length === 0 && (
        <p className="sommario">Nessuna richiesta da mostrare.</p>
      )}

      {righe !== null && righe.length > 0 && (
        <div className="scheda-tabella">
          <table className="tabella">
            <thead>
              <tr>
                <th>Chi</th>
                <th>Tipologia</th>
                <th>Quando</th>
                <th>Durata</th>
                <th>Stato</th>
                <th>Azioni</th>
              </tr>
            </thead>
            <tbody>
              {/* Le richieste in attesa arrivano in cima: le ordina il server,
                  perché sono le uniche su cui c'è qualcosa da decidere. */}
              {righe.map((riga) => (
                <tr key={riga.id}>
                  <td>{riga.richiedente}</td>
                  <td>
                    {riga.tipoEtichetta}
                    {riga.motivoEtichetta !== null && (
                      <span className="nota"> · {riga.motivoEtichetta}</span>
                    )}
                    {riga.note !== null && <p className="nota">{riga.note}</p>}
                  </td>
                  <td>{periodo(riga)}</td>
                  <td>{riga.riepilogo}</td>
                  <td>
                    <Pillola tinta={TINTA_STATO[riga.stato]}>
                      {ETICHETTA_STATO[riga.stato]}
                    </Pillola>
                    {riga.notaEsito !== null && <p className="nota">{riga.notaEsito}</p>}
                  </td>
                  <td className="azioni">
                    {riga.stato !== 'IN_ATTESA' ? null : rifiuto !== null &&
                      rifiuto.id === riga.id ? (
                      // Il rifiuto chiede la nota sul posto: un rifiuto senza
                      // motivazione fa riaprire la stessa richiesta domani, e
                      // il server lo rifiuta con 422 NOTA_OBBLIGATORIA.
                      <span className="conferma">
                        <input
                          type="text"
                          placeholder="Perché no?"
                          maxLength={500}
                          value={rifiuto.nota}
                          onChange={(evento) =>
                            setRifiuto({ id: riga.id, nota: evento.target.value })
                          }
                        />
                        <button
                          type="button"
                          disabled={inCorso || rifiuto.nota.trim() === ''}
                          onClick={() =>
                            void decidi(() => rifiutaAssenza(riga.id, rifiuto.nota.trim()))
                          }
                        >
                          Rifiuta
                        </button>
                        <button type="button" onClick={() => setRifiuto(null)} disabled={inCorso}>
                          Lascia
                        </button>
                      </span>
                    ) : (
                      <>
                        <button
                          type="button"
                          disabled={inCorso}
                          onClick={() => void decidi(() => approvaAssenza(riga.id, null))}
                        >
                          Approva
                        </button>
                        <button
                          type="button"
                          disabled={inCorso}
                          onClick={() => setRifiuto({ id: riga.id, nota: '' })}
                        >
                          Rifiuta
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  )
}
