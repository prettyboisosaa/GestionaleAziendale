import { useEffect, useState } from 'react'
import {
  approvaAssenza,
  ETICHETTA_STATO,
  rifiutaAssenza,
  TINTA_STATO,
  tutteLeAssenze,
} from './api'
import type { RigaAssenza, StatoAssenza } from './api'
import CalendarioAssenze from './CalendarioAssenze'
import { testo } from '../api/http'
import Pannello from '../componenti/Pannello'
import { Pillola } from '../componenti/Stato'

/** Gli stati che il filtro dello storico offre, nell'ordine della tendina. */
const STATI: StatoAssenza[] = ['APPROVATA', 'RIFIUTATA', 'RITIRATA', 'IN_ATTESA']

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

/** La decisione in attesa di conferma su una riga. */
type Decisione =
  | { id: number; tipo: 'approva' }
  | { id: number; tipo: 'rifiuta'; nota: string }

/**
 * Ferie e permessi di tutti, per chi li decide.
 *
 * **Tre blocchi, nell'ordine in cui si usano.** Prima ciò su cui si può agire —
 * le richieste in attesa, che sono poche e hanno i due pulsanti. Poi il
 * calendario di chi sarà via, che è il **contesto per decidere**: era la cosa
 * che mancava, perché per sapere se nella settimana del 14 c'è già mezzo
 * ufficio in ferie bisognava leggere le righe e tenere le date a mente. In
 * fondo lo storico delle chiuse, che si consulta e non si tocca.
 *
 * Prima era una tabella sola con tutti gli stati mescolati e un filtro:
 * decidere e consultare nello stesso elenco, distinti solo dall'ordinamento che
 * arriva dal server.
 */
