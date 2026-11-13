import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import Icona from './Icona'
import type { NomeIcona } from './Icona'
import { movimentoRidotto } from './movimento'

/** Una riga di dettaglio: l'icona a sinistra, il testo accanto. */
export type RigaAnteprima = { icona: NomeIcona; testo: string }

/**
 * L'anteprima di un evento: la scheda che compare accanto a ciò che si è
 * cliccato.
 *
 * È il terzo riquadro della tavola 05. Nasce da un'osservazione sul
 * calendario settimanale: fino a ieri un clic su un blocco apriva **il
 * pannello di correzione**, cioè un modulo con i campi editabili. Ma un clic
 * su un blocco quasi sempre non vuol dire «voglio cambiarlo»: vuol dire «che
 * cos'è questo?». Si apriva un modulo per rispondere a una domanda, e per
 * uscirne bisognava annullare una modifica che non si stava facendo.
 *
 * <p>Qui in mezzo c'è un passo che risponde e basta — che cos'è, quando, per
 * quanto — e che offre la modifica come **una** delle azioni possibili invece
 * che come l'unica.
 *
 * <p><strong>È un popover e non una finestra modale</strong>, e la differenza
 * si vede: compare ancorata al blocco che si è cliccato, così la settimana
 * attorno resta leggibile e si capisce di quale blocco si sta parlando. Una
 * modale centrata coprirebbe proprio il contesto che serve a interpretarlo.
 */
export default function AnteprimaEvento({
  ancora,
  stato,
  titolo,
  righe,
  piede,
  onModifica,
  onElimina,
  etichettaElimina = 'Elimina',
  onChiudi,
}: {
  /**
   * Il rettangolo dell'elemento da cui nasce, in coordinate di finestra
   * (`getBoundingClientRect`). Si posiziona accanto a quello.
   */
  ancora: DOMRect
  /** Il pallino con l'etichetta in alto: «Evento», «Lavoro», «Assenza». */
  stato?: { testo: string; colore: string }
  titolo: string
  righe: RigaAnteprima[]
  /** Quello che va in fondo: le persone, un totale, una nota. */
  piede?: ReactNode
  onModifica?: () => void
  onElimina?: () => void
  etichettaElimina?: string
  onChiudi: () => void
}) {
  const scheda = useRef<HTMLDivElement>(null)
  const [posizione, setPosizione] = useState<{ sinistra: number; alto: number } | null>(null)
  const [chiudendo, setChiudendo] = useState(false)

  function chiudi() {
    if (chiudendo) return
    if (movimentoRidotto()) {
      onChiudi()
      return
    }
    setChiudendo(true)
  }

  /**
   * Dove mettersi.
   *
   * `useLayoutEffect` e non `useEffect`: la misura serve **prima** che il
   * browser dipinga, altrimenti la scheda compare per un fotogramma
   * nell'angolo in alto a sinistra e poi salta al suo posto. È uno sfarfallio
   * breve, e si nota lo stesso.
   *
   * Si prova a destra del blocco; se non ci sta, a sinistra; se non ci sta da
   * nessuna delle due, sopra o sotto. In ogni caso si resta dentro la
   * finestra con un margine, perché una scheda tagliata a metà dal bordo
   * nasconde proprio le azioni, che stanno in alto a destra.
   */
  useLayoutEffect(() => {
    const elemento = scheda.current
    if (elemento === null) return

    const mia = elemento.getBoundingClientRect()
    const margine = 12
    const larghezza = window.innerWidth
    const altezza = window.innerHeight

    let sinistra = ancora.right + margine
    if (sinistra + mia.width > larghezza - margine) {
      sinistra = ancora.left - mia.width - margine
    }
    if (sinistra < margine) {
      // Né a destra né a sinistra: la si centra sul blocco e si lascia che sia
      // la riga sotto a tenerla dentro lo schermo.
      sinistra = ancora.left + ancora.width / 2 - mia.width / 2
    }
    sinistra = Math.min(Math.max(margine, sinistra), larghezza - mia.width - margine)

    let alto = ancora.top + ancora.height / 2 - mia.height / 2
    alto = Math.min(Math.max(margine, alto), altezza - mia.height - margine)

    setPosizione({ sinistra, alto })
  }, [ancora])

  // Esc chiude, come in ogni altra finestra del gestionale.
  useEffect(() => {
    function tasto(evento: KeyboardEvent) {
      if (evento.key === 'Escape') {
        evento.preventDefault()
        chiudi()
      }
    }
    document.addEventListener('keydown', tasto)
    return () => document.removeEventListener('keydown', tasto)
  })

  useEffect(() => {
    function fuori(evento: MouseEvent) {
      if (!scheda.current?.contains(evento.target as Node)) chiudi()
    }
    document.addEventListener('mousedown', fuori, true)
    return () => document.removeEventListener('mousedown', fuori, true)
  })

  return (
    <div
      ref={scheda}
      className={`anteprima${chiudendo ? ' anteprima-esce' : ''}`}
      role="dialog"
      aria-label={titolo}
      style={{
        left: posizione?.sinistra ?? 0,
        top: posizione?.alto ?? 0,
        // Finché non si è misurata, è trasparente invece che altrove: così non
        // c'è nessun fotogramma in cui si vede nel posto sbagliato.
        visibility: posizione === null ? 'hidden' : 'visible',
      }}
      onAnimationEnd={(evento) => {
        if (evento.target !== evento.currentTarget) return
        if (chiudendo) onChiudi()
      }}
    >
      <div className="anteprima-testata">
        {stato !== undefined && (
          <span className="anteprima-stato">
            <span
              className="anteprima-pallino"
              style={{ backgroundColor: stato.colore }}
              aria-hidden="true"
            />
            {stato.testo}
          </span>
        )}

        <div className="anteprima-azioni">
          {onModifica !== undefined && (
            <button type="button" aria-label="Modifica" onClick={onModifica}>
              <Icona nome="matita" />
            </button>
          )}
          {onElimina !== undefined && (
            <button
              type="button"
              className="anteprima-distruttiva"
              aria-label={etichettaElimina}
              onClick={onElimina}
            >
              <Icona nome="cestino" />
            </button>
          )}
          <button type="button" aria-label="Chiudi" autoFocus onClick={chiudi}>
            <Icona nome="chiudi" />
          </button>
        </div>
      </div>

      <h3 className="anteprima-titolo">{titolo}</h3>

      <ul className="anteprima-righe">
        {righe.map((riga, indice) => (
          <li key={indice}>
            <span className="anteprima-segno" aria-hidden="true">
              <Icona nome={riga.icona} />
            </span>
            {riga.testo}
          </li>
        ))}
      </ul>

      {piede !== undefined && <div className="anteprima-piede">{piede}</div>}
    </div>
  )
}
