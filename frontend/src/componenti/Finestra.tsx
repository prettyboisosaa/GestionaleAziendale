import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react'
import type { AnimationEvent, MouseEvent, ReactNode } from 'react'
import { movimentoRidotto } from './movimento'

/**
 * La finestra: il foglio che sta sopra la pagina.
 *
 * È il `<dialog>` nativo della tavola 05 del design system — raggio 24, ombra
 * lg, apertura con un leggero rimbalzo di 220 ms — e sotto ci sono tutte le
 * modali del gestionale. `showModal()` e non un `<div>` sovrapposto: dà da sé
 * la messa a fuoco intrappolata, lo sfondo e la chiusura con Esc, tre cose che
 * un div andrebbe a riscrivere peggio.
 *
 * <p><strong>Il motivo per cui questo componente esiste è la chiusura.</strong>
 * L'apertura si anima da sola: l'elemento compare e il CSS fa il resto. Quella
 * in uscita no — se chi possiede la finestra smonta il componente, React porta
 * via il nodo prima che qualunque animazione possa girare, e il foglio sparisce
 * di scatto. Il rimbalzo all'andata e il taglio netto al ritorno sono la cosa
 * che fa sembrare «non finita» un'interfaccia per il resto curata.
 *
 * <p>Qui la chiusura è quindi in due tempi: si marca la finestra come in
 * uscita, si lascia correre l'animazione, e solo quando il browser dice che è
 * finita (`animationend`) si avvisa chi possiede lo stato. Chi preme «Annulla»
 * non se ne accorge; chi legge il codice sì, ed è il motivo di questo commento.
 *
 * <p>Con `prefers-reduced-motion` non si aspetta niente: chi ha chiesto meno
 * movimento non deve pagarlo in ritardo.
 */

/**
 * La funzione che chiude la finestra animandola.
 *
 * Sta in un contesto perché a premere «Annulla» è un pulsante che vive nei
 * figli, non qui: senza, ogni finestra dovrebbe farsi passare la funzione di
 * chiusura di mano in mano fino al pulsante.
 *
 * L'argomento facoltativo è cosa fare **dopo** che l'animazione è finita. Serve
 * alla conferma: «esci davvero» non deve partire mentre il foglio è ancora a
 * metà strada, altrimenti la pagina cambia sotto un'animazione che sta ancora
 * girando.
 */
type Chiusura = (dopo?: () => void) => void

const ContestoFinestra = createContext<Chiusura>(() => {})

/** La chiusura animata della finestra che contiene chi chiama. */
export function useChiudiFinestra(): Chiusura {
  return useContext(ContestoFinestra)
}

