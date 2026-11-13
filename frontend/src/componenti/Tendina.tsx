import { useEffect, useId, useRef, useState } from 'react'
import Icona from './Icona'

/**
 * Una voce della tendina.
 *
 * `descrizione` e `colore` sono facoltativi ed è il punto: sono loro a
 * giustificare questo componente. Dove le voci sono solo nomi — un elenco di
 * persone, un anno — il `<select>` nativo resta la scelta giusta e questa
 * tendina non va usata.
 */
export type VoceTendina<T extends string> = {
  valore: T
  nome: string
  /** La riga sotto il nome: che cos'è questa voce. */
  descrizione?: string
  /** Un colore della tavolozza, come pastiglia a sinistra. */
  colore?: string
  disponibile?: boolean
}

/**
 * La tendina di selezione della tavola 05.
 *
 * <p><strong>Quando usarla al posto di un `<select>` nativo.</strong> Il
 * nativo è migliore quasi sempre: lo disegna il sistema operativo, funziona
 * con la tastiera senza che nessuno ci pensi, e su un telefono apre la ruota a
 * cui la gente è abituata. Si sostituisce solo quando la scelta ha bisogno di
 * **mostrare qualcosa che il nativo non sa mostrare**, e nel gestionale è una
 * cosa sola: il colore e la riga di spiegazione accanto alla voce.
 *
 * <p>Il tipo di un'attività è l'esempio: «Riunione», «Sopralluogo»,
 * «Formazione» hanno ciascuno la tinta con cui compariranno nel calendario
 * settimanale, e sceglierli da un elenco di parole significa scoprire il
 * colore dopo. Lo stesso vale per l'urgenza di una task, dove la differenza
 * fra «alta» e «urgente» la dice il colore prima della parola.
 *
 * <p>La tastiera è riscritta a mano perché un `<div>` non la porta con sé:
 * frecce per muoversi, Invio e Spazio per scegliere, Esc per chiudere, Home e
 * Fine per i due estremi. È il prezzo di non usare il nativo, ed è la ragione
 * per cui non lo si sostituisce dove non serve.
 */
