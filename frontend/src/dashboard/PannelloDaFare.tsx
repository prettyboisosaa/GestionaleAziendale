import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import FullCalendar from '@fullcalendar/react'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import itLocale from '@fullcalendar/core/locales/it'
import type { EventClickArg, EventInput } from '@fullcalendar/core'
import { mioCalendario } from './api'
import type { EventoCalendario } from './api'
import Pannello from '../componenti/Pannello'
import { testo } from '../api/http'
import {
  ORA_LAVORO_FINE,
  ORA_LAVORO_INIZIO,
  ORA_SCALA_FINE,
  ORA_SCALA_INIZIO,
  fascia,
  scrollIniziale,
} from '../tempo/orario'
import { oggiIso, piuUnGiorno } from './orario'

/**
 * La giornata pianificata, in sola lettura.
 *
 * Guarda **avanti**: che cosa va fatto oggi. Il pannello sotto guarda indietro
 * — dove sono andate le ore. Le due domande sono diverse e per questo sono due
 * riquadri, non due viste dello stesso.
 *
 * **Sola lettura, di proposito.** Qui non si registra niente: da questo
 * pannello si va al lavoro di una task, e il tempo si scrive in quello sotto.
 * Un calendario su cui a volte si può scrivere e a volte no sarebbe peggio di
 * due calendari che dicono ciascuno cosa fa.
 *
 * Carica i propri dati da sé: un errore qui lascia in piedi gli altri tre
 * pannelli, che è il punto di averli separati.
 *
 * ⚠️ **Le scadenze stanno nella fascia a giornata intera**, in cima, e la
 * griglia delle ore resta vuota. Non è un difetto di questo componente: in
 * questo gestionale una task ha una data di inizio e una scadenza, non una
 * fascia oraria, quindi un lavoro «pianificato dalle 9 alle 11» non esiste
 * come dato. La griglia c'è perché è la stessa scala del pannello sotto — ed è
 * ciò che permette di leggere a colpo d'occhio il pianificato sopra e il
 * registrato sotto — e si riempirà da sé il giorno in cui la pianificazione
 * oraria diventerà un dato.
 */
export default function PannelloDaFare({
  /**
   * Cambia quando qualcosa altrove è stato scritto: un cambio di stato di una
   * task, una sessione registrata. Serve come *segnale* di ricarica, non come
   * dato — in questa applicazione non c'è nessun polling.
   */
  segnale,
}: {
  segnale: unknown
}) {
  const [eventi, setEventi] = useState<EventoCalendario[] | null>(null)
  const [errore, setErrore] = useState<string | null>(null)
  const navigate = useNavigate()

  const oggi = oggiIso()

  useEffect(() => {
    let annullato = false

    mioCalendario(oggi, oggi)
      .then((trovati) => {
        if (!annullato) {
          setEventi(trovati)
          setErrore(null)
        }
      })
      .catch((motivo: unknown) => {
        if (!annullato) {
          setErrore(testo(motivo))
        }
      })

    return () => {
      annullato = true
    }
  }, [oggi, segnale])

  const perIlCalendario: EventInput[] = (eventi ?? []).map((evento) => ({
    id: evento.id,
    title: evento.titolo,
    start: evento.inizio,
    // La fine dell'API è compresa, quella di FullCalendar no.
    end: piuUnGiorno(evento.fine),
    allDay: true,
    // Le festività tingono la giornata invece di occupare una riga: sono lo
    // sfondo del giorno, non una cosa da fare.
    display: evento.tipo === 'FESTIVITA' ? 'background' : 'auto',
    classNames: classiEvento(evento),
    extendedProps: { url: evento.url },
  }))

  function eventoCliccato(arg: EventClickArg) {
    arg.jsEvent.preventDefault()
    const url = arg.event.extendedProps.url as string | null | undefined
    if (url !== null && url !== undefined) {
      void navigate(url)
    }
  }

  const daFare = (eventi ?? []).filter((evento) => evento.tipo !== 'FESTIVITA')

  return (
    <Pannello
      titolo="Da fare oggi"
      sottotitolo="sola lettura · ora corrente"
      azioni={
        daFare.length > 0 ? (
          <span className="nota">
            {daFare.length === 1 ? '1 cosa' : `${daFare.length} cose`}
          </span>
        ) : undefined
      }
      carico={eventi === null && errore === null}
      errore={errore}
      classe="pannello-calendario"
    >
      <FullCalendar
        plugins={[timeGridPlugin, interactionPlugin]}
        locale={itLocale}
        initialView="timeGridDay"
        // Nessuna barra: il pannello mostra **oggi** e non si naviga. Il
        // titolo del giorno lo dice già l'intestazione del pannello, e tre
        // pulsanti su un riquadro di sola lettura inviterebbero a un gesto
        // che qui non porta da nessuna parte.
        headerToolbar={false}
        // La scala è la stessa del pannello sotto, e arriva da `orario.ts`
        // perché deve esserlo: due blocchi alla stessa altezza nei due
        // riquadri devono essere la stessa ora, altrimenti leggerli insieme
        // inganna. La griglia è alta quanto il pannello e scorre dentro di
        // sé, così la fascia di lavoro è quella che si vede senza che le ore
        // serali spariscano.
        slotMinTime={fascia(ORA_SCALA_INIZIO)}
        slotMaxTime={fascia(ORA_SCALA_FINE)}
        slotDuration="00:30:00"
        // L'orario di lavoro evidenziato. Non limita niente: dice dov'è.
        businessHours={{
          daysOfWeek: [1, 2, 3, 4, 5],
          startTime: fascia(ORA_LAVORO_INIZIO).slice(0, 5),
          endTime: fascia(ORA_LAVORO_FINE).slice(0, 5),
        }}
        // La linea dell'ora, che è metà del senso di questo pannello: dice
        // dove si è nella giornata senza doverlo contare.
        nowIndicator
        // Si arriva già all'ora corrente, non alle otto del mattino: alle
        // cinque del pomeriggio la parte della giornata che interessa è
        // quella che resta. Un'ora indietro, per vedere da dove si viene.
        scrollTime={scrollIniziale()}
        allDaySlot
        allDayText="Scadenze"
        expandRows
        height="100%"
        events={perIlCalendario}
        eventClick={eventoCliccato}
        // Niente selezione e niente trascinamento: è il pannello che si
        // guarda. Sono i valori di default, dichiarati perché la loro assenza
        // qui è una scelta e non una dimenticanza.
        selectable={false}
        editable={false}
      />
    </Pannello>
  )
}

/** Le classi CSS di un evento: tipo, stato e, se c'è, il conflitto. */
function classiEvento(evento: EventoCalendario): string[] {
  const classi = ['evento', `evento-${evento.tipo.toLowerCase()}`]
  if (evento.stato !== null) {
    classi.push(`evento-${evento.stato.toLowerCase().replace('_', '-')}`)
  }
  if (evento.conflitto) {
    classi.push('evento-conflitto')
  }
  return classi
}
