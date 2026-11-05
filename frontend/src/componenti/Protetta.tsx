import type { ReactNode } from 'react'
import type { UtenteCorrente } from '../autenticazione/api'
import { ha } from '../autenticazione/permessi'

type Proprieta = {
  utente: UtenteCorrente
  /** Un codice di permesso, oppure più codici di cui ne basta uno. */
  permesso: string | string[]
  children: ReactNode
}

/**
 * Mostra il contenuto solo se l'utente collegato ha il permesso richiesto.
 *
 * Questa è una comodità per chi guarda, **non una misura di sicurezza**:
 * chiunque può chiamare l'API a mano, e il controllo che conta è il
 * `@PreAuthorize` sul metodo del controller. Qui si evita soltanto di aprire
 * una pagina che riceverebbe 403 a ogni richiesta.
 */
export default function Protetta({ utente, permesso, children }: Proprieta) {
  const richiesti = Array.isArray(permesso) ? permesso : [permesso]

  if (!ha(utente, ...richiesti)) {
    return (
      <main className="pagina">
        <div className="colonna">
          <h1>Non autorizzato</h1>
          <p className="sommario">
            Per questa pagina serve il permesso <strong>{richiesti.join(' oppure ')}</strong>,
            che la tua utenza non ha.
          </p>
        </div>
      </main>
    )
  }

  return <>{children}</>
}
