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
import {
  ETICHETTA_ATTIVITA,
  TINTA_ATTIVITA,
  durata,
  istanteLocale,
  miaSettimana,
} from './api'
import type { RigaSessione, SettimanaDiTempo, SituazioneTempo } from './api'
import PannelloSessione from './PannelloSessione'
import type { Apertura, LavoroScrivibile, TaskScrivibile } from './PannelloSessione'
import {
  ORA_LAVORO_FINE,
  ORA_LAVORO_INIZIO,
  ORA_SCALA_FINE,
  ORA_SCALA_INIZIO,
  fascia,
  scrollIniziale,
} from './orario'
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
  lavori,
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
  /**
   * I lavori visibili, per le attività che citano un lavoro senza averne una
   * task: la manutenzione per il cliente X. Arrivano da chi sta sopra, come
   * le task, e non da una rotta di questo componente.
   */
  lavori: LavoroScrivibile[]
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
    // Senza task il titolo è l'etichetta dell'attività: è la sola cosa che
    // nomini quel blocco, e un rettangolo senza titolo non si legge.
    title: sessione.task ?? ETICHETTA_ATTIVITA[sessione.tipoAttivita],
    start: sessione.avviataIl,
    // Una sessione aperta si disegna fino ad adesso: è l'unico blocco della
    // griglia che sta ancora crescendo, e senza una fine non avrebbe altezza.
    end: sessione.chiusaIl ?? istanteLocale(new Date(adesso)),
    classNames: classiSessione(sessione),
    extendedProps: {
      // In coda al titolo: il codice del lavoro se c'è, altrimenti niente —
      // un'attività interna non ne ha uno, e un trattino non direbbe nulla.
      codiceLavoro: sessione.codiceLavoro,
      // La descrizione finisce nel `title` del blocco, cioè nel suggerimento
      // del browser: su una griglia a fasce orarie un blocco di mezz'ora non
      // ha lo spazio per una riga di testo, e troncarla la renderebbe
      // illeggibile. Chi vuole leggerla apre il pannello o ci passa sopra.
      descrizione: sessione.descrizione,
      attivita: ETICHETTA_ATTIVITA[sessione.tipoAttivita],
    },
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
          // La scala arriva da `dashboard/orario.ts` e non è più scritta qui:
          // nella dashboard questa griglia sta sotto quella del pianificato, e
          // due blocchi alla stessa altezza devono essere la stessa ora. Due
          // valori in due file si sposterebbero l'uno senza l'altro.
          slotMinTime={fascia(ORA_SCALA_INIZIO)}
          slotMaxTime={fascia(ORA_SCALA_FINE)}
          slotDuration="00:30:00"
          // Si arriva all'ora corrente invece che alle sette del mattino: la
          // parte della giornata su cui si scrive è quella vicina ad adesso.
          scrollTime={scrollIniziale()}
          // L'orario di lavoro. Non limita niente: dice dov'è.
          businessHours={{
            daysOfWeek: [1, 2, 3, 4, 5],
            startTime: fascia(ORA_LAVORO_INIZIO).slice(0, 5),
            endTime: fascia(ORA_LAVORO_FINE).slice(0, 5),
          }}
          // Le sessioni di una persona non si sovrappongono — lo garantisce il
          // server — quindi un blocco può prendersi tutta la larghezza della
          // colonna invece di stringersi per far posto a un vicino che non
          // arriverà.
          slotEventOverlap={false}
          expandRows
          // Alta quanto il pannello che la contiene, e scorre dentro di sé:
          // con `auto` la griglia cresceva fino a quattordici ore di altezza
          // e la pagina scorreva per contenerla, che è esattamente ciò che la
          // dashboard a due colonne vuole evitare.
          height="100%"
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
          lavori={lavori}
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
 *
 * **La tinta dice il lavoro, non il tipo di attività** — tranne dove un lavoro
 * non c'è. Il colpo d'occhio che questa griglia esiste per dare è «su quali
 * progetti è andata la settimana», e colorare per tipo lo cancellerebbe
 * proprio nel caso quotidiano. Le attività che non appartengono a un lavoro
 * prendono invece una tinta del loro tipo: non avrebbero nessun colore, e un
 * blocco grigio in mezzo agli altri sembrerebbe un errore.
 */
function classiSessione(sessione: RigaSessione): string[] {
  const classi = ['sessione']

  const tintaDelTipo = TINTA_ATTIVITA[sessione.tipoAttivita]
  if (sessione.lavoroId !== null) {
    // Un lavoro c'è: la tinta è la sua, anche per una manutenzione. Che cosa
    // sia quell'ora lo dice il titolo del blocco, che senza una task porta
    // l'etichetta dell'attività.
    classi.push(tintaLavoro(sessione.lavoroId))
  } else if (tintaDelTipo !== null) {
    classi.push(tintaDelTipo)
  }

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
 * Dentro il blocco: l'ora, il titolo e il codice del lavoro.
 *
 * Il codice c'è perché le tinte sono otto e i lavori possono essere più di
 * otto: quando due coincidono l'informazione non va persa — si legge invece
 * di vedersi, ma c'è. Dove un lavoro non c'è non si scrive niente al suo
 * posto: un trattino occuperebbe lo spazio senza dire nulla.
 *
 * Il suggerimento del browser porta la descrizione, se qualcuno l'ha scritta.
 * Nel blocco non ci starebbe — mezz'ora di griglia sono poche righe di pixel
 * — e troncarla a tre parole la renderebbe meno utile di non averla.
 */
function contenutoSessione(arg: EventContentArg) {
  const codice = arg.event.extendedProps.codiceLavoro as string | null
  const descrizione = arg.event.extendedProps.descrizione as string | null
  const attivita = arg.event.extendedProps.attivita as string

  // Il titolo già nomina la task, oppure l'attività dove una task non c'è:
  // ripetere l'attività in quel secondo caso direbbe due volte la stessa cosa.
  const suggerimento = [arg.event.title === attivita ? null : attivita, descrizione]
    .filter((pezzo) => pezzo !== null)
    .join(' · ')

  return (
    <div title={suggerimento === '' ? undefined : suggerimento}>
      <div className="fc-event-time">{arg.timeText}</div>
      <div className="fc-event-title">
        {arg.event.title}
        {codice !== null && <span className="lavoro-del-blocco">{codice}</span>}
      </div>
    </div>
  )
}

/** Da «2026-11-06» a «06/11». */
function breve(iso: string): string {
  const [, mese, giorno] = iso.split('-')
  return `${giorno}/${mese}`
}
