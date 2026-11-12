import type { AndamentoLavoro, SettimanaLavoro } from './api'

/**
 * I due grafici dell'andamento di un lavoro: le ore nel tempo e il ritmo di
 * chiusura delle task.
 *
 * **Niente libreria di grafici**, come in `BarraRipartizione` e nel calendario
 * delle assenze: qui ci sono una spezzata e una fila di rettangoli, e una
 * dipendenza da trecento kilobyte per disegnarli porterebbe con sé la sua
 * tavolozza, i suoi tipi di carattere e i suoi tooltip — cioè un secondo
 * design system dentro il primo. L'SVG a mano usa i token di Fluida e basta.
 *
 * **Due grafici e non uno con due assi.** Ore e numero di task sono due
 * grandezze diverse, e sovrapporle su due scale verticali farebbe incrociare
 * due linee in un punto che non significa niente: un asse per grafico è la
 * regola, e due domande diverse sono due riquadri.
 *
 * Le coordinate si calcolano su una tela fissa e l'SVG si ridimensiona con il
 * suo contenitore: così le etichette restano proporzionate a qualunque
 * larghezza, senza bisogno di misurare il DOM a ogni render.
 */

/** La tela su cui si disegna: unità SVG, non pixel sullo schermo. */
const LARGHEZZA = 720
const ALTEZZA = 260

/** Lo spazio per le etichette degli assi, attorno all'area dei dati. */
const MARGINE = { sopra: 24, destra: 16, sotto: 32, sinistra: 48 }

const AREA = {
  x: MARGINE.sinistra,
  y: MARGINE.sopra,
  larghezza: LARGHEZZA - MARGINE.sinistra - MARGINE.destra,
  altezza: ALTEZZA - MARGINE.sopra - MARGINE.sotto,
}

/**
 * Quante etichette al massimo sull'asse del tempo.
 *
 * Un lavoro lungo un anno ha cinquantadue settimane, e cinquantadue date in
 * fondo al grafico diventano una striscia nera. Si scrivono una ogni n, con n
 * scelto perché non se ne superino mai otto — l'ultima è sempre fra quelle,
 * perché «dove siamo arrivati» è la domanda che si fa guardando a destra.
 */
const ETICHETTE_MASSIME = 8

/** Da «2026-11-09» a «9 nov». */
function breve(iso: string): string {
  return new Date(iso).toLocaleDateString('it-IT', { day: 'numeric', month: 'short' })
}

/** Le ore senza decimali inutili. */
function ore(valore: number): string {
  return valore.toLocaleString('it-IT', { maximumFractionDigits: 1 })
}

/**
 * Il totale progressivo delle ore, settimana per settimana.
 *
 * Il server manda le ore di **ciascuna** settimana, non il cumulato: il
 * progressivo è una somma che il grafico rifà comunque per collocare i punti,
 * e farsela mandare vorrebbe dire due definizioni dello stesso numero.
 */
function cumulate(settimane: SettimanaLavoro[]): number[] {
  let totale = 0
  return settimane.map((settimana) => {
    totale += settimana.ore
    return totale
  })
}

/** Ogni quante settimane si scrive una data in fondo all'asse. */
function passoEtichette(quante: number): number {
  return Math.max(1, Math.ceil(quante / ETICHETTE_MASSIME))
}

/**
 * L'andamento delle ore: quanto si è consumato, rispetto a quanto il piano
 * prevedeva di consumare a quel punto.
 *
 * **La linea del piano è il senso del grafico.** La sola curva delle ore dice
 * che sono state spese, non che siano troppe: è il confronto con la
 * diagonale — il monte ore distribuito uniformemente fra l'inizio e la
 * scadenza — che fa vedere, a metà lavoro, se si sta andando più in fretta di
 * quanto il budget regga. Senza monte ore quella diagonale non esiste, e il
 * grafico lo dice invece di disegnare una retta inventata.
 */
