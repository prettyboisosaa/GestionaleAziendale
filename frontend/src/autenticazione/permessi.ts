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
