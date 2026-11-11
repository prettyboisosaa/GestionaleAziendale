import { useEffect, useState } from 'react'
import { Link, NavLink } from 'react-router'
import Icona from './Icona'
import type { UtenteCorrente } from '../autenticazione/api'
import { durata, pausa } from '../tempo/api'
import type { SituazioneTempo } from '../tempo/api'

type Proprieta = {
  utente: UtenteCorrente
  /** Null finche' non e' stato caricato: la barra del timer non compare. */
  tempo: SituazioneTempo | null
  onTempo: (situazione: SituazioneTempo) => void
  onEsci: () => void
  /** Vero da schermo stretto, dove la navigazione è un menù da aprire. */
  mostraMenu: boolean
  onMenu: () => void
}

/**
 * La barra in alto: il timer, chi è collegato, l'uscita.
 *
 * **Le voci di navigazione non sono più qui**: stanno nella sidebar, che è
 * comune a tutta l'app e le dichiara in `componenti/voci.ts`. Una riga di
 * voci in testata e una colonna di icone a sinistra sarebbero due
 * navigazioni, e la seconda volta che se ne aggiunge una ne verrebbe
 * aggiornata una sola.
 *
 * Quello che resta è ciò che non è un luogo: che cosa sto facendo adesso, chi
 * sono, come esco. Da schermo stretto si aggiunge il pulsante che apre la
 * sidebar, perché lì l'hover non esiste e qualcosa deve poterlo fare.
 *
 * Qui vive la **barra del timer**, quando una task sta scorrendo. È il posto
 * dove il dipendente si accorge di averla avviata senza andare a cercarla: il
 * gestionale non ha notifiche, e ciò che una notifica annuncerebbe si mette in
 * evidenza nelle schermate.
 */
export default function Intestazione({
  utente,
  tempo,
  onTempo,
  onEsci,
  mostraMenu,
  onMenu,
}: Proprieta) {
  return (
    <header className="barra">
      {mostraMenu && (
        <button
          type="button"
          className="barra-menu"
          onClick={onMenu}
          aria-label="Apri la navigazione"
        >
          <Icona nome="menu" />
        </button>
      )}

      {/* Il marchio resta anche qui, in piccolo: da schermo stretto la
          sidebar è chiusa, e senza di esso la testata non direbbe dove si è. */}
      <div className="barra-marchio">
        <Link to="/dashboard">Gestionale</Link>
      </div>

      {tempo?.inCorso != null && (
        <BarraTimer inCorso={tempo.inCorso} onTempo={onTempo} />
      )}

      <div className="barra-utente">
        {/* L'email diventa la porta del profilo: era già il posto dove si
            legge chi è collegato, ed è lì che si va a cercare le proprie
            cose. Una voce «Profilo» nella barra delle sezioni la metterebbe
            fra i luoghi del gestionale, che sono di tutti. */}
        <NavLink
          to="/profilo"
          className={({ isActive }) => (isActive ? 'attivo' : undefined)}
        >
          {utente.email}
        </NavLink>
        <button type="button" onClick={onEsci}>
          Esci
        </button>
      </div>
    </header>
  )
}

/**
 * La task che sta scorrendo, con il tempo che avanza e un pulsante per la
 * pausa.
 *
 * Il tempo lo conta il browser dall'istante di avvio: il server manda
 * `avviataIl`, non un numero di minuti, perché un numero sarebbe già vecchio
 * quando arriva. Il tick è di trenta secondi e non di uno — si mostrano
 * minuti, e un ridisegno al secondo per cambiare cifra ogni sessanta è lavoro
 * buttato.
 */
function BarraTimer({
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
    <div className="barra-timer">
      <span className="timer-punto" aria-hidden="true" />
      <Link to={`/lavori/${inCorso.lavoroId}`} className="timer-task">
        {inCorso.task}
      </Link>
      <span className="timer-durata">{durata(minuti)}</span>
      <button
        type="button"
        className="minuscolo"
        disabled={inAttesa}
        onClick={() => {
          setInAttesa(true)
          pausa()
            .then(onTempo)
            .catch(() => undefined)
            .finally(() => setInAttesa(false))
        }}
      >
        Pausa
      </button>
    </div>
  )
}
