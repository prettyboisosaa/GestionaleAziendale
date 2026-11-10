import { useCallback, useEffect, useState } from 'react'
import FullCalendar from '@fullcalendar/react'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import itLocale from '@fullcalendar/core/locales/it'
import type {
  DateSelectArg,
  DatesSetArg,
  EventClickArg,
  EventContentArg,
  EventInput,
} from '@fullcalendar/core'
import { durata, istanteLocale, miaSettimana } from './api'
import type { RigaSessione, SettimanaDiTempo, SituazioneTempo } from './api'
import PannelloSessione from './PannelloSessione'
import type { Apertura, TaskScrivibile } from './PannelloSessione'
import { testo } from '../api/http'
import { tintaLavoro } from '../stato/stati'

/**
 * La settimana lavorata, a fasce orarie.
 *
 * Il calendario che sta sopra, nella dashboard, guarda avanti: task per
 * scadenza, assenze, festività. Questo guarda indietro, e mostra dove sono
 * cadute le ore nella giornata — un'informazione che il database ha dalla V15
 * e che fino a qui nessuna schermata leggeva, perché del tempo misurato si
 * vedeva solo il totale di oggi.
 *
 * **Le ore visibili sono più larghe dell'orario di lavoro.** Si vede dalle 7
 * alle 21 benché si lavori dalle 9 alle 18, e la fascia fuori dall'orario è
 * velata invece che non disegnata: chi ha lavorato alle venti deve poterlo
 * vedere. Una griglia che si fermasse alle 18 gli direbbe che quell'ora non è
 * esistita, e il consuntivo di un lavoro perderebbe ore vere.
 *
 * **Il sabato e la domenica ci sono** per la stessa ragione. Non è un invito
 * a lavorarci: è che se è accaduto, il consuntivo lo deve sapere.
 */
