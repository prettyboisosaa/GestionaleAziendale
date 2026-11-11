import { useEffect, useState } from 'react'

/**
 * Sotto questa larghezza la barra laterale non si espande: diventa un menù.
 *
 * Lo stesso numero sta nella media query di `App.css` che annulla il margine
 * del corpo. Sono due copie, e non si può fare altrimenti — il CSS non legge
 * una costante TypeScript — ma stanno scritte entrambe con questo valore
 * accanto, così chi ne cambia una trova il rimando all'altra.
 */
const SOGLIA_MOBILE = 700

/**
 * Vero quando lo schermo è troppo stretto perché la barra laterale si
 * espanda.
 *
 * Non è un modulo di componenti, ed è il motivo per cui sta in un file suo:
 * un hook esportato accanto a un componente rompe il ricaricamento a caldo di
 * Vite, che pretende che un modulo esporti solo componenti.
 */
export function useSchermoStretto(): boolean {
  const [stretto, setStretto] = useState(
    () =>
      typeof window !== 'undefined'
      && window.matchMedia(`(max-width: ${SOGLIA_MOBILE}px)`).matches,
  )

  useEffect(() => {
    const osservato = window.matchMedia(`(max-width: ${SOGLIA_MOBILE}px)`)
    const cambiato = (evento: MediaQueryListEvent) => setStretto(evento.matches)
    osservato.addEventListener('change', cambiato)
    return () => osservato.removeEventListener('change', cambiato)
  }, [])

  return stretto
}
