import type { KpiLavoro, RigaLavoro } from './api'

/**
 * I cinque riquadri in cima alla pagina di un lavoro: a che punto è, quante
 * ore ha speso, quante gliene restano, che cosa è in ritardo, quando scade.
 *
 * **Non sono i quattro KPI di prima.** Quelli — scostamento, avanzamento,
 * consegne in tempo, stagnazione — erano misure di qualità del lavoro, giuste
 * per chi lo analizza a posteriori, e rispondevano tutte in percentuale. Chi
 * apre la pagina di un lavoro aperto fa invece cinque domande concrete, e le
 * fa in quest'ordine: le percentuali che servono sono due, e le altre tre
 * risposte sono ore, conteggi e una data.
 *
 * I numeri di qualità non sono spariti: restano nella sezione «Avanzamento»,
 * che è dove si va quando la domanda è come è andata, non come va.
 *
 * **Due riquadri sono pieni, gli altri tre no.** Il pieno è un accento, e in
 * Fluida gli accenti non coprono più di un decimo dello schermo: il verde
 * profondo sta sull'avanzamento, che è la risposta alla domanda principale, e
 * il corallo compare **solo** quando le ore rimaste sono poche davvero. Un
 * corallo che ci fosse sempre non direbbe più niente.
 */
export default function PannelloKpi({
  lavoro,
  kpi,
}: {
  lavoro: RigaLavoro
  /**
   * Gli indicatori del server. Possono mancare — hanno una rotta loro, che
   * può fallire — e i riquadri si riempiono comunque: l'avanzamento sulle
   * task lo si ricava dai conteggi che arrivano già con il lavoro.
   */
  kpi: KpiLavoro | null
}) {
  const totali = lavoro.taskTotali
  const quota = totali === 0 ? null : Math.round((lavoro.taskCompletate / totali) * 100)
  const rimaste = lavoro.monteOre === null ? null : lavoro.monteOre - lavoro.oreConsumate
  const aperte = totali - lavoro.taskCompletate
  const giorni = giorniAllaScadenza(lavoro.dataFinePrevista)

  // Poche ore: sotto un decimo del monte ore, o sotto zero. È la stessa soglia
  // delle schede dell'elenco, perché è lo stesso giudizio.
  const poche =
    rimaste !== null
    && lavoro.monteOre !== null
    && lavoro.monteOre > 0
    && rimaste <= lavoro.monteOre / 10

  return (
    <div className="riquadri-kpi">
      <Riquadro
        etichetta="Avanzamento"
        valore={quota === null ? 'n/d' : `${quota}%`}
        base={
          totali === 0
            ? 'nessuna task'
            : `${lavoro.taskCompletate} di ${totali} task completate`
        }
        tono="pieno"
      />

      <Riquadro
        etichetta="Ore usate"
        valore={`${ore(lavoro.oreConsumate)} h`}
        base={
          lavoro.monteOre === null
            ? 'monte ore non fissato'
            : `su ${ore(lavoro.monteOre)} h preventivate · ${percentuale(
                lavoro.oreConsumate,
                lavoro.monteOre,
              )}`
        }
      />

      <Riquadro
        etichetta="Ore rimaste"
        valore={rimaste === null ? '—' : `${ore(rimaste)} h`}
        base={
          lavoro.monteOre === null
            ? 'serve un monte ore'
            : `${percentuale(rimaste ?? 0, lavoro.monteOre)} del monte ore`
        }
        tono={poche ? 'allarme' : undefined}
      />

      <Riquadro
        etichetta="In ritardo"
        valore={String(lavoro.taskInRitardo)}
        base={aperte === 1 ? 'su 1 task aperta' : `su ${aperte} task aperte`}
        /* Il numero si accende solo se c'è qualcosa in ritardo: uno zero in
           rosso sarebbe un allarme per una buona notizia. */
        acceso={lavoro.taskInRitardo > 0}
      />

      <Riquadro
        etichetta="Scadenza"
        valore={scadenza(lavoro.dataFinePrevista)}
        base={frase(giorni)}
        acceso={giorni < 0}
      />

      {/* Le consegne in tempo e la stagnazione restano disponibili per chi le
          cerca, ma sotto i cinque riquadri e in una riga sola: sono giudizi
          sul passato del lavoro, e non competono con i numeri di oggi. */}
      {kpi !== null && (
        <p className="nota nota-kpi">
          Consegne in tempo {quotaKpi(kpi.consegneInTempo)} su {kpi.taskConDataCompletamento}{' '}
          task datate · {kpi.taskFerme} task ferme da {kpi.giorniDiStagnazione} giorni su{' '}
          {kpi.taskInCorso} in corso
        </p>
      )}
    </div>
  )
}

/**
 * Un riquadro: etichetta piccola sopra, il numero grande, la sua base sotto.
 *
 * **Sotto ogni numero c'è la sua base**, e non è riempitivo: «33%» pesa in
 * modo molto diverso su tre task o su quaranta, e senza il denominatore chi
 * legge non lo sa.
 */
