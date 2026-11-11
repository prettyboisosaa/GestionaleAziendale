import type { RigaLavoro } from './api'
import type { RigaTask } from '../task/api'
import { ETICHETTA_TASK, tinta } from '../stato/stati'
import { Pillola } from '../componenti/Stato'

/**
 * Le task del lavoro collocate nel tempo, una riga per task.
 *
 * Fatta con una griglia CSS e non con una libreria di Gantt: per disegnare
 * delle barre su un asse di date servono due percentuali, e in questo schema
 * non esistono dipendenze fra task — nessun predecessore, nessuna milestone —
 * cioè proprio le cose per cui una libreria di Gantt varrebbe il suo peso.
 * Quando serviranno, questo componente è il punto da sostituire.
 *
 * L'asse va dall'inizio del lavoro alla sua fine prevista. Le task che
 * escono da quell'intervallo **non si tagliano**: l'asse si allarga per
 * contenerle, perché una task che sfonda la fine prevista del lavoro è
 * esattamente la cosa che questo disegno deve far vedere.
 */
export default function BarraTemporale({
  lavoro,
  task,
}: {
  lavoro: RigaLavoro
  task: RigaTask[]
}) {
  if (task.length === 0) {
    return <p className="sommario">Nessuna task da collocare nel tempo.</p>
  }

  const asse = intervallo(lavoro, task)
  const durata = asse.fine - asse.inizio

  return (
    <div className="barra-temporale">
      <div className="barra-temporale-asse">
        <span className="nota">{breve(asse.inizio)}</span>
        <span className="nota">{breve(asse.fine)}</span>
      </div>

      <ul className="barra-temporale-righe">
        {task.map((una) => {
          const da = giorno(una.dataInizio).getTime()
          // La scadenza è **compresa**: una task che scade il 28 va lavorata
          // tutto il 28, quindi la barra arriva alla mezzanotte del 29. Senza
          // questo, l'ultimo giorno di ogni barra sparirebbe — ed è la stessa
          // ragione per cui il calendario della dashboard somma un giorno.
          const a = unGiornoDopo(una.scadenza).getTime()

          const sinistra = ((da - asse.inizio) / durata) * 100
          const larghezza = ((a - da) / durata) * 100

          return (
            <li key={una.id} className="barra-temporale-riga">
              <span className="barra-temporale-nome">
                <Pillola tinta={tinta(una.stato)}>{ETICHETTA_TASK[una.stato]}</Pillola>
                <span className="barra-temporale-titolo">{una.titolo}</span>
              </span>

              <span className="barra-temporale-pista">
                {/* Il titolo nell'attributo `title`: su una barra di pochi
                    pixel non ci sta scritto niente, e troncarlo a due lettere
                    sarebbe peggio di non scriverlo. */}
                <span
                  className={`barra-temporale-blocco tinta-${tinta(una.stato)}`}
                  style={{ left: `${sinistra}%`, width: `${Math.max(larghezza, 0.8)}%` }}
                  title={`${una.titolo} · ${breve(da)} – ${breve(giorno(una.scadenza).getTime())}`}
                />
              </span>
            </li>
          )
        })}
      </ul>

      {/* Il segno di oggi sta fuori dalle righe e le attraversa: dentro una
          riga sarebbe un trattino, qui è una linea che dice dove siamo. */}
      {posizioneDiOggi(asse) !== null && (
        <div className="barra-temporale-oggi" style={{ left: `${posizioneDiOggi(asse)}%` }}>
          <span className="nota">oggi</span>
        </div>
      )}
    </div>
  )
}

/**
 * «2026-11-06» come data **locale**.
 *
 * ⚠️ Non `new Date(iso)`: quella stringa si legge come mezzanotte **UTC**, e
 * in un fuso a ovest di Greenwich la barra slitterebbe al giorno prima. È la
 * stessa trappola che `istanteLocale` commenta in `tempo/api.ts`.
 */
function giorno(iso: string): Date {
  const [anno, mese, g] = iso.split('-').map(Number)
  return new Date(anno, mese - 1, g)
}

/** La mezzanotte del giorno dopo: serve a includere il giorno di scadenza. */
function unGiornoDopo(iso: string): Date {
  const quando = giorno(iso)
  quando.setDate(quando.getDate() + 1)
  return quando
}

/**
 * L'asse: il periodo del lavoro, allargato se qualche task ne esce.
 *
 * Si allarga invece di tagliare perché una task che comincia prima del lavoro
 * o che sfonda la fine prevista è un'anomalia da vedere, non da nascondere.
 */
function intervallo(lavoro: RigaLavoro, task: RigaTask[]) {
  const inizi = task.map((una) => giorno(una.dataInizio).getTime())
  const fini = task.map((una) => unGiornoDopo(una.scadenza).getTime())

  const inizio = Math.min(giorno(lavoro.dataInizio).getTime(), ...inizi)
  const fine = Math.max(unGiornoDopo(lavoro.dataFinePrevista).getTime(), ...fini)

  // Un lavoro di un giorno solo darebbe durata zero e una divisione per zero
  // in ogni percentuale: si garantisce almeno una giornata di asse.
  return { inizio, fine: Math.max(fine, inizio + 86_400_000) }
}

/** Dove cade oggi sull'asse, o null se oggi è fuori dal periodo disegnato. */
function posizioneDiOggi(asse: { inizio: number; fine: number }): number | null {
  const adesso = Date.now()
  if (adesso < asse.inizio || adesso > asse.fine) {
    return null
  }
  return ((adesso - asse.inizio) / (asse.fine - asse.inizio)) * 100
}

/** Da un istante a «06/11». */
function breve(quando: number): string {
  return new Date(quando).toLocaleDateString('it-IT', { day: '2-digit', month: '2-digit' })
}
