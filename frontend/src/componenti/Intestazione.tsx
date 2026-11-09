import { useEffect, useState } from 'react'
import { Link, NavLink } from 'react-router'
import type { UtenteCorrente } from '../autenticazione/api'
import { ha } from '../autenticazione/permessi'
import { durata, pausa } from '../tempo/api'
import type { SituazioneTempo } from '../tempo/api'

type Proprieta = {
  utente: UtenteCorrente
  /** Null finche' non e' stato caricato: la barra del timer non compare. */
  tempo: SituazioneTempo | null
  onTempo: (situazione: SituazioneTempo) => void
  onEsci: () => void
}

/**
 * La barra in alto: identità, dove si può andare, chi è collegato.
 *
 * Le voci che dipendono da un permesso compaiono in base a quello. Come in
 * `Protetta`, serve a non mostrare porte chiuse, non a tenerle chiuse: quello
 * lo fa il backend.
 *
 * Le prime tre non hanno condizione: poggiano su permessi di base, che ogni
 * utente attivo ha per costruzione. Una guardia che è sempre vera non protegge
 * niente e fa credere il contrario a chi legge.
 *
 * Qui vive anche la **barra del timer**, quando una task sta scorrendo. È il
 * posto dove il dipendente si accorge di averla avviata senza andare a
 * cercarla: il gestionale non ha notifiche, e ciò che una notifica
 * annuncerebbe si mette in evidenza nelle schermate.
 */
export default function Intestazione({ utente, tempo, onTempo, onEsci }: Proprieta) {
  return (
    <header className="barra">
      <div className="barra-marchio">
        <span className="azienda">Anitia</span>
        <Link to="/lavori">Gestionale</Link>
      </div>

      {/* NavLink invece di Link: segna da sé la voce della pagina aperta, così
          la barra dice anche dove si è e non solo dove si può andare. */}
      <nav className="barra-voci">
        {/* Prima voce perché è la pagina di atterraggio: la barra racconta
            l'ordine in cui si attraversa il gestionale, e si comincia da
            «cosa devo fare oggi». */}
        <NavLink
          to="/dashboard"
          className={({ isActive }) => (isActive ? 'attivo' : undefined)}
        >
          Oggi
        </NavLink>
        <NavLink to="/lavori" className={({ isActive }) => (isActive ? 'attivo' : undefined)}>
          Lavori
        </NavLink>
        {ha(utente, 'ORE_RICHIESTE_GESTISCI', 'ORE_RICHIEDI') && (
          <NavLink
            to="/richieste-ore"
            className={({ isActive }) => (isActive ? 'attivo' : undefined)}
          >
            Richieste ore
          </NavLink>
        )}
        <NavLink to="/assenze" className={({ isActive }) => (isActive ? 'attivo' : undefined)}>
          Assenze
        </NavLink>
        {/* Due voci distinte e non una pagina che si comporta in due modi come
            «Richieste ore»: qui le righe non sono le stesse. Il dipendente vede
            le proprie, l'amministratore quelle di tutti, e fonderle vorrebbe
            dire una tabella con una colonna che a metà degli utenti non dice
            niente. */}
        {ha(utente, 'ASSENZE_GESTISCI') && (
          <NavLink
            to="/richieste-assenza"
            className={({ isActive }) => (isActive ? 'attivo' : undefined)}
          >
            Richieste assenza
          </NavLink>
        )}
        {ha(utente, 'UTENTI_VISUALIZZA') && (
          <NavLink to="/utenti" className={({ isActive }) => (isActive ? 'attivo' : undefined)}>
            Utenti
          </NavLink>
        )}
      </nav>

      {tempo?.inCorso != null && (
        <BarraTimer inCorso={tempo.inCorso} onTempo={onTempo} />
      )}

      <div className="barra-utente">
        <span>{utente.email}</span>
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
