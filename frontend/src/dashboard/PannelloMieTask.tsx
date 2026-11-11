import { Link } from 'react-router'
import type { MiaTask, ContatoriDashboard } from './api'
import { scadenzaBreve } from './orario'
import Pannello from '../componenti/Pannello'
import { Punto } from '../componenti/Stato'
import { TINTA_URGENZA, ETICHETTA_URGENZA } from '../task/api'
import type { UrgenzaTask } from '../task/api'
import { apertaTask } from '../stato/stati'

/**
 * I filtri della lista. Sono gli stessi dei contatori in intestazione, perché
 * i contatori *sono* i filtri: un solo elenco con più punti d'ingresso, non
 * cinque liste diverse.
 */
export type Filtro = 'TUTTE' | 'URGENTI' | 'OGGI' | 'SETTIMANA' | 'IN_CORSO'

/**
 * Quante righe stanno nel pannello.
 *
 * Non è una paginazione: è il numero di task che una persona guarda prima di
 * mettersi a lavorare. Oltre, la colonna diventa un elenco da scorrere e
 * smette di dire «comincia da qui» — per l'elenco intero c'è «Vedi tutte».
 */
const RIGHE = 7

/**
 * Quanto una task viene prima delle altre, in numero: più alto, più in alto.
 *
 * Serve perché l'ordinamento è su tre criteri e `sort` vuole un confronto
 * solo. L'urgenza mancante vale meno di «bassa» e non come «media»: le task
 * create prima della V19 non hanno un'urgenza, e dargliene una inventata le
 * metterebbe davanti a task che qualcuno ha deciso di marcare basse.
 */
const PESO_URGENZA: Record<UrgenzaTask, number> = {
  CRITICAL: 4,
  HIGH: 3,
  MEDIUM: 2,
  LOW: 1,
}

/**
 * Le mie task, in ordine di quello che viene prima.
 *
 * **Le scadute sono sempre in cima**, prima di qualunque urgenza: una task in
 * ritardo è un fatto, l'urgenza è una previsione, e un fatto viene prima.
 * Dopo, l'urgenza decisa dal responsabile; a pari urgenza, la scadenza più
 * vicina.
 *
 * I quattro contatori in intestazione hanno preso il posto delle card che
 * stavano in cima alla pagina: dicevano gli stessi numeri e occupavano una
 * riga intera sopra i calendari, mentre qui stanno accanto al titolo
 * dell'elenco che filtrano — che è la cosa che fanno.
 */
export default function PannelloMieTask({
  task,
  contatori,
  conflitti,
  carico,
  errore,
  filtro,
  onFiltro,
  mostraCompletate,
  onMostraCompletate,
  puoAggiornare,
  inAggiornamento,
  onAvvia,
  onPausa,
  onCompleta,
}: {
  task: MiaTask[]
  contatori: ContatoriDashboard | null
  /** Gli id delle task che scadono in un giorno di assenza approvata. */
  conflitti: Set<number>
  carico: boolean
  errore: string | null
  filtro: Filtro
  onFiltro: (filtro: Filtro) => void
  mostraCompletate: boolean
  onMostraCompletate: (mostra: boolean) => void
  puoAggiornare: boolean
  /** L'id della task su cui una scrittura è in corso, per spegnerne i pulsanti. */
  inAggiornamento: number | null
  onAvvia: (task: MiaTask) => void
  onPausa: (task: MiaTask) => void
  onCompleta: (task: MiaTask) => void
}) {
  const ordinate = [...task].sort(confronta)
  const visibili = ordinate.slice(0, RIGHE)
  const nascoste = ordinate.length - visibili.length

  return (
    <Pannello
      titolo="Le mie task"
      sottotitolo="per priorità e scadenza"
      azioni={<Link to="/lavori">Vedi tutte</Link>}
      carico={carico}
      errore={errore}
      vuoto={
        !carico && errore === null && ordinate.length === 0
          ? filtro === 'TUTTE'
            ? 'Non hai task aperte.'
            : 'Niente da mostrare con questo filtro.'
          : null
      }
      classe="pannello-task"
    >
      {contatori !== null && (
        <Contatori filtro={filtro} contatori={contatori} onFiltro={onFiltro} />
      )}

      <ul className="righe-task">
        {visibili.map((una) => (
          <li key={una.taskId} className="riga-task">
            <div className="riga-task-capo">
              {/* Il punto dice l'urgenza, e l'etichetta che porta dentro la
                  rende leggibile a chi non distingue i colori. Dove l'urgenza
                  manca non si disegna niente: un punto grigio direbbe «bassa»,
                  che è una cosa che nessuno ha deciso. */}
              {una.urgenzaTask !== null && (
                <Punto
                  tinta={TINTA_URGENZA[una.urgenzaTask]}
                  titolo={`Urgenza ${ETICHETTA_URGENZA[una.urgenzaTask].toLowerCase()}`}
                />
              )}
              <Link to={`/lavori/${una.lavoroId}`} className="riga-task-titolo">
                {una.titolo}
              </Link>
              <span
                className={
                  una.giorniAllaScadenza < 0
                    ? 'riga-task-scadenza in-ritardo'
                    : 'riga-task-scadenza'
                }
              >
                {scadenzaBreve(una.scadenza, una.giorniAllaScadenza)}
              </span>
            </div>

            <div className="riga-task-coda">
              <span className="nota">
                {una.codiceLavoro} · {una.nomeLavoro}
              </span>

              {puoAggiornare && apertaTask(una.stato) && (
                <span className="riga-task-azioni">
                  {una.inCorso ? (
                    <button
                      type="button"
                      className="minuscolo"
                      disabled={inAggiornamento === una.taskId}
                      onClick={() => onPausa(una)}
                    >
                      Pausa
                    </button>
                  ) : (
                    <button
                      type="button"
                      className="minuscolo"
                      disabled={inAggiornamento === una.taskId}
                      onClick={() => onAvvia(una)}
                    >
                      Avvia
                    </button>
                  )}
                  <button
                    type="button"
                    className="minuscolo"
                    disabled={inAggiornamento === una.taskId}
                    onClick={() => onCompleta(una)}
                  >
                    Completa
                  </button>
                </span>
              )}
            </div>

            {conflitti.has(una.taskId) && (
              <p className="nota nota-conflitto">
                Scade in un giorno in cui sei in ferie o permesso.
              </p>
            )}
          </li>
        ))}
      </ul>

      <div className="pannello-piede">
        {nascoste > 0 && (
          <span className="nota">
            {nascoste === 1 ? 'e un’altra' : `e altre ${nascoste}`} ·{' '}
            <Link to="/lavori">vedi tutte</Link>
          </span>
        )}
        <label className="nota">
          <input
            type="checkbox"
            checked={mostraCompletate}
            onChange={(evento) => onMostraCompletate(evento.target.checked)}
          />
          Mostra completate
        </label>
      </div>
    </Pannello>
  )
}

