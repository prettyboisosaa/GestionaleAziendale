import { Link } from 'react-router'
import type { MiaTask, ContatoriDashboard } from './api'
import { scadenzaBreve } from './orario'
import Icona from '../componenti/Icona'
import Pannello from '../componenti/Pannello'
import { Pillola } from '../componenti/Stato'
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

  /**
   * «4 di 5 fatte», con la barra sotto il titolo.
   *
   * Si conta su `task`, cioè su quello che il pannello ha davvero in mano
   * dopo il filtro, e non su tutte le task dell'utente: un avanzamento che
   * parlasse di righe non mostrate sarebbe un numero che non torna con
   * l'elenco sotto, e chi legge crederebbe all'elenco.
   *
   * ⚠️ Con il filtro «completate» spento le chiuse non arrivano fin qui, e la
   * frazione resta 0 di N. È giusto così: dice «di quelle che vedi, nessuna è
   * fatta», che è quello che si vede.
   */
  const fatte = ordinate.filter((una) => !apertaTask(una.stato)).length
  const quota = ordinate.length === 0 ? 0 : fatte / ordinate.length

  return (
    <Pannello
      titolo="Le mie task"
      sottotitolo="per priorità e scadenza"
      azioni={
        <>
          {ordinate.length > 0 && (
            <span className="nota">
              {fatte} di {ordinate.length} fatte
            </span>
          )}
          <Link to="/lavori">Vedi tutte</Link>
        </>
      }
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
      {/* La barra di avanzamento sotto il titolo, in acqua: è il primo
          elemento del pannello nel design system, e dice in un colpo d'occhio
          quello che la frazione dice in cifre. Larghezza dal rapporto grezzo,
          non dalla percentuale arrotondata. */}
      {ordinate.length > 0 && (
        <div
          className="avanzamento-task"
          role="img"
          aria-label={`${fatte} task fatte su ${ordinate.length}`}
        >
          <span style={{ width: `${quota * 100}%` }} />
        </div>
      )}

      {contatori !== null && (
        <Contatori filtro={filtro} contatori={contatori} onFiltro={onFiltro} />
      )}

      <ul className="righe-task">
        {visibili.map((una) => (
          <li
            key={una.taskId}
            className={
              apertaTask(una.stato) ? 'riga-task' : 'riga-task riga-task-fatta'
            }
          >
            <div className="riga-task-capo">
              <Link to={`/lavori/${una.lavoroId}`} className="riga-task-titolo">
                {una.titolo}
              </Link>

              {/* La pillola dell'urgenza a destra, come nel design system: al
                  posto del punto colorato, che diceva la stessa cosa senza
                  scriverla. Dove l'urgenza manca non si disegna niente — una
                  pillola grigia direbbe «bassa», che nessuno ha deciso. */}
              {una.urgenzaTask !== null && (
                <Pillola tinta={TINTA_URGENZA[una.urgenzaTask]}>
                  {ETICHETTA_URGENZA[una.urgenzaTask]}
                </Pillola>
              )}
            </div>

            {/* Scadenza e lavoro su una riga sola, tenue: nel mock sotto il
                titolo c'è **una** riga di contesto, non due informazioni che
                si contendono l'attenzione con il titolo stesso. */}
            <div className="riga-task-sotto">
              <span
                className={
                  una.giorniAllaScadenza < 0
                    ? 'riga-task-scadenza in-ritardo'
                    : 'riga-task-scadenza'
                }
              >
                {scadenzaBreve(una.scadenza, una.giorniAllaScadenza)}
              </span>
              <span className="nota">
                {una.codiceLavoro} · {una.nomeLavoro}
              </span>
            </div>

            <div className="riga-task-coda">

              {/* Due simboli al posto di due parole.
                  «Avvia», «Pausa» e «Completa» erano tre etichette di testo su
                  ogni riga di un elenco di otto: moltiplicate per otto
                  diventavano ventiquattro parole da saltare per leggere i
                  titoli, che sono la ragione per cui l'elenco esiste. Il
                  triangolo del player e la casella da spuntare dicono la
                  stessa cosa in due simboli che si riconoscono senza
                  leggerli — e sono i due gesti che si fanno cento volte al
                  giorno, quindi è quello che serve.

                  Il nome resta, in `aria-label` e in `title`: è quello che
                  legge chi usa un assistente vocale, e quello che compare a
                  chi si ferma col mouse. Un'icona senza nome è un indovinello
                  per tutti e un muro per qualcuno. */}
              {puoAggiornare && apertaTask(una.stato) && (
                <span className="riga-task-azioni">
                  {una.inCorso ? (
                    <button
                      type="button"
                      className="comando comando-player comando-in-corso"
                      disabled={inAggiornamento === una.taskId}
                      onClick={() => onPausa(una)}
                      aria-label={`Metti in pausa ${una.titolo}`}
                      title="Pausa"
                    >
                      <Icona nome="pausa" />
                    </button>
                  ) : (
                    <button
                      type="button"
                      className="comando comando-player"
                      disabled={inAggiornamento === una.taskId}
                      onClick={() => onAvvia(una)}
                      aria-label={`Avvia il timer su ${una.titolo}`}
                      title="Avvia"
                    >
                      <Icona nome="avvia" />
                    </button>
                  )}

                  {/* La casella da spuntare e non un secondo pulsante tondo:
                      completare una task è il gesto della lista di cose da
                      fare, e la casella è il modo in cui quel gesto si
                      scrive. Non è un `input` vero perché il server può
                      rifiutare, e una casella che torna indietro da sola
                      sarebbe peggio di un pulsante che non ha funzionato. */}
                  <button
                    type="button"
                    className="comando comando-spunta"
                    disabled={inAggiornamento === una.taskId}
                    onClick={() => onCompleta(una)}
                    aria-label={`Completa ${una.titolo}`}
                    title="Completa"
                  >
                    <Icona nome="spunta" />
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
