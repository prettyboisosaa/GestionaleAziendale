import type { ReactNode } from 'react'
import type { UtenteCorrente } from '../autenticazione/api'
import { ha, tutti } from '../autenticazione/permessi'

type Proprieta = {
  utente: UtenteCorrente
  /** Un codice di permesso, oppure più codici. */
  permesso: string | string[]
  /**
   * Con più codici: `'uno'` ne basta uno, `'tutti'` li vuole tutti. Il valore
   * di partenza è `'uno'`, che è il caso più comune e quello che corrisponde a
   * `hasAnyAuthority` sul backend.
   */
  modo?: 'uno' | 'tutti'
  children: ReactNode
}

/**
 * Mostra il contenuto solo se l'utente collegato ha il permesso richiesto.
 *
 * Questa è una comodità per chi guarda, **non una misura di sicurezza**:
 * chiunque può chiamare l'API a mano, e il controllo che conta è il
 * `@PreAuthorize` sul metodo del controller. Qui si evita soltanto di aprire
 * una pagina che riceverebbe 403 a ogni richiesta — e perché l'eviti davvero,
 * `modo` deve dire la stessa cosa che dice l'annotazione: `and` sul server
 * vuole `'tutti'` qui.
 */
export default function Protetta({ utente, permesso, modo = 'uno', children }: Proprieta) {
  const richiesti = Array.isArray(permesso) ? permesso : [permesso]
  const autorizzato =
    modo === 'tutti' ? tutti(utente, ...richiesti) : ha(utente, ...richiesti)

  if (!autorizzato) {
    return (
      <main className="pagina">
        <div className="colonna">
          <h1>Non autorizzato</h1>
          <p className="sommario">
            Per questa pagina {richiesti.length > 1 && modo === 'tutti' ? 'servono i permessi' : 'serve il permesso'}{' '}
            <strong>{richiesti.join(modo === 'tutti' ? ' e ' : ' oppure ')}</strong>, che la tua
            utenza non ha.
          </p>
        </div>
      </main>
    )
  }

  return <>{children}</>
}