/**
 * I quattro contatori, che sono i quattro filtri.
 *
 * Premere quello già attivo lo spegne: è l'unico modo di tornare a «tutte»
 * senza un quinto pulsante che dica «tutte», e `aria-pressed` racconta lo
 * stato a chi non vede lo sfondo acceso.
 */
function Contatori({
  filtro,
  contatori,
  onFiltro,
}: {
  filtro: Filtro
  contatori: ContatoriDashboard
  onFiltro: (filtro: Filtro) => void
}) {
  const quali: { filtro: Filtro; etichetta: string; valore: number; tinta: string }[] = [
    { filtro: 'URGENTI', etichetta: 'Urgenti', valore: contatori.critiche, tinta: 'urgente' },
    { filtro: 'OGGI', etichetta: 'Oggi', valore: contatori.oggi, tinta: 'attesa' },
    {
      filtro: 'SETTIMANA',
      etichetta: 'Settimana',
      valore: contatori.settimana,
      tinta: 'attivo',
    },
    { filtro: 'IN_CORSO', etichetta: 'In corso', valore: contatori.inCorso, tinta: 'in-corso' },
  ]

  return (
    <div className="contatori">
      {quali.map((uno) => (
        <button
          key={uno.filtro}
          type="button"
          className={`contatore contatore-${uno.tinta}${
            filtro === uno.filtro ? ' contatore-scelto' : ''
          }`}
          aria-pressed={filtro === uno.filtro}
          onClick={() => onFiltro(filtro === uno.filtro ? 'TUTTE' : uno.filtro)}
        >
          <span className="contatore-valore">{uno.valore}</span>
          <span className="contatore-etichetta">{uno.etichetta}</span>
        </button>
      ))}
    </div>
  )
}

/** Scadute prima, poi per urgenza, poi per scadenza più vicina. */
function confronta(una: MiaTask, altra: MiaTask): number {
  const unaScaduta = una.giorniAllaScadenza < 0 ? 0 : 1
  const altraScaduta = altra.giorniAllaScadenza < 0 ? 0 : 1
  if (unaScaduta !== altraScaduta) {
    return unaScaduta - altraScaduta
  }

  const pesoUna = una.urgenzaTask === null ? 0 : PESO_URGENZA[una.urgenzaTask]
  const pesoAltra = altra.urgenzaTask === null ? 0 : PESO_URGENZA[altra.urgenzaTask]
  if (pesoUna !== pesoAltra) {
    return pesoAltra - pesoUna
  }

  return una.scadenza.localeCompare(altra.scadenza)
}
