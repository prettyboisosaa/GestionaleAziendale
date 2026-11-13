import { useEffect, useId, useRef, useState } from 'react'
import Icona from './Icona'

/**
 * La tendina data della tavola 05: il campo che apre un mese.
 *
 * <p><strong>⚠️ Perché le date non passano mai da `new Date(stringa)`.</strong>
 * Una stringa `'2026-11-19'` la specifica di JavaScript la legge come
 * **mezzanotte UTC**, non come mezzanotte locale. In Italia, che è avanti,
 * `new Date('2026-11-19').getDate()` dà 19 — ma `toLocaleDateString()` su una
 * macchina a ovest, o un `setHours` incauto, riportano al 18. In un gestionale
 * dove un giorno sbagliato sposta delle ore lavorate da una settimana
 * all'altra, è il genere di errore che non si vede finché non lo trova
 * qualcuno a consuntivo.
 *
 * <p>Qui dentro una data è quindi sempre o la tripletta `{anno, mese, giorno}`
 * o la sua stringa ISO, e l'unico `new Date(...)` è quello a tre argomenti —
 * `new Date(anno, mese, giorno)` — che è l'ora locale per definizione. Lo
 * stesso vale per `oggi()`: si leggono anno, mese e giorno dall'orologio
 * locale, non si taglia un istante UTC.
 */

/** Una data senza ora e senza fuso: i tre numeri e basta. */
type Giorno = { anno: number; mese: number; giorno: number }

const GIORNI = ['lunedì', 'martedì', 'mercoledì', 'giovedì', 'venerdì', 'sabato', 'domenica']
const INIZIALI = ['L', 'M', 'M', 'G', 'V', 'S', 'D']
const MESI = [
  'gennaio', 'febbraio', 'marzo', 'aprile', 'maggio', 'giugno',
  'luglio', 'agosto', 'settembre', 'ottobre', 'novembre', 'dicembre',
]

/** `'2026-11-19'` → `{anno: 2026, mese: 10, giorno: 19}`. Mese da zero. */
function daIso(iso: string): Giorno | null {
  const pezzi = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso)
  if (pezzi === null) return null
  return { anno: +pezzi[1], mese: +pezzi[2] - 1, giorno: +pezzi[3] }
}

function aIso(g: Giorno): string {
  const mm = String(g.mese + 1).padStart(2, '0')
  const gg = String(g.giorno).padStart(2, '0')
  return `${g.anno}-${mm}-${gg}`
}

function oggi(): Giorno {
  const ora = new Date()
  return { anno: ora.getFullYear(), mese: ora.getMonth(), giorno: ora.getDate() }
}

function uguali(a: Giorno | null, b: Giorno | null): boolean {
  if (a === null || b === null) return false
  return a.anno === b.anno && a.mese === b.mese && a.giorno === b.giorno
}

/** Il giorno della settimana con lunedì = 0, che è come si legge un calendario qui. */
function indiceSettimana(g: Giorno): number {
  return (new Date(g.anno, g.mese, g.giorno).getDay() + 6) % 7
}

/** «Giovedì 19 novembre 2026», come lo scrive la tavola. */
function perEsteso(g: Giorno): string {
  const nome = GIORNI[indiceSettimana(g)]
  return `${nome[0].toUpperCase()}${nome.slice(1)} ${g.giorno} ${MESI[g.mese]} ${g.anno}`
}

/** Sposta di `quanti` giorni restando in ora locale. */
function sposta(g: Giorno, quanti: number): Giorno {
  const d = new Date(g.anno, g.mese, g.giorno + quanti)
  return { anno: d.getFullYear(), mese: d.getMonth(), giorno: d.getDate() }
}

/**
 * Le celle del mese: i giorni del mese più la coda del precedente e la testa
 * del successivo, perché la griglia cominci di lunedì e non abbia buchi.
 */
function celle(anno: number, mese: number): Giorno[] {
  const primo = new Date(anno, mese, 1)
  const scarto = (primo.getDay() + 6) % 7
  const quanti = new Date(anno, mese + 1, 0).getDate()

  const righe: Giorno[] = []
  for (let i = 0; i < scarto; i++) {
    righe.push(sposta({ anno, mese, giorno: 1 }, i - scarto))
  }
  for (let g = 1; g <= quanti; g++) {
    righe.push({ anno, mese, giorno: g })
  }
  // Fino a riempire l'ultima settimana: una griglia che finisce a metà riga
  // salta all'occhio più di qualche giorno grigio in più.
  while (righe.length % 7 !== 0) {
    righe.push(sposta(righe[righe.length - 1], 1))
  }
  return righe
}

