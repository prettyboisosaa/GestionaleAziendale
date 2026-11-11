import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import listPlugin from '@fullcalendar/list'
import interactionPlugin from '@fullcalendar/interaction'
import itLocale from '@fullcalendar/core/locales/it'
import type { DatesSetArg, EventClickArg, EventInput } from '@fullcalendar/core'
import { miaDashboard, mioCalendario } from './api'
import type { EventoCalendario, MiaTask, RiepilogoDashboard } from './api'
import { ETICHETTA_ATTIVITA, avvia, completa, conferma, durata, pausa } from '../tempo/api'
import type { SituazioneTempo } from '../tempo/api'
import CalendarioSettimanale from '../tempo/CalendarioSettimanale'
import { elencoLavori } from '../lavoro/api'
import type { RigaLavoro } from '../lavoro/api'
import { testo } from '../api/http'
import type { UtenteCorrente } from '../autenticazione/api'
import { ha } from '../autenticazione/permessi'
import { Pillola } from '../componenti/Stato'
import { apertaTask, ETICHETTA_TASK, tinta } from '../stato/stati'

/**
 * I filtri della lista. Sono gli stessi delle card in cima, perché le card
 * *sono* i filtri: il documento chiede un solo componente lista con più punti
 * d'ingresso, non cinque liste diverse.
 */
type Filtro = 'TUTTE' | 'URGENTI' | 'OGGI' | 'SETTIMANA' | 'IN_CORSO'

const ETICHETTA_FILTRO: Record<Filtro, string> = {
  TUTTE: 'Tutte',
  URGENTI: 'Urgenti',
  OGGI: 'Oggi',
  SETTIMANA: 'Settimana',
  IN_CORSO: 'In corso',
}

/** Settimana su desktop, lista su mobile, come chiede il documento. */
const VISTA_INIZIALE =
  typeof window !== 'undefined' && window.matchMedia('(max-width: 700px)').matches
    ? 'listWeek'
    : 'dayGridWeek'

/** La data di oggi in formato ISO, nel fuso di chi guarda. */
function oggiIso(): string {
  const adesso = new Date()
  const mese = String(adesso.getMonth() + 1).padStart(2, '0')
  const giorno = String(adesso.getDate()).padStart(2, '0')
  return `${adesso.getFullYear()}-${mese}-${giorno}`
}

/**
 * Il giorno dopo, in ISO. Serve a tradurre la fine *inclusiva* dell'API nella
 * fine *esclusiva* che FullCalendar vuole per gli eventi a giornata intera.
 *
 * Passa da `Date.UTC` e non dal costruttore locale: con quest'ultimo, in un
 * fuso a ovest di Greenwich, «2026-11-06» diventerebbe il 5 novembre sera e
 * l'evento slitterebbe di un giorno.
 */
function piuUnGiorno(iso: string): string {
  const [anno, mese, giorno] = iso.split('-').map(Number)
  return new Date(Date.UTC(anno, mese - 1, giorno + 1)).toISOString().slice(0, 10)
}

function menoUnGiorno(iso: string): string {
  const [anno, mese, giorno] = iso.split('-').map(Number)
  return new Date(Date.UTC(anno, mese - 1, giorno - 1)).toISOString().slice(0, 10)
}

/** La domenica di questa settimana, in ISO. La settimana ISO finisce lì. */
function fineSettimanaIso(): string {
  const adesso = new Date()
  // getDay(): 0 = domenica. Quanti giorni mancano alla domenica di questa
  // settimana, contando la settimana da lunedi'.
  const mancanti = (7 - adesso.getDay()) % 7
  const domenica = new Date(adesso.getFullYear(), adesso.getMonth(), adesso.getDate() + mancanti)
  const mese = String(domenica.getMonth() + 1).padStart(2, '0')
  const giorno = String(domenica.getDate()).padStart(2, '0')
  return `${domenica.getFullYear()}-${mese}-${giorno}`
}

/** Da «2026-11-06» a «06/11». */
function breve(iso: string): string {
  const [, mese, giorno] = iso.split('-')
  return `${giorno}/${mese}`
}