export default function CalendarioSettimanale({
  tempo,
  task,
  onErrore,
  onScritto,
}: {
  /**
   * Serve come *segnale*, non come dato: cambia solo quando una sessione
   * viene avviata, messa in pausa o confermata, e allora la settimana va
   * riletta. In `App.tsx` non c'è nessun polling, quindi un cambio di questo
   * oggetto vuol dire che qualcosa è stato scritto davvero.
   */
  tempo: SituazioneTempo | null
  /**
   * Le task aperte a cui chi guarda è assegnato, per la tendina di chi scrive
   * a mano. Arrivano da chi sta sopra — la dashboard le ha già in
   * `RiepilogoDashboard.task` — invece di chiederle con una rotta propria.
   */
  task: TaskScrivibile[]
  onErrore: (messaggio: string) => void
  /**
   * Chiamata dopo una scrittura dal pannello. Non ricarica la settimana — a
   * quello ci pensa il cambio di `tempo` — ma serve a chi sta sopra per
   * rimettere in pari quello che la griglia non sa: la barra del timer in
   * testata e le ore consumate delle task nella lista.
   */
  onScritto: (situazione: SituazioneTempo) => void
}) {
  const [settimana, setSettimana] = useState<SettimanaDiTempo | null>(null)
  /** Un giorno qualunque della settimana mostrata. Lo decide il calendario. */
  const [giorno, setGiorno] = useState<string | null>(null)
  /** Perché il pannello è aperto, se è aperto. */
  const [apertura, setApertura] = useState<Apertura | null>(null)
  /**
   * Adesso, e serve solo a dare un'altezza alla sessione aperta. Sta in stato
   * e non si legge durante il disegno: un `new Date()` nel corpo darebbe un
   * risultato diverso a ogni ridisegno senza che il dato sia cambiato. È lo
   * stesso schema della barra del timer in testata.
   */
  const [adesso, setAdesso] = useState(() => Date.now())

  /** Vero se una sessione di questa settimana sta ancora scorrendo. */
  const scorre = (settimana?.sessioni ?? []).some(
    (sessione) => sessione.chiusaIl === null,
  )

  const carica = useCallback(
    (quale: string) =>
      miaSettimana(quale)
        .then(setSettimana)
        .catch((motivo: unknown) => onErrore(testo(motivo))),
    [onErrore],
  )

  // Si rilegge al cambio di settimana e dopo ogni scrittura del tempo. La
  // dipendenza da `tempo` è il motivo per cui premere Avvia nella lista qui
  // sotto fa comparire il blocco qui sopra senza ricaricare la pagina.
  useEffect(() => {
    if (giorno !== null) {
      void carica(giorno)
    }
  }, [giorno, tempo, carica])

  // Il blocco della sessione che scorre deve crescere, ma quando non ce n'è
  // una non c'è niente da ridisegnare: il tick parte solo allora, come in
  // testata, e mezzo minuto basta per una griglia a mezz'ore.
  useEffect(() => {
    if (!scorre) {
      return
    }
    const tick = setInterval(() => setAdesso(Date.now()), 30_000)
    return () => clearInterval(tick)
  }, [scorre])

  /**
   * FullCalendar avvisa a ogni cambio di periodo. `startStr` è il primo
   * giorno visibile, cioè un lunedì: al server basta un giorno qualunque
   * della settimana voluta, ed è lui a risalire al lunedì — la settimana la
   * decide un posto solo.
   */
  function periodoCambiato(arg: DatesSetArg) {
    setGiorno(arg.startStr.slice(0, 10))
    // Cambiare settimana con il pannello aperto lo lascerebbe su un giorno
    // che non è più in vista.
    setApertura(null)
  }

  /**
   * Un blocco cliccato si apre nel pannello.
   *
   * L'id dell'evento è quello della sessione, ma la sessione intera si
   * ripesca dalla risposta del server invece di ricostruirla dall'evento:
   * `origine` e `confermata` servono al pannello e non sono cose che si
   * leggono da un rettangolo.
   */
  function bloccoCliccato(arg: EventClickArg) {
    const trovata = settimana?.sessioni.find(
      (sessione) => String(sessione.id) === arg.event.id,
    )
    if (trovata !== undefined) {
      setApertura({ tipo: 'correggi', sessione: trovata })
    }
  }

  /**
   * Una zona vuota scelta apre il pannello già compilato con quelle ore.
   *
   * `select` e non `dateClick`: il primo restituisce un **intervallo** — un
   * clic secco vale uno slot, e trascinando si sceglie la durata — mentre il
   * secondo darebbe un istante, e la fine andrebbe indovinata. Riempire un
   * buco è dire «da qui a qui», ed è quel gesto che il pannello deve
   * ricevere già fatto.
   */
  function zonaScelta(arg: DateSelectArg) {
    // Si passa da `start`/`end`, che sono `Date`, e non da `startStr`:
    // quelle stringhe portano l'offset del fuso quando l'evento ha un'ora, e
    // tagliarle a fette funzionerebbe finché qualcuno non cambia un'opzione
    // del calendario. `istanteLocale` legge l'orologio da parete, che è
    // quello che il server si aspetta.
    const da = istanteLocale(arg.start)
    const a = istanteLocale(arg.end)

    setApertura({
      tipo: 'scrivi',
      giorno: da.slice(0, 10),
      inizio: da.slice(11, 16),
      fine: a.slice(11, 16),
    })
  }

  const eventi: EventInput[] = (settimana?.sessioni ?? []).map((sessione) => ({
    id: String(sessione.id),
    title: sessione.task,
    start: sessione.avviataIl,
    // Una sessione aperta si disegna fino ad adesso: è l'unico blocco della
    // griglia che sta ancora crescendo, e senza una fine non avrebbe altezza.
    end: sessione.chiusaIl ?? istanteLocale(new Date(adesso)),
    classNames: classiSessione(sessione),
    extendedProps: { codiceLavoro: sessione.codiceLavoro },
  }))

  const vuota = settimana !== null && settimana.sessioni.length === 0
  /** Fuori dalla settimana corrente la griglia si guarda e non si tocca. */
  const modificabile = settimana?.modificabile ?? false

  return (
    <>
      {settimana !== null && (
        <p className="totale-settimana">
          Dal {breve(settimana.inizio)} al {breve(settimana.fine)} hai registrato{' '}
          <strong>{durata(settimana.minutiTotali)}</strong>.
          {!modificabile && (
            // La ragione sta scritta: una griglia inerte senza spiegazione
            // sembra rotta.
            <> Questa settimana è chiusa: il tempo si sistema entro la settimana
              in cui si è lavorato.</>
          )}
        </p>
      )}

      <div
        className={
          modificabile
            ? 'scheda-calendario scheda-settimana'
            : 'scheda-calendario scheda-settimana settimana-chiusa'
        }
      >
        <FullCalendar
          plugins={[timeGridPlugin, interactionPlugin]}
          locale={itLocale}
          initialView="timeGridWeek"
          headerToolbar={{ left: 'prev,next today', center: 'title', right: '' }}
          buttonText={{ today: 'Questa settimana' }}
          firstDay={1}
          allDaySlot={false}
          nowIndicator
          slotMinTime="07:00:00"
          slotMaxTime="21:00:00"
          slotDuration="00:30:00"
          // L'orario di lavoro. Non limita niente: dice dov'è.
          businessHours={{
            daysOfWeek: [1, 2, 3, 4, 5],
            startTime: '09:00',
            endTime: '18:00',
          }}
          // Le sessioni di una persona non si sovrappongono — lo garantisce il
          // server — quindi un blocco può prendersi tutta la larghezza della
          // colonna invece di stringersi per far posto a un vicino che non
          // arriverà.
          slotEventOverlap={false}
          expandRows
          height="auto"
          events={eventi}
          eventContent={contenutoSessione}
          datesSet={periodoCambiato}
          eventClick={modificabile ? bloccoCliccato : undefined}
          // Trascinare una zona vuota apre il pannello. Spento fuori dalla
          // settimana corrente: una selezione che porta a un 409 è una
          // selezione che non andava permessa.
          selectable={modificabile}
          select={zonaScelta}
          selectMirror
          // Due limiti, entrambi presi dalle regole del server, per non far
          // compiere un gesto che finirebbe in un rifiuto.
          //
          // 1. Una sessione sta dentro UN giorno: trascinando di traverso fra
          //    le colonne si otterrebbe un intervallo su più giorni, che non è
          //    una sessione ma tante. `end` è esclusiva, da cui l'istante
          //    prima.
          // 2. Non si seleziona il futuro. È il limite che si incontra per
          //    primo, e di gran lunga: la fascia dell'orario di lavoro è
          //    evidenziata fino alle 18, quindi per mezza giornata il gesto
          //    più naturale — trascinare dentro la fascia — cadrebbe su ore
          //    che non sono ancora passate. Lasciarlo fare e poi rifiutarlo
          //    è il modo di far sembrare rotta una regola che è giusta.
          selectAllow={(periodo) =>
            periodo.start.toDateString()
              === new Date(periodo.end.getTime() - 1).toDateString()
            && periodo.end.getTime() <= Date.now()
          }
        />
      </div>

      {vuota && (
        <p className="settimana-vuota">Questa settimana non hai registrato niente.</p>
      )}

      {/* La nota sta sempre, non solo a griglia vuota: dice come si aggiunge
          una cosa, e soprattutto perché trascinando sul pomeriggio non accade
          niente. Il futuro non è selezionabile — è la regola del server, qui
          messa in parole invece di lasciarla scoprire a tentativi. */}
      {modificabile && (
        <p className="settimana-vuota">
          Hai lavorato a qualcosa senza avviare il timer? Trascina quelle ore
          sulla griglia. Si registra solo tempo già passato.
        </p>
      )}

      {apertura !== null && (
        <PannelloSessione
          apertura={apertura}
          task={task}
          onChiudi={() => setApertura(null)}
          onFatto={onScritto}
        />
      )}
    </>
  )
}

