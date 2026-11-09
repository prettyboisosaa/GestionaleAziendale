import { Fragment, useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { cambiaMonteOre, dettaglioLavoro } from './api'
import type { RigaLavoro } from './api'
import {
  assegna,
  assegnabili as caricaAssegnabili,
  cambiaStatoTask,
  creaTask,
  rimuoviAssegnazione,
  taskDelLavoro,
} from '../task/api'
import type { Assegnatario, RigaTask, StatoTask } from '../task/api'
import { avvia as avviaTempo, completa as completaTempo, pausa as pausaTempo } from '../tempo/api'
import type { SituazioneTempo } from '../tempo/api'
import {
  allocaOre,
  apriRichiestaOre,
  movimenti as caricaMovimenti,
  richiesteDelLavoro,
} from '../ore/api'
import type { RigaMovimento, RigaRichiestaOre } from '../ore/api'
import { testo } from '../api/http'
import type { UtenteCorrente } from '../autenticazione/api'
import { ha } from '../autenticazione/permessi'
import { Pillola } from '../componenti/Stato'
import {
  apertaTask,
  ETICHETTA_LAVORO,
  ETICHETTA_TASK,
  PASSAGGI_TASK,
  tinta,
} from '../stato/stati'

const STATI_RICHIESTA: Record<RigaRichiestaOre['stato'], string> = {
  IN_ATTESA: 'In attesa',
  APPROVATA: 'Approvata',
  APPROVATA_PARZIALE: 'Approvata in parte',
  RIFIUTATA: 'Rifiutata',
}

const TIPI_MOVIMENTO: Record<RigaMovimento['tipo'], string> = {
  MONTE_INIZIALE: 'Monte ore iniziale',
  MODIFICA_MONTE: 'Monte ore modificato',
  ALLOCAZIONE: 'Ore allocate',
  RILASCIO: 'Ore rilasciate',
}

// Le etichette e i passaggi vengono da `stato/stati`, dove stanno insieme ai
// colori: tre schermate mostrano gli stessi stati, e tre mappe scritte a mano
// sono tre occasioni di rinominarne due su tre.
const STATI_TASK = ETICHETTA_TASK
const STATI_LAVORO = ETICHETTA_LAVORO
const PASSAGGI = PASSAGGI_TASK

function data(iso: string): string {
  return new Date(iso).toLocaleDateString('it-IT')
}

/**
 * Le task aperte prima, quelle chiuse sotto, e dentro i due gruppi l'ordine
 * che arriva dal server — data di inizio.
 *
 * Le chiuse non si nascondono: una task completata ieri deve restare visibile
 * oggi, e un'annullata racconta perché il lavoro si è fermato. Ma non devono
 * stare in mezzo a quelle su cui c'è ancora da fare qualcosa.
 *
 * `toSorted` e non `sort`: l'elenco arriva dallo stato di React, e ordinarlo
 * sul posto lo muterebbe.
 */
function ordinaTask(elenco: RigaTask[]): RigaTask[] {
  return elenco.toSorted(
    (uno, altro) => Number(!apertaTask(uno.stato)) - Number(!apertaTask(altro.stato)),
  )
}

/**
 * Le ore con due decimali solo quando servono: «8» invece di «8,00», ma
 * «7,5» resta «7,5». Un trattino per ciò che non è stato deciso, perché uno
 * zero direbbe un'altra cosa.
 */
function ore(valore: number | null): string {
  if (valore === null) {
    return '—'
  }
  return valore.toLocaleString('it-IT', { maximumFractionDigits: 2 })
}

export default function PaginaLavoro({
  utente,
  tempo,
  onTempo,
}: {
  utente: UtenteCorrente
  tempo: SituazioneTempo | null
  onTempo: (situazione: SituazioneTempo) => void
}) {
  const parametri = useParams()
  const id = Number(parametri.id)

  const puoFissareMonteOre = ha(utente, 'ORE_MONTE_GESTISCI')
  const puoChiedereOre = ha(utente, 'ORE_RICHIEDI', 'ORE_RICHIESTE_GESTISCI')

  const [lavoro, setLavoro] = useState<RigaLavoro | null>(null)
  const [task, setTask] = useState<RigaTask[] | null>(null)
  const [richieste, setRichieste] = useState<RigaRichiestaOre[]>([])
  const [storico, setStorico] = useState<RigaMovimento[] | null>(null)
  const [candidati, setCandidati] = useState<Assegnatario[]>([])
  const [errore, setErrore] = useState<string | null>(null)
  const [avviso, setAvviso] = useState<string | null>(null)
  const [inCorso, setInCorso] = useState(false)
  const [mostraForm, setMostraForm] = useState(false)

  // Gestire le task e distribuirne il budget sono due cose diverse, con due
  // permessi diversi, e ciascuno esiste in due versioni: su tutti i lavori o
  // solo sui propri. Qui si decide cosa mostrare — il controllo vero è sul
  // server — ma la condizione va scritta come la scrive lui, altrimenti si
  // offre un pulsante che risponderà 403.
  //
  // Per questo entrano nel conto `responsabileId` e l'id di chi guarda: i
  // permessi `_PROPRI` valgono solo sui lavori di cui si è responsabili, ed è
  // la stessa cosa che il servizio verifica con `eResponsabile`.
  const sonoResponsabile = lavoro !== null && lavoro.responsabileId === utente.id
  const puoGestire =
    ha(utente, 'TASK_GESTISCI_TUTTI') ||
    (ha(utente, 'TASK_GESTISCI_PROPRI') && sonoResponsabile)
  const puoAllocare =
    ha(utente, 'ORE_ALLOCA_TUTTI') ||
    (ha(utente, 'ORE_ALLOCA_PROPRI') && sonoResponsabile)

  /**
   * Vero se la task è assegnata a chi guarda.
   *
   * `TASK_AGGIORNA_STATO` è un permesso di base, quindi ce l'hanno tutti: la
   * condizione che decide è l'assegnazione, ed è esattamente quella che
   * `vietaSeNonPuoCambiareStato` verifica sul server. Senza, la tendina dello
   * stato comparirebbe su ogni task del lavoro e risponderebbe 403 su quelle
   * di qualcun altro.
   */
  const miaTask = (singola: RigaTask) =>
    singola.assegnatari.some((persona) => persona.utenteId === utente.id)

  const [titolo, setTitolo] = useState('')
  const [descrizione, setDescrizione] = useState('')
  const [dataInizio, setDataInizio] = useState('')
  const [scadenza, setScadenza] = useState('')
  const [stimaOre, setStimaOre] = useState('')

  // Il monte ore
  const [mostraMonteOre, setMostraMonteOre] = useState(false)
  const [nuovoMonteOre, setNuovoMonteOre] = useState('')
  const [motivoMonteOre, setMotivoMonteOre] = useState('')

  // L'allocazione, aperta su una task alla volta
  const [allocaPer, setAllocaPer] = useState<number | null>(null)
  const [oreDaAllocare, setOreDaAllocare] = useState('')
  const [motivoAllocazione, setMotivoAllocazione] = useState('')

  // La richiesta di ore. `oreMancanti` arriva dal tentativo di allocazione
  // andato a vuoto: la differenza fra quello che servirebbe e le ore libere.
  const [mostraRichiesta, setMostraRichiesta] = useState(false)
  const [taskRichiesta, setTaskRichiesta] = useState('')
  const [oreRichieste, setOreRichieste] = useState('')
  const [motivoRichiesta, setMotivoRichiesta] = useState('')

  const ricarica = useCallback(async () => {
    const [dettaglio, elenco, loro] = await Promise.all([
      dettaglioLavoro(id),
      taskDelLavoro(id),
      richiesteDelLavoro(id),
    ])
    setLavoro(dettaglio)
    setTask(elenco)
    setRichieste(loro)
  }, [id])

  // Il primo caricamento chiama le API direttamente invece di passare da
  // `ricarica`: una funzione che scrive stato, invocata dentro un effect,
  // innesca un secondo render prima che il primo sia finito. `ricarica` resta
  // per i gestori degli eventi, dove il render in più è quello che si vuole.
  useEffect(() => {
    let annullato = false

    Promise.all([dettaglioLavoro(id), taskDelLavoro(id), richiesteDelLavoro(id)])
      .then(([dettaglio, elenco, loro]) => {
        if (annullato) {
          return
        }
        setLavoro(dettaglio)
        setTask(elenco)
        setRichieste(loro)
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

  // I candidati li sa solo chi può assegnare: la rotta risponde 403 agli altri.
  useEffect(() => {
    if (!puoGestire) {
      return
    }
    let annullato = false

    caricaAssegnabili(id)
      .then((elenco) => {
        if (!annullato) {
          setCandidati(elenco)
        }
      })
      .catch(() => {
        // Non è un errore da mostrare: senza candidati i menu restano vuoti.
      })

    return () => {
      annullato = true
    }
  }, [id, puoGestire])

  /**
   * Esegue l'azione e ricarica tutto. Ricarica anche le ore, non solo le
   * task: allocare, chiudere una task o concedere ore cambia il budget del
   * lavoro, e mostrare un totale vecchio accanto a una task nuova sarebbe
   * peggio di non mostrarlo.
   */
  async function agisci(azione: () => Promise<unknown>) {
    setInCorso(true)
    setErrore(null)
    setAvviso(null)
    try {
      await azione()
      await ricarica()
      if (storico !== null) {
        setStorico(await caricaMovimenti(id))
      }
      return true
    } catch (motivo) {
      setErrore(testo(motivo))
      return false
    } finally {
      setInCorso(false)
    }
  }

  /**
   * Come {@link agisci}, ma per le azioni del timer: la risposta e' la
   * situazione del tempo e va passata in alto, perche' la barra in testata la
   * mostri. Il resto si ricarica comunque — avviare una task ne cambia lo
   * stato, completarla restituisce ore al pool del lavoro.
   */
  async function agisciTempo(azione: () => Promise<SituazioneTempo>) {
    await agisci(async () => {
      onTempo(await azione())
    })
  }

  async function inviaNuovaTask(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    const riuscita = await agisci(() =>
      creaTask(id, {
        titolo,
        descrizione: descrizione.trim() === '' ? null : descrizione,
        dataInizio,
        scadenza,
        stimaOre: stimaOre.trim() === '' ? null : Number(stimaOre),
      }),
    )
    if (riuscita) {
      setTitolo('')
      setDescrizione('')
      setDataInizio('')
      setScadenza('')
      setStimaOre('')
      setMostraForm(false)
    }
  }

  async function inviaMonteOre(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    const riuscita = await agisci(() =>
      cambiaMonteOre(id, Number(nuovoMonteOre), motivoMonteOre),
    )
    if (riuscita) {
      setNuovoMonteOre('')
      setMotivoMonteOre('')
      setMostraMonteOre(false)
    }
  }

  /**
   * Alloca ore a una task. Se il pool non basta il server risponde 409, e qui
   * si apre la richiesta all'amministratore **precompilata con la
   * differenza**: non si legge il numero dal messaggio d'errore, si calcola
   * da `oreLibere`, che è già sulla pagina.
   */
  async function inviaAllocazione(evento: FormEvent<HTMLFormElement>, idTask: number) {
    evento.preventDefault()
    const chieste = Number(oreDaAllocare)

    setInCorso(true)
    setErrore(null)
    setAvviso(null)
    try {
      await allocaOre(id, idTask, chieste, motivoAllocazione)
      await ricarica()
      if (storico !== null) {
        setStorico(await caricaMovimenti(id))
      }
      setOreDaAllocare('')
      setMotivoAllocazione('')
      setAllocaPer(null)
    } catch (motivo) {
      setErrore(testo(motivo))

      const libere = lavoro?.oreLibere ?? 0
      if (puoChiedereOre && chieste > libere) {
        const mancanti = chieste - libere
        setTaskRichiesta(String(idTask))
        setOreRichieste(String(mancanti))
        setMotivoRichiesta(
          motivoAllocazione.trim() === ''
            ? `Servono ${ore(mancanti)} ore in più di quelle libere sul lavoro.`
            : motivoAllocazione,
        )
        setMostraRichiesta(true)
        setAvviso(
          `Il pool non basta: ${ore(libere)} ore libere su ${ore(chieste)} richieste. ` +
            `Alloca quelle che restano, e chiedi all'amministratore le ${ore(mancanti)} ` +
            `che mancano — la richiesta è già compilata qui sotto.`,
        )
      }
    } finally {
      setInCorso(false)
    }
  }

  async function inviaRichiesta(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    const riuscita = await agisci(() =>
      apriRichiestaOre(id, {
        taskId: taskRichiesta === '' ? null : Number(taskRichiesta),
        oreRichieste: Number(oreRichieste),
        motivo: motivoRichiesta,
      }),
    )
    if (riuscita) {
      setTaskRichiesta('')
      setOreRichieste('')
      setMotivoRichiesta('')
      setMostraRichiesta(false)
    }
  }

  async function apriStorico() {
    setErrore(null)
    try {
      setStorico(await caricaMovimenti(id))
    } catch (motivo) {
      setErrore(testo(motivo))
    }
  }

  const inAttesa = richieste.filter((richiesta) => richiesta.stato === 'IN_ATTESA')

  return (
    <main className="pagina-larga">
      <div className="testata">
        <h1>{lavoro === null ? 'Lavoro' : `${lavoro.codice} · ${lavoro.nome}`}</h1>
        <Link to="/lavori">Torna all'elenco</Link>
      </div>

      {errore !== null && (
        <p className="errore" role="alert">
          {errore}
        </p>
      )}

      {avviso !== null && (
        <p className="avviso" role="status">
          {avviso}
        </p>
      )}

      {lavoro !== null && (
        <div className="scheda-lavoro">
          <p>
            <Pillola tinta={tinta(lavoro.stato)}>{STATI_LAVORO[lavoro.stato]}</Pillola> ·
            responsabile{' '}
            {lavoro.responsabile ?? 'da assegnare'} · {data(lavoro.dataInizio)} –{' '}
            {data(lavoro.dataFinePrevista)}
          </p>
          {lavoro.descrizione !== null && <p className="nota">{lavoro.descrizione}</p>}
        </div>
      )}

      {/* Il monte ore. Le ore libere sono in evidenza perché sono il numero su
          cui si decide: tutto il resto è contesto. */}
      {lavoro !== null && (
        <section className="scheda-ore">
          <div className="testata">
            <h2>Monte ore</h2>
            {puoFissareMonteOre && !mostraMonteOre && (
              <button
                type="button"
                className="secondario"
                onClick={() => {
                  setNuovoMonteOre(lavoro.monteOre === null ? '' : String(lavoro.monteOre))
                  setMostraMonteOre(true)
                }}
              >
                {lavoro.monteOre === null ? 'Fissa il monte ore' : 'Modifica'}
              </button>
            )}
          </div>

          {lavoro.monteOre === null ? (
            <p className="sommario">
              Il monte ore non è ancora stato fissato: finché manca, non si possono
              distribuire ore alle task.
            </p>
          ) : (
            <div className="cifre">
              <p className="cifra">
                <span className="cifra-etichetta">Monte ore</span>
                <span className="cifra-valore">{ore(lavoro.monteOre)}</span>
              </p>
              <p className="cifra">
                <span className="cifra-etichetta">Allocate</span>
                <span className="cifra-valore">{ore(lavoro.oreAllocate)}</span>
              </p>
              <p className="cifra">
                <span className="cifra-etichetta">Consumate</span>
                <span className="cifra-valore">{ore(lavoro.oreConsumate)}</span>
              </p>
              <p className="cifra cifra-forte">
                <span className="cifra-etichetta">Libere</span>
                <span className="cifra-valore">{ore(lavoro.oreLibere)}</span>
              </p>
            </div>
          )}

          {puoFissareMonteOre && mostraMonteOre && (
            <form className="modulo" onSubmit={inviaMonteOre}>
              <div className="campo">
                <label htmlFor="monteOre">Monte ore</label>
                <input
                  id="monteOre"
                  type="number"
                  min="0"
                  step="0.25"
                  required
                  value={nuovoMonteOre}
                  onChange={(evento) => setNuovoMonteOre(evento.target.value)}
                  disabled={inCorso}
                />
                <p className="nota">
                  Non può scendere sotto le {ore(lavoro.oreAllocate)} ore già allocate alle
                  task.
                </p>
              </div>
              <div className="campo">
                <label htmlFor="motivoMonteOre">Motivo</label>
                <input
                  id="motivoMonteOre"
                  required
                  maxLength={500}
                  value={motivoMonteOre}
                  onChange={(evento) => setMotivoMonteOre(evento.target.value)}
                  disabled={inCorso}
                />
                <p className="nota">
                  Resta scritto nello storico: è il motivo per cui a fine lavoro si può
                  spiegare la differenza dal preventivo.
                </p>
              </div>
              <div className="azioni">
                <button type="submit" disabled={inCorso}>
                  {inCorso ? 'Salvo…' : 'Salva'}
                </button>
                <button
                  type="button"
                  className="secondario"
                  disabled={inCorso}
                  onClick={() => setMostraMonteOre(false)}
                >
                  Annulla
                </button>
              </div>
            </form>
          )}
        </section>
      )}

      {/* Le richieste in attesa in evidenza: il gestionale non ha notifiche,
          quindi è qui che una richiesta aperta si fa notare. */}
      {inAttesa.length > 0 && (
        <p className="avviso" role="status">
          {inAttesa.length === 1
            ? 'Una richiesta di ore è in attesa di risposta.'
            : `${inAttesa.length} richieste di ore sono in attesa di risposta.`}
        </p>
      )}

      <section className="gruppo-permessi">
        <div className="testata">
          <h2>Task</h2>
          {puoGestire && !mostraForm && (
            <button type="button" className="secondario" onClick={() => setMostraForm(true)}>
              Nuova task
            </button>
          )}
        </div>

        {puoGestire && mostraForm && (
          <form className="modulo" onSubmit={inviaNuovaTask}>
            <div className="campo">
              <label htmlFor="titolo">Titolo</label>
              <input
                id="titolo"
                required
                maxLength={200}
                value={titolo}
                onChange={(evento) => setTitolo(evento.target.value)}
                disabled={inCorso}
              />
            </div>
            <div className="campo">
              <label htmlFor="descrizione">Descrizione</label>
              <textarea
                id="descrizione"
                rows={2}
                value={descrizione}
                onChange={(evento) => setDescrizione(evento.target.value)}
                disabled={inCorso}
              />
            </div>
            <div className="campo">
              <label htmlFor="inizio">Inizio</label>
              <input
                id="inizio"
                type="date"
                required
                value={dataInizio}
                onChange={(evento) => setDataInizio(evento.target.value)}
                disabled={inCorso}
              />
            </div>
            <div className="campo">
              <label htmlFor="scadenza">Scadenza</label>
              <input
                id="scadenza"
                type="date"
                required
                min={dataInizio === '' ? undefined : dataInizio}
                value={scadenza}
                onChange={(evento) => setScadenza(evento.target.value)}
                disabled={inCorso}
              />
            </div>
            <div className="campo">
              <label htmlFor="stima">Stima in ore</label>
              <input
                id="stima"
                type="number"
                min="0.25"
                step="0.25"
                value={stimaOre}
                onChange={(evento) => setStimaOre(evento.target.value)}
                disabled={inCorso}
              />
              <p className="nota">
                Facoltativa. Non alloca niente: le ore si prendono dal monte ore dopo,
                task per task.
              </p>
            </div>
            <button type="submit" disabled={inCorso}>
              {inCorso ? 'Creo…' : 'Crea task'}
            </button>
          </form>
        )}

        {errore === null && task === null && (
          <p className="sommario" role="status">
            Carico le task…
          </p>
        )}

        {task !== null && task.length === 0 && (
          <p className="sommario">Nessuna task da mostrare.</p>
        )}

        {task !== null && task.length > 0 && (
          <div className="scheda-tabella">
            <table className="tabella">
              <thead>
                <tr>
                  <th>Titolo</th>
                  <th>Periodo</th>
                  <th>Ore</th>
                  <th>Stato</th>
                  <th>Assegnata a</th>
                </tr>
              </thead>
              <tbody>
                {ordinaTask(task).map((singola) => {
                  const aperta = apertaTask(singola.stato)
                  return (
                    <Fragment key={singola.id}>
                      <tr
                        className={
                          singola.stato === 'ANNULLATO' ? 'riga-annullata' : undefined
                        }
                      >
                        <td>
                          <span className="codice-permesso">{singola.titolo}</span>
                          {singola.descrizione !== null && (
                            <span className="nota">{singola.descrizione}</span>
                          )}
                        </td>
                        <td>
                          {data(singola.dataInizio)} – {data(singola.scadenza)}
                        </td>
                        <td>
                          <span className="ore-riga">
                            {ore(singola.oreConsumate)} / {ore(singola.oreAllocate)}
                          </span>
                          <span className="nota">
                            stimate {ore(singola.stimaOre)}
                          </span>
                          {singola.sforamento && (
                            <span className="origine origine-sforamento">
                              sfora di{' '}
                              {ore(singola.oreConsumate - singola.oreAllocate)}
                            </span>
                          )}
                          {puoAllocare && aperta && allocaPer !== singola.id && (
                            <button
                              type="button"
                              className="minuscolo"
                              disabled={inCorso}
                              onClick={() => {
                                setAllocaPer(singola.id)
                                setOreDaAllocare('')
                                setMotivoAllocazione('')
                              }}
                            >
                              Alloca ore
                            </button>
                          )}
                        </td>
                        <td>
                          {(puoGestire || miaTask(singola)) && aperta ? (
                            <select
                              aria-label={`Stato di ${singola.titolo}`}
                              value={singola.stato}
                              disabled={inCorso}
                              onChange={(evento) =>
                                void agisci(() =>
                                  cambiaStatoTask(
                                    id,
                                    singola.id,
                                    evento.target.value as StatoTask,
                                  ),
                                )
                              }
                            >
                              {PASSAGGI.map((stato) => (
                                <option key={stato} value={stato}>
                                  {STATI_TASK[stato]}
                                </option>
                              ))}
                            </select>
                          ) : (
                            <Pillola tinta={tinta(singola.stato)}>
                              {STATI_TASK[singola.stato]}
                            </Pillola>
                          )}

                          {/* Il timer, per chi ci lavora. Sta sotto la tendina
                              dello stato perche' e' la stessa domanda vista da
                              due lati: la tendina dice a che punto e' la task,
                              i pulsanti se ci sto lavorando adesso. */}
                          {miaTask(singola) && aperta && (
                            <div className="azioni-timer">
                              {tempo?.inCorso?.taskId === singola.id ? (
                                <button
                                  type="button"
                                  className="minuscolo"
                                  disabled={inCorso}
                                  onClick={() => void agisciTempo(pausaTempo)}
                                >
                                  Pausa
                                </button>
                              ) : (
                                <button
                                  type="button"
                                  className="minuscolo"
                                  disabled={inCorso}
                                  onClick={() =>
                                    void agisciTempo(() => avviaTempo(id, singola.id))
                                  }
                                >
                                  Avvia
                                </button>
                              )}
                              <button
                                type="button"
                                className="minuscolo"
                                disabled={inCorso}
                                onClick={() =>
                                  void agisciTempo(() => completaTempo(id, singola.id))
                                }
                              >
                                Completa
                              </button>
                            </div>
                          )}
                        </td>
                        <td className="assegnatari">
                          {singola.assegnatari.map((persona) => (
                            <span
                              key={persona.utenteId}
                              className="origine origine-aggiunto"
                            >
                              {persona.cognome} {persona.nome}
                              {puoGestire && (
                                <button
                                  type="button"
                                  className="togli"
                                  aria-label={`Rimuovi ${persona.cognome}`}
                                  disabled={inCorso}
                                  onClick={() =>
                                    void agisci(() =>
                                      rimuoviAssegnazione(id, singola.id, persona.utenteId),
                                    )
                                  }
                                >
                                  ×
                                </button>
                              )}
                            </span>
                          ))}

                          {puoGestire && candidati.length > 0 && (
                            <select
                              aria-label="Assegna una persona"
                              value=""
                              disabled={inCorso}
                              onChange={(evento) => {
                                const scelto = Number(evento.target.value)
                                if (scelto > 0) {
                                  void agisci(() => assegna(id, singola.id, scelto))
                                }
                              }}
                            >
                              <option value="">Assegna…</option>
                              {candidati.map((persona) => (
                                <option key={persona.utenteId} value={persona.utenteId}>
                                  {persona.cognome} {persona.nome}
                                </option>
                              ))}
                            </select>
                          )}
                        </td>
                      </tr>

                      {/* Il modulo dell'allocazione sta in una riga sotto la
                          task, non in un riquadro staccato: così resta chiaro
                          a quale task si stanno dando le ore. */}
                      {allocaPer === singola.id && (
                        <tr>
                          <td colSpan={5}>
                            <form
                              className="modulo modulo-riga"
                              onSubmit={(evento) => void inviaAllocazione(evento, singola.id)}
                            >
                              <div className="campo">
                                <label htmlFor={`ore-${singola.id}`}>Ore da allocare</label>
                                <input
                                  id={`ore-${singola.id}`}
                                  type="number"
                                  min="0.25"
                                  step="0.25"
                                  required
                                  value={oreDaAllocare}
                                  onChange={(evento) =>
                                    setOreDaAllocare(evento.target.value)
                                  }
                                  disabled={inCorso}
                                />
                                <p className="nota">
                                  Libere sul lavoro: {ore(lavoro?.oreLibere ?? null)}.
                                </p>
                              </div>
                              <div className="campo">
                                <label htmlFor={`motivo-${singola.id}`}>Motivo</label>
                                <input
                                  id={`motivo-${singola.id}`}
                                  required
                                  maxLength={500}
                                  value={motivoAllocazione}
                                  onChange={(evento) =>
                                    setMotivoAllocazione(evento.target.value)
                                  }
                                  disabled={inCorso}
                                />
                              </div>
                              <div className="azioni">
                                <button type="submit" disabled={inCorso}>
                                  {inCorso ? 'Alloco…' : 'Alloca'}
                                </button>
                                <button
                                  type="button"
                                  className="secondario"
                                  disabled={inCorso}
                                  onClick={() => setAllocaPer(null)}
                                >
                                  Annulla
                                </button>
                              </div>
                            </form>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Le richieste di ore */}
      <section className="gruppo-permessi">
        <div className="testata">
          <h2>Richieste di ore</h2>
          {puoChiedereOre && !mostraRichiesta && (
            <button
              type="button"
              className="secondario"
              onClick={() => setMostraRichiesta(true)}
            >
              Chiedi ore
            </button>
          )}
        </div>

        {puoChiedereOre && mostraRichiesta && (
          <form className="modulo" onSubmit={inviaRichiesta}>
            <div className="campo">
              <label htmlFor="taskRichiesta">Task</label>
              <select
                id="taskRichiesta"
                value={taskRichiesta}
                onChange={(evento) => setTaskRichiesta(evento.target.value)}
                disabled={inCorso}
              >
                <option value="">Nessuna — le ore restano nel pool</option>
                {(task ?? [])
                  .filter((singola) => apertaTask(singola.stato))
                  .map((singola) => (
                    <option key={singola.id} value={singola.id}>
                      {singola.titolo}
                    </option>
                  ))}
              </select>
              <p className="nota">
                Indicando una task, le ore concesse le vengono allocate direttamente:
                è la garanzia che non finiscano altrove.
              </p>
            </div>
            <div className="campo">
              <label htmlFor="oreRichieste">Ore richieste</label>
              <input
                id="oreRichieste"
                type="number"
                min="0.25"
                step="0.25"
                required
                value={oreRichieste}
                onChange={(evento) => setOreRichieste(evento.target.value)}
                disabled={inCorso}
              />
            </div>
            <div className="campo">
              <label htmlFor="motivoRichiesta">Motivo</label>
              <textarea
                id="motivoRichiesta"
                rows={3}
                required
                maxLength={500}
                value={motivoRichiesta}
                onChange={(evento) => setMotivoRichiesta(evento.target.value)}
                disabled={inCorso}
              />
              <p className="nota">
                È quello su cui l'amministratore decide, insieme ai numeri del lavoro.
              </p>
            </div>
            <div className="azioni">
              <button type="submit" disabled={inCorso}>
                {inCorso ? 'Invio…' : 'Invia richiesta'}
              </button>
              <button
                type="button"
                className="secondario"
                disabled={inCorso}
                onClick={() => setMostraRichiesta(false)}
              >
                Annulla
              </button>
            </div>
          </form>
        )}

        {richieste.length === 0 ? (
          <p className="sommario">Nessuna richiesta di ore per questo lavoro.</p>
        ) : (
          <div className="scheda-tabella">
            <table className="tabella">
              <thead>
                <tr>
                  <th>Aperta il</th>
                  <th>Task</th>
                  <th>Chieste</th>
                  <th>Concesse</th>
                  <th>Stato</th>
                  <th>Motivo</th>
                </tr>
              </thead>
              <tbody>
                {richieste.map((richiesta) => (
                  <tr key={richiesta.id}>
                    <td>{data(richiesta.creataIl)}</td>
                    <td>{richiesta.task ?? '— pool del lavoro'}</td>
                    <td>{ore(richiesta.oreRichieste)}</td>
                    <td>{ore(richiesta.oreConcesse)}</td>
                    <td>
                      <span
                        className={
                          richiesta.stato === 'IN_ATTESA'
                            ? 'origine origine-attesa'
                            : richiesta.stato === 'RIFIUTATA'
                              ? 'origine origine-sforamento'
                              : 'origine origine-aggiunto'
                        }
                      >
                        {STATI_RICHIESTA[richiesta.stato]}
                      </span>
                      {richiesta.gestitaDa !== null && (
                        <span className="nota">{richiesta.gestitaDa}</span>
                      )}
                    </td>
                    <td>
                      <span className="nota">{richiesta.motivo}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Lo storico: si carica su richiesta, perché è la cosa più lunga della
          pagina e la si consulta solo quando serve capire un numero. */}
      <section className="gruppo-permessi">
        <div className="testata">
          <h2>Storico delle ore</h2>
          {storico === null && (
            <button type="button" className="secondario" onClick={() => void apriStorico()}>
              Mostra
            </button>
          )}
        </div>

        {storico !== null && storico.length === 0 && (
          <p className="sommario">Nessun movimento di ore su questo lavoro.</p>
        )}

        {storico !== null && storico.length > 0 && (
          <div className="scheda-tabella">
            <table className="tabella">
              <thead>
                <tr>
                  <th>Quando</th>
                  <th>Cosa</th>
                  <th>Ore</th>
                  <th>Task</th>
                  <th>Chi</th>
                  <th>Motivo</th>
                </tr>
              </thead>
              <tbody>
                {storico.map((movimento) => (
                  <tr key={movimento.id}>
                    <td>{data(movimento.creatoIl)}</td>
                    <td>{TIPI_MOVIMENTO[movimento.tipo]}</td>
                    <td className={movimento.ore < 0 ? 'ore-uscita' : 'ore-entrata'}>
                      {movimento.ore > 0 ? '+' : ''}
                      {ore(movimento.ore)}
                    </td>
                    <td>{movimento.task ?? '—'}</td>
                    <td>{movimento.autore}</td>
                    <td>
                      <span className="nota">{movimento.motivo ?? '—'}</span>
                      {movimento.richiestaId !== null && (
                        <span className="nota">
                          dalla richiesta #{movimento.richiestaId}
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </main>
  )
}
