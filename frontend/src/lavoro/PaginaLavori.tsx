import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { annullaLavoro, elencoLavori, eliminaLavoro } from './api'
import type { RigaLavoro } from './api'
import { testo } from '../api/http'
import type { UtenteCorrente } from '../autenticazione/api'
import { ha } from '../autenticazione/permessi'
import { Pillola } from '../componenti/Stato'
import { chiusoLavoro, ETICHETTA_LAVORO, tinta } from '../stato/stati'
import type { StatoLavoro } from '../stato/stati'

type Azione = {
  id: number
  tipo: 'annulla' | 'elimina'
}

/** Il filtro per stato: gli stati, più «tutti». */
type Filtro = StatoLavoro | 'TUTTI'

/**
 * L'ordine della tendina, che non è quello dell'enum: prima quello che si
 * guarda di più.
 */
const FILTRI: Filtro[] = ['TUTTI', 'ATTIVO', 'IN_CORSO', 'BOZZA', 'COMPLETATO', 'ANNULLATO']

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
  /**
   * Ricerca e stato filtrano **in locale**, al contrario dell'interruttore
   * degli annullati: quelli cambiano quali righe il server manda, questi solo
   * quali di quelle già in mano si mostrano. Un giro di rete per nascondere
   * delle righe che si hanno già è un giro di rete sprecato, e la ricerca
   * reagirebbe a ogni lettera battuta.
   */
  const [cerca, setCerca] = useState('')
  const [filtro, setFiltro] = useState<Filtro>('TUTTI')

  // Due permessi, due portate. `LAVORI_MODIFICA_TUTTI` annulla e cancella
  // qualunque lavoro; `LAVORI_MODIFICA_PROPRI` annulla solo quelli di cui si è
  // responsabili, e non cancella niente — la cancellazione è definitiva e resta
  // all'amministratore.
  const puoModificareTutti = ha(utente, 'LAVORI_MODIFICA_TUTTI')
  const puoModificarePropri = ha(utente, 'LAVORI_MODIFICA_PROPRI')

  /**
   * Chi ha solo il permesso sui propri annulla il lavoro di cui è
   * responsabile, e il confronto è sull'id: il nome formattato sarebbe
   * ambiguo fra due omonimi. È la stessa condizione che il servizio verifica
   * con `eResponsabile`, quindi il pulsante compare dove non prenderà 403.
   */
  const puoAnnullare = (riga: RigaLavoro) =>
    puoModificareTutti || (puoModificarePropri && riga.responsabileId === utente.id)

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

  const cercato = cerca.trim().toLowerCase()
  const visibili = (righe ?? []).filter((riga) => {
    if (filtro !== 'TUTTI' && riga.stato !== filtro) {
      return false
    }
    if (cercato === '') {
      return true
    }
    // Si cerca anche nella descrizione: il nome di un lavoro è spesso un
    // codice interno, e la parola che si ricorda sta nel testo.
    return (
      riga.nome.toLowerCase().includes(cercato)
      || riga.codice.toLowerCase().includes(cercato)
      || (riga.descrizione ?? '').toLowerCase().includes(cercato)
    )
  })

  function azzera() {
    setCerca('')
    setFiltro('TUTTI')
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

      <div className="barra-lavori">
        <div className="campo campo-cerca">
          <label htmlFor="cerca-lavoro">Cerca</label>
          <input
            id="cerca-lavoro"
            type="search"
            placeholder="Nome, codice o descrizione"
            value={cerca}
            onChange={(evento) => setCerca(evento.target.value)}
          />
        </div>

        <div className="campo">
          <label htmlFor="stato-lavoro">Stato</label>
          <select
            id="stato-lavoro"
            value={filtro}
            onChange={(evento) => setFiltro(evento.target.value as Filtro)}
          >
            {FILTRI.map((uno) => (
              <option key={uno} value={uno}>
                {uno === 'TUTTI' ? 'Tutti' : ETICHETTA_LAVORO[uno]}
              </option>
            ))}
          </select>
        </div>

        <label className="interruttore-annullati">
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

      {/* Nessun risultato è una cosa diversa da nessun lavoro: qui c'è un
          rimedio da offrire, perché è un filtro acceso a nascondere tutto. */}
      {righe !== null && righe.length > 0 && visibili.length === 0 && (
        <p className="sommario">
          Nessun lavoro con questi filtri.{' '}
          <button type="button" className="minuscolo" onClick={azzera}>
            Azzera filtri
          </button>
        </p>
      )}

      {visibili.length > 0 && (
        <div className="griglia-lavori">
          {visibili.map((riga) => (
            <CardLavoro
              key={riga.id}
              riga={riga}
              azione={azione !== null && azione.id === riga.id ? azione : null}
              inCorso={inCorso}
              puoAnnullare={puoAnnullare(riga)}
              puoEliminare={puoModificareTutti}
              onChiedi={setAzione}
              onEsegui={(scelta) => void esegui(scelta)}
              onDesisti={() => setAzione(null)}
            />
          ))}
        </div>
      )}
    </main>
  )
}

