import type { KpiLavoro } from './api'

/**
 * I quattro indicatori di avanzamento, come li manda il server.
 *
 * Si riusano `.cifre` e `.cifra`, le classi con cui la sezione «Monte ore»
 * mostra già i suoi numeri nella stessa pagina: quattro riquadri con una
 * grammatica visiva diversa da quelli accanto sarebbero due linguaggi a dieci
 * centimetri di distanza.
 *
 * **Ogni valore può essere nullo**, e allora si scrive «n/d». Non è pigrizia:
 * un null vuol dire che il denominatore era zero, cioè che non c'era niente da
 * misurare. Un lavoro senza ore allocate non è «in anticipo del 100%», e
 * scrivere uno zero farebbe prendere una decisione su un numero inventato.
 *
 * **Sotto ogni numero c'è la sua base.** «Consegne in tempo 100%» pesa in modo
 * molto diverso su una task o su quaranta, e senza il denominatore chi legge
 * non lo sa.
 */
export default function PannelloKpi({ kpi }: { kpi: KpiLavoro }) {
  return (
    <div className="cifre cifre-kpi">
      <p className="cifra">
        <span className="cifra-etichetta">Scostamento ore</span>
        <span className={classeScostamento(kpi.scostamentoOre)}>
          {segno(kpi.scostamentoOre)}
        </span>
        <span className="nota">
          {ore(kpi.oreConsumate)} su {ore(kpi.oreAllocate)} allocate
        </span>
      </p>

      <p className="cifra">
        <span className="cifra-etichetta">Avanzamento</span>
        <span className="cifra-valore">{percentuale(kpi.avanzamento)}</span>
        <span className="nota">sulle ore · {kpi.taskCompletate}/{kpi.taskConsiderate} task</span>
      </p>

      <p className="cifra">
        <span className="cifra-etichetta">Consegne in tempo</span>
        <span className="cifra-valore">{percentuale(kpi.consegneInTempo)}</span>
        <span className="nota">
          {/* La differenza fra completate e «con data» sono le task chiuse
              prima della V21, che non hanno una data di completamento: dirlo
              evita che un 100% su due task sembri un dato solido. */}
          su {kpi.taskConDataCompletamento} task datate
          {kpi.taskCompletate > kpi.taskConDataCompletamento
            && ` di ${kpi.taskCompletate} completate`}
        </span>
      </p>

      <p className="cifra">
        <span className="cifra-etichetta">Stagnazione</span>
        <span className="cifra-valore">{percentuale(kpi.stagnazione)}</span>
        <span className="nota">
          {kpi.taskFerme} ferme da {kpi.giorniDiStagnazione} giorni su {kpi.taskInCorso} in
          corso
        </span>
      </p>
    </div>
  )
}

/** «42,5%», oppure «n/d» quando non c'era niente da dividere. */
function percentuale(valore: number | null): string {
  if (valore === null) {
    return 'n/d'
  }
  return `${valore.toLocaleString('it-IT', { maximumFractionDigits: 1 })}%`
}

/**
 * Lo scostamento **con il segno**, perché sotto budget è un'informazione
 * buona quanto sopra: «−12%» dice che è andata meglio del previsto, e un
 * valore assoluto lo confonderebbe con uno sforamento.
 */
function segno(valore: number | null): string {
  if (valore === null) {
    return 'n/d'
  }
  const scritto = percentuale(valore)
  return valore > 0 ? `+${scritto}` : scritto
}

/**
 * Lo scostamento è il solo numero di questa fila che sia un segnale e non una
 * misura: sopra budget prende il rosso, sotto il verde, a zero resta
 * inchiostro. Gli altri tre sono contesto e restano neutri.
 */
function classeScostamento(valore: number | null): string {
  if (valore === null || valore === 0) {
    return 'cifra-valore'
  }
  return valore > 0 ? 'cifra-valore kpi-sopra' : 'cifra-valore kpi-sotto'
}

/** Le ore con due decimali solo quando servono: «8» invece di «8,00». */
function ore(valore: number): string {
  return valore.toLocaleString('it-IT', { maximumFractionDigits: 2 })
}