export function GraficoOre({ andamento }: { andamento: AndamentoLavoro }) {
  const settimane = andamento.settimane
  if (settimane.length === 0) {
    return <p className="sommario">Nessuna ora registrata su questo lavoro.</p>
  }

  const totali = cumulate(settimane)
  const consumate = totali[totali.length - 1]

  // Il soffitto: il più alto fra il monte ore e le ore davvero consumate. Il
  // secondo caso è quello che conta — un lavoro che ha sforato deve vedere la
  // propria linea uscire sopra il piano, non appiattirsi contro il bordo.
  const tetto = Math.max(andamento.monteOre ?? 0, consumate, 1)

  const passo = settimane.length === 1 ? 0 : AREA.larghezza / (settimane.length - 1)
  const ascissa = (indice: number) =>
    settimane.length === 1 ? AREA.x + AREA.larghezza / 2 : AREA.x + indice * passo
  const ordinata = (valore: number) =>
    AREA.y + AREA.altezza - (valore / tetto) * AREA.altezza

  const punti = totali.map((valore, indice) => ({
    x: ascissa(indice),
    y: ordinata(valore),
    valore,
    settimana: settimane[indice],
  }))

  const spezzata = punti.map((punto) => `${punto.x},${punto.y}`).join(' ')
  // L'area sotto la linea si chiude sulla base: è un velo, non una seconda
  // serie, e serve solo a dare peso alla curva contro la griglia.
  const velo =
    `${punti[0].x},${AREA.y + AREA.altezza} `
    + spezzata
    + ` ${punti[punti.length - 1].x},${AREA.y + AREA.altezza}`

  // La diagonale del piano: zero all'inizio, il monte ore alla scadenza. Si
  // colloca sul tempo vero e non sulle settimane disegnate, perché la
  // scadenza può cadere prima o dopo l'ultima settimana della serie.
  const piano = andamento.monteOre === null ? null : lineaPiano(andamento, ascissa, ordinata)

  const salto = passoEtichette(settimane.length)
  const griglia = [0, 0.5, 1]

  return (
    <figure className="grafico">
      <figcaption className="grafico-testa">
        <span className="grafico-titolo">Andamento ore</span>
        <span className="nota">Ore usate nel tempo rispetto al monte ore</span>
      </figcaption>

      {/* Due serie: la legenda c'è sempre, e il tratteggio del piano è la
          seconda codifica — si distingue dalla linea piena anche stampato in
          bianco e nero o da chi non separa le due tinte. */}
      <div className="grafico-legenda">
        <span className="voce-legenda">
          <svg width="22" height="8" aria-hidden="true">
            <line x1="1" y1="4" x2="21" y2="4" className="tratto-usate" />
          </svg>
          Usate
        </span>
        {piano !== null && (
          <span className="voce-legenda">
            <svg width="22" height="8" aria-hidden="true">
              <line x1="1" y1="4" x2="21" y2="4" className="tratto-piano" />
            </svg>
            Piano
          </span>
        )}
      </div>

      <svg
        className="grafico-tela"
        viewBox={`0 0 ${LARGHEZZA} ${ALTEZZA}`}
        role="img"
        aria-label={
          `Ore usate settimana per settimana: ${ore(consumate)} ore in totale`
          + (andamento.monteOre === null
            ? ', monte ore non fissato.'
            : ` su un monte ore di ${ore(andamento.monteOre)}.`)
        }
      >
        {/* La griglia è recessiva: tre linee e non una per settimana. Serve a
            misurare l'altezza di un punto, non a dividere il piano. */}
        {griglia.map((quota) => {
          const y = ordinata(tetto * quota)
          return (
            <g key={quota}>
              <line
                x1={AREA.x}
                y1={y}
                x2={AREA.x + AREA.larghezza}
                y2={y}
                className="grafico-griglia"
              />
              <text x={AREA.x - 8} y={y + 4} className="grafico-tacca grafico-tacca-y">
                {ore(tetto * quota)} h
              </text>
            </g>
          )
        })}

        {piano !== null && (
          <line
            x1={piano.x1}
            y1={piano.y1}
            x2={piano.x2}
            y2={piano.y2}
            className="tratto-piano"
          />
        )}

        <polygon points={velo} className="velo-usate" />
        <polyline points={spezzata} className="tratto-usate" fill="none" />

        {punti.map((punto) => (
          <circle
            key={punto.settimana.inizio}
            cx={punto.x}
            cy={punto.y}
            r="4"
            className="punto-usate"
          >
            {/* Il titolo nativo è il livello di lettura al passaggio del
                mouse: niente stato, niente posizionamento a mano, e lo
                leggono anche le tecnologie assistive. */}
            <title>
              {`Settimana del ${breve(punto.settimana.inizio)}: ${ore(punto.valore)} h in tutto`
                + ` (${ore(punto.settimana.ore)} h in settimana)`}
            </title>
          </circle>
        ))}

        {settimane.map((settimana, indice) =>
          indice % salto === 0 || indice === settimane.length - 1 ? (
            <text
              key={settimana.inizio}
              x={ascissa(indice)}
              y={ALTEZZA - 10}
              className="grafico-tacca"
              textAnchor="middle"
            >
              {breve(settimana.inizio)}
            </text>
          ) : null,
        )}
      </svg>
    </figure>
  )
}

