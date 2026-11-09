import type { UtenteCorrente } from './api'

/**
 * Vero se l'utente ha almeno uno dei permessi indicati.
 *
 * Serve a decidere cosa mostrare, **non** a proteggere qualcosa: i permessi
 * arrivano dal server e il controllo che conta è il `@PreAuthorize` sul metodo
 * del controller. Qui si evita di mostrare porte chiuse.
 */
export function ha(utente: UtenteCorrente, ...codici: string[]): boolean {
  return codici.some((codice) => utente.permessi.includes(codice))
}

/**
 * Vero se l'utente ha **tutti** i permessi indicati.
 *
 * Serve dove il `@PreAuthorize` del backend usa `and` e non `hasAnyAuthority`:
 * creare un utente vuole `UTENTI_CREA` *e* `RUOLI_ASSEGNA`, e la pagina dei
 * permessi vuole `UTENTI_VISUALIZZA` *e* `RUOLI_ASSEGNA`. Con il solo `ha` la
 * schermata si apriva e poi prendeva 403 alla prima richiesta, che è
 * esattamente quello che `Protetta` dice di evitare.
 */
export function tutti(utente: UtenteCorrente, ...codici: string[]): boolean {
  return codici.every((codice) => utente.permessi.includes(codice))
}