function Riquadro({
  etichetta,
  valore,
  base,
  tono,
  acceso,
}: {
  etichetta: string
  valore: string
  base: string
  /** `pieno` per il riquadro principale, `allarme` per quello che chiede di agire. */
  tono?: 'pieno' | 'allarme'
  /** Solo il numero in corallo, senza riempire il riquadro. */
  acceso?: boolean
}) {
  const classe = tono === undefined ? 'riquadro-kpi' : `riquadro-kpi riquadro-${tono}`
  return (
    <div className={classe}>
      <span className="riquadro-etichetta">{etichetta}</span>
      <span className={acceso === true ? 'riquadro-valore riquadro-acceso' : 'riquadro-valore'}>
        {valore}
      </span>
      <span className="riquadro-base">{base}</span>
    </div>
  )
}

/**
 * L'avviso in cima alla pagina: una frase sola, scritta solo quando c'è
 * qualcosa da dire.
 *
 * Mette a confronto le tre percentuali che non si possono confrontare a
 * occhio — quanto tempo è passato, quante ore sono state spese, quante task
 * sono chiuse — perché il guaio di un lavoro è quasi sempre nella loro
 * distanza, non in nessuna delle tre presa da sola. Un lavoro al 96% delle ore
 * e al 33% delle task è in difficoltà, e nessuno dei due numeri lo dice.
 *
 * **Niente avviso quando non c'è niente di storto.** Una fascia colorata
 * permanente si smette di leggere dopo due giorni, e allora non serve più il
 * giorno in cui conta.
 */
export function AvvisoLavoro({ lavoro }: { lavoro: RigaLavoro }) {
  const totali = lavoro.taskTotali
  if (totali === 0 || lavoro.monteOre === null || lavoro.monteOre <= 0) {
    return null
  }

  const task = (lavoro.taskCompletate / totali) * 100
  const spese = (lavoro.oreConsumate / lavoro.monteOre) * 100
  const tempo = quotaTempo(lavoro.dataInizio, lavoro.dataFinePrevista)

  // Venti punti: meno sarebbe un allarme a ogni lavoro che va un po' a
  // strappi — e tutti vanno a strappi — più vorrebbe dire accorgersene
  // quando non c'è più margine per rimediare.
  const soglia = 20
  const avanti = Math.max(spese, tempo)
  if (avanti - task < soglia) {
    return null
  }

  return (
    <p className="avviso avviso-lavoro" role="status">
      Il tempo è al {Math.round(tempo)}% e le ore al {Math.round(spese)}%, ma le task sono
      solo al {Math.round(task)}%. Il lavoro sta consumando più del previsto: rivedi il
      piano prima della scadenza.
    </p>
  )
}

/** Quanta parte del tempo preventivato è passata, da 0 a 100 (e oltre). */
function quotaTempo(inizio: string, fine: string): number {
  const partenza = Date.parse(inizio)
  const arrivo = Date.parse(fine)
  if (arrivo <= partenza) {
    return 100
  }
  const passato = Date.now() - partenza
  return Math.min(100, Math.max(0, (passato / (arrivo - partenza)) * 100))
}

/**
 * Quanti giorni mancano alla scadenza. Negativo se è passata.
 *
 * Si confrontano **date e non istanti**: una scadenza oggi alle 23 non è
 * «fra zero giorni e tre ore», è oggi.
 */
function giorniAllaScadenza(iso: string): number {
  const giorno = 24 * 60 * 60 * 1000
  const oggi = new Date()
  const zero = Date.UTC(oggi.getFullYear(), oggi.getMonth(), oggi.getDate())
  const scade = Date.parse(`${iso}T00:00:00Z`)
  return Math.round((scade - zero) / giorno)
}

function frase(giorni: number): string {
  if (giorni < 0) {
    return giorni === -1 ? 'scaduto ieri' : `scaduto da ${-giorni} giorni`
  }
  if (giorni === 0) {
    return 'scade oggi'
  }
  return giorni === 1 ? 'domani' : `tra ${giorni} giorni`
}

/** Da «2026-11-20» a «20 nov». */
function scadenza(iso: string): string {
  return new Date(iso).toLocaleDateString('it-IT', { day: 'numeric', month: 'short' })
}

/** Le ore con un decimale solo quando serve: «288», «7,5». */
function ore(valore: number): string {
  return valore.toLocaleString('it-IT', { maximumFractionDigits: 1 })
}

/** Una quota di un totale, arrotondata: «96%». */
function percentuale(parte: number, totale: number): string {
  if (totale <= 0) {
    return 'n/d'
  }
  return `${Math.round((parte / totale) * 100)}%`
}

/** Una percentuale del server, che può non esserci. */
function quotaKpi(valore: number | null): string {
  if (valore === null) {
    return 'n/d'
  }
  return `${valore.toLocaleString('it-IT', { maximumFractionDigits: 1 })}%`
}
