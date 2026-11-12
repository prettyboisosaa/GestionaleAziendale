import { Link } from 'react-router'
import Icona from './Icona'
type Proprieta = {
  /** Vero da schermo stretto, dove la navigazione è un menù da aprire. */
  mostraMenu: boolean
  onMenu: () => void
}

/**
 * La barra in alto: il marchio, e da schermo stretto il pulsante del menù.
 *
 * **Le voci di navigazione non sono più qui**: stanno nella sidebar, che è
 * comune a tutta l'app e le dichiara in `componenti/voci.ts`. Una riga di
 * voci in testata e una colonna di icone a sinistra sarebbero due
 * navigazioni, e la seconda volta che se ne aggiunge una ne verrebbe
 * aggiornata una sola.
 *
 * **Chi sono e come esco non sono più qui**: erano l'email in alto a destra,
 * in mezzo alle cose del gestionale. Stanno in fondo alla sidebar, perché il
 * proprio account non è una sezione dell'applicazione — è chi la sta usando.
 * Quello che resta in testata è il marchio, e da schermo stretto il pulsante
 * che apre la sidebar, perché lì l'hover non esiste e qualcosa deve poterlo
 * fare.
 *
 * **Il timer non è più qui.** Era una striscia fra il marchio e l'email; ora è
 * una scheda in fondo alla sidebar, dove il design system lo colloca. Tenerlo
 * in due posti vorrebbe dire due cronometri che contano lo stesso minuto.
 */
export default function Intestazione({
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

    </header>
  )
}
