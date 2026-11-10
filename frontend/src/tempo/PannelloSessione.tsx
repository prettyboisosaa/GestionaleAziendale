import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { annulla, correggi, durata, registraManuale } from './api'
import type { RigaSessione, SituazioneTempo } from './api'
import { testo } from '../api/http'

/**
 * Una task su cui chi guarda può registrare tempo.
 *
 * Il pannello dichiara quel poco che gli serve invece di importare `MiaTask`
 * dalla dashboard: `MiaTask` è compatibile e si passa così com'è, ma il tempo
 * non deve dipendere da che forma ha il riepilogo di un'altra schermata.
 */
export type TaskScrivibile = {
  taskId: number
  lavoroId: number
  titolo: string
  codiceLavoro: string
}

/**
 * Perché il pannello è aperto: per correggere una sessione che c'è, o per
 * scriverne una che non c'è.
 *
 * Un'unione e non due componenti, né un `sessione: RigaSessione | null` con
 * dei campi che a volte contano: i due casi condividono quasi tutto — gli
 * stessi campi, lo stesso modo di mostrare un errore, lo stesso ricaricamento
 * dopo — e quello che cambia è esattamente ciò che l'unione rende visibile.
 */
export type Apertura =
  | { tipo: 'correggi'; sessione: RigaSessione }
  | { tipo: 'scrivi'; giorno: string; inizio: string; fine: string }

/**
 * Il pannello di una sessione: la si scrive, la si corregge, la si annulla.
 *
 * Si apre cliccando un blocco del calendario settimanale, o trascinando una
 * zona vuota. È un `<dialog>` nativo e non un componente di libreria: in
 * questo gestionale non c'è nessuna libreria di componenti, e `showModal()` dà
 * da sé la messa a fuoco intrappolata, la chiusura con Esc e lo sfondo — tre
 * cose che un `<div>` sovrapposto andrebbe a riscrivere peggio.
 *
 * **Correggendo, la task non si cambia.** `PUT /api/mio-tempo/sessioni/{id}`
 * accetta due istanti e nient'altro, di proposito: una sessione è il tempo
 * passato su *quella* task, e spostarlo su un'altra non è correggere un
 * orario, è dire che si è lavorato a qualcos'altro. La strada per quello è
 * annullare e riscrivere, che da qui si fa in due clic.
 */
