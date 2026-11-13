import { useState } from 'react'
import type { FormEvent } from 'react'
import {
  ATTIVITA,
  ATTIVITA_INIZIALE,
  DESCRIZIONE_ATTIVITA,
  ETICHETTA_ATTIVITA,
  RICHIEDE_LAVORO,
  RICHIEDE_TASK,
  annulla,
  coloreAttivita,
  correggi,
  durata,
  istanteLocale,
  registraAttivita,
} from './api'
import type { RigaSessione, SituazioneTempo, TipoAttivita } from './api'
import { testo } from '../api/http'
import Finestra, { BottoneChiudi, BottoneDesisti } from '../componenti/Finestra'
import ConfermaAzione from '../componenti/ConfermaAzione'
import Icona from '../componenti/Icona'
import SelettoreData from '../componenti/SelettoreData'

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
   * L'ultimo giorno che il selettore lascia scegliere: oggi.
   *
   * In uno stato con inizializzatore pigro, e non calcolato mentre si
   * disegna: `new Date()` in mezzo al JSX darebbe un valore diverso a ogni
   * ridisegno senza che niente sia cambiato. Letto una volta all'apertura del
   * pannello, che è la finestra di tempo in cui quel valore conta — e se
   * qualcuno lo tenesse aperto oltre la mezzanotte, il server rifiuterebbe
   * comunque le ore di domani.
   */
  const [ultimoGiornoScrivibile] = useState(() =>
    istanteLocale(new Date()).slice(0, 10),
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
  /** Vero mentre la conferma d'annullamento è aperta sopra il pannello. */
  const [chiedoAnnullamento, setChiedoAnnullamento] = useState(false)

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
    <Finestra
      onChiudi={onChiudi}
      // Mentre la richiesta è in volo non si chiude da fuori: l'operazione
      // potrebbe essere già arrivata al server.
      chiudibileDaFuori={!inAttesa}
      className="finestra-modulo"
      etichetta={titolo(sessione)}
    >
      <>
      <form className="modulo-finestra" onSubmit={salva}>
        {/* L'occhiello sopra il titolo: dice **da dove** si è arrivati e in che
            stato è ciò che si sta guardando. Su una registrazione già salvata
            è l'unica cosa che distingue a colpo d'occhio una correzione da una
            scrittura nuova — i campi sono gli stessi, e senza l'occhiello la
            differenza la si capisce solo leggendo il titolo. */}
        <p className="finestra-occhiello">
          {sessione === null
            ? 'Calendario · nuova registrazione'
            : 'Calendario · evento salvato'}
        </p>

        <div className="finestra-testata">
          <h2>{titolo(sessione)}</h2>

          {/* Il pulsante tondo in alto a destra. Non sostituisce «Annulla» in
              fondo: chiudere e desistere sono lo stesso esito, ma chi ha
              scorso il modulo fino in fondo non deve risalire per uscirne, e
              chi lo apre per sbaglio non deve scendere. */}
          <BottoneChiudi disabilitato={inAttesa}>
            <Icona nome="chiudi" />
          </BottoneChiudi>
        </div>

        {sessione !== null && (
          <p className="finestra-sottotitolo">
            {sessione.codiceLavoro ?? ETICHETTA_ATTIVITA[sessione.tipoAttivita]}
            {!scorre && ` · ${durata(sessione.minuti)}`}
          </p>
        )}

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

        {/* --- «Attività»: che cos'era quel tempo ---------------------------

            Nella tavola questo è un campo solo, in cima. Qui dentro è uno di
            tre, perché il gestionale distingue cose che la tavola non doveva
            distinguere — ma **lo slot è uno**, e chi compila vede sempre un
            campo solo al primo posto:

              · scrivendo lavoro su task → la task, da scegliere;
              · correggendo una sessione con task → la task, in sola lettura,
                perché da qui non si sposta;
              · negli altri casi → il lavoro citato, facoltativo o no.

            Resta per primo perché decide la forma del resto del modulo. */}
        {conTask && sessione !== null && (
          <div className="campo">
            <label htmlFor="attivita-sessione">Attività</label>
            {/* Un campo spento e non un testo nudo: occupa lo stesso posto e
                la stessa forma del campo che si vedrebbe scrivendo, così il
                modulo non cambia struttura fra le due modalità. La nota qui
                sotto dice perché non si tocca. */}
            <input
              id="attivita-sessione"
              type="text"
              value={sessione.task ?? ETICHETTA_ATTIVITA[sessione.tipoAttivita]}
              readOnly
              disabled
            />
            <p className="nota">
              La task non si cambia da qui: se hai lavorato ad altro, annulla
              questa registrazione e riscrivila.
            </p>
          </div>
        )}

        {scegliLaTask && (
          <div className="campo">
            <label htmlFor="attivita-sessione">Attività</label>
            {/* Resta un `<select>` nativo: le voci sono titoli di task, cioè
                nomi, e per i nomi il controllo del sistema è migliore di
                qualunque cosa si possa riscrivere — porta la tastiera con sé
                e sul telefono apre la ruota a cui la gente è abituata. */}
            <select
              id="attivita-sessione"
              value={scelta}
              onChange={(evento) => setScelta(evento.target.value)}
              required
            >
              <option value="">Scegli la task…</option>
              {task.map((una) => (
                <option key={una.taskId} value={una.taskId}>
                  {una.titolo} · {una.codiceLavoro}
                </option>
              ))}
            </select>
            {task.length === 0 && (
              <p className="nota">
                Non hai task aperte a cui sei assegnato. Se quelle ore non erano
                lavoro su una task, scegli qui sotto il tipo che le descrive.
              </p>
            )}
          </div>
        )}

        {scegliIlLavoro && !scorre && (
          <div className="campo">
            <label htmlFor="attivita-sessione">Attività</label>
            <select
              id="attivita-sessione"
              value={sceltaLavoro}
              onChange={(evento) => setSceltaLavoro(evento.target.value)}
              required={vuoleLavoro}
            >
              {/* L'opzione vuota c'è dove il lavoro è facoltativo: una
                  riunione può non riguardarne nessuno, e costringere a
                  scegliere farebbe citare un lavoro a caso. */}
              {!vuoleLavoro && <option value="">Nessun lavoro collegato</option>}
              {vuoleLavoro && <option value="">Scegli il lavoro…</option>}
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

        {/* --- «Tipo di attività»: le pastiglie ------------------------------

            Pastiglie e non più una tendina, come nella tavola. Il guadagno è
            che **i tipi si vedono tutti insieme**: in una tendina chiusa si
            legge solo quello scelto, e chi non sa che esiste «Sopralluogo»
            continua a registrare «Altro» — che è il modo in cui un consuntivo
            diventa illeggibile.

            Quello che la tendina sapeva fare e qui andava ritrovato:
            · **il colore**, che resta, come punto dentro ogni pastiglia: è la
              tinta con cui quelle ore compariranno nella griglia;
            · **la descrizione**, che non può stare in dieci pastiglie senza
              farne dieci paragrafi: compare sotto, e riguarda quella scelta.
              Si legge nel momento in cui serve, cioè dopo aver scelto. */}
        <fieldset className="campo gruppo-pastiglie" disabled={scorre}>
          <legend>Tipo di attività</legend>

          <div className="pastiglie-tipo">
            {tipiOfferti.map((uno) => {
              const colore = coloreAttivita(uno)
              return (
                <button
                  key={uno}
                  type="button"
                  // `aria-pressed` e non `aria-checked`: sono pulsanti che
                  // restano premuti, ed è il ruolo che un lettore di schermo
                  // annuncia correttamente senza che servano i ruoli di un
                  // gruppo radio riscritto a mano.
                  aria-pressed={tipo === uno}
                  className={`pastiglia-tipo${tipo === uno ? ' pastiglia-tipo-scelta' : ''}`}
                  onClick={() => setTipo(uno)}
                >
                  {colore !== undefined && (
                    <span
                      className="pastiglia-punto"
                      style={{ backgroundColor: colore }}
                      aria-hidden="true"
                    />
                  )}
                  {ETICHETTA_ATTIVITA[uno]}
                </button>
              )
            })}
          </div>

          <p className="nota">{DESCRIZIONE_ATTIVITA[tipo]}</p>
        </fieldset>

        {scorre ? (
          <p className="sommario">
            La stai lavorando adesso. Mettila in pausa dalla lista delle task
            per poterne correggere gli orari.
          </p>
        ) : (
          <>
            {/* Giorno, dalle e alle su una riga sola, come nella tavola: sono
                tre pezzi della stessa frase — «il 12 novembre dalle 9:30 alle
                11» — e messi uno sotto l'altro si leggevano come tre domande
                separate. */}
            <div className="campi-riga">
              <div className="campo">
                <label htmlFor="giorno-sessione">Giorno</label>
                {/* Il `SelettoreData` al posto dell'`<input type="date">`: il
                    giorno si sceglie quasi sempre **guardando la settimana** —
                    «quel martedì in cui ero dal cliente» — e il campo nativo lo
                    fa digitare a cifre.

                    Niente date future: si registra solo tempo già passato, ed è
                    la stessa regola che il server fa valere. Dirla qui vuol
                    dire che i giorni di domani sono spenti nella griglia,
                    invece di essere scelti e poi rifiutati. */}
                <SelettoreData
                  id="giorno-sessione"
                  valore={giorno}
                  onCambia={setGiorno}
                  max={ultimoGiornoScrivibile}
                  obbligatorio
                />
              </div>

              <div className="campo campo-ora">
                <label htmlFor="inizio-sessione">Dalle</label>
                <input
                  id="inizio-sessione"
                  type="time"
                  value={inizio}
                  onChange={(evento) => setInizio(evento.target.value)}
                  required
                />
              </div>

              <div className="campo campo-ora">
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
                di testo inventata è peggio di nessuna riga. */}
            <div className="campo">
              <label htmlFor="descrizione-sessione">
                Descrizione <span className="etichetta-tenue">(facoltativa)</span>
              </label>
              <textarea
                id="descrizione-sessione"
                value={descrizione}
                onChange={(evento) => setDescrizione(evento.target.value)}
                maxLength={500}
                rows={3}
                placeholder="Dettagli utili per chi legge il consuntivo"
              />
            </div>
          </>
        )}

        {errore !== null && (
          <p className="errore" role="alert">
            {errore}
          </p>
        )}

        {/* Il piede della tavola: «Annulla» di contorno e il principale a
            destra. L'annullamento della registrazione **non sta qui in mezzo**
            — sta a sinistra, staccato, e non assomiglia agli altri due: è
            l'unica azione del modulo che porta via qualcosa, e messa in fila
            con «Salva» si preme per inerzia. */}
        <div className="azioni-modulo">
          {sessione !== null && (
            <button
              type="button"
              className="azione-tenue-distruttiva"
              disabled={inAttesa}
              onClick={() => setChiedoAnnullamento(true)}
            >
              Annulla registrazione
            </button>
          )}

          <BottoneDesisti disabilitato={inAttesa} />

          {!scorre && (
            <button type="submit" className="accento" disabled={inAttesa}>
              {inAttesa ? 'Salvo…' : sessione === null ? 'Registra' : 'Salva modifiche'}
            </button>
          )}
        </div>
      </form>

      {/* La conferma, sopra il pannello. Un `<dialog>` dentro un altro
          funziona: il top layer li impila, e il secondo copre il primo senza
          che serva niente di speciale.

          Esiste perché annullare una registrazione **porta via del tempo
          misurato**, e dalla griglia la stessa azione passa già da una
          conferma: offrirla senza da qui vorrebbe dire che la stessa cosa è
          pericolosa o no a seconda di dove la si preme. */}
      {chiedoAnnullamento && sessione !== null && (
        <ConfermaAzione
          icona="cestino"
          variante="distruttiva"
          titolo="Annullare questa registrazione?"
          descrizione={
            <>
              <strong>{durata(sessione.minuti)}</strong> escono dai conti
              {sessione.codiceLavoro !== null ? ` di ${sessione.codiceLavoro}` : ''}.
              La riga non viene cancellata — resta nello storico, marcata come
              annullata — ma quelle ore non contano più da nessuna parte.
            </>
          }
          etichettaConferma="Annulla la registrazione"
          etichettaAnnulla="Lascia stare"
          inCorso={inAttesa}
          onConferma={() => {
            setChiedoAnnullamento(false)
            void agisci(() => annulla(sessione.id))
          }}
          onChiudi={() => setChiedoAnnullamento(false)}
        />
      )}
      </>
    </Finestra>
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