/**
 * Un lavoro come scheda: nome, descrizione e stato, che è quello che serve a
 * scegliere dove entrare.
 *
 * **L'intera scheda è un collegamento vero**, non un `onClick` su un `div`: il
 * nome è dentro un `<a>` che si allarga a coprire la scheda con un `::after`,
 * così si apre in una nuova finestra con il tasto centrale, si copia
 * l'indirizzo col destro e la tastiera ci arriva una volta sola invece che su
 * ogni riga. I pulsanti delle azioni stanno **sopra** quel velo, altrimenti
 * premerli aprirebbe il dettaglio.
 */
function CardLavoro({
  riga,
  azione,
  inCorso,
  puoAnnullare,
  puoEliminare,
  onChiedi,
  onEsegui,
  onDesisti,
}: {
  riga: RigaLavoro
  /** L'azione in attesa di conferma su *questo* lavoro, se c'è. */
  azione: Azione | null
  inCorso: boolean
  puoAnnullare: boolean
  puoEliminare: boolean
  onChiedi: (azione: Azione) => void
  onEsegui: (azione: Azione) => void
  onDesisti: () => void
}) {
  // Un lavoro completato non si annulla, e uno già annullato nemmeno: il
  // server rifiuta entrambi, e offrire il pulsante sarebbe una promessa falsa.
  // La cancellazione si offre solo sulle bozze, e solo a chi ha il permesso su
  // tutti i lavori: cancellare è definitivo, e `LAVORI_MODIFICA_PROPRI` non lo
  // concede. Se poi quella bozza ha delle task, il server risponde 409 e il
  // messaggio spiega di annullarla.
  const annullabile = !chiusoLavoro(riga.stato) && puoAnnullare
  const eliminabile = riga.stato === 'BOZZA' && puoEliminare

  return (
    <article
      className={
        riga.stato === 'ANNULLATO' ? 'card-lavoro card-lavoro-spenta' : 'card-lavoro'
      }
    >
      <div className="card-lavoro-capo">
        <span className="card-lavoro-codice">{riga.codice}</span>
        <Pillola tinta={tinta(riga.stato)}>{ETICHETTA_LAVORO[riga.stato]}</Pillola>
      </div>

      <h2 className="card-lavoro-nome">
        <Link to={`/lavori/${riga.id}`}>{riga.nome}</Link>
      </h2>

      {/* Una descrizione che manca si dice: uno spazio vuoto farebbe pensare
          che la scheda non si sia caricata del tutto. */}
      <p
        className={
          riga.descrizione === null
            ? 'card-lavoro-descrizione card-lavoro-senza'
            : 'card-lavoro-descrizione'
        }
      >
        {riga.descrizione ?? 'Nessuna descrizione.'}
      </p>

      <div className="card-lavoro-piede">
        <span className="nota">{riga.responsabile ?? 'Da assegnare'}</span>
        <span className="nota">entro {data(riga.dataFinePrevista)}</span>
      </div>

      {(annullabile || eliminabile || azione !== null) && (
        <div className="card-lavoro-azioni">
          {azione !== null ? (
            <span className="conferma">
              {azione.tipo === 'annulla' ? 'Annullare?' : 'Cancellare?'}
              <button
                type="button"
                className="minuscolo"
                onClick={() => onEsegui(azione)}
                disabled={inCorso}
              >
                Sì
              </button>
              <button
                type="button"
                className="minuscolo"
                onClick={onDesisti}
                disabled={inCorso}
              >
                No
              </button>
            </span>
          ) : (
            <>
              {annullabile && (
                <button
                  type="button"
                  className="minuscolo"
                  onClick={() => onChiedi({ id: riga.id, tipo: 'annulla' })}
                >
                  Annulla
                </button>
              )}
              {eliminabile && (
                <button
                  type="button"
                  className="minuscolo"
                  onClick={() => onChiedi({ id: riga.id, tipo: 'elimina' })}
                >
                  Elimina
                </button>
              )}
            </>
          )}
        </div>
      )}
    </article>
  )
}
