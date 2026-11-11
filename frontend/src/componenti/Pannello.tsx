import type { ReactNode } from 'react'

/**
 * Il riquadro con cui sono fatti i blocchi della dashboard.
 *
 * Quattro blocchi che si somigliavano erano scritti quattro volte, ciascuno
 * con la sua intestazione e il suo modo di dire «sto caricando»: il quinto
 * l'avrebbe detto in un quinto modo. Qui l'intestazione, il corpo e i tre
 * stati — carico, vuoto, errore — si dichiarano una volta.
 *
 * **I tre stati sono esclusivi e hanno una precedenza.** L'errore vince su
 * tutto, perché un pannello che mostra dati vecchi senza dire che l'ultimo
 * aggiornamento è fallito mente; il caricamento viene dopo; il vuoto è
 * l'ultimo, perché «non c'è niente» si può dire solo quando si è guardato
 * davvero. Un pannello in errore non blocca gli altri: ciascuno ha i suoi
 * stati, ed è la ragione per cui stanno qui e non in un unico messaggio in
 * cima alla pagina.
 */
export default function Pannello({
  titolo,
  sottotitolo,
  azioni,
  carico = false,
  errore = null,
  vuoto = null,
  classe,
  children,
}: {
  titolo: string
  /** La riga sotto il titolo: che cosa è questo pannello, in poche parole. */
  sottotitolo?: string
  /** A destra nell'intestazione: un collegamento, un totale, dei contatori. */
  azioni?: ReactNode
  carico?: boolean
  /** Il messaggio d'errore già leggibile, oppure null. */
  errore?: string | null
  /**
   * Che cosa dire quando non c'è niente da mostrare. Passandolo si dichiara
   * che il pannello è vuoto: `null` vuol dire che qualcosa c'è.
   */
  vuoto?: string | null
  /** Un modificatore, per il pannello che ha bisogno di uno stile suo. */
  classe?: string
  children?: ReactNode
}) {
  return (
    <section className={classe === undefined ? 'pannello-blocco' : `pannello-blocco ${classe}`}>
      <div className="pannello-testata">
        <div className="pannello-titolo">
          <h2>{titolo}</h2>
          {sottotitolo !== undefined && <span className="nota">{sottotitolo}</span>}
        </div>
        {azioni !== undefined && <div className="pannello-azioni">{azioni}</div>}
      </div>

      <div className="pannello-corpo">
        {errore !== null ? (
          <p className="errore" role="alert">
            {errore}
          </p>
        ) : carico ? (
          <p className="sommario" role="status">
            Carico…
          </p>
        ) : vuoto !== null ? (
          <p className="sommario">{vuoto}</p>
        ) : (
          children
        )}
      </div>
    </section>
  )
}
