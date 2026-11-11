import Pannello from '../componenti/Pannello'
import CalendarioSettimanale from '../tempo/CalendarioSettimanale'
import type { LavoroScrivibile, TaskScrivibile } from '../tempo/PannelloSessione'
import { durata } from '../tempo/api'
import type { SituazioneTempo } from '../tempo/api'
import { MINUTI_PREVISTI, ORE_PREVISTE } from '../tempo/orario'

/**
 * Le ore registrate, sulla stessa scala del pannello sopra.
 *
 * Guarda **indietro**: dove sono andate le ore. Il pannello sopra guarda
 * avanti, e la scala oraria è la stessa — arriva da `tempo/orario.ts` — così
 * il pianificato e il registrato si leggono uno sull'altro.
 *
 * L'intestazione porta il contatore della giornata: quanto si è registrato su
 * quanto se ne aspetta. È la cosa che dice se c'è ancora qualcosa da scrivere
 * prima di andare a casa, e prima stava in una riga di prosa in mezzo alla
 * pagina, dove per leggerla bisognava cercarla.
 */
export default function PannelloFatto({
  tempo,
  task,
  lavori,
  onErrore,
  onScritto,
}: {
  tempo: SituazioneTempo | null
  task: TaskScrivibile[]
  lavori: LavoroScrivibile[]
  onErrore: (messaggio: string) => void
  onScritto: (situazione: SituazioneTempo) => void
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
      azioni={tempo === null ? undefined : <Contatore tempo={tempo} />}
      classe="pannello-calendario"
    >
      <CalendarioSettimanale
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
 * «5h / 8h», verde a giornata completa.
 *
 * Conta i minuti **chiusi** di oggi, che sono quelli che il server dichiara
 * registrati. La sessione che sta scorrendo adesso non entra nel numero —
 * crescerebbe mentre si guarda, e il contatore direbbe una cosa diversa a
 * ogni ridisegno — ma la sua presenza si dice accanto: senza, a chi ha il
 * timer avviato il totale sembrerebbe fermo per sbaglio.
 *
 * Verde solo da giornata piena: un contatore che si colora a metà strada
 * smette di dire «fatto» e comincia a dire «stai andando», che è una cosa che
 * il numero già dice da sé.
 */
function Contatore({ tempo }: { tempo: SituazioneTempo }) {
  const completa = tempo.minutiOggi >= MINUTI_PREVISTI

  return (
    <span className={completa ? 'contatore-ore completa' : 'contatore-ore'}>
      <span className="nota">oggi </span>
      <strong>{durata(tempo.minutiOggi)}</strong>
      <span className="nota"> / {ORE_PREVISTE}h</span>
      {tempo.inCorso !== null && (
        <span className="nota"> · più quella in corso</span>
      )}
    </span>
  )
}
