import { oreMinuti, percentuale } from './api'

/** Un pezzo della barra: una cosa su cui è andato del tempo. */
export type Segmento = {
  chiave: string
  etichetta: string
  minuti: number
  /** La classe della tinta: `lavoro-0…7`, oppure niente per il tono neutro. */
  tinta?: string
}

/**
 * La ripartizione di un tempo come una barra a segmenti.
 *
 * È la risposta visiva alla domanda del report: una colonna di percentuali si
 * legge un numero per volta, mentre «metà di questo mese è andata su un solo
 * progetto» si vede. I numeri restano scritti sotto, nelle righe: la barra
 * serve al colpo d'occhio, non a sostituirli.
 *
 * **Niente libreria di grafici**, come in `BarraTemporale`: una barra è un div
 * con una larghezza in percentuale, e importare un motore di grafici per
 * disegnarla costerebbe più di quanto risolve. Qui non c'è nemmeno un asse.
 *
 * Le larghezze vengono dal rapporto grezzo e non dalla percentuale
 * arrotondata, così i segmenti riempiono **esattamente** il totale: con
 * percentuali a un decimale la barra chiuderebbe a 99,8% e lascerebbe una
 * fessura che sembra un errore di dati.
 */
export default function BarraRipartizione({
  segmenti,
  totale,
}: {
  segmenti: Segmento[]
  totale: number
}) {
  if (totale <= 0 || segmenti.length === 0) {
    return null
  }

  return (
    <div className="barra-ripartizione" role="presentation">
      {segmenti.map((segmento) => (
        <span
          key={segmento.chiave}
          className={
            segmento.tinta === undefined
              ? 'segmento-ripartizione segmento-neutro'
              : `segmento-ripartizione ${segmento.tinta}`
          }
          style={{ width: `${(segmento.minuti / totale) * 100}%` }}
          // Il titolo nativo e non un tooltip costruito: su un segmento
          // sottile serve solo a dire che cos'è, e questa informazione sta
          // comunque scritta nella riga sotto la barra.
          title={`${segmento.etichetta} · ${oreMinuti(segmento.minuti)} · ${percentuale(
            segmento.minuti,
            totale,
          )}`}
        />
      ))}
    </div>
  )
}