export default function Tendina<T extends string>({
  voci,
  valore,
  onCambia,
  etichetta,
  id,
  disabilitata = false,
  larghezzaPiena = true,
}: {
  voci: VoceTendina<T>[]
  valore: T
  onCambia: (valore: T) => void
  /** Il nome del controllo per chi non lo vede, quando non c'è una `<label>`. */
  etichetta?: string
  id?: string
  disabilitata?: boolean
  larghezzaPiena?: boolean
}) {
  const [aperta, setAperta] = useState(false)
  const [chiudendo, setChiudendo] = useState(false)

  /** La voce evidenziata dalla tastiera, che non è ancora quella scelta. */
  const [puntata, setPuntata] = useState(0)

  const guscio = useRef<HTMLDivElement>(null)
  const elenco = useRef<HTMLDivElement>(null)
  const idGenerato = useId()
  const idElenco = `${id ?? idGenerato}-voci`

  const scelta = voci.find((una) => una.valore === valore) ?? voci[0]

  function apri() {
    if (disabilitata) return
    setPuntata(Math.max(0, voci.findIndex((una) => una.valore === valore)))
    setChiudendo(false)
    setAperta(true)
  }

  function chiudi() {
    if (!aperta) return
    setChiudendo(true)
  }

  function scegli(voce: VoceTendina<T>) {
    if (voce.disponibile === false) return
    onCambia(voce.valore)
    chiudi()
  }

  // Un clic fuori chiude. Si ascolta sul documento e non sul guscio perché il
  // clic che interessa è proprio quello che cade altrove.
  useEffect(() => {
    if (!aperta) return

    function fuori(evento: MouseEvent) {
      if (!guscio.current?.contains(evento.target as Node)) {
        setChiudendo(true)
      }
    }
    // `true`: in fase di cattura, così la tendina si chiude anche quando il
    // clic finisce su un elemento che ferma la propagazione.
    document.addEventListener('mousedown', fuori, true)
    return () => document.removeEventListener('mousedown', fuori, true)
  }, [aperta])

  // La voce puntata resta in vista quando ci si muove con le frecce in un
  // elenco più lungo del pannello.
  useEffect(() => {
    if (!aperta) return
    const voce = elenco.current?.children[puntata] as HTMLElement | undefined
    voce?.scrollIntoView({ block: 'nearest' })
  }, [aperta, puntata])

  function tasti(evento: React.KeyboardEvent) {
    if (disabilitata) return

    if (!aperta) {
      if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(evento.key)) {
        evento.preventDefault()
        apri()
      }
      return
    }

    switch (evento.key) {
      case 'Escape':
        evento.preventDefault()
        chiudi()
        break
      case 'ArrowDown':
        evento.preventDefault()
        setPuntata((prima) => (prima + 1) % voci.length)
        break
      case 'ArrowUp':
        evento.preventDefault()
        setPuntata((prima) => (prima - 1 + voci.length) % voci.length)
        break
      case 'Home':
        evento.preventDefault()
        setPuntata(0)
        break
      case 'End':
        evento.preventDefault()
        setPuntata(voci.length - 1)
        break
      case 'Enter':
      case ' ':
        evento.preventDefault()
        scegli(voci[puntata])
        break
    }
  }

  return (
    <div
      className={`tendina${larghezzaPiena ? '' : ' tendina-stretta'}`}
      ref={guscio}
    >
      <button
        type="button"
        id={id}
        className={`tendina-scelta${aperta && !chiudendo ? ' tendina-scelta-aperta' : ''}`}
        disabled={disabilitata}
        // `combobox` e non `listbox`: il pulsante non è l'elenco, lo comanda.
        role="combobox"
        aria-expanded={aperta}
        aria-controls={idElenco}
        aria-haspopup="listbox"
        aria-label={etichetta}
        onClick={() => (aperta ? chiudi() : apri())}
        onKeyDown={tasti}
      >
        {scelta?.colore !== undefined && (
          <span
            className="tendina-pastiglia"
            style={{ backgroundColor: scelta.colore }}
            aria-hidden="true"
          />
        )}
        <span className="tendina-nome">{scelta?.nome}</span>
        <span className="tendina-gallone" aria-hidden="true">
          <Icona nome="freccia-giu" />
        </span>
      </button>

      {aperta && (
        <div
          id={idElenco}
          ref={elenco}
          className={`tendina-voci${chiudendo ? ' tendina-voci-esce' : ''}`}
          role="listbox"
          aria-label={etichetta}
          onAnimationEnd={(evento) => {
            if (evento.target !== evento.currentTarget) return
            if (chiudendo) {
              setAperta(false)
              setChiudendo(false)
            }
          }}
        >
          {voci.map((una, indice) => {
            const attiva = una.valore === valore
            const fuoriUso = una.disponibile === false
            return (
              <div
                key={una.valore}
                role="option"
                aria-selected={attiva}
                aria-disabled={fuoriUso}
                className={`tendina-voce${attiva ? ' tendina-voce-scelta' : ''}${
                  indice === puntata ? ' tendina-voce-puntata' : ''
                }${fuoriUso ? ' tendina-voce-spenta' : ''}`}
                onMouseEnter={() => setPuntata(indice)}
                onClick={() => scegli(una)}
              >
                {una.colore !== undefined && (
                  <span
                    className="tendina-pastiglia"
                    style={{ backgroundColor: una.colore }}
                    aria-hidden="true"
                  />
                )}

                <span className="tendina-etichette">
                  <span className="tendina-voce-nome">{una.nome}</span>
                  {una.descrizione !== undefined && (
                    <span className="tendina-voce-descrizione">{una.descrizione}</span>
                  )}
                </span>

                {attiva && (
                  <span className="tendina-spunta" aria-hidden="true">
                    <Icona nome="spunta" />
                  </span>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
