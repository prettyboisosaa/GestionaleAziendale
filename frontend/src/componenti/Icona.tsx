/**
 * Le icone della navigazione, disegnate a mano in SVG.
 *
 * Nessuna libreria di icone, per la stessa ragione per cui qui non c'è una
 * libreria di componenti: ne servono otto, e un pacchetto ne porterebbe
 * duemila nel bundle per usarne otto. Sono tratti su una griglia di 24, con
 * `currentColor`, così prendono il colore del testo accanto e la voce attiva
 * si tinge senza che l'icona debba saperlo.
 *
 * `aria-hidden` su tutte: l'icona non è l'informazione. Nella sidebar chiusa
 * il nome della voce resta leggibile agli assistenti vocali perché sta
 * nell'etichetta del collegamento, non perché l'icona lo dica.
 */

export type NomeIcona =
  | 'oggi'
  | 'lavori'
  | 'ore'
  | 'assenze'
  | 'richieste-assenza'
  | 'utenti'
  | 'report'
  | 'menu'
  | 'chiudi'
  | 'avvia'
  | 'pausa'
  | 'spunta'
  | 'attesa'
  // --- 05 · finestre -------------------------------------------------------
  // Le icone che vivono dentro modali, tendine e notifiche. Stanno qui e non
  // in un file a parte per la ragione di sempre: sono tratti su griglia 24
  // con `currentColor`, e un secondo componente identico a questo si
  // sarebbe scostato dal primo al terzo ritocco.
  | 'cestino'
  | 'esci'
  | 'calendario'
  | 'luogo'
  | 'matita'
  | 'freccia-giu'
  | 'freccia-sinistra'
  | 'freccia-destra'
  | 'attenzione'
  | 'informazione'
  | 'errore'
  | 'spunta-cerchio'

export default function Icona({ nome }: { nome: NomeIcona }) {
  return (
    <svg
      className="icona"
      viewBox="0 0 24 24"
      width="20"
      height="20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {tratti(nome)}
    </svg>
  )
}

