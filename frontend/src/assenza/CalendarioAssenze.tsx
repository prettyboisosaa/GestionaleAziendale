import { useCallback, useEffect, useState } from 'react'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import itLocale from '@fullcalendar/core/locales/it'
import type { DatesSetArg, EventInput } from '@fullcalendar/core'
import { calendarioAssenze } from './api'
import type { RigaCalendarioAssenza } from './api'
import Pannello from '../componenti/Pannello'
import { tintaPersona } from '../stato/stati'
import { testo } from '../api/http'

/**
 * Chi sarà via, mese per mese.
 *
 * È l'informazione che mancava a chi approva: l'elenco delle richieste dice
 * quali esistono, ma per sapere se nella settimana del 14 c'è già mezzo
 * ufficio in ferie bisognava leggere le righe e tenere le date a mente.
 *
 * **Le approvate sono piene, quelle in attesa tratteggiate.** Le prime sono un
 * fatto su cui pianificare, le seconde un rischio da vedere *prima* di
 * decidere — ed è proprio guardandole che si decide. Il tratteggio in questa
 * interfaccia significa già «dato non definitivo»: lo usa il calendario del
 * tempo per le ore che nessun orologio ha misurato.
 *
 * `dayGridMonth` viene da `@fullcalendar/daygrid`, che era già nel
 * `package.json` e non era mai stato importato da nessuno: nessuna dipendenza
 * nuova.
 */
export default function CalendarioAssenze() {
  const [righe, setRighe] = useState<RigaCalendarioAssenza[] | null>(null)
  const [errore, setErrore] = useState<string | null>(null)
  /** Il periodo che il calendario ha in vista, deciso da `datesSet`. */
  const [periodo, setPeriodo] = useState<{ da: string; a: string } | null>(null)

  /**
   * FullCalendar avvisa a ogni cambio di mese. `endStr` è **esclusivo**,
   * mentre la rotta vuole estremi compresi: da qui il giorno tolto.
   */
  const periodoCambiato = useCallback((arg: DatesSetArg) => {
    setPeriodo({
      da: arg.startStr.slice(0, 10),
      a: menoUnGiorno(arg.endStr.slice(0, 10)),
    })
  }, [])

  useEffect(() => {
    if (periodo === null) {
      return
    }
    let annullato = false

    calendarioAssenze(periodo.da, periodo.a)
      .then((trovate) => {
        if (!annullato) {
          setRighe(trovate)
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
  }, [periodo])

  const eventi: EventInput[] = (righe ?? []).map((una) => ({
    id: String(una.id),
    // Il nome prima del tipo: la domanda a cui il calendario risponde è «chi
    // non c'è», e il tipo è la precisazione — un permesso di due ore non pesa
    // come una settimana di ferie.
    title: `${una.richiedente} · ${una.tipoEtichetta}`,
    start: una.dataInizio,
    // ⚠️ `dataFine` è **compresa** e FullCalendar vuole una fine esclusiva per
    // gli eventi a giornata intera: senza il giorno in più, l'ultimo giorno di
    // ogni assenza sparisce dal calendario. È la stessa trappola che
    // `piuUnGiorno` commenta nella dashboard.
    end: piuUnGiorno(una.dataFine),
    allDay: true,
    classNames: [
      'assenza-evento',
      una.stato === 'IN_ATTESA' ? 'assenza-in-attesa' : 'assenza-approvata',
      // Un colore per persona: con cinque colleghi via nella stessa settimana,
      // rettangoli tutti della stessa tinta costringono a leggere ogni nome per
      // capire quante persone sono davvero fuori — e tre giorni di una stessa
      // persona sembrano tre colleghi in ferie. La tinta viene dal nome, quindi
      // non cambia da un mese all'altro.
      tintaPersona(una.richiedente),
    ],
  }))

  const inAttesa = (righe ?? []).filter((una) => una.stato === 'IN_ATTESA').length

  return (
    <Pannello
      titolo="Chi è via"
      sottotitolo="un colore per persona · approvate piene, in attesa tratteggiate"
      azioni={
        inAttesa > 0 ? (
          <span className="nota">
            {inAttesa === 1 ? '1 da decidere' : `${inAttesa} da decidere`} nel periodo
          </span>
        ) : undefined
      }
      errore={errore}
      classe="pannello-calendario pannello-assenze"
    >
      <FullCalendar
        plugins={[dayGridPlugin]}
        locale={itLocale}
        initialView="dayGridMonth"
        headerToolbar={{ left: 'prev,next today', center: 'title', right: '' }}
        buttonText={{ today: 'Questo mese' }}
        firstDay={1}
        // Gli eventi non si cliccano: da qui si guarda. Decidere si fa
        // nell'elenco qui sopra, dove ci sono la nota e il motivo — approvare
        // da un rettangolo che non li mostra vorrebbe dire decidere senza
        // leggere.
        // Come la settimana: niente altezza imposta, niente scorrimento
        // interno. Un mese ha cinque o sei righe di celle, e quante siano lo
        // sa il calendario — non una misura scritta qui.
        height="auto"
        events={eventi}
        datesSet={periodoCambiato}
        // Un giorno con mezza azienda in ferie non deve diventare una colonna
        // di dieci righe: oltre il terzo compare «+N».
        dayMaxEvents={3}
        moreLinkText={(quanti) => `+${quanti}`}
      />
    </Pannello>
  )
}

/**
 * Il giorno dopo, in ISO: traduce la fine **compresa** dell'API in quella
 * esclusiva che FullCalendar vuole.
 *
 * Passa da `Date.UTC` e non dal costruttore locale: con quest'ultimo, in un
 * fuso a ovest di Greenwich, «2026-11-06» diventerebbe il 5 novembre sera e
 * l'assenza slitterebbe di un giorno.
 */
function piuUnGiorno(iso: string): string {
  const [anno, mese, giorno] = iso.split('-').map(Number)
  return new Date(Date.UTC(anno, mese - 1, giorno + 1)).toISOString().slice(0, 10)
}

function menoUnGiorno(iso: string): string {
  const [anno, mese, giorno] = iso.split('-').map(Number)
  return new Date(Date.UTC(anno, mese - 1, giorno - 1)).toISOString().slice(0, 10)
}
