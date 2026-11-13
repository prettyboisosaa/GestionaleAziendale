import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
} from 'react'
import type { ReactNode } from 'react'
import Icona from './Icona'
import type { NomeIcona } from './Icona'
import { movimentoRidotto } from './movimento'

/**
 * Le notifiche rapide: la pastiglia che compare in basso e se ne va da sola.
 *
 * È la tavola 05 del design system, riquadro «notifiche rapide»: quattro toni
 * — riuscito, attenzione, informazione, errore — ciascuno con la sua pastiglia
 * tonda a sinistra e, quando serve, **un'azione a destra**: «Annulla» su
 * un'operazione appena fatta, «Riprova» su una fallita.
 *
 * <p><strong>Non è un sistema di notifiche.</strong> Il gestionale non ne ha
 * uno e non lo avrà qui: non c'è niente che arrivi dal server senza che
 * qualcuno abbia premuto un pulsante, nessuna coda, nessun non letto. Questo
 * dice soltanto com'è finita la cosa che si è appena fatta, e scompare. Ciò
 * che una notifica vera annuncerebbe continua a stare in evidenza nelle
 * schermate, dov'era.
 *
 * <p>Perché serviva: fino a ieri l'esito di un'azione era un `.avviso` che
 * compariva **in cima alla pagina**. Chi premeva «Rimuovi» in fondo a una
 * tabella lunga riceveva la conferma fuori dallo schermo, e la cosa sembrava
 * non aver funzionato — tanto che l'unico rimedio era ricaricare per
 * controllare. Un messaggio che nasce dove non si sta guardando non è un
 * messaggio.
 *
 * <p>L'errore è l'eccezione che resta: gli altri toni se ne vanno dopo cinque
 * secondi, un errore no. Chi deve rileggere che cosa non ha funzionato — e
 * magari premere «Riprova» — non deve rincorrere la pastiglia.
 */

export type TonoNotifica = 'successo' | 'attenzione' | 'informazione' | 'errore'

type Azione = { etichetta: string; onPremi: () => void }

type Notifica = {
  id: number
  tono: TonoNotifica
  testo: string
  azione?: Azione
  /** Vero mentre scorre l'animazione d'uscita. */
  esce: boolean
}

type Opzioni = {
  azione?: Azione
  /** Quanto resta, in millisecondi. `null` per non farla sparire mai. */
  durata?: number | null
}

type ApiNotifiche = {
  successo: (testo: string, opzioni?: Opzioni) => void
  attenzione: (testo: string, opzioni?: Opzioni) => void
  informazione: (testo: string, opzioni?: Opzioni) => void
  errore: (testo: string, opzioni?: Opzioni) => void
}

const DURATA = 5000

/** Quante se ne tengono sullo schermo insieme. Oltre, le più vecchie escono. */
const QUANTE = 4

const Contesto = createContext<ApiNotifiche | null>(null)

/**
 * Le notifiche, da qualunque punto dell'albero.
 *
 * Fuori dal `ContenitoreNotifiche` non esplode: restituisce un'api che non fa
 * niente. Un componente non deve rompersi in un test perché il provider non
 * c'è — e un messaggio di conferma non vale una schermata bianca.
 */
export function useNotifiche(): ApiNotifiche {
  const api = useContext(Contesto)
  return api ?? VUOTA
}

const VUOTA: ApiNotifiche = {
  successo: () => {},
  attenzione: () => {},
  informazione: () => {},
  errore: () => {},
}

const SEGNI: Record<TonoNotifica, NomeIcona> = {
  successo: 'spunta-cerchio',
  attenzione: 'attenzione',
  informazione: 'informazione',
  errore: 'errore',
}

export default function ContenitoreNotifiche({ children }: { children: ReactNode }) {
  const [righe, setRighe] = useState<Notifica[]>([])

  // Il contatore degli identificativi. In un ref perché due notifiche aperte
  // nello stesso istante non devono poter ricevere lo stesso numero, e uno
  // stato aggiornato in modo asincrono non lo garantisce.
  const prossimo = useRef(1)

  const porta = useCallback((id: number) => {
    setRighe((prima) => prima.filter((una) => una.id !== id))
  }, [])

  /** Avvia l'uscita, e toglie la riga quando l'animazione è finita. */
  const congeda = useCallback(
    (id: number) => {
      if (movimentoRidotto()) {
        porta(id)
        return
      }
      setRighe((prima) =>
        prima.map((una) => (una.id === id ? { ...una, esce: true } : una)),
      )
    },
    [porta],
  )

  const aggiungi = useCallback(
    (tono: TonoNotifica, testo: string, opzioni?: Opzioni) => {
      const id = prossimo.current++
      const durata =
        opzioni?.durata !== undefined
          ? opzioni.durata
          : // L'errore resta finché non lo si chiude: vedi il commento in testa.
            tono === 'errore'
            ? null
            : DURATA

      setRighe((prima) => {
        const tenute = prima.slice(-(QUANTE - 1))
        return [...tenute, { id, tono, testo, azione: opzioni?.azione, esce: false }]
      })

      if (durata !== null) {
        window.setTimeout(() => congeda(id), durata)
      }
    },
    [congeda],
  )

  const api = useMemo<ApiNotifiche>(
    () => ({
      successo: (testo, opzioni) => aggiungi('successo', testo, opzioni),
      attenzione: (testo, opzioni) => aggiungi('attenzione', testo, opzioni),
      informazione: (testo, opzioni) => aggiungi('informazione', testo, opzioni),
      errore: (testo, opzioni) => aggiungi('errore', testo, opzioni),
    }),
    [aggiungi],
  )

  return (
    <Contesto.Provider value={api}>
      {children}

      {/* `aria-live="polite"` e non `assertive`: chi usa un lettore di schermo
          deve sentire l'esito quando ha finito la frase in corso, non al
          posto suo. `role="status"` lo dice ai browser che non leggono
          aria-live da solo. */}
      <div className="notifiche" role="status" aria-live="polite">
        {righe.map((una) => (
          <div
            key={una.id}
            className={`notifica notifica-${una.tono}${una.esce ? ' notifica-esce' : ''}`}
            onAnimationEnd={(evento) => {
              // Come nella finestra: l'evento risale dai figli, e qui dentro
              // la pastiglia dell'icona ha una sua animazione d'entrata.
              if (evento.target !== evento.currentTarget) return
              if (una.esce) porta(una.id)
            }}
          >
            <span className="notifica-segno" aria-hidden="true">
              <Icona nome={SEGNI[una.tono]} />
            </span>

            <p className="notifica-testo">{una.testo}</p>

            {una.azione !== undefined && (
              <button
                type="button"
                className="notifica-azione"
                onClick={() => {
                  una.azione?.onPremi()
                  congeda(una.id)
                }}
              >
                {una.azione.etichetta}
              </button>
            )}

            <button
              type="button"
              className="notifica-chiudi"
              // Il nome dice cosa si chiude, non «chiudi»: in un elenco di
              // quattro pastiglie, quattro pulsanti chiamati tutti «chiudi»
              // non si distinguono.
              aria-label={`Chiudi: ${una.testo}`}
              onClick={() => congeda(una.id)}
            >
              <Icona nome="chiudi" />
            </button>
          </div>
        ))}
      </div>
    </Contesto.Provider>
  )
}
