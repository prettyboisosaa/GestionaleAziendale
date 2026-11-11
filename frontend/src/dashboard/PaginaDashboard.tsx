import { useCallback, useEffect, useState } from 'react'
import { miaDashboard } from './api'
import type { MiaTask, RiepilogoDashboard } from './api'
import { ETICHETTA_ATTIVITA, avvia, completa, conferma, durata, pausa } from '../tempo/api'
import type { SituazioneTempo } from '../tempo/api'
import { elencoLavori } from '../lavoro/api'
import type { RigaLavoro } from '../lavoro/api'
import { testo } from '../api/http'
import type { UtenteCorrente } from '../autenticazione/api'
import { ha } from '../autenticazione/permessi'
import { apertaTask } from '../stato/stati'
import { breve, dataDiOggi, saluto } from './orario'
import PannelloDaFare from './PannelloDaFare'
import PannelloFatto from './PannelloFatto'
import PannelloMieTask from './PannelloMieTask'
import type { Filtro } from './PannelloMieTask'
import PannelloMieRichieste, { PannelloDaApprovare } from './PannelloRichieste'

/**
 * La giornata, su una schermata sola.
 *
 * **Due colonne e quattro pannelli**, al posto della colonna unica in cui
 * tutto stava in fila: a sinistra i due calendari — il pianificato e il
 * registrato, la stessa scala oraria — e a destra le task e le richieste. La
 * colonna destra parte alla stessa altezza del primo calendario, così
 * l'agenda e le task del giorno si leggono insieme senza scorrere. Prima, per
 * arrivare alle task bisognava passare oltre due calendari, e i lati dello
 * schermo restavano vuoti.
 *
 * **I quattro contatori non sono spariti**: le card che occupavano una riga
 * intera sopra i calendari sono diventate i contatori nell'intestazione di
 * «Le mie task», che è l'elenco che filtrano.
 *
 * **Ogni pannello carica da sé quello che mostra.** I due di destra hanno le
 * loro rotte e i loro stati, così un errore in uno non spegne gli altri tre;
 * questa pagina tiene solo ciò che è condiviso — il riepilogo delle task, che
 * serve a due pannelli, e i lavori per la tendina del registro.
 */
