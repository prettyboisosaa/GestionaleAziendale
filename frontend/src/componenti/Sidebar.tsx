import { useEffect, useRef, useState } from 'react'
import { NavLink } from 'react-router'
import Icona from './Icona'
import SchedaTimer from './SchedaTimer'
import { vociDi } from './voci'
import type { UtenteCorrente } from '../autenticazione/api'
import type { SituazioneTempo } from '../tempo/api'

/**
 * Quanto si aspetta prima di aprire la barra al passaggio del mouse.
 *
 * Serve perché il bordo sinistro è una strada di passaggio: il mouse lo
 * attraversa per andare altrove, e una barra che si apre a ogni transito
 * diventa un lampo fastidioso. Chi vuole leggere le etichette si fermerà più
 * di due decimi di secondo; chi sta passando, no.
 */
const RITARDO_APERTURA = 180


/**
 * La navigazione laterale, comune a tutta l'app.
 *
 * Una barra di icone che si allarga al passaggio del mouse **spingendo** il
 * contenuto: le sezioni si stringono per farle posto invece di sparirci
 * sotto. È la ragione per cui le due larghezze sono variabili CSS e non
 * valori scritti dove servono — il margine del corpo le deve conoscere
 * entrambe, e lo fa da sé con un selettore di fratellanza.
 *
 * Su schermo stretto l'hover non esiste e l'espansione non ha senso: la barra
 * si ritira dietro un pulsante in testata e si apre come menù a scomparsa,
 * sopra il contenuto, con uno sfondo che la chiude al tocco.
 *
 * **In fondo c'è il timer**, quando una sessione sta scorrendo. Prima stava in
 * testata; il design system lo mette qui, e la ragione sta nel fatto che la
 * barra è l'unica cosa che non cambia mai pagina: ciò che deve restare sotto
 * gli occhi mentre si lavora appartiene a lei.
 */
