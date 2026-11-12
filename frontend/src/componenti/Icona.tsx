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
  }
}