export default function Finestra({
  onChiudi,
  chiudibileDaFuori = true,
  angolo = false,
  className = '',
  etichetta,
  children,
}: {
  /** Chiamata quando la finestra ha finito di chiudersi. */
  onChiudi: () => void
  /**
   * Se un clic sullo sfondo e il tasto Esc chiudono.
   *
   * Si mette a `false` mentre una richiesta è in volo: l'operazione potrebbe
   * essere già arrivata al server, e chiudere lascerebbe chi guarda senza
   * sapere com'è finita.
   */
  chiudibileDaFuori?: boolean
  /**
   * Nell'angolo in alto a destra invece che in mezzo. Per le conferme che
   * nascono da un pulsante della testata: compaiono accanto al punto in cui si
   * è premuto, invece di coprire la pagina al centro per una domanda da un
   * clic.
   */
  angolo?: boolean
  className?: string
  /** Il nome della finestra per chi non la vede. */
  etichetta?: string
  /**
   * Il contenuto.
   *
   * ⚠️ Un componente **non vede il contesto che fornisce lui stesso**: chi
   * rende una `Finestra` non può usare `useChiudiFinestra` per il proprio
   * pulsante «chiudi». I figli sì, e per questo i pulsanti che chiudono sono
   * componenti a sé — {@link BottoneChiudi} qui sotto, o un corpo separato
   * come fa `ConfermaAzione`.
   *
   * La via alternativa sarebbe passare la chiusura come funzione ai figli
   * (`children(chiudi)`), ed è stata scartata: il controllo statico la
   * scambia per una lettura di ref durante il disegno, e un avviso che
   * compare a ogni controllo è un avviso che fra un mese nessuno legge più.
   */
  children: ReactNode
}) {
  const riferimento = useRef<HTMLDialogElement>(null)
  const [chiudendo, setChiudendo] = useState(false)

  // Cosa fare quando l'animazione d'uscita è finita. In un ref e non in uno
  // stato: cambiarlo non deve ridisegnare niente, e serve subito dentro il
  // gestore dell'evento.
  const dopoLaChiusura = useRef<(() => void) | null>(null)

  useEffect(() => {
    // showModal() e non l'attributo `open`: solo il primo mette il dialogo nel
    // top layer, con lo sfondo e la messa a fuoco intrappolata.
    riferimento.current?.showModal()
  }, [])

  const concludi = useCallback(() => {
    const dopo = dopoLaChiusura.current
    dopoLaChiusura.current = null
    riferimento.current?.close()
    if (dopo !== null) {
      dopo()
    } else {
      onChiudi()
    }
  }, [onChiudi])

  const chiudi = useCallback<Chiusura>(
    (dopo) => {
      // Due chiusure sovrapposte — Esc mentre l'uscita è già partita — non
      // devono far partire due volte quello che viene dopo.
      if (chiudendo) return

      dopoLaChiusura.current = dopo ?? null

      if (movimentoRidotto()) {
        concludi()
        return
      }
      setChiudendo(true)
    },
    [chiudendo, concludi],
  )

  /**
   * Un clic fuori chiude senza fare niente.
   *
   * L'evento arriva sul `<dialog>` stesso quando si colpisce lo sfondo, perché
   * quello è il suo `::backdrop` e non un elemento a parte: se il bersaglio è
   * il dialogo e non qualcosa dentro di lui, il clic è caduto fuori dal
   * riquadro.
   */
  function fuori(evento: MouseEvent<HTMLDialogElement>) {
    if (evento.target === riferimento.current && chiudibileDaFuori) {
      chiudi()
    }
  }

  /**
   * La fine dell'animazione d'uscita.
   *
   * Il controllo sul bersaglio non è una precauzione teorica: `animationend`
   * risale dai figli, e dentro una finestra ci sono elementi animati — la
   * pastiglia dell'icona, le voci di una tendina. Senza, la prima di quelle
   * animazioni a finire chiuderebbe la finestra.
   */
  function finita(evento: AnimationEvent<HTMLDialogElement>) {
    if (evento.target !== riferimento.current) return
    if (!chiudendo) return
    concludi()
  }

  return (
    <ContestoFinestra.Provider value={chiudi}>
      <dialog
        ref={riferimento}
        className={`finestra${angolo ? ' finestra-angolo' : ''}${
          chiudendo ? ' finestra-esce' : ''
        } ${className}`.trim()}
        aria-label={etichetta}
        // onCancel è Esc: il browser lo manda da sé. Si ferma sempre, anche
        // quando la chiusura è permessa, perché altrimenti il dialogo sparirebbe
        // di scatto saltando l'animazione d'uscita — che è tutto il punto di
        // questo componente.
        onCancel={(evento) => {
          evento.preventDefault()
          if (chiudibileDaFuori) chiudi()
        }}
        onClick={fuori}
        onAnimationEnd={finita}
      >
        {children}
      </dialog>
    </ContestoFinestra.Provider>
  )
}

/**
 * Il tondo «chiudi» in alto a destra di una finestra.
 *
 * Un componente e non un pulsante scritto nel modulo, per il vincolo
 * spiegato sopra: `useChiudiFinestra` legge il contesto che `Finestra`
 * fornisce, e solo un **figlio** lo vede. Stando qui, chiude animandosi come
 * tutte le altre vie d'uscita invece di sparire di scatto.
 */
export function BottoneChiudi({
  disabilitato = false,
  children,
}: {
  disabilitato?: boolean
  children: ReactNode
}) {
  const chiudi = useChiudiFinestra()
  return (
    <button
      type="button"
      className="finestra-chiudi"
      aria-label="Chiudi"
      disabled={disabilitato}
      onClick={() => chiudi()}
    >
      {children}
    </button>
  )
}

/**
 * Il pulsante di contorno che desiste, in fondo a un modulo.
 *
 * Stessa ragione di {@link BottoneChiudi}: deve stare dentro la finestra per
 * poterla chiudere con la sua animazione.
 */
export function BottoneDesisti({
  disabilitato = false,
  etichetta = 'Annulla',
}: {
  disabilitato?: boolean
  etichetta?: string
}) {
  const chiudi = useChiudiFinestra()
  return (
    <button
      type="button"
      className="secondario"
      disabled={disabilitato}
      onClick={() => chiudi()}
    >
      {etichetta}
    </button>
  )
}
