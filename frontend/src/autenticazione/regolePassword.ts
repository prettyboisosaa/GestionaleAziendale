/**
 * Che cosa deve avere una password perché sia accettabile.
 *
 * È la **copia** della regola che decide, che vive nel backend in
 * `RegolePassword.java`. Sta anche qui per una ragione precisa: la spunta dei
 * requisiti si deve aggiornare mentre si scrive, e chiedere al server a ogni
 * tasto sarebbe una richiesta per lettera. Il server resta la fonte di
 * verità — se le due copie divergessero, a valere sarebbe la sua, e questo
 * modulo mentirebbe. Toccandone una, va toccata l'altra.
 *
 * Il «carattere speciale» è inteso come carattere **non alfanumerico**:
 * chiedere un carattere alfanumerico oltre a una cifra non vorrebbe dire
 * niente, perché una cifra è già alfanumerica.
 */

export const LUNGHEZZA_MINIMA = 8

/** Un requisito, con l'etichetta da mostrare e il modo di verificarlo. */
export type Requisito = {
  /** Serve a React come chiave, e non si mostra. */
  id: string
  etichetta: string
  soddisfatto: (password: string) => boolean
}

/**
 * I tre requisiti, nell'ordine in cui si notano guardando il campo: la
 * lunghezza prima, che è l'unica che si vede a occhio.
 */
export const REQUISITI: Requisito[] = [
  {
    id: 'lunghezza',
    etichetta: `Almeno ${LUNGHEZZA_MINIMA} caratteri`,
    soddisfatto: (password) => password.length >= LUNGHEZZA_MINIMA,
  },
  {
    id: 'numero',
    etichetta: 'Almeno un numero',
    soddisfatto: (password) => /\d/.test(password),
  },
  {
    id: 'speciale',
    etichetta: 'Almeno un carattere speciale, come ! @ #',
    soddisfatto: (password) => /[^A-Za-z0-9]/.test(password),
  },
]

/** Vero se la password rispetta tutti i requisiti. */
export function passwordValida(password: string): boolean {
  return REQUISITI.every((requisito) => requisito.soddisfatto(password))
}
