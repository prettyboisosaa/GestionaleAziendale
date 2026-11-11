import { useEffect, useRef } from 'react'
import type { MouseEvent } from 'react'

/**
 * «Sei sicuro di voler uscire?»
 *
 * Un `<dialog>` nativo, come `PannelloSessione`: in questo gestionale non c'è
 * nessuna libreria di componenti, e `showModal()` dà da sé la messa a fuoco
 * intrappolata, lo sfondo e la chiusura con Esc — tre cose che un `<div>`
 * sovrapposto andrebbe a riscrivere peggio.
 *
 * Sta in un componente suo e non dentro `Intestazione` perché la conferma
 * deve valere per **ogni** punto da cui si esce. Oggi il pulsante è uno solo,
 * nella barra in alto, ma l'uscita vive in `App`, che è l'unico posto che
 * possiede la sessione: mettendo la modale lì, un secondo pulsante aggiunto
 * domani passa dalla conferma senza doversene ricordare.
 */
export default function ConfermaUscita({
  onAnnulla,
  onEsci,
}: {
  onAnnulla: () => void
  onEsci: () => void
}) {
  const riferimento = useRef<HTMLDialogElement>(null)

  // showModal() e non l'attributo `open`: solo il primo mette il dialogo nel
  // top layer, con lo sfondo e la messa a fuoco intrappolata.
  useEffect(() => {
    riferimento.current?.showModal()
  }, [])

  /**
   * Un clic fuori chiude senza fare niente.
   *
   * L'evento arriva sul `<dialog>` stesso quando si colpisce lo sfondo,
   * perché quello è il suo `::backdrop` e non un elemento a parte: se il
   * bersaglio è il dialogo e non qualcosa dentro di lui, il clic è caduto
   * fuori dal riquadro.
   */
  function fuori(evento: MouseEvent<HTMLDialogElement>) {
    if (evento.target === riferimento.current) {
      onAnnulla()
    }
  }

  return (
    <dialog
      // Nell'angolo in alto a destra, non in mezzo: nasce dal pulsante
      // «Esci» della testata e compare accanto al punto in cui si è premuto,
      // invece di coprire la pagina al centro per una domanda da un clic.
      className="pannello pannello-angolo"
      ref={riferimento}
      // onCancel è Esc: il browser lo manda da sé, e senza questo il dialogo
      // si chiuderebbe lasciando lo stato che dice che è aperto.
      onCancel={onAnnulla}
      onClick={fuori}
    >
      <div className="modulo">
        <div className="testata">
          <h2>Sei sicuro di voler uscire?</h2>
        </div>

        <p className="sommario">
          La sessione si chiude e tornerai alla schermata di accesso. Il tempo
          già registrato resta dov'è; una task avviata continua a scorrere.
        </p>

        <div className="azioni">
          {/* «Annulla» è il primo e tiene la messa a fuoco iniziale: la
              conferma si apre per far fermare un attimo, non per far premere
              Invio sul pulsante che esce. */}
          <button type="button" autoFocus onClick={onAnnulla}>
            Annulla
          </button>
          <button type="button" className="secondario" onClick={onEsci}>
            Esci
          </button>
        </div>
      </div>
    </dialog>
  )
}
