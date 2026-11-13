import { useEffect, useRef, useState } from 'react'
import type { FormEvent, MouseEvent } from 'react'
import { azzeraPassword } from './api'
import type { RigaUtente } from './api'
import { testo } from '../api/http'

/**
 * Assegna una password nuova a un'altra persona.
 *
 * Un `<dialog>` nativo come {@link ConfermaUscita}: `showModal()` dà da sé la
 * messa a fuoco intrappolata, lo sfondo e la chiusura con Esc.
 *
 * <p><strong>La password si vede mentre la si scrive</strong>, ed è una scelta
 * e non una dimenticanza. Nelle altre schermate il campo è oscurato perché chi
 * digita sta riscrivendo un segreto che conosce già; qui chi digita la sta
 * *inventando per qualcun altro* e dovrà dettargliela a voce. Oscurarla
 * significherebbe non poterla rileggere, cioè sbagliarla e lasciare fuori la
 * persona che si stava aiutando. Non c'è niente da nascondere a chi ha già il
 * permesso di scriverla.
 *
 * <p>Per la stessa ragione non c'è il campo «ripeti la password»: serve a
 * difendersi dai refusi in un campo che non si può rileggere, e questo si
 * rilegge.
 */
export default function ModaleResetPassword({
  persona,
  onChiudi,
  onFatto,
}: {
  persona: RigaUtente
  onChiudi: () => void
  onFatto: (messaggio: string) => void
}) {
  const riferimento = useRef<HTMLDialogElement>(null)
  const [password, setPassword] = useState('')
  const [errore, setErrore] = useState<string | null>(null)
  const [inCorso, setInCorso] = useState(false)

  useEffect(() => {
    riferimento.current?.showModal()
  }, [])

  function fuori(evento: MouseEvent<HTMLDialogElement>) {
    // L'evento arriva sul <dialog> stesso quando si colpisce lo sfondo: se il
    // bersaglio è il dialogo e non qualcosa dentro di lui, il clic è caduto
    // fuori dal riquadro. Mentre la richiesta è in volo non si chiude: la
    // password potrebbe essere già stata cambiata.
    if (evento.target === riferimento.current && !inCorso) {
      onChiudi()
    }
  }

  async function invia(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    setErrore(null)
    setInCorso(true)

    try {
      await azzeraPassword(persona.id, password)
      onFatto(
        `Password di ${persona.nome} ${persona.cognome} cambiata. ` +
          'Comunicagliela a voce: il gestionale non manda email.',
      )
    } catch (motivo) {
      // I casi dal server: WEAK_PASSWORD con il difetto preciso,
      // RESET_DI_SE_STESSI se è la propria utenza. `testo` toglie il codice.
      setErrore(testo(motivo))
      setInCorso(false)
    }
  }

  return (
    <dialog
      className="pannello"
      ref={riferimento}
      onCancel={(evento) => {
        if (inCorso) {
          evento.preventDefault()
          return
        }
        onChiudi()
      }}
      onClick={fuori}
    >
      <form className="modulo" onSubmit={invia}>
        <div className="testata">
          <h2>
            Nuova password per {persona.nome} {persona.cognome}
          </h2>
        </div>

        <p className="sommario">
          La password attuale non serve e non viene chiesta. Quella nuova non
          la riceve nessuno per email: dettagliela a voce, e invitala a
          cambiarla da «Profilo» al primo accesso.
        </p>

        <div className="campo">
          <label htmlFor="password-nuova">Password</label>
          <input
            id="password-nuova"
            // In chiaro di proposito: vedi il commento in testa al file.
            type="text"
            required
            autoFocus
            autoComplete="off"
            spellCheck={false}
            value={password}
            onChange={(evento) => setPassword(evento.target.value)}
            disabled={inCorso}
            aria-describedby="regole-password"
          />
          <p id="regole-password" className="nota">
            Almeno 8 caratteri, almeno un numero e almeno un carattere
            speciale.
          </p>
        </div>

        {errore !== null && (
          <p className="errore" role="alert">
            {errore}
          </p>
        )}

        <div className="azioni">
          <button type="button" onClick={onChiudi} disabled={inCorso}>
            Annulla
          </button>
          <button type="submit" className="secondario" disabled={inCorso}>
            {inCorso ? 'Cambio…' : 'Cambia password'}
          </button>
        </div>
      </form>
    </dialog>
  )
}