export default function PaginaDashboard({
  utente,
  tempo,
  onTempo,
}: {
  utente: UtenteCorrente
  tempo: SituazioneTempo | null
  onTempo: (situazione: SituazioneTempo) => void
}) {
  const [riepilogo, setRiepilogo] = useState<RiepilogoDashboard | null>(null)
  const [errore, setErrore] = useState<string | null>(null)
  const [erroreTask, setErroreTask] = useState<string | null>(null)
  const [filtro, setFiltro] = useState<Filtro>('TUTTE')
  const [mostraCompletate, setMostraCompletate] = useState(false)
  /** L'id della task su cui è in corso il cambio di stato, per spegnerne i pulsanti. */
  const [inAggiornamento, setInAggiornamento] = useState<number | null>(null)
  /** La chiusura d'ufficio che si sta confermando, per id di **sessione**. */
  const [inConferma, setInConferma] = useState<number | null>(null)
  /**
   * I lavori visibili, per le attività del calendario che citano un lavoro
   * senza averne una task — la manutenzione per il cliente X.
   *
   * Si chiedono con `elencoLavori`, che vuole `LAVORI_VISUALIZZA_PROPRI`: è
   * un permesso **base** dalla V14, quindi ogni dipendente ce l'ha e la
   * chiamata non va messa dietro un controllo. Un elenco vuoto non è un
   * errore: vuol dire che non si è coinvolti in nessun lavoro, e le attività
   * che non ne citano uno si registrano comunque.
   */
  const [lavori, setLavori] = useState<RigaLavoro[]>([])

  const puoAggiornare = ha(utente, 'TASK_AGGIORNA_STATO')
  /**
   * Se si possono leggere le **proprie** richieste di ore.
   *
   * Sono i tre permessi del `@PreAuthorize` su `GET /api/richieste-ore`, e
   * nessuno dei tre è di base: chiedere quella rotta a chi non li ha vuol
   * dire un 403, e nel pannello un 403 su un ramo farebbe cadere anche
   * l'altro.
   */
  const puoOre = ha(
    utente,
    'ORE_RICHIEDI',
    'ORE_RICHIESTE_GESTISCI',
    'LAVORI_VISUALIZZA_TUTTI',
  )
  const puoApprovareOre = ha(utente, 'ORE_RICHIESTE_GESTISCI')
  const puoApprovareAssenze = ha(utente, 'ASSENZE_GESTISCI')
  const approva = puoApprovareOre || puoApprovareAssenze

  const caricaRiepilogo = useCallback(() => miaDashboard().then(setRiepilogo), [])

  useEffect(() => {
    let annullato = false

    miaDashboard()
      .then((dati) => {
        if (!annullato) {
          setRiepilogo(dati)
          setErroreTask(null)
        }
      })
      .catch((motivo: unknown) => {
        if (!annullato) {
          // L'errore del riepilogo sta **dentro** il pannello delle task, che
          // è quello che non può disegnarsi: in cima alla pagina direbbe che
          // tutta la dashboard è rotta mentre i calendari funzionano.
          setErroreTask(testo(motivo))
        }
      })

    // I lavori servono solo a una tendina del pannello del registro: un
    // errore qui non deve coprire la dashboard di chi la stava guardando.
    elencoLavori()
      .then((elenco) => {
        if (!annullato) {
          setLavori(elenco)
        }
      })
      .catch(() => {
        if (!annullato) {
          setLavori([])
        }
      })

    return () => {
      annullato = true
    }
  }, [])

  async function agisci(taskId: number, azione: () => Promise<SituazioneTempo>) {
    setInAggiornamento(taskId)
    setErrore(null)
    try {
      onTempo(await azione())
      await caricaRiepilogo()
    } catch (motivo) {
      setErrore(testo(motivo))
    } finally {
      setInAggiornamento(null)
    }
  }

  /**
   * Accetta una chiusura d'ufficio così com'è.
   *
   * Non passa da `agisci`, che tiene l'attesa su un **id di task**: confermare
   * riguarda una sessione, e due chiusure sulla stessa task spegnerebbero
   * entrambi i bottoni. Da quando una sessione può non avere una task — una
   * riunione, una manutenzione interna — quell'id poteva anche non esserci.
   */
  async function confermo(idSessione: number) {
    setInConferma(idSessione)
    setErrore(null)
    try {
      onTempo(await conferma(idSessione))
      await caricaRiepilogo()
    } catch (motivo) {
      setErrore(testo(motivo))
    } finally {
      setInConferma(null)
    }
  }

  const tutte = riepilogo === null ? [] : riepilogo.task
  const conflitti = new Set(riepilogo === null ? [] : riepilogo.taskInConflitto)

  const visibili = tutte.filter((task) => {
    switch (filtro) {
      case 'URGENTI':
        return apertaTask(task.stato) && task.urgenzaTask === 'CRITICAL'
      case 'OGGI':
        return apertaTask(task.stato) && task.giorniAllaScadenza <= 0
      case 'SETTIMANA':
        return apertaTask(task.stato) && task.giorniAllaScadenza <= 7
      case 'IN_CORSO':
        return task.stato === 'IN_CORSO'
      default:
        // Le annullate non compaiono mai: sono storia, e non c'è niente da
        // farci. Le completate seguono l'interruttore del pannello.
        return (
          task.stato !== 'ANNULLATO'
          && (mostraCompletate || task.stato !== 'COMPLETATO')
        )
    }
  })

  return (
    <main className="pagina-dashboard">
      <div className="testata">
        <h1>{saluto(utente.nome)}</h1>
        <span className="nota">{dataDiOggi()}</span>
      </div>

      {errore !== null && (
        <p className="errore" role="alert">
          {errore}
        </p>
      )}

      {/* Le chiusure d'ufficio da guardare.

          Sta in cima e non in un pannello suo: una sessione chiusa dal sistema
          e' l'unica cosa che il tracciamento automatico chiede al dipendente,
          e chiederla dove passa comunque e' il modo di non trasformarla in un
          adempimento. Non ci sono notifiche in questo gestionale: cio' che una
          notifica annuncerebbe si mette in evidenza nelle schermate. */}
      {tempo !== null && tempo.daConfermare.length > 0 && (
        <div className="avviso">
          <p>
            {tempo.daConfermare.length === 1
              ? 'Una sessione è stata chiusa dal sistema perché era rimasta aperta troppo a lungo. Controlla che le ore siano giuste.'
              : `${tempo.daConfermare.length} sessioni sono state chiuse dal sistema perché erano rimaste aperte troppo a lungo. Controlla che le ore siano giuste.`}
          </p>
          <ul className="elenco-conferme">
            {tempo.daConfermare.map((sessione) => (
              <li key={sessione.id}>
                {/* Senza task la riga si nomina con l'attività: è la sola
                    cosa che la descriva, e un posto vuoto farebbe sembrare
                    che il dato manchi. */}
                <span className="codice-permesso">
                  {sessione.task ?? ETICHETTA_ATTIVITA[sessione.tipoAttivita]}
                </span>
                <span className="nota">
                  {sessione.codiceLavoro !== null && `${sessione.codiceLavoro} · `}
                  {breve(sessione.avviataIl.slice(0, 10))} · {durata(sessione.minuti)}
                </span>
                <button
                  type="button"
                  className="minuscolo"
                  disabled={inConferma === sessione.id}
                  onClick={() => void confermo(sessione.id)}
                >
                  Confermo
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Le due zone. Sotto i 1200px la destra scende sotto i calendari: due
          colonne da mezza larghezza non reggono né una griglia di sette
          giorni né una riga di task. */}
      <div className="griglia-dashboard">
        <div className="colonna-calendari">
          {/* Il segnale di ricarica è `tempo`: cambia solo quando qualcosa è
              stato scritto davvero, e in questa applicazione non c'è polling. */}
          <PannelloDaFare segnale={tempo} />

          <PannelloFatto
            tempo={tempo}
            // Le task su cui si può scrivere sono quelle a cui si è assegnati
            // e ancora aperte: il riepilogo le ha già, e non serve una rotta
            // per richiederle. Le chiuse restano fuori perché il server le
            // rifiuterebbe — su una task completata le ore residue sono già
            // tornate nel pool del lavoro.
            task={tutte.filter((una) => apertaTask(una.stato))}
            // I lavori che un'attività può citare. Gli annullati restano
            // fuori — `elencoLavori` li esclude di suo — perché non si fa
            // manutenzione su un lavoro che non si farà.
            lavori={lavori}
            onErrore={setErrore}
            onScritto={(situazione) => {
              onTempo(situazione)
              caricaRiepilogo().catch((motivo: unknown) => setErrore(testo(motivo)))
            }}
          />
        </div>

        <div className="colonna-laterale">
          <PannelloMieTask
            task={visibili}
            contatori={riepilogo?.contatori ?? null}
            conflitti={conflitti}
            carico={riepilogo === null && erroreTask === null}
            errore={erroreTask}
            filtro={filtro}
            onFiltro={setFiltro}
            mostraCompletate={mostraCompletate}
            onMostraCompletate={setMostraCompletate}
            puoAggiornare={puoAggiornare}
            inAggiornamento={inAggiornamento}
            onAvvia={(task: MiaTask) =>
              void agisci(task.taskId, () => avvia(task.lavoroId, task.taskId))
            }
            onPausa={(task: MiaTask) => void agisci(task.taskId, pausa)}
            onCompleta={(task: MiaTask) =>
              void agisci(task.taskId, () => completa(task.lavoroId, task.taskId))
            }
          />

          <PannelloMieRichieste puoOre={puoOre} />

          {/* Chi approva vede **anche** la coda degli altri, sotto le proprie:
              le due cose non si fondono, perché su una si aspetta e sull'altra
              si decide. */}
          {approva && (
            <PannelloDaApprovare
              puoOre={puoApprovareOre}
              puoAssenze={puoApprovareAssenze}
            />
          )}
        </div>
      </div>
    </main>
  )
}