function tratti(nome: NomeIcona) {
  switch (nome) {
    // Il sole: «oggi». Il cerchio del giorno, con i raggi a dire che è
    // questo e non un altro.
    case 'oggi':
      return (
        <>
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M19.1 4.9l-1.4 1.4M6.3 17.7l-1.4 1.4" />
        </>
      )
    // La valigetta: i lavori.
    case 'lavori':
      return (
        <>
          <rect x="2.5" y="7" width="19" height="13" rx="2" />
          <path d="M9 7V5.5A1.5 1.5 0 0 1 10.5 4h3A1.5 1.5 0 0 1 15 5.5V7" />
          <path d="M2.5 12h19" />
        </>
      )
    // L'orologio con la matita: le ore che si chiedono o si correggono.
    case 'ore':
      return (
        <>
          <path d="M20.5 12a8.5 8.5 0 1 0-8.5 8.5" />
          <path d="M12 7.5V12l3 1.8" />
          <path d="m15.5 20.5 5-5 2 2-5 5h-2z" />
        </>
      )
    // L'ombrellone: le assenze. L'asta e la cupola, con la sabbia sotto.
    case 'assenze':
      return (
        <>
          <path d="M12 4a8 8 0 0 1 8 8H4a8 8 0 0 1 8-8z" />
          <path d="M12 4v16" />
          <path d="M9 20h6" />
        </>
      )
    // Il calendario con il più: una richiesta d'assenza da aprire o decidere.
    case 'richieste-assenza':
      return (
        <>
          <rect x="3" y="5" width="18" height="16" rx="2" />
          <path d="M3 10h18M8 3v4M16 3v4" />
          <path d="M12 13v5M9.5 15.5h5" />
        </>
      )
    // Il gruppo di persone: gli utenti.
    case 'utenti':
      return (
        <>
          <circle cx="9" cy="8" r="3.2" />
          <path d="M3 20c0-3.3 2.7-5.5 6-5.5s6 2.2 6 5.5" />
          <path d="M16.5 5.2a3.2 3.2 0 0 1 0 6.1" />
          <path d="M18 14.9c2 .7 3.5 2.5 3.5 5.1" />
        </>
      )
    // Tre colonne di altezza diversa: la ripartizione del tempo. Non un
    // grafico a torta, perché la pagina non ne disegna nessuno.
    case 'report':
      return (
        <>
          <path d="M3 21h18" />
          <path d="M7 21V11M12 21V4M17 21v-6" />
        </>
      )
    case 'menu':
      return <path d="M4 7h16M4 12h16M4 17h16" />
    case 'chiudi':
      return <path d="M6 6l12 12M18 6L6 18" />
    // Il triangolo del play: avvia il timer. Pieno e non contornato — e' il
    // solo simbolo di questa serie che sia un gesto e non una cosa, e un
    // triangolo di soli tratti a venti pixel si legge come una freccia.
    case 'avvia':
      return <path d="M9 6.5v11l9-5.5z" fill="currentColor" stroke="none" />
    // Le due barre della pausa, pieni per lo stesso motivo del play.
    case 'pausa':
      return (
        <path
          d="M9.5 6h1.8v12H9.5zM12.7 6h1.8v12h-1.8z"
          fill="currentColor"
          stroke="none"
        />
      )
    // La spunta: completa. Un tratto solo, come la spunta che si fa a penna
    // su una lista di cose da fare.
    case 'spunta':
      return <path d="M5 12.5l4.5 4.5L19 7.5" />
    // L'orologio nudo: una richiesta che aspetta una risposta. È quello di
    // «ore» senza la matita — lì il tempo si corregge, qui si aspetta.
    case 'attesa':
      return (
        <>
          <circle cx="12" cy="12" r="8.5" />
          <path d="M12 7.5V12l3 1.8" />
        </>
      )

    // --- 05 · finestre -----------------------------------------------------

    // Il cestino: l'azione che porta via qualcosa per sempre. Compare solo
    // dove la cancellazione è davvero definitiva — nel gestionale è una sola
    // (un lavoro in bozza), perché tutto il resto si annulla e resta.
    case 'cestino':
      return (
        <>
          <path d="M4 7h16" />
          <path d="M9.5 7V5.5A1.5 1.5 0 0 1 11 4h2a1.5 1.5 0 0 1 1.5 1.5V7" />
          <path d="M6.5 7l.8 11.6A1.5 1.5 0 0 0 8.8 20h6.4a1.5 1.5 0 0 0 1.5-1.4L17.5 7" />
          <path d="M10.5 11v5.5M13.5 11v5.5" />
        </>
      )
    // La porta con la freccia: uscire. La freccia punta fuori, non dentro.
    case 'esci':
      return (
        <>
          <path d="M14 4.5H6.5A1.5 1.5 0 0 0 5 6v12a1.5 1.5 0 0 0 1.5 1.5H14" />
          <path d="M18.5 12H10" />
          <path d="M14.5 8.2L18.5 12l-4 3.8" />
        </>
      )
    // Il calendario: una data da scegliere, o il giorno di un evento.
    case 'calendario':
      return (
        <>
          <rect x="3.5" y="5" width="17" height="15" rx="2.5" />
          <path d="M3.5 9.5h17" />
          <path d="M8 3.5V6M16 3.5V6" />
        </>
      )
    // Il segnaposto: dove si tiene una cosa.
    case 'luogo':
      return (
        <>
          <path d="M12 21s6.5-5.4 6.5-10.2A6.5 6.5 0 0 0 5.5 10.8C5.5 15.6 12 21 12 21z" />
          <circle cx="12" cy="10.5" r="2.3" />
        </>
      )
    // La matita: modificare quello che si sta guardando.
    case 'matita':
      return (
        <>
          <path d="M4.5 19.5l.6-3.4L15.4 5.8a2 2 0 0 1 2.8 2.8L7.9 18.9z" />
          <path d="M14 7.2l2.8 2.8" />
        </>
      )
    // Il gallone: una tendina da aprire. Verso il basso da chiusa; la stessa
    // icona ruotata di mezzo giro quando è aperta, in CSS, così non ci sono
    // due disegni da tenere allineati.
    case 'freccia-giu':
      return <path d="M7 10l5 5 5-5" />
    case 'freccia-sinistra':
      return <path d="M14 6l-6 6 6 6" />
    case 'freccia-destra':
      return <path d="M10 6l6 6-6 6" />

    // I tre segnali delle notifiche, più la spunta. Sono cerchi e non
    // triangoli o quadrati: nella tavola delle finestre i quattro toast hanno
    // la stessa pastiglia tonda, e ciò che cambia è il colore e il segno
    // dentro — così la forma non compete con il colore nel dire di che si
    // tratta.
    case 'attenzione':
      return (
        <>
          <circle cx="12" cy="12" r="8.5" />
          <path d="M12 7.8v4.8" />
          <path d="M12 16.1v.1" strokeWidth="2.2" />
        </>
      )
    case 'informazione':
      return (
        <>
          <circle cx="12" cy="12" r="8.5" />
          <path d="M12 11.2v5" />
          <path d="M12 7.9v.1" strokeWidth="2.2" />
        </>
      )
    case 'errore':
      return (
        <>
          <circle cx="12" cy="12" r="8.5" />
          <path d="M9.2 9.2l5.6 5.6M14.8 9.2l-5.6 5.6" />
        </>
      )
    case 'spunta-cerchio':
      return (
        <>
          <circle cx="12" cy="12" r="8.5" />
          <path d="M8.3 12.3l2.6 2.6 4.8-5.4" />
        </>
      )
  }
}