export default function SelettoreData({
  valore,
  onCambia,
  id,
  min,
  max,
  disabilitato = false,
  obbligatorio = false,
}: {
  /** La data in ISO (`2026-11-19`), o stringa vuota se non c'è. */
  valore: string
  onCambia: (iso: string) => void
  id?: string
  /** Estremi in ISO, come li dichiarerebbe un `<input type="date">`. */
  min?: string
  max?: string
  disabilitato?: boolean
  obbligatorio?: boolean
}) {
  const scelto = daIso(valore)
  const [aperta, setAperta] = useState(false)
  const [chiudendo, setChiudendo] = useState(false)

  /** Il mese che il pannello sta mostrando, che non è detto sia quello scelto. */
  const [mese, setMese] = useState<{ anno: number; mese: number }>(() => {
    const base = scelto ?? oggi()
    return { anno: base.anno, mese: base.mese }
  })

  /** Il giorno che la tastiera sta puntando. */
  const [puntato, setPuntato] = useState<Giorno>(() => scelto ?? oggi())

  const guscio = useRef<HTMLDivElement>(null)
  const idGenerato = useId()
  const idPannello = `${id ?? idGenerato}-mese`

  const limiteMin = min !== undefined ? daIso(min) : null
  const limiteMax = max !== undefined ? daIso(max) : null

  function fuoriDaiLimiti(g: Giorno): boolean {
    const numero = g.anno * 10000 + g.mese * 100 + g.giorno
    if (limiteMin !== null && numero < limiteMin.anno * 10000 + limiteMin.mese * 100 + limiteMin.giorno) {
      return true
    }
    if (limiteMax !== null && numero > limiteMax.anno * 10000 + limiteMax.mese * 100 + limiteMax.giorno) {
      return true
    }
    return false
  }

  function apri() {
    if (disabilitato) return
    const base = scelto ?? oggi()
    setMese({ anno: base.anno, mese: base.mese })
    setPuntato(base)
    setChiudendo(false)
    setAperta(true)
  }

  function chiudi() {
    if (!aperta) return
    setChiudendo(true)
  }

  function scegli(g: Giorno) {
    if (fuoriDaiLimiti(g)) return
    onCambia(aIso(g))
    chiudi()
  }

  useEffect(() => {
    if (!aperta) return
    function fuori(evento: MouseEvent) {
      if (!guscio.current?.contains(evento.target as Node)) setChiudendo(true)
    }
    document.addEventListener('mousedown', fuori, true)
    return () => document.removeEventListener('mousedown', fuori, true)
  }, [aperta])

  /**
   * Sposta il giorno puntato, voltando pagina quando si esce dal mese.
   *
   * Le due cose si fanno **qui**, nel gestore del tasto, e non in un effetto
   * che guardi `puntato` per correggere `mese` dopo: un effetto che chiama
   * `setState` fa partire un secondo disegno a ogni freccia, e tenere
   * premuto il tasto significherebbe due ridisegni per ogni ripetizione. Lo
   * spostamento è un evento, e lo stato che ne consegue si calcola
   * nell'evento.
   *
   * `sposta` passa sempre da `new Date(anno, mese, giorno)` — l'unico
   * costruttore che lavora in ora locale — quindi il 31 gennaio più un giorno
   * è il 1° febbraio senza che qui si debba sapere quanti giorni ha un mese.
   */
  function muovi(quanti: number) {
    const dove = sposta(puntato, quanti)
    setPuntato(dove)
    if (dove.anno !== mese.anno || dove.mese !== mese.mese) {
      setMese({ anno: dove.anno, mese: dove.mese })
    }
  }

  /** Avanti o indietro di un mese, portandosi dietro il giorno puntato. */
  function voltaMese(quanti: number) {
    const dove = { anno: mese.anno, mese: mese.mese + quanti }
    setMese(dove)
    // `new Date` normalizza il mese 12 in gennaio dell'anno dopo, e il -1 in
    // dicembre di quello prima: non c'è nessun riporto da fare a mano.
    const normalizzato = new Date(dove.anno, dove.mese, 1)
    setPuntato({
      anno: normalizzato.getFullYear(),
      mese: normalizzato.getMonth(),
      // Il 31 in un mese che ne ha 30 scivolerebbe al mese dopo: si tiene il
      // giorno quando esiste, altrimenti l'ultimo disponibile.
      giorno: Math.min(
        puntato.giorno,
        new Date(dove.anno, dove.mese + 1, 0).getDate(),
      ),
    })
  }

  function tasti(evento: React.KeyboardEvent) {
    if (disabilitato) return

    if (!aperta) {
      if (['ArrowDown', 'Enter', ' '].includes(evento.key)) {
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
      case 'ArrowLeft':
        evento.preventDefault()
        muovi(-1)
        break
      case 'ArrowRight':
        evento.preventDefault()
        muovi(1)
        break
      // Su e giù di una settimana e non di un giorno: la griglia ha sette
      // colonne, quindi è il movimento che corrisponde a quello che si vede.
      case 'ArrowUp':
        evento.preventDefault()
        muovi(-7)
        break
      case 'ArrowDown':
        evento.preventDefault()
        muovi(7)
        break
      case 'PageUp':
        evento.preventDefault()
        voltaMese(-1)
        break
      case 'PageDown':
        evento.preventDefault()
        voltaMese(1)
        break
      case 'Enter':
      case ' ':
        evento.preventDefault()
        scegli(puntato)
        break
    }
  }

  const giorni = celle(mese.anno, mese.mese)
  const adesso = oggi()

  return (
    <div className="selettore-data" ref={guscio}>
      <button
        type="button"
        id={id}
        className={`campo-data${aperta && !chiudendo ? ' campo-data-aperto' : ''}${
          obbligatorio && valore === '' ? ' campo-data-vuoto' : ''
        }`}
        disabled={disabilitato}
        aria-haspopup="dialog"
        aria-expanded={aperta}
        aria-controls={idPannello}
        onClick={() => (aperta ? chiudi() : apri())}
        onKeyDown={tasti}
      >
        <span className="campo-data-segno" aria-hidden="true">
          <Icona nome="calendario" />
        </span>
        <span className={scelto === null ? 'campo-data-vuota' : undefined}>
          {scelto === null ? 'Scegli una data' : perEsteso(scelto)}
        </span>
      </button>

      {aperta && (
        <div
          id={idPannello}
          className={`mese${chiudendo ? ' mese-esce' : ''}`}
          role="dialog"
          aria-label="Scegli una data"
          onAnimationEnd={(evento) => {
            if (evento.target !== evento.currentTarget) return
            if (chiudendo) {
              setAperta(false)
              setChiudendo(false)
            }
          }}
        >
          <div className="mese-testata">
            <strong>
              {MESI[mese.mese][0].toUpperCase()}
              {MESI[mese.mese].slice(1)} {mese.anno}
            </strong>
            <div className="mese-frecce">
              <button
                type="button"
                aria-label="Mese precedente"
                onClick={() => voltaMese(-1)}
              >
                <Icona nome="freccia-sinistra" />
              </button>
              <button
                type="button"
                aria-label="Mese successivo"
                onClick={() => voltaMese(1)}
              >
                <Icona nome="freccia-destra" />
              </button>
            </div>
          </div>

          <div className="mese-iniziali" aria-hidden="true">
            {INIZIALI.map((lettera, indice) => (
              <span key={indice}>{lettera}</span>
            ))}
          </div>

          <div className="mese-giorni" role="grid">
            {giorni.map((g) => {
              const altroMese = g.mese !== mese.mese
              const spento = fuoriDaiLimiti(g)
              return (
                <button
                  key={aIso(g)}
                  type="button"
                  role="gridcell"
                  tabIndex={-1}
                  disabled={spento}
                  aria-selected={uguali(g, scelto)}
                  // Il nome per esteso: in una griglia di numeri nudi, «19»
                  // da solo non dice né il mese né il giorno della settimana.
                  aria-label={perEsteso(g)}
                  className={`giorno${altroMese ? ' giorno-altro-mese' : ''}${
                    uguali(g, scelto) ? ' giorno-scelto' : ''
                  }${uguali(g, adesso) ? ' giorno-oggi' : ''}${
                    uguali(g, puntato) ? ' giorno-puntato' : ''
                  }`}
                  onClick={() => scegli(g)}
                >
                  {g.giorno}
                </button>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}
