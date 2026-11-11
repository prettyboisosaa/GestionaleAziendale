import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import {
  ATTIVITA,
  ATTIVITA_INIZIALE,
  ETICHETTA_ATTIVITA,
  RICHIEDE_LAVORO,
  RICHIEDE_TASK,
  annulla,
  correggi,
  durata,
  registraAttivita,
} from './api'
import type { RigaSessione, SituazioneTempo, TipoAttivita } from './api'
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
 * Un lavoro che un'attività può citare pur non avendone una task: la
 * manutenzione per il cliente X.
 *
 * Stesso criterio di `TaskScrivibile`: `RigaLavoro` è compatibile e si passa
 * così com'è, ma qui si dichiara soltanto quello che serve a disegnare
 * un'opzione.
 */
export type LavoroScrivibile = {
  id: number
  codice: string
  nome: string
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
 * Il pannello di una registrazione: la si scrive, la si corregge, la si
 * annulla.
 *
 * Si apre cliccando un blocco del calendario settimanale, o trascinando una
 * zona vuota. È un `<dialog>` nativo e non un componente di libreria: in
 * questo gestionale non c'è nessuna libreria di componenti, e `showModal()` dà
 * da sé la messa a fuoco intrappolata, la chiusura con Esc e lo sfondo — tre
 * cose che un `<div>` sovrapposto andrebbe a riscrivere peggio.
 *
 * **Si registra anche quello che non è una task.** Il campo *Attività* viene
 * prima di tutto il resto, perché decide che forma ha il modulo: con «Lavoro
 * su task» chiede la task, con una manutenzione cliente chiede il lavoro, con
 * una riunione interna non chiede né l'una né l'altro. È la cosa che prima non
 * si poteva dire, e che faceva appendere le ore di una riunione a una task che
 * non la riguardava.
 *
 * **Correggendo, la task non si cambia** — ma tipo, lavoro e descrizione sì.
 * `PUT /api/mio-tempo/sessioni/{id}` non accetta una task, di proposito: una
 * sessione è il tempo passato su *quella* task, e spostarlo su un'altra non è
 * correggere un orario, è dire che si è lavorato a qualcos'altro. La strada
 * per quello è annullare e riscrivere, che da qui si fa in due clic. Il tipo
 * invece si corregge, perché chi registra può sbagliare tendina e quel dato
 * non l'ha misurato nessuno.
 */
export default function PannelloSessione({
  apertura,
  task,
  lavori,
  onChiudi,
  onFatto,
}: {
  apertura: Apertura
  /** Le task aperte a cui chi guarda è assegnato. Serve solo scrivendo. */
  task: TaskScrivibile[]
  /** I lavori che chi guarda può vedere, per le attività che ne citano uno. */
  lavori: LavoroScrivibile[]
  onChiudi: () => void
  onFatto: (situazione: SituazioneTempo) => void
}) {
  const riferimento = useRef<HTMLDialogElement>(null)

  const sessione = apertura.tipo === 'correggi' ? apertura.sessione : null

  /**
   * Vero se la sessione che si corregge è agganciata a una task.
   *
   * Cambia la forma del modulo più di qualunque altra cosa: con una task il
   * lavoro si legge da lei e non si scelgono né l'una né l'altro, e il tipo
   * `LAVORO_TASK` resta disponibile. Senza, il lavoro si può citare e
   * `LAVORO_TASK` va tolto dalla tendina — il server lo rifiuterebbe, perché
   * una task da aggiungere non c'è.
   */
  const conTask = sessione !== null && sessione.taskId !== null

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
   * Che attività è. Correggendo parte da quello che la sessione dice;
   * scrivendo dal caso quotidiano, che è il lavoro su task.
   *
   * Qui un valore iniziale **c'è** e si preseleziona, al contrario della task:
   * il campo è obbligatorio, nove registrazioni su dieci sono lavoro su task,
   * e un «Scegli…» da aprire ogni volta sarebbe un clic in più per dire la
   * cosa più ovvia. Sbagliarlo per distrazione costa poco — si corregge dallo
   * stesso pannello — mentre sbagliare la task scriverebbe ore sul lavoro di
   * qualcun altro, e per quella il valore iniziale resta vuoto.
   */
  const [tipo, setTipo] = useState<TipoAttivita>(
    () => sessione?.tipoAttivita ?? ATTIVITA_INIZIALE,
  )
  /**
   * La task scelta, scrivendo. Non c'è un valore iniziale sensato da
   * indovinare: preselezionare la prima dell'elenco farebbe scrivere ore sulla
   * task sbagliata a chi salva senza guardare la tendina.
   */
  const [scelta, setScelta] = useState('')
  /** Il lavoro citato. Vuoto vuol dire «nessuno», che per molti tipi va bene. */
  const [sceltaLavoro, setSceltaLavoro] = useState(() =>
    sessione !== null && sessione.taskId === null && sessione.lavoroId !== null
      ? String(sessione.lavoroId)
      : '',
  )
  const [descrizione, setDescrizione] = useState(() => sessione?.descrizione ?? '')
  const [errore, setErrore] = useState<string | null>(null)
  const [inAttesa, setInAttesa] = useState(false)

  /** Una sessione che sta scorrendo non ha una fine da correggere. */
  const scorre = sessione !== null && sessione.chiusaIl === null

  /**
   * Che cosa il tipo scelto pretende. Le due regole sono anche sul server, e
   * stanno anche qui per decidere quale tendina mostrare: aprire un campo per
   * poi farlo rifiutare è il modo di far sembrare rotta una regola giusta.
   */
  const vuoleTask = RICHIEDE_TASK.includes(tipo)
  const vuoleLavoro = RICHIEDE_LAVORO.includes(tipo)

  /** La task si scrive solo in creazione: correggendo non si cambia. */
  const scegliLaTask = sessione === null && vuoleTask
  /**
   * Il lavoro si cita quando la task non c'è — né scelta né agganciata. Con
   * una task il lavoro è il suo, e una seconda tendina direbbe che si può
   * spostare altrove.
   */
  const scegliIlLavoro = !conTask && !scegliLaTask

  /**
   * I tipi offerti. Correggendo una sessione **senza** task, «Lavoro su task»
   * esce dall'elenco: non c'è una task da aggiungere, e il server lo
   * rifiuterebbe con un 400.
   */
  const tipiOfferti = ATTIVITA.filter(
    (uno) => !(sessione !== null && !conTask && RICHIEDE_TASK.includes(uno)),
  )

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

  /** La descrizione come va mandata: vuota vuol dire «non scritta», cioè null. */
  function testoScritto(): string | null {
    const pulita = descrizione.trim()
    return pulita === '' ? null : pulita
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

    const lavoroCitato = sceltaLavoro === '' ? null : Number(sceltaLavoro)
    if (vuoleLavoro && scegliIlLavoro && lavoroCitato === null) {
      setErrore(`«${ETICHETTA_ATTIVITA[tipo]}» vuole il lavoro a cui si riferisce.`)
      return
    }

    if (sessione !== null) {
      void agisci(() =>
        correggi(sessione.id, {
          tipoAttivita: tipo,
          // Con una task il lavoro è il suo: mandarne un altro sarebbe
          // chiedere di spostare la sessione, e questa non è la strada.
          lavoroId: conTask ? null : lavoroCitato,
          descrizione: testoScritto(),
          avviataIl: da,
          chiusaIl: a,
        }),
      )
      return
    }

    if (scegliLaTask) {
      const destinazione = task.find((una) => String(una.taskId) === scelta)
      if (destinazione === undefined) {
        setErrore('Scegli la task su cui hai lavorato.')
        return
      }
      void agisci(() =>
        registraAttivita({
          tipoAttivita: tipo,
          taskId: destinazione.taskId,
          lavoroId: null,
          descrizione: testoScritto(),
          avviataIl: da,
          chiusaIl: a,
        }),
      )
      return
    }

    void agisci(() =>
      registraAttivita({
        tipoAttivita: tipo,
        taskId: null,
        lavoroId: lavoroCitato,
        descrizione: testoScritto(),
        avviataIl: da,
        chiusaIl: a,
      }),
    )
  }

  return (
    <dialog className="pannello" ref={riferimento} onCancel={onChiudi}>
      <form className="modulo" onSubmit={salva}>
        <div className="testata">
          <h2>{titolo(sessione)}</h2>
          {sessione !== null && (
            <span className="nota">
              {sessione.codiceLavoro ?? ETICHETTA_ATTIVITA[sessione.tipoAttivita]}
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

        {/* L'attività sta per prima perché decide che forma ha il resto del
            modulo: quale tendina compare, e che cosa è obbligatorio. */}
        <div className="campo">
          <label htmlFor="attivita-sessione">Attività</label>
          <select
            id="attivita-sessione"
            value={tipo}
            onChange={(evento) => setTipo(evento.target.value as TipoAttivita)}
            disabled={scorre}
          >
            {tipiOfferti.map((uno) => (
              <option key={uno} value={uno}>
                {ETICHETTA_ATTIVITA[uno]}
              </option>
            ))}
          </select>
          {conTask && (
            <p className="nota">
              La task non si cambia da qui: se hai lavorato ad altro, annulla
              questa registrazione e riscrivila.
            </p>
          )}
        </div>

        {scegliLaTask && (
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
                Non hai task aperte a cui sei assegnato. Se quelle ore non erano
                lavoro su una task, scegli l’attività che le descrive.
              </p>
            )}
          </div>
        )}

        {scegliIlLavoro && !scorre && (
          <div className="campo">
            <label htmlFor="lavoro-sessione">Lavoro</label>
            <select
              id="lavoro-sessione"
              value={sceltaLavoro}
              onChange={(evento) => setSceltaLavoro(evento.target.value)}
              required={vuoleLavoro}
            >
              {/* L'opzione vuota c'è dove il lavoro è facoltativo: una
                  riunione può non riguardarne nessuno, e costringere a
                  scegliere farebbe citare un lavoro a caso. */}
              {!vuoleLavoro && <option value="">Nessuno</option>}
              {vuoleLavoro && <option value="">Scegli…</option>}
              {lavori.map((uno) => (
                <option key={uno.id} value={uno.id}>
                  {uno.codice} · {uno.nome}
                </option>
              ))}
            </select>
            {/* Queste ore si registrano, ma non erodono il budget con cui il
                lavoro è stato sviluppato: è una distinzione che chi compila ha
                diritto di conoscere, perché cambia cosa significa il numero. */}
            <p className="nota">
              {vuoleLavoro
                ? 'Queste ore restano registrate sul lavoro, ma non consumano il suo monte ore di sviluppo.'
                : 'Facoltativo: serve se quell’attività riguardava un lavoro preciso.'}
            </p>
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

            {/* Mai obbligatoria, nemmeno senza task: un campo che si deve
                riempire per forza si riempie con qualunque cosa, e una riga
                di testo inventata è peggio di nessuna riga. Il limite è
                quello della colonna, dichiarato invece di far troncare. */}
            <div className="campo">
              <label htmlFor="descrizione-sessione">Cosa hai fatto</label>
              <textarea
                id="descrizione-sessione"
                value={descrizione}
                onChange={(evento) => setDescrizione(evento.target.value)}
                maxLength={500}
                rows={3}
                placeholder="Facoltativo"
              />
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
              Annulla registrazione
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

/**
 * Che cosa si legge in testa al pannello.
 *
 * Scrivendo è una domanda, perché il modulo è vuoto. Correggendo è il titolo
 * della task, o — dove una task non c'è — l'etichetta dell'attività: è
 * l'unica cosa che nomini quella riga, e lasciare la domanda farebbe sembrare
 * di stare scrivendo una registrazione nuova.
 */
function titolo(sessione: RigaSessione | null): string {
  if (sessione === null) {
    return 'Che cosa hai fatto'
  }
  return sessione.task ?? ETICHETTA_ATTIVITA[sessione.tipoAttivita]
}
