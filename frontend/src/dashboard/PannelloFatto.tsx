import Pannello from '../componenti/Pannello'
import CalendarioSettimanale from '../tempo/CalendarioSettimanale'
import type { ManicoSettimana } from '../tempo/CalendarioSettimanale'
import { Punto } from '../componenti/Stato'
import type { LavoroScrivibile, TaskScrivibile } from '../tempo/PannelloSessione'
import type { SituazioneTempo } from '../tempo/api'

/**
 * Le ore registrate, sulla stessa scala del pannello sopra.
 *
 * Guarda **indietro**: dove sono andate le ore. Il pannello sopra guarda
 * avanti, e la scala oraria è la stessa — arriva da `tempo/orario.ts` — così
 * il pianificato e il registrato si leggono uno sull'altro.
 *
 * **Il contatore della giornata non è più qui.** Stava in queste azioni e
 * adesso sta in testata alla pagina, accanto al saluto, dove il design system
 * lo mette: è il numero che si guarda per primo entrando, non una proprietà di
 * questo riquadro. Al suo posto c'è la legenda delle tinte, che invece spiega
 * proprio la griglia qui sotto.
 */
export default function PannelloFatto({
  tempo,
  task,
  lavori,
  onErrore,
  onScritto,
  manico,
}: {
  tempo: SituazioneTempo | null
  task: TaskScrivibile[]
  lavori: LavoroScrivibile[]
  onErrore: (messaggio: string) => void
  onScritto: (situazione: SituazioneTempo) => void
  /** Per il pulsante «Registra ore» che sta in testata alla pagina. */
  manico?: React.Ref<ManicoSettimana>
}) {
  return (
    <Pannello
      titolo="Cosa ho fatto"
      // Il piano intitola questo riquadro «Cosa ho fatto oggi», ma la griglia
      // qui sotto è **la settimana**, e dirle «oggi» mentre mostra sette
      // colonne sarebbe un'etichetta che smentisce quello che si vede. Resta
      // la settimana perché il tempo si corregge entro la settimana in cui si
      // è lavorato — è la finestra del server — e una vista a un giorno solo
      // toglierebbe il modo di rimediare al giovedì dimenticato. Il contatore
      // accanto dice la giornata, che è il numero che interessa adesso.
      sottotitolo="la settimana · trascina sulla griglia per registrare le ore"
      azioni={<Legenda />}
      classe="pannello-calendario"
    >
      <CalendarioSettimanale
        ref={manico}
        tempo={tempo}
        task={task}
        lavori={lavori}
        onErrore={onErrore}
        onScritto={onScritto}
      />
    </Pannello>
  )
}

/**
 * I tre colori della griglia, spiegati in tre parole.
 *
 * Nel design system la legenda sta nella testata della scheda del calendario,
 * ed è lì che serve: i blocchi sono tinti per lavoro e per tipo di attività, e
 * senza una chiave quelle tinte sono decorazione. Riusa `Punto`, che è già la
 * grammatica con cui questo gestionale dice «questo colore vale questa cosa».
 */
function Legenda() {
  return (
    <span className="legenda-calendario">
      <span className="legenda-voce">
        <Punto tinta="in-corso" titolo="Tinta delle task" /> Task
      </span>
      <span className="legenda-voce">
        <Punto tinta="assenza" titolo="Tinta delle attività" /> Attività
      </span>
      <span className="legenda-voce">
        <Punto tinta="attesa" titolo="Tinta delle ore da confermare" /> Da confermare
      </span>
    </span>
  )
}
