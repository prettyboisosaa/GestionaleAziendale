import { Link, NavLink } from 'react-router'
import type { UtenteCorrente } from '../autenticazione/api'
import { ha } from '../autenticazione/permessi'

type Proprieta = {
  utente: UtenteCorrente
  onEsci: () => void
}

/**
 * La barra in alto: identità, dove si può andare, chi è collegato.
 *
 * Le voci che dipendono da un permesso compaiono in base a quello. Come in
 * `Protetta`, serve a non mostrare porte chiuse, non a tenerle chiuse: quello
 * lo fa il backend.
 *
 * Le prime quattro non hanno condizione: poggiano su permessi di base, che
 * ogni utente attivo ha per costruzione. Una guardia che è sempre vera non
 * protegge niente e fa credere il contrario a chi legge.
 */
export default function Intestazione({ utente, onEsci }: Proprieta) {
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
        <NavLink
          to="/giornata"
          className={({ isActive }) => (isActive ? 'attivo' : undefined)}
        >
          Giornata
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

      <div className="barra-utente">
        <span>{utente.email}</span>
        <button type="button" onClick={onEsci}>
          Esci
        </button>
      </div>
    </header>
  )
}