export default function Sidebar({
  utente,
  tempo,
  onTempo,
  onEsci,
  mobile,
  aperta,
  onChiudi,
}: {
  utente: UtenteCorrente
  /** Null finché non è stato caricato: la scheda del timer non compare. */
  tempo: SituazioneTempo | null
  onTempo: (situazione: SituazioneTempo) => void
  /** Esce dalla sessione: il comando sta in fondo alla barra, accanto al nome. */
  onEsci: () => void
  /** Vero sotto la soglia: niente hover, la barra è un menù. */
  mobile: boolean
  /** Da mobile: se il menù è aperto. Ignorato da desktop. */
  aperta: boolean
  onChiudi: () => void
}) {
  /**
   * Da desktop: se la barra è allargata. Da mobile lo decide `aperta`, che
   * arriva da fuori perché il pulsante che la apre sta in testata.
   */
  const [espansa, setEspansa] = useState(false)
  const attesa = useRef<number | null>(null)

  const voci = vociDi(utente)
  const larga = mobile ? aperta : espansa

  // L'attesa pendente va spenta quando il componente sparisce, altrimenti
  // scatterebbe su uno stato che non esiste più.
  useEffect(() => () => {
    if (attesa.current !== null) {
      clearTimeout(attesa.current)
    }
  }, [])

  function apriFraPoco() {
    if (mobile) {
      return
    }
    if (attesa.current !== null) {
      clearTimeout(attesa.current)
    }
    attesa.current = window.setTimeout(() => setEspansa(true), RITARDO_APERTURA)
  }

  function chiudiSubito() {
    if (attesa.current !== null) {
      clearTimeout(attesa.current)
      attesa.current = null
    }
    setEspansa(false)
  }

  /**
   * La messa a fuoco apre **subito**, senza il ritardo.
   *
   * Chi naviga col Tab non sta passando per sbaglio: ha scelto di arrivare
   * qui, e far aspettare due decimi di secondo l'etichetta di una voce che ha
   * già selezionato è un ritardo senza scopo. Il ritardo difende dal mouse,
   * non dalla tastiera.
   */
  function fuocoDentro() {
    if (!mobile) {
      setEspansa(true)
    }
  }

  return (
    <>
      {/* Lo sfondo esiste solo da mobile, e serve a chiudere il menù
          toccando fuori. Da desktop la barra si chiude da sé quando il mouse
          se ne va, e uno sfondo intercetterebbe i clic sul contenuto. */}
      {mobile && aperta && (
        <div className="sidebar-velo" onClick={onChiudi} aria-hidden="true" />
      )}

      <nav
        className={`sidebar${larga ? ' sidebar-larga' : ''}${mobile ? ' sidebar-mobile' : ''}`}
        aria-label="Navigazione principale"
        onMouseEnter={apriFraPoco}
        onMouseLeave={chiudiSubito}
        onFocus={fuocoDentro}
        onBlur={chiudiSubito}
      >
        {/* Il logo in cima: da chiusa il solo simbolo, da aperta il nome
            intero. Da mobile è anche il pulsante che richiude il menù, perché
            lì l'hover non c'è e qualcosa deve poterlo fare. */}
        <button
          type="button"
          className="sidebar-marchio"
          // Da desktop non è un pulsante che fa qualcosa: il clic alterna
          // l'espansione per chi usa un touch screen su schermo largo, dove
          // l'hover non arriva mai.
          onClick={() => (mobile ? onChiudi() : setEspansa((prima) => !prima))}
          aria-expanded={larga}
          aria-label={larga ? 'Chiudi la navigazione' : 'Apri la navigazione'}
        >
          <span className="sidebar-simbolo" aria-hidden="true">
            A
          </span>
          <span className="sidebar-etichetta">Anitia</span>
        </button>

        <ul className="sidebar-voci">
          {voci.map((voce) => (
            <li key={voce.id}>
              <NavLink
                to={voce.percorso}
                className={({ isActive }) =>
                  isActive ? 'sidebar-voce attivo' : 'sidebar-voce'
                }
                // Da chiusa si vede solo l'icona, ma il nome deve restare
                // leggibile: `title` lo dà al mouse, e il testo dell'etichetta
                // — presente nel DOM anche quando è visivamente nascosto — lo
                // dà agli assistenti vocali.
                title={voce.etichetta}
                onClick={mobile ? onChiudi : undefined}
              >
                <Icona nome={voce.icona} />
                <span className="sidebar-etichetta">{voce.etichetta}</span>
              </NavLink>
            </li>
          ))}
        </ul>

        {/* Dopo le voci e spinta in fondo dal `margin-top: auto` del foglio:
            non è una voce di navigazione e non deve stare nel loro elenco. */}
        {tempo?.inCorso != null && (
          <SchedaTimer inCorso={tempo.inCorso} onTempo={onTempo} />
        )}

        {/* Il proprio spazio, in fondo alla barra.
            Prima era l'email in testata, in alto a destra, in mezzo alle cose
            del gestionale: ma il proprio account non è una sezione
            dell'applicazione, è chi la sta usando — e il design system lo
            mette qui, sotto a tutto, come fa ogni interfaccia che abbia una
            barra laterale. Da chiusa resta la sola pastiglia con le
            iniziali; da aperta compaiono il nome, l'email e l'uscita. */}
        <div className="sidebar-conto">
          <NavLink
            to="/profilo"
            className={({ isActive }) =>
              isActive ? 'sidebar-io attivo' : 'sidebar-io'
            }
            title={`${utente.nome} ${utente.cognome} · il tuo profilo`}
            onClick={mobile ? onChiudi : undefined}
          >
            <span className="sidebar-io-gettone" aria-hidden="true">
              {utente.nome.charAt(0)}
              {utente.cognome.charAt(0)}
            </span>
            <span className="sidebar-etichetta sidebar-io-testo">
              <span className="sidebar-io-nome">
                {utente.nome} {utente.cognome}
              </span>
              <span className="sidebar-io-email">{utente.email}</span>
            </span>
          </NavLink>

          <button
            type="button"
            className="sidebar-esci sidebar-etichetta"
            onClick={onEsci}
            title="Esci"
          >
            Esci
          </button>
        </div>
      </nav>
    </>
  )
}