/**
 * Gli estremi della diagonale del piano, in coordinate della tela.
 *
 * Si costruisce sul **tempo reale** e non sugli indici delle settimane: la
 * scadenza del lavoro cade quasi sempre in mezzo a una settimana, e spesso
 * fuori dall'intervallo disegnato — prima, se il lavoro ha sforato la data;
 * dopo, se è ancora in corso. Collocarla sull'ultima settimana disegnata
 * inclinerebbe la retta di quanto basta a far sembrare in regola un lavoro che
 * non lo è.
 */
function lineaPiano(
  andamento: AndamentoLavoro,
  ascissa: (indice: number) => number,
  ordinata: (valore: number) => number,
) {
  const settimane = andamento.settimane
  const prima = Date.parse(settimane[0].inizio)
  const ultima = Date.parse(settimane[settimane.length - 1].inizio)
  const inizio = Date.parse(andamento.dataInizio)
  const fine = Date.parse(andamento.dataFinePrevista)

  // Quanti indici di settimana «vale» un istante, anche frazionario e anche
  // fuori dall'intervallo: è quello che permette alla retta di uscire dai
  // bordi della tela invece di piegarsi per restarci dentro.
  const indice = (istante: number) =>
    ultima === prima ? 0 : ((istante - prima) / (ultima - prima)) * (settimane.length - 1)

  return {
    x1: ascissa(indice(inizio)),
    y1: ordinata(0),
    x2: ascissa(indice(fine)),
    y2: ordinata(andamento.monteOre ?? 0),
  }
}

/**
 * Le task chiuse settimana per settimana: il ritmo con cui il lavoro avanza.
 *
 * Una barra per settimana, comprese quelle a zero. Le settimane vuote sono
 * l'informazione: tre zeri di fila dicono che il lavoro si è fermato, e una
 * serie che saltasse le settimane senza chiusure li nasconderebbe facendo
 * sembrare continuo un avanzamento a strappi.
 *
 * I numeri sono scritti sopra ogni barra invece che su un asse verticale: sono
 * conteggi piccoli — zero, uno, due — e una scala graduata per distinguere due
 * da tre è più ingombro che lettura.
 */
export function GraficoTask({ andamento }: { andamento: AndamentoLavoro }) {
  const settimane = andamento.settimane
  if (settimane.length === 0) {
    return <p className="sommario">Nessuna task chiusa su questo lavoro.</p>
  }

  const massimo = Math.max(1, ...settimane.map((settimana) => settimana.taskCompletate))
  const totale = settimane.reduce((somma, settimana) => somma + settimana.taskCompletate, 0)

  // Un ventesimo di passo fra una barra e l'altra per parte: le barre si
  // staccano senza che la fila diventi una pettinatura di stecchini.
  const passo = AREA.larghezza / settimane.length
  const larghezzaBarra = Math.max(6, passo * 0.56)

  // La base di una barra a zero: un trattino, non il nulla. Dice «questa
  // settimana è passata e non si è chiuso niente», che è diverso da «questa
  // settimana non c'è».
  const minima = 4

  const salto = passoEtichette(settimane.length)

  return (
    <figure className="grafico">
      <figcaption className="grafico-testa">
        <span className="grafico-titolo">Task completate a settimana</span>
        <span className="nota">Ritmo di chiusura delle task</span>
      </figcaption>

      <svg
        className="grafico-tela"
        viewBox={`0 0 ${LARGHEZZA} ${ALTEZZA}`}
        role="img"
        aria-label={`${totale} task chiuse in ${settimane.length} settimane.`}
      >
        {settimane.map((settimana, indice) => {
          const centro = AREA.x + passo * (indice + 0.5)
          const piena = (settimana.taskCompletate / massimo) * (AREA.altezza - 24)
          const altezza = Math.max(minima, piena)
          const y = AREA.y + AREA.altezza - altezza

          return (
            <g key={settimana.inizio}>
              <rect
                x={centro - larghezzaBarra / 2}
                y={y}
                width={larghezzaBarra}
                height={altezza}
                rx="4"
                className={
                  settimana.taskCompletate === 0 ? 'barra-grafico barra-vuota' : 'barra-grafico'
                }
              >
                <title>
                  {`Settimana del ${breve(settimana.inizio)}: `
                    + (settimana.taskCompletate === 1
                      ? '1 task chiusa'
                      : `${settimana.taskCompletate} task chiuse`)}
                </title>
              </rect>
              <text
                x={centro}
                y={y - 8}
                className="grafico-valore"
                textAnchor="middle"
              >
                {settimana.taskCompletate}
              </text>
              {(indice % salto === 0 || indice === settimane.length - 1) && (
                <text
                  x={centro}
                  y={ALTEZZA - 10}
                  className="grafico-tacca"
                  textAnchor="middle"
                >
                  {breve(settimana.inizio)}
                </text>
              )}
            </g>
          )
        })}
      </svg>
    </figure>
  )
}