/**
 * Le classi di un blocco: la tinta del suo lavoro, più quello che c'è da dire
 * sulla qualità del dato.
 *
 * Si tratteggia il blocco **la cui ora del giorno non l'ha misurata nessuno**,
 * e su una griglia che colloca per ora del giorno è la distinzione che conta:
 *
 * - `AUTOMATICA` è una chiusura al tetto, e la sua ora di fine è una
 *   convenzione — il dipendente non c'era quando è stata scritta;
 * - `IMPORTATA` viene da una vecchia voce di dichiarazione, e di quelle righe
 *   la V16 dice che «era vera la durata, non l'ora del giorno»: le nove del
 *   mattino le ha scelte la migrazione.
 *
 * Entrambe contano nei totali — non contarle farebbe sparire lavoro vero dal
 * consuntivo di un lavoro — ma un blocco pieno rivendicherebbe una precisione
 * che quel dato non ha.
 */
function classiSessione(sessione: RigaSessione): string[] {
  const classi = ['sessione', tintaLavoro(sessione.lavoroId)]

  // Scritta a mano: l'ora del giorno l'ha scelta una persona a memoria. È una
  // classe sua e non `sessione-dedotta` perché le due cose non si somigliano
  // quanto il tratteggio suggerisce — là il sistema ha dedotto, qui qualcuno
  // ha ricordato — e un domani si potrebbero volere distinguibili.
  if (sessione.origine === 'MANUALE') {
    classi.push('sessione-manuale')
  }

  if (
    sessione.origine === 'AUTOMATICA'
    || sessione.origine === 'IMPORTATA'
    || !sessione.confermata
  ) {
    classi.push('sessione-dedotta')
  }
  if (sessione.chiusaIl === null) {
    classi.push('sessione-in-corso')
  }
  return classi
}

/**
 * Dentro il blocco: l'ora, la task e il codice del lavoro.
 *
 * Il codice c'è perché le tinte sono otto e i lavori possono essere più di
 * otto: quando due coincidono l'informazione non va persa — si legge invece
 * di vedersi, ma c'è.
 */
function contenutoSessione(arg: EventContentArg) {
  const codice = arg.event.extendedProps.codiceLavoro as string
  return (
    <>
      <div className="fc-event-time">{arg.timeText}</div>
      <div className="fc-event-title">
        {arg.event.title}
        <span className="lavoro-del-blocco">{codice}</span>
      </div>
    </>
  )
}

/** Da «2026-11-06» a «06/11». */
function breve(iso: string): string {
  const [, mese, giorno] = iso.split('-')
  return `${giorno}/${mese}`
}
