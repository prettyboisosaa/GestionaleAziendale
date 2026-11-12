import type { NomeIcona } from './Icona'
import type { UtenteCorrente } from '../autenticazione/api'
import { ha } from '../autenticazione/permessi'

/**
 * Le voci della navigazione, in un posto solo.
 *
 * Erano scritte dentro il JSX dell'intestazione, ciascuna con la sua guardia
 * accanto: aggiungerne una voleva dire ricordarsi di aggiungere anche la
 * rotta, e cambiarne il permesso voleva dire cercarlo in mezzo al markup.
 * Qui una voce è un dato — icona, etichetta, percorso, permessi — e la
 * sidebar si limita a disegnare quelle che toccano a chi guarda.
 *
 * ⚠️ **Nascondere una voce non protegge niente.** Questo elenco decide cosa
 * si vede, non cosa si può fare: le rotte restano avvolte in `Protetta` in
 * `App.tsx`, e il confine vero è il `@PreAuthorize` sul metodo del controller.
 * Chi digita l'indirizzo a mano trova la pagina chiusa lì, non qui.
 */
export type Voce = {
  /** Serve a React come chiave e non si mostra. */
  id: string
  etichetta: string
  percorso: string
  icona: NomeIcona
  /**
   * I permessi che fanno comparire la voce: **almeno uno** basta, come fa
   * `ha`. Lista vuota vuol dire «si vede sempre»: le prime due voci poggiano
   * su permessi di base, che ogni utente attivo ha per costruzione, e una
   * guardia sempre vera non protegge niente facendo credere il contrario.
   */
  permessi: string[]
}

export const VOCI: Voce[] = [
  // Prima voce perché è la pagina di atterraggio: la barra racconta l'ordine
  // in cui si attraversa il gestionale, e si comincia da «cosa devo fare oggi».
  { id: 'oggi', etichetta: 'Oggi', percorso: '/dashboard', icona: 'oggi', permessi: [] },
  { id: 'lavori', etichetta: 'Lavori', percorso: '/lavori', icona: 'lavori', permessi: [] },
  {
    id: 'richieste-ore',
    etichetta: 'Richieste ore',
    percorso: '/richieste-ore',
    icona: 'ore',
    // Una pagina, due mestieri: chi apre le richieste e chi le decide. È la
    // stessa coppia di permessi che `App.tsx` mette sulla rotta.
    permessi: ['ORE_RICHIESTE_GESTISCI', 'ORE_RICHIEDI'],
  },
  { id: 'assenze', etichetta: 'Assenze', percorso: '/assenze', icona: 'assenze', permessi: [] },
  {
    id: 'richieste-assenza',
    etichetta: 'Richieste assenza',
    percorso: '/richieste-assenza',
    icona: 'richieste-assenza',
    permessi: ['ASSENZE_GESTISCI'],
  },
  {
    id: 'utenti',
    etichetta: 'Utenti',
    percorso: '/utenti',
    icona: 'utenti',
    permessi: ['UTENTI_VISUALIZZA'],
  },
  {
    id: 'report',
    etichetta: 'Report',
    percorso: '/report',
    icona: 'report',
    // Lo stesso permesso che `App.tsx` mette sulla rotta. È quello che dice
    // già «vedi il tempo registrato da tutti»: il report ne è il riassunto,
    // e un permesso nuovo avrebbe solo duplicato un significato esistente.
    permessi: ['TEMPO_VISUALIZZA_TUTTI'],
  },
]

/** Le voci che toccano a chi guarda, nell'ordine in cui sono dichiarate. */
export function vociDi(utente: UtenteCorrente): Voce[] {
  return VOCI.filter((voce) => voce.permessi.length === 0 || ha(utente, ...voce.permessi))
}
