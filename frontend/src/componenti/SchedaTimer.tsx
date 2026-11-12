import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { durata, pausa } from '../tempo/api'
import type { SituazioneTempo } from '../tempo/api'

/**
 * Il timer che sta scorrendo, in fondo alla barra laterale.
 *
 * Stava in testata, in una riga sottile fra il marchio e l'email. Il design
 * system lo mette **in fondo alla navigazione**, come una scheda a sé: un
 * cronometro grande, su che cosa, e un pulsante corallo per fermarlo. Il
 * cambio non è estetico — in testata il timer era una striscia da leggere, qui
 * è un oggetto che si vede da lontano, ed è esattamente quello che deve fare:
 * il gestionale non ha notifiche, e ciò che una notifica annuncerebbe si mette
 * in evidenza nelle schermate.
 *
 * Il **corallo** è la sua unica comparsa in tutta la barra. Fluida dice che
 * gli accenti non coprono più del 10% di una schermata, e un pulsante alto
 * quaranta pixel in fondo a una colonna scura ci sta dentro: è il solo gesto
 * che interrompe qualcosa, e si deve trovare senza cercarlo.
 *
 * Il tempo lo conta il browser dall'istante di avvio: il server manda
 * `avviataIl`, non un numero di minuti, perché un numero sarebbe già vecchio
 * quando arriva. Il tick è di trenta secondi e non di uno — si mostrano
 * minuti, e un ridisegno al secondo per cambiare cifra ogni sessanta è lavoro
 * buttato.
 */
export default function SchedaTimer({
  inCorso,
  onTempo,
}: {
  inCorso: NonNullable<SituazioneTempo['inCorso']>
  onTempo: (situazione: SituazioneTempo) => void
}) {
  const [adesso, setAdesso] = useState(() => Date.now())
  const [inAttesa, setInAttesa] = useState(false)

  useEffect(() => {
    const tick = setInterval(() => setAdesso(Date.now()), 30_000)
    return () => clearInterval(tick)
  }, [])

  const minuti = Math.max(
    0,
    Math.floor((adesso - new Date(inCorso.avviataIl).getTime()) / 60_000),
  )

  return (
    <div className="scheda-timer">
      {/* Da barra chiusa resta solo questo: un pallino che pulsa, perché
          sessantasei pixel non bastano a un cronometro e una scheda tagliata
          a metà direbbe meno di niente. Chi lo vede sa che qualcosa sta
          andando e apre la barra per sapere cosa. */}
      <span className="scheda-timer-punto" aria-hidden="true" />

      <div className="scheda-timer-corpo sidebar-etichetta">
        <span className="scheda-timer-titolo">Timer attivo</span>
        <span className="scheda-timer-durata">{durata(minuti)}</span>
        <Link to={`/lavori/${inCorso.lavoroId}`} className="scheda-timer-task">
          {inCorso.codiceLavoro} · {inCorso.task}
        </Link>
        <button
          type="button"
          className="scheda-timer-ferma"
          disabled={inAttesa}
          onClick={() => {
            setInAttesa(true)
            pausa()
              .then(onTempo)
              .catch(() => undefined)
              .finally(() => setInAttesa(false))
          }}
        >
          Ferma
        </button>
      </div>
    </div>
  )
}
