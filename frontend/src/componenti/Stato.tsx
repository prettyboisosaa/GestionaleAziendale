/**
 * Il badge e il punto con cui si mostra uno stato.
 *
 * Due componenti e non una classe CSS da ricordarsi: la regola del documento
 * — fondo al 12–15% del colore, testo pieno, etichetta sempre presente — vale
 * in cinque schermate, e ripeterla a mano in ognuna significa che la sesta la
 * sbaglierà.
 *
 * L'etichetta di testo non è mai facoltativa. È quello che rende lo stato
 * leggibile a chi non distingue i colori, e il motivo per cui il punto da
 * solo non si usa mai senza il titolo accanto.
 */

type ProprietaPillola = {
  /** Il modificatore della tinta: `attivo`, `in-corso`, `completato`, … */
  tinta: string
  /** Il testo, che c'è sempre: «Completato», «Annullata», «In attesa». */
  children: string
}

/** Il badge a pillola: fondo velato, testo nel colore pieno. */
export function Pillola({ tinta, children }: ProprietaPillola) {
  return <span className={`pillola pillola-${tinta}`}>{children}</span>
}

type ProprietaPunto = {
  tinta: string
  /** Letto dagli assistenti vocali, per cui il punto è invisibile. */
  titolo: string
}

/**
 * Il punto accanto a un titolo, negli elenchi: dice lo stato senza rubare la
 * larghezza di una colonna.
 */
export function Punto({ tinta, titolo }: ProprietaPunto) {
  return <span className={`punto punto-${tinta}`} role="img" aria-label={titolo} />
}