export default function PannelloSessione({
  apertura,
  task,
  onChiudi,
  onFatto,
}: {
  apertura: Apertura
  /** Le task aperte a cui chi guarda è assegnato. Serve solo scrivendo. */
  task: TaskScrivibile[]
  onChiudi: () => void
  onFatto: (situazione: SituazioneTempo) => void
}) {
  const riferimento = useRef<HTMLDialogElement>(null)

  const sessione = apertura.tipo === 'correggi' ? apertura.sessione : null

  const [giorno, setGiorno] = useState(() =>
    apertura.tipo === 'correggi'
      ? apertura.sessione.avviataIl.slice(0, 10)
      : apertura.giorno,
  )
  const [inizio, setInizio] = useState(() =>
    apertura.tipo === 'correggi'
      ? apertura.sessione.avviataIl.slice(11, 16)
      : apertura.inizio,
  )
  const [fine, setFine] = useState(() =>
    apertura.tipo === 'correggi'
      ? (apertura.sessione.chiusaIl?.slice(11, 16) ?? '')
      : apertura.fine,
  )
  /**
   * La task scelta, scrivendo. Non c'è un valore iniziale sensato da
   * indovinare: preselezionare la prima dell'elenco farebbe scrivere ore sulla
   * task sbagliata a chi salva senza guardare la tendina.
   */
  const [scelta, setScelta] = useState('')
  const [errore, setErrore] = useState<string | null>(null)
  const [inAttesa, setInAttesa] = useState(false)

  /** Una sessione che sta scorrendo non ha una fine da correggere. */
  const scorre = sessione !== null && sessione.chiusaIl === null

  // showModal() e non l'attributo `open`: solo il primo mette il dialogo nel
  // top layer, con lo sfondo e la messa a fuoco intrappolata.
  useEffect(() => {
    riferimento.current?.showModal()
  }, [])

  async function agisci(azione: () => Promise<SituazioneTempo>) {
    setInAttesa(true)
    setErrore(null)
    try {
      onFatto(await azione())
      onChiudi()
    } catch (motivo) {
      setErrore(testo(motivo))
    } finally {
      setInAttesa(false)
    }
  }

  function salva(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()

    // Questi due controlli sono anche sul server, e stanno anche qui perché
    // sono cose che si vedono guardando i campi: farli dire al server
    // costerebbe un giro di rete per sapere quello che si sa già.
    //
    // Fine uguale o precedente all'inizio vuol dire che la sessione
    // scavalcherebbe la mezzanotte, e da questa griglia non si scrive: le ore
    // visibili arrivano alle 21, e un blocco a cavallo di due giorni non
    // avrebbe dove disegnarsi. Capita al timer, non a chi compila.
    if (fine <= inizio) {
      setErrore('La fine deve venire dopo l’inizio.')
      return
    }

    const da = `${giorno}T${inizio}:00`
    const a = `${giorno}T${fine}:00`

    if (sessione !== null) {
      void agisci(() => correggi(sessione.id, da, a))
      return
    }

    const destinazione = task.find((una) => String(una.taskId) === scelta)
    if (destinazione === undefined) {
      setErrore('Scegli la task su cui hai lavorato.')
      return
    }

    void agisci(() =>
      registraManuale(destinazione.lavoroId, destinazione.taskId, da, a),
    )
  }

  return (
    <dialog className="pannello" ref={riferimento} onCancel={onChiudi}>
      <form className="modulo" onSubmit={salva}>
        <div className="testata">
          <h2>{sessione === null ? 'Che cosa hai fatto' : sessione.task}</h2>
          {sessione !== null && (
            <span className="nota">
              {sessione.codiceLavoro}
              {!scorre && ` · ${durata(sessione.minuti)}`}
            </span>
          )}
        </div>

        {sessione?.origine === 'AUTOMATICA' && (
          <p className="sommario">
            Questa sessione l’ha chiusa il sistema, perché era rimasta aperta
            oltre il massimo. L’ora di fine è una deduzione: se non è giusta,
            correggila.
          </p>
        )}

        {sessione?.origine === 'IMPORTATA' && (
          <p className="sommario">
            Questa sessione viene da una vecchia dichiarazione: di quelle righe
            era vera la durata, non l’ora del giorno.
          </p>
        )}

        {sessione === null && (
          <div className="campo">
            <label htmlFor="task-sessione">Task</label>
            <select
              id="task-sessione"
              value={scelta}
              onChange={(evento) => setScelta(evento.target.value)}
              required
            >
              <option value="">Scegli…</option>
              {task.map((una) => (
                <option key={una.taskId} value={una.taskId}>
                  {una.titolo} · {una.codiceLavoro}
                </option>
              ))}
            </select>
            {task.length === 0 && (
              <p className="nota">
                Non hai task aperte a cui sei assegnato: il tempo si registra
                solo su quelle.
              </p>
            )}
          </div>
        )}

        {scorre ? (
          <p className="sommario">
            La stai lavorando adesso. Mettila in pausa dalla lista delle task
            per poterne correggere gli orari.
          </p>
        ) : (
          <>
            <div className="campo">
              <label htmlFor="giorno-sessione">Giorno</label>
              <input
                id="giorno-sessione"
                type="date"
                value={giorno}
                onChange={(evento) => setGiorno(evento.target.value)}
                required
              />
            </div>

            <div className="campo-in-riga">
              <div className="campo">
                <label htmlFor="inizio-sessione">Dalle</label>
                <input
                  id="inizio-sessione"
                  type="time"
                  value={inizio}
                  onChange={(evento) => setInizio(evento.target.value)}
                  required
                />
              </div>

              <div className="campo">
                <label htmlFor="fine-sessione">Alle</label>
                <input
                  id="fine-sessione"
                  type="time"
                  value={fine}
                  onChange={(evento) => setFine(evento.target.value)}
                  required
                />
              </div>
            </div>
          </>
        )}

        {errore !== null && (
          <p className="errore" role="alert">
            {errore}
          </p>
        )}

        <div className="azioni">
          {!scorre && (
            <button type="submit" disabled={inAttesa}>
              {inAttesa ? 'Salvo…' : 'Salva'}
            </button>
          )}

          {/* Annullare non cancella: la riga resta, fuori dai conti. Vale
              anche su una sessione che sta scorrendo — un timer avviato per
              sbaglio si toglie così. Scrivendo non c'è niente da annullare. */}
          {sessione !== null && (
            <button
              type="button"
              className="secondario"
              disabled={inAttesa}
              onClick={() => void agisci(() => annulla(sessione.id))}
            >
              Annulla sessione
            </button>
          )}

          <button type="button" className="secondario" onClick={onChiudi}>
            Chiudi
          </button>
        </div>
      </form>
    </dialog>
  )
}