/** Le classi CSS di un evento: tipo, stato e, se c'è, il conflitto. */
function classiEvento(evento: EventoCalendario): string[] {
  const classi = ['evento', `evento-${evento.tipo.toLowerCase()}`]
  if (evento.stato !== null) {
    classi.push(`evento-${evento.stato.toLowerCase().replace('_', '-')}`)
  }
  if (evento.conflitto) {
    classi.push('evento-conflitto')
  }
  return classi
}

/**
 * Vero se la task è "viva" in quel giorno, con la stessa regola che il
 * calendario usa per disegnarla: una task in corso occupa tutta la barra dal
 * suo inizio alla scadenza, le altre solo il giorno in cui scadono.
 *
 * È quello che tiene fede al secondo principio del documento — calendario e
 * lista sono due viste degli stessi dati: cliccando un giorno, la lista mostra
 * esattamente ciò che in quel giorno si vede sopra.
 */
function vivaIl(task: MiaTask, giorno: string): boolean {
  // Aperta e non «in corso»: il discrimine è lo stesso del server, e deve
  // restarlo. IN_CORSO si diventa premendo avvia, quindi una task appena
  // assegnata — ATTIVO — risultava viva solo il giorno della scadenza, e
  // cliccare un giorno della settimana in cui va fatta la nascondeva.
  if (apertaTask(task.stato)) {
    return task.dataInizio <= giorno && giorno <= task.scadenza
  }
  return task.scadenza === giorno
}

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
  const [eventi, setEventi] = useState<EventoCalendario[]>([])
  const [errore, setErrore] = useState<string | null>(null)
  const [filtro, setFiltro] = useState<Filtro>('TUTTE')
  /** Il giorno scelto sul calendario, che restringe ulteriormente la lista. */
  const [giorno, setGiorno] = useState<string | null>(null)
  const [mostraCompletate, setMostraCompletate] = useState(false)
  /** L'id della task su cui è in corso il cambio di stato, per spegnerne i pulsanti. */
  const [inAggiornamento, setInAggiornamento] = useState<number | null>(null)
  /** La chiusura d'ufficio che si sta confermando, per id di **sessione**. */
  const [inConferma, setInConferma] = useState<number | null>(null)
  /** L'intervallo visibile del calendario, per poterlo ricaricare dopo un cambio di stato. */
  const [intervallo, setIntervallo] = useState<{ da: string; a: string } | null>(null)
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

  const navigate = useNavigate()
  const puoAggiornare = ha(utente, 'TASK_AGGIORNA_STATO')

  const caricaRiepilogo = useCallback(() => miaDashboard().then(setRiepilogo), [])

  const caricaEventi = useCallback(
    (da: string, a: string) => mioCalendario(da, a).then(setEventi),
    [],
  )

  useEffect(() => {
    let annullato = false

    miaDashboard()
      .then((dati) => {
        if (!annullato) {
          setRiepilogo(dati)
        }
      })
      .catch((motivo: unknown) => {
        if (!annullato) {
          setErrore(testo(motivo))
        }
      })

    // I lavori servono solo a una tendina del pannello del calendario:
    // un errore qui non deve coprire la dashboard di chi la stava guardando.
    // Senza di loro si registra tutto tranne le attività che citano un
    // lavoro, e quelle il pannello le rifiuta con un messaggio suo.
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

  /**
   * FullCalendar avvisa a ogni cambio di vista o di periodo. `endStr` è
   * esclusivo, mentre l'API vuole estremi compresi: da qui il giorno tolto.
   */
  function periodoCambiato(arg: DatesSetArg) {
    const da = arg.startStr.slice(0, 10)
    const a = menoUnGiorno(arg.endStr.slice(0, 10))
    setIntervallo({ da, a })
    caricaEventi(da, a).catch((motivo: unknown) => setErrore(testo(motivo)))
  }

  /**
   * Avvia, pausa, completa: un clic, senza aprire il dettaglio.
   *
   * Ogni azione risponde con la situazione del tempo, che va passata in alto
   * perche' la barra in testata la mostri; e si ricarica tutto il resto,
   * perche' i conteggi delle card cambiano e il calendario disegna la task in
   * modo diverso — una task avviata diventa una barra dove prima c'era un
   * segno.
   */
  async function agisci(taskId: number, azione: () => Promise<SituazioneTempo>) {
    setInAggiornamento(taskId)
    setErrore(null)
    try {
      onTempo(await azione())
      await ricarica()
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
      await ricarica()
    } catch (motivo) {
      setErrore(testo(motivo))
    } finally {
      setInConferma(null)
    }
  }

  /**
   * Quello che va riletto dopo una scrittura sul tempo: le ore consumate di
   * una task stanno nel riepilogo, e le barre del calendario nel periodo in
   * vista.
   */
  async function ricarica() {
    await caricaRiepilogo()
    if (intervallo !== null) {
      await caricaEventi(intervallo.da, intervallo.a)
    }
  }

  const oggi = oggiIso()
  const fineSettimana = fineSettimanaIso()
  const tutte = riepilogo === null ? [] : riepilogo.task
  const conflitti = new Set(riepilogo === null ? [] : riepilogo.taskInConflitto)

  const visibili = tutte
    .filter((task) => {
      switch (filtro) {
        case 'URGENTI':
          // Il livello più alto e nient'altro. «Alta» non entra: una card che
          // ne contasse due livelli direbbe un numero che l'etichetta non
          // spiega, e chi la clicca si troverebbe una lista più larga di
          // quello che ha chiesto.
          return task.stato !== 'COMPLETATO' && task.stato !== 'ANNULLATO'
            && task.urgenzaTask === 'CRITICAL'
        case 'OGGI':
          return task.stato !== 'COMPLETATO' && task.stato !== 'ANNULLATO'
            && task.scadenza === oggi
        case 'SETTIMANA':
          return task.stato !== 'COMPLETATO' && task.stato !== 'ANNULLATO'
            && task.scadenza >= oggi && task.scadenza <= fineSettimana
        case 'IN_CORSO':
          return task.stato === 'IN_CORSO'
        default:
          // «Tutte» non vuol dire proprio tutte: le annullate restano fuori,
          // perché non c'è niente da farci. Le completate seguono lo stesso
          // interruttore del calendario, così le due viste concordano.
          return task.stato !== 'ANNULLATO'
            && (mostraCompletate || task.stato !== 'COMPLETATO')
      }
    })
    .filter((task) => giorno === null || vivaIl(task, giorno))

  const eventiCalendario: EventInput[] = eventi
    .filter(
      (evento) =>
        mostraCompletate || !(evento.tipo === 'TASK' && evento.stato === 'COMPLETATO'),
    )
    .map((evento) => ({
      id: evento.id,
      title: evento.titolo,
      start: evento.inizio,
      // La fine dell'API è compresa, quella di FullCalendar no.
      end: piuUnGiorno(evento.fine),
      allDay: true,
      // Le festività tingono la cella invece di occupare una riga: sono lo
      // sfondo della giornata, non una cosa da fare.
      display: evento.tipo === 'FESTIVITA' ? 'background' : 'auto',
      classNames: classiEvento(evento),
      extendedProps: { url: evento.url },
    }))

  function eventoCliccato(arg: EventClickArg) {
    arg.jsEvent.preventDefault()
    const url = arg.event.extendedProps.url as string | null | undefined
    if (url !== null && url !== undefined) {
      void navigate(url)
    }
  }

  return (
    <main className="pagina-larga">
      <div className="testata">
        <h1>{saluto(utente.nome)}</h1>
        <span className="nota">{dataDiOggi()}</span>
      </div>

      {errore !== null && (
        <p className="errore" role="alert">
          {errore}
        </p>
      )}

      {riepilogo === null && errore === null && (
        <p className="sommario" role="status">
          Carico la giornata…
        </p>
      )}

      {/* Le chiusure d'ufficio da guardare.

          Sta in cima e non in una pagina sua: una sessione chiusa dal sistema
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

      {riepilogo !== null && (
        <>
          {tempo !== null && (
            <p className="sommario">
              Oggi hai registrato <strong>{durata(tempo.minutiOggi)}</strong>
              {tempo.inCorso !== null && ', più la task che stai facendo adesso'}.
            </p>
          )}

          {/* Le card sono pulsanti: ognuna applica il suo filtro alla lista
              qui sotto. È il «più punti d'ingresso, un solo elenco». */}
          <div className="card-riga">
            <Card
              etichetta="Urgenti"
              valore={riepilogo.contatori.critiche}
              tinta="urgente"
              attiva={filtro === 'URGENTI'}
              onClick={() => setFiltro(filtro === 'URGENTI' ? 'TUTTE' : 'URGENTI')}
            />
            <Card
              etichetta="Oggi"
              valore={riepilogo.contatori.oggi}
              tinta="attesa"
              attiva={filtro === 'OGGI'}
              onClick={() => setFiltro(filtro === 'OGGI' ? 'TUTTE' : 'OGGI')}
            />
            <Card
              etichetta="Settimana"
              valore={riepilogo.contatori.settimana}
              tinta="attivo"
              attiva={filtro === 'SETTIMANA'}
              onClick={() => setFiltro(filtro === 'SETTIMANA' ? 'TUTTE' : 'SETTIMANA')}
            />
            <Card
              etichetta="In corso"
              valore={riepilogo.contatori.inCorso}
              tinta="in-corso"
              attiva={filtro === 'IN_CORSO'}
              onClick={() => setFiltro(filtro === 'IN_CORSO' ? 'TUTTE' : 'IN_CORSO')}
            />
          </div>

          {/* Due calendari, due domande. Questo guarda avanti: che cosa
              devo fare, per scadenza. Quello sotto guarda indietro: dove
              sono andate le ore. Impilati e non affiancati di proposito —
              una griglia di sette giorni per quattordici ore stretta in
              mezza pagina non si legge. */}
          <div className="testata">
            <h2>Da fare</h2>
          </div>

          <div className="scheda-calendario">
            <FullCalendar
              plugins={[dayGridPlugin, listPlugin, interactionPlugin]}
              locale={itLocale}
              initialView={VISTA_INIZIALE}
              headerToolbar={{
                left: 'prev,next oggi',
                center: 'title',
                right: 'dayGridWeek,dayGridMonth,listWeek',
              }}
              customButtons={{
                oggi: {
                  text: 'Oggi',
                  click: () => setGiorno(null),
                },
              }}
              buttonText={{ week: 'Settimana', month: 'Mese', list: 'Lista' }}
              firstDay={1}
              height="auto"
              events={eventiCalendario}
              datesSet={periodoCambiato}
              dateClick={(arg: { dateStr: string }) =>
                setGiorno(arg.dateStr === giorno ? null : arg.dateStr)
              }
              eventClick={eventoCliccato}
            />
          </div>

          <div className="testata">
            <h2>Cosa ho fatto</h2>
          </div>

          {/* Il ricaricamento della griglia lo fa il cambio di `tempo`. Qui
              si rimette in pari il resto: le ore consumate di una task
              cambiano se una sessione viene corretta o annullata, e quelle
              stanno nel riepilogo. */}
          <CalendarioSettimanale
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

          <div className="testata">
            <h2>Le mie task</h2>
          </div>

          <div className="filtro">
            {(Object.keys(ETICHETTA_FILTRO) as Filtro[]).map((uno) => (
              <button
                key={uno}
                type="button"
                className={filtro === uno ? 'filtro-scelto' : undefined}
                onClick={() => setFiltro(uno)}
              >
                {ETICHETTA_FILTRO[uno]}
              </button>
            ))}

            {giorno !== null && (
              <button type="button" className="filtro-giorno" onClick={() => setGiorno(null)}>
                giorno: {breve(giorno)} ✕
              </button>
            )}

            <label>
              <input
                type="checkbox"
                checked={mostraCompletate}
                onChange={(evento) => setMostraCompletate(evento.target.checked)}
              />
              Mostra completate
            </label>
          </div>

          {visibili.length === 0 ? (
            <p className="sommario">Niente da mostrare con questo filtro.</p>
          ) : (
            <div className="scheda-tabella">
              <table className="tabella">
                <thead>
                  <tr>
                    <th>Task</th>
                    <th>Lavoro</th>
                    <th>Scadenza</th>
                    <th>Stato</th>
                    {puoAggiornare && <th>Azioni</th>}
                  </tr>
                </thead>
                <tbody>
                  {visibili.map((task) => (
                    <tr key={task.taskId}>
                      <td>
                        <span className="codice-permesso">{task.titolo}</span>
                        {conflitti.has(task.taskId) && (
                          <p className="nota nota-conflitto">
                            Scade in un giorno in cui sei in ferie o permesso.
                          </p>
                        )}
                      </td>
                      <td>
                        <a href={`/lavori/${task.lavoroId}`}>{task.codiceLavoro}</a>{' '}
                        <span className="nota">{task.nomeLavoro}</span>
                      </td>
                      <td>
                        {breve(task.scadenza)}
                        <span className="nota"> · {scadenzaInParole(task)}</span>
                      </td>
                      <td>
                        <Pillola tinta={tinta(task.stato)}>
                          {ETICHETTA_TASK[task.stato]}
                        </Pillola>
                      </td>
                      {puoAggiornare && (
                        <td className="azioni">
                          {/* Un clic solo, senza aprire il dettaglio:
                              altrimenti i dati invecchiano e la dashboard
                              smette di dire la verità.

                              «Avvia» compare anche su una task gia' in corso
                              su cui il timer non sta scorrendo: in corso vuol
                              dire che il lavoro e' cominciato, non che lo si
                              stia facendo in questo momento.

                              Su una task chiusa o annullata non c'e' niente da
                              offrire: il server rifiuterebbe, e un pulsante
                              che risponde 403 e' un pulsante che non andava
                              mostrato. */}
                          {apertaTask(task.stato) && (
                            <>
                          {task.inCorso ? (
                            <button
                              type="button"
                              disabled={inAggiornamento === task.taskId}
                              onClick={() => void agisci(task.taskId, pausa)}
                            >
                              Pausa
                            </button>
                          ) : (
                            <button
                              type="button"
                              disabled={inAggiornamento === task.taskId}
                              onClick={() =>
                                void agisci(task.taskId, () =>
                                  avvia(task.lavoroId, task.taskId),
                                )
                              }
                            >
                              Avvia
                            </button>
                          )}
                          <button
                            type="button"
                            disabled={inAggiornamento === task.taskId}
                            onClick={() =>
                              void agisci(task.taskId, () =>
                                completa(task.lavoroId, task.taskId),
                              )
                            }
                          >
                            Completa
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
          )}
        </>
      )}
    </main>
  )
}

type ProprietaCard = {
  etichetta: string
  valore: number
  tinta: string
  attiva: boolean
  onClick: () => void
}

function Card({ etichetta, valore, tinta, attiva, onClick }: ProprietaCard) {
  return (
    <button
      type="button"
      className={`card card-${tinta}${attiva ? ' card-attiva' : ''}`}
      onClick={onClick}
      aria-pressed={attiva}
    >
      <span className="card-valore">{valore}</span>
      <span className="card-etichetta">{etichetta}</span>
    </button>
  )
}

/** «In ritardo di 3 giorni», «scade oggi», «fra 5 giorni». */
function scadenzaInParole(task: MiaTask): string {
  if (task.stato === 'COMPLETATO' || task.stato === 'ANNULLATO') {
    return ETICHETTA_TASK[task.stato].toLowerCase()
  }
  const giorni = task.giorniAllaScadenza
  if (giorni < 0) {
    const quanti = Math.abs(giorni)
    return quanti === 1 ? 'in ritardo di 1 giorno' : `in ritardo di ${quanti} giorni`
  }
  if (giorni === 0) {
    return 'scade oggi'
  }
  return giorni === 1 ? 'fra 1 giorno' : `fra ${giorni} giorni`
}

function saluto(nome: string): string {
  const ora = new Date().getHours()
  if (ora < 13) {
    return 'Buongiorno, ' + nome + ".";
  }
  return ora < 18 ? 'Buon pomeriggio, ' + nome + "." : 'Buonasera, ' + nome + "."
}


function dataDiOggi(): string {
  return new Date().toLocaleDateString('it-IT', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  })
}