export default function PaginaAssenze() {
  const [righe, setRighe] = useState<RigaAssenza[] | null>(null)
  const [errore, setErrore] = useState<string | null>(null)
  const [filtro, setFiltro] = useState<StatoAssenza | ''>('')
  /** Chi si sta guardando nello storico. Vuoto = tutti. */
  const [chi, setChi] = useState('')
  const [decisione, setDecisione] = useState<Decisione | null>(null)
  /**
   * La riga su cui una decisione è in corso, per id.
   *
   * Non un booleano globale: con quello, decidere la richiesta di Marco
   * spegneva i pulsanti di tutte le altre righe.
   */
  const [inCorso, setInCorso] = useState<number | null>(null)

  // Si chiede tutto una volta e si divide qui: i tre blocchi guardano lo stesso
  // elenco, e tre chiamate con tre filtri diversi mostrerebbero tre
  // fotografie scattate in momenti diversi.
  useEffect(() => {
    let annullato = false

    tutteLeAssenze(null, null)
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

  async function decidi(id: number, azione: () => Promise<RigaAssenza>) {
    setInCorso(id)
    setErrore(null)
    try {
      const aggiornata = await azione()
      // Si sostituisce la riga invece di ricaricare: il server restituisce la
      // richiesta decisa, e rileggerla sarebbe una chiamata sprecata. La riga
      // esce da sé dal blocco «Da decidere» e compare nello storico, perché i
      // due blocchi si ricavano dallo stesso elenco guardando lo stato.
      setRighe((attuali) =>
        attuali === null
          ? null
          : attuali.map((una) => (una.id === aggiornata.id ? aggiornata : una)),
      )
      setDecisione(null)
    } catch (motivo) {
      setErrore(testo(motivo))
    } finally {
      setInCorso(null)
    }
  }

  const tutte = righe ?? []
  const daDecidere = tutte.filter((una) => una.stato === 'IN_ATTESA')
  const chiuse = tutte.filter((una) => una.stato !== 'IN_ATTESA')

  /**
   * Le persone della tendina si ricavano dalle righe già caricate, non da
   * `GET /api/utenti`: quella rotta vuole `UTENTI_VISUALIZZA`, e chi decide le
   * assenze può non averlo. Chi non ha mai chiesto niente non compare, ed è
   * giusto — non c'è niente da filtrare.
   */
  const persone = [...new Set(tutte.map((una) => una.richiedente))]
    .filter((uno): uno is string => uno !== null)
    .sort((uno, altro) => uno.localeCompare(altro))

  const visibili = chiuse
    .filter((una) => filtro === '' || una.stato === filtro)
    .filter((una) => chi === '' || una.richiedente === chi)

  const carico = righe === null && errore === null

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

      {/* Da decidere: in cima, perché è l'unica cosa su cui si agisce. */}
      <Pannello
        titolo="Da decidere"
        sottotitolo="richieste in attesa di una risposta"
        azioni={
          daDecidere.length > 0 ? (
            <Pillola tinta="attesa">{String(daDecidere.length)}</Pillola>
          ) : undefined
        }
        carico={carico}
        vuoto={!carico && daDecidere.length === 0 ? 'Nessuna richiesta in attesa.' : null}
      >
        <Elenco
          righe={daDecidere}
          decisione={decisione}
          inCorso={inCorso}
          onChiedi={setDecisione}
          onDesisti={() => setDecisione(null)}
          onNota={(id, nota) => setDecisione({ id, tipo: 'rifiuta', nota })}
          onDecidi={decidi}
        />
      </Pannello>

      {/* Il contesto per decidere, a tutta larghezza: un mese si legge male
          in mezza pagina. */}
      <CalendarioAssenze />

      <Pannello
        titolo="Storico"
        sottotitolo="richieste già decise o ritirate"
        azioni={
          <>
            <label className="nota">
              Stato{' '}
              <select
                value={filtro}
                onChange={(evento) => setFiltro(evento.target.value as StatoAssenza | '')}
              >
                <option value="">Tutti</option>
                {STATI.map((uno) => (
                  <option key={uno} value={uno}>
                    {ETICHETTA_STATO[uno]}
                  </option>
                ))}
              </select>
            </label>
            {/* Il filtro per persona: l'API lo accetta da sempre, ma la pagina
                lo passava a `null` e quindi non esisteva. Qui si applica in
                locale sulle righe già in mano, senza un giro di rete per
                nascondere qualcosa che si ha già. */}
            <label className="nota">
              Chi{' '}
              <select value={chi} onChange={(evento) => setChi(evento.target.value)}>
                <option value="">Tutti</option>
                {persone.map((uno) => (
                  <option key={uno} value={uno}>
                    {uno}
                  </option>
                ))}
              </select>
            </label>
          </>
        }
        carico={carico}
        vuoto={
          !carico && visibili.length === 0
            ? chiuse.length === 0
              ? 'Nessuna richiesta decisa.'
              : 'Nessuna richiesta con questi filtri.'
            : null
        }
      >
        <Elenco
          righe={visibili}
          decisione={null}
          inCorso={null}
          onChiedi={setDecisione}
          onDesisti={() => setDecisione(null)}
          onNota={() => undefined}
          onDecidi={decidi}
        />
      </Pannello>
    </main>
  )
}

/**
 * La tabella delle richieste, la stessa nei due blocchi.
 *
 * I pulsanti compaiono solo sulle righe in attesa, quindi nello storico la
 * colonna delle azioni non si disegna affatto: una colonna vuota su venti
 * righe è spazio che toglie larghezza alle altre.
 */
function Elenco({
  righe,
  decisione,
  inCorso,
  onChiedi,
  onDesisti,
  onNota,
  onDecidi,
}: {
  righe: RigaAssenza[]
  decisione: Decisione | null
  inCorso: number | null
  onChiedi: (decisione: Decisione) => void
  onDesisti: () => void
  onNota: (id: number, nota: string) => void
  onDecidi: (id: number, azione: () => Promise<RigaAssenza>) => void
}) {
  if (righe.length === 0) {
    return null
  }

  const conAzioni = righe.some((una) => una.stato === 'IN_ATTESA')

  return (
    <div className="scheda-tabella scheda-tabella-nuda">
      <table className="tabella">
        <thead>
          <tr>
            <th>Chi</th>
            <th>Tipologia</th>
            <th>Quando</th>
            <th>Durata</th>
            <th>Stato</th>
            {conAzioni && <th>Azioni</th>}
          </tr>
        </thead>
        <tbody>
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

              {conAzioni && (
                <td className="azioni">
                  {riga.stato !== 'IN_ATTESA' ? null : decisione !== null
                    && decisione.id === riga.id ? (
                    decisione.tipo === 'rifiuta' ? (
                      // Il rifiuto chiede la nota sul posto: un rifiuto senza
                      // motivazione fa riaprire la stessa richiesta domani, e
                      // il server lo respinge con 422 NOTA_OBBLIGATORIA.
                      <span className="conferma">
                        <input
                          type="text"
                          placeholder="Perché no?"
                          maxLength={500}
                          value={decisione.nota}
                          onChange={(evento) => onNota(riga.id, evento.target.value)}
                        />
                        <button
                          type="button"
                          className="minuscolo"
                          disabled={inCorso !== null || decisione.nota.trim() === ''}
                          onClick={() =>
                            onDecidi(riga.id, () =>
                              rifiutaAssenza(riga.id, decisione.nota.trim()),
                            )
                          }
                        >
                          Rifiuta
                        </button>
                        <button
                          type="button"
                          className="minuscolo"
                          onClick={onDesisti}
                          disabled={inCorso !== null}
                        >
                          Lascia
                        </button>
                      </span>
                    ) : (
                      // Anche approvare si conferma: concede giorni e sposta
                      // la richiesta fuori dalla coda, e un clic solo su una
                      // riga fra dieci è poco per una cosa che non si disfa.
                      <span className="conferma">
                        Approvare?
                        <button
                          type="button"
                          className="minuscolo"
                          disabled={inCorso !== null}
                          onClick={() =>
                            onDecidi(riga.id, () => approvaAssenza(riga.id, null))
                          }
                        >
                          Sì
                        </button>
                        <button
                          type="button"
                          className="minuscolo"
                          onClick={onDesisti}
                          disabled={inCorso !== null}
                        >
                          No
                        </button>
                      </span>
                    )
                  ) : (
                    <>
                      <button
                        type="button"
                        className="minuscolo"
                        disabled={inCorso !== null}
                        onClick={() => onChiedi({ id: riga.id, tipo: 'approva' })}
                      >
                        Approva
                      </button>
                      <button
                        type="button"
                        className="minuscolo"
                        disabled={inCorso !== null}
                        onClick={() => onChiedi({ id: riga.id, tipo: 'rifiuta', nota: '' })}
                      >
                        Rifiuta
                      </button>
                    </>
                  )}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
