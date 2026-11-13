
import type { ReactNode } from 'react'
import Finestra, { useChiudiFinestra } from './Finestra'
import Icona from './Icona'
import type { NomeIcona } from './Icona'

/**
 * «Vuoi uscire?», «Eliminare l'evento?»: la finestra che fa fermare un attimo.
 *
 * È la forma della tavola 05 del design system: la pastiglia tonda con
 * l'icona in alto, la domanda, una riga che dice **cosa succede davvero**, e
 * due pulsanti in fondo con il principale a destra.
 *
 * <p>Le due varianti non cambiano solo colore. In quella `distruttiva` il
 * pulsante di destra è corallo scuro e la messa a fuoco iniziale resta su
 * «Annulla»: la finestra si apre per far fermare, non per far premere Invio
 * su quello che cancella. In quella `normale` il principale è l'inchiostro,
 * come ogni altra azione del gestionale.
 *
 * <p><strong>La descrizione non è decorazione.</strong> Nel gestionale quasi
 * niente si cancella davvero — un lavoro si annulla, una sessione esce dai
 * conti ma la riga resta — e la differenza fra «annullare» e «cancellare» la
 * deve dire questa riga, perché dal nome del pulsante non si vede. Dove
 * l'azione è davvero definitiva, si scrive che non si può tornare indietro.
 */
export default function ConfermaAzione({
  icona,
  variante = 'normale',
  titolo,
  descrizione,
  opzione,
  etichettaConferma,
  etichettaAnnulla = 'Annulla',
  inCorso = false,
  onConferma,
  onChiudi,
  angolo = false,
}: {
  icona: NomeIcona
  variante?: 'normale' | 'distruttiva'
  titolo: string
  descrizione: ReactNode
  /** La casella facoltativa fra la descrizione e i pulsanti. */
  opzione?: { etichetta: string; segnata: boolean; onCambia: (v: boolean) => void }
  etichettaConferma: string
  etichettaAnnulla?: string
  /** Vero mentre la richiesta è in volo: i pulsanti si spengono e non si chiude. */
  inCorso?: boolean
  onConferma: () => void
  onChiudi: () => void
  angolo?: boolean
}) {
  return (
    <Finestra
      onChiudi={onChiudi}
      chiudibileDaFuori={!inCorso}
      angolo={angolo}
      className={`conferma${variante === 'distruttiva' ? ' conferma-distruttiva' : ''}`}
      etichetta={titolo}
    >
      <Corpo
        icona={icona}
        variante={variante}
        titolo={titolo}
        descrizione={descrizione}
        opzione={opzione}
        etichettaConferma={etichettaConferma}
        etichettaAnnulla={etichettaAnnulla}
        inCorso={inCorso}
        onConferma={onConferma}
      />
    </Finestra>
  )
}

/**
 * Il contenuto, separato dal guscio per una ragione sola: `useChiudiFinestra`
 * legge un contesto che `Finestra` fornisce, e un componente non vede il
 * contesto che fornisce lui stesso. Deve starci dentro.
 */
function Corpo({
  icona,
  variante,
  titolo,
  descrizione,
  opzione,
  etichettaConferma,
  etichettaAnnulla,
  inCorso,
  onConferma,
}: {
  icona: NomeIcona
  variante: 'normale' | 'distruttiva'
  titolo: string
  descrizione: ReactNode
  opzione?: { etichetta: string; segnata: boolean; onCambia: (v: boolean) => void }
  etichettaConferma: string
  etichettaAnnulla: string
  inCorso: boolean
  onConferma: () => void
}) {
  const chiudi = useChiudiFinestra()

  return (
    <div className="conferma-corpo">
      <span className="conferma-segno" aria-hidden="true">
        <Icona nome={icona} />
      </span>

      <h2>{titolo}</h2>
      <p className="conferma-descrizione">{descrizione}</p>

      {opzione !== undefined && (
        <label className="conferma-opzione">
          <input
            type="checkbox"
            checked={opzione.segnata}
            disabled={inCorso}
            onChange={(evento) => opzione.onCambia(evento.target.checked)}
          />
          <span>{opzione.etichetta}</span>
        </label>
      )}

      <div className="azioni">
        {/* «Annulla» tiene la messa a fuoco iniziale, in entrambe le varianti:
            chi arriva qui con la tastiera e preme Invio non deve confermare
            per inerzia una cosa che si è aperta apposta per farlo fermare. */}
        <button
          type="button"
          className="secondario"
          autoFocus
          disabled={inCorso}
          onClick={() => chiudi()}
        >
          {etichettaAnnulla}
        </button>

        <button
          type="button"
          className={variante === 'distruttiva' ? 'distruttiva' : undefined}
          disabled={inCorso}
          // La conferma parte **dopo** l'animazione d'uscita: se partisse
          // subito, la pagina cambierebbe sotto un foglio ancora a metà
          // strada, e si vedrebbe.
          onClick={() => chiudi(onConferma)}
        >
          {inCorso ? 'Un attimo…' : etichettaConferma}
        </button>
      </div>
    </div>
  )
}

