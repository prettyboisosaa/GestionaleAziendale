/**
 * Chi ha chiesto al sistema operativo meno animazioni.
 *
 * In un file suo e non dentro `Finestra`: lo leggono la finestra, le
 * notifiche e l'anteprima, e una funzione pura esportata accanto a un
 * componente impedisce il ricaricamento a caldo di quel componente durante lo
 * sviluppo.
 *
 * <p><strong>Perché questo si controlla in JavaScript e non solo in CSS.</strong>
 * Le finestre di questo gestionale aspettano `animationend` prima di
 * smontarsi, per poter animare anche l'uscita. Se le animazioni fossero
 * spente del tutto quell'evento non arriverebbe mai, e una finestra chiusa
 * resterebbe aperta per sempre: chi legge questo valore salta l'attesa invece
 * di aspettare un evento che non arriva. In `App.css` la regola per
 * `prefers-reduced-motion` riduce la durata a un istante anziché togliere
 * l'animazione, che è la rete di protezione per lo stesso motivo.
 */
export function movimentoRidotto(): boolean {
  return (
    typeof window !== 'undefined' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}
