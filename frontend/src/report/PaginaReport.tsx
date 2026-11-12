import { useEffect, useState } from 'react'
import Pannello from '../componenti/Pannello'
import BarraRipartizione from './BarraRipartizione'
import type { Segmento } from './BarraRipartizione'
import { oreMinuti, percentuale, reportPersona, reportPersone } from './api'
import type { ReportPersona, RiepilogoPersona, VoceLavoro } from './api'
import { tintaLavoro } from '../stato/stati'
import { testo } from '../api/http'

/**
 * Su cosa ha lavorato l'azienda, persona per persona.
 *
 * La domanda a cui risponde non aveva prima nessuna schermata: il calendario
 * di una persona mostra le sue sessioni una per una, e i KPI di un lavoro
 * dicono quante ore ci sono andate senza dire da chi. Per sapere come Mario ha
 * speso il mese bisognava sommare a mano.
 *
 * **Tre livelli, uno per clic.** L'elenco delle persone sta sempre aperto;
 * aprendo una persona arriva la sua ripartizione sui lavori, e dentro ogni
 * lavoro le sue task sono già lì — sono poche, e un terzo giro di rete per
 * aprire una riga sarebbe un'attesa dove non ce n'è bisogno.
 *
 * Le percentuali sono sempre **sul tempo della persona**: su cento ore di
 * Mario, quaranta sul lavoro A. Non sul budget del lavoro e non su una
 * giornata teorica — la domanda è come il tempo è stato speso, non se è
 * bastato.
 */
/**
 * L'esito di una chiamata, **con il periodo che aveva chiesto**.
 *
 * Non un `righe | null` accanto a un flag di caricamento: così lo stato della
 * pagina si *ricava* dal confronto fra ciò che si ha in mano e ciò che dicono i
 * campi data, e non esiste nessun istante in cui si leggono i numeri di marzo
 * sotto un intervallo che dice aprile. Azzerare i dati dentro l'effetto
 * otterrebbe lo stesso, ma scrivendo stato durante un render — che è
 * esattamente ciò che React chiede di non fare.
 *
 * È per questo che entrambe le rotte si portano dietro `da` e `a`.
 */
type Esito = { da: string; a: string; persone?: RiepilogoPersona[]; errore?: string }

type EsitoPersona = {
  utenteId: number
  da: string
  a: string
  dettaglio?: ReportPersona
  errore?: string
}

export default function PaginaReport() {
  const [da, setDa] = useState(primoDelMese)
  const [a, setA] = useState(oggi)
  const [esito, setEsito] = useState<Esito | null>(null)
  /** Chi è aperto, e che cosa si sa di lui. */
  const [aperta, setAperta] = useState<number | null>(null)
  const [esitoPersona, setEsitoPersona] = useState<EsitoPersona | null>(null)

  const rovescio = a < da

  useEffect(() => {
    // Date invertite: non si chiama il server per farsi dire un 400 che si sa
    // già. L'elenco resta quello di prima, con l'avviso sopra.
    if (rovescio) {
      return
    }
    let annullato = false

    reportPersone(da, a)
      .then((arrivate) => {
        if (!annullato) {
          setEsito({ da, a, persone: arrivate.persone })
        }
      })
      .catch((motivo: unknown) => {
        if (!annullato) {
          setEsito({ da, a, errore: testo(motivo) })
        }
      })

    return () => {
      annullato = true
    }
  }, [da, a, rovescio])

  /**
   * Il dettaglio si rilegge anche quando cambia solo il periodo, non soltanto
   * quando si apre una persona: lasciare aperta la ripartizione di marzo sotto
   * un elenco che ormai mostra aprile sarebbe il modo più efficace di far
   * leggere il numero sbagliato.
   */
  useEffect(() => {
    if (aperta === null || rovescio) {
      return
    }
    let annullato = false

    reportPersona(aperta, da, a)
      .then((arrivato) => {
        if (!annullato) {
          setEsitoPersona({ utenteId: aperta, da, a, dettaglio: arrivato })
        }
      })
      .catch((motivo: unknown) => {
        if (!annullato) {
          setEsitoPersona({ utenteId: aperta, da, a, errore: testo(motivo) })
        }
      })

    return () => {
      annullato = true
    }
  }, [aperta, da, a, rovescio])

  // «Fresco» vuol dire: risponde alle date che si vedono adesso. Quello che
  // resta di una richiesta precedente non si mostra, si considera in arrivo.
  const fresco = esito !== null && esito.da === da && esito.a === a
  const elenco = fresco ? (esito.persone ?? []) : []
  const errore = fresco ? (esito.errore ?? null) : null
  const carico = !fresco && !rovescio
  const conOre = elenco.filter((una) => una.minutiTotali > 0).length

  const suo =
    esitoPersona !== null &&
    esitoPersona.utenteId === aperta &&
    esitoPersona.da === da &&
    esitoPersona.a === a
  const dettaglio = suo ? (esitoPersona.dettaglio ?? null) : null
  const erroreDettaglio = suo ? (esitoPersona.errore ?? null) : null

  return (
    <main className="pagina-larga">
      <div className="testata">
        <h1>Report</h1>
      </div>

      {errore !== null && (
        <p className="errore" role="alert">
          {errore}
        </p>
      )}

      <Pannello
        titolo="Come è stato speso il tempo"
        sottotitolo="le percentuali sono sul totale di ciascuna persona"
        azioni={
          <>
            <label className="nota">
              Dal{' '}
              <input
                type="date"
                value={da}
                max={a}
                onChange={(evento) => setDa(evento.target.value)}
              />
            </label>
            <label className="nota">
              al{' '}
              <input
                type="date"
                value={a}
                min={da}
                onChange={(evento) => setA(evento.target.value)}
              />
            </label>
          </>
        }
        carico={carico}
        errore={rovescio ? 'L’intervallo finisce prima di cominciare.' : null}
        vuoto={
          !carico && !rovescio && elenco.length === 0 ? 'Nessuna persona da mostrare.' : null
        }
      >
        {conOre === 0 && elenco.length > 0 && (
          <p className="nota">Nessuna ora registrata in questo periodo.</p>
        )}

        <ul className="elenco-report">
          {elenco.map((una) => (
            <Persona
              key={una.utenteId}
              persona={una}
              aperta={aperta === una.utenteId}
              dettaglio={aperta === una.utenteId ? dettaglio : null}
              errore={aperta === una.utenteId ? erroreDettaglio : null}
              onApri={() => setAperta(aperta === una.utenteId ? null : una.utenteId)}
            />
          ))}
        </ul>
      </Pannello>
    </main>
  )
}

/** Una persona nell'elenco, chiusa o aperta sulla sua ripartizione. */
function Persona({
  persona,
  aperta,
  dettaglio,
  errore,
  onApri,
}: {
  persona: RiepilogoPersona
  aperta: boolean
  dettaglio: ReportPersona | null
  errore: string | null
  onApri: () => void
}) {
  // Una persona a zero ore non si apre: non c'è niente sotto, e un pannello
  // vuoto che si apre e si chiude è peggio di una riga che non si apre.
  const vuota = persona.minutiTotali === 0

  return (
    <li className={aperta ? 'riga-report riga-report-aperta' : 'riga-report'}>
      <button
        type="button"
        className="riga-report-capo"
        onClick={onApri}
        disabled={vuota}
        aria-expanded={aperta}
      >
        <span className="riga-report-chi">
          {persona.cognome} {persona.nome}
          {!persona.attivo && (
            // Un disattivato è in elenco solo perché ha ore nel periodo: il
            // suo lavoro è stato fatto, ma chi legge non deve cercarlo fra chi
            // è in forza oggi.
            <span className="nota"> · disattivato</span>
          )}
        </span>
        <span className="riga-report-ore">{oreMinuti(persona.minutiTotali)}</span>
        {dettaglio !== null ? (
          <BarraRipartizione
            segmenti={segmentiDi(dettaglio)}
            totale={dettaglio.minutiTotali}
          />
        ) : (
          <span className="barra-ripartizione-vuota" />
        )}
      </button>

      {persona.minutiNonConfermati > 0 && (
        <p className="nota riga-report-avviso">
          {oreMinuti(persona.minutiNonConfermati)} da confermare, già nel totale.
        </p>
      )}

      {aperta && (
        <div className="riga-report-corpo">
          {errore !== null && (
            <p className="errore" role="alert">
              {errore}
            </p>
          )}
          {errore === null && dettaglio === null && <p className="nota">Carico…</p>}
          {dettaglio !== null && <Ripartizione dettaglio={dettaglio} />}
        </div>
      )}
    </li>
  )
}

/** I lavori di una persona, e in fondo il tempo che non è di nessun lavoro. */
function Ripartizione({ dettaglio }: { dettaglio: ReportPersona }) {
  const totale = dettaglio.minutiTotali
  const senzaLavoro = dettaglio.senzaLavoro.reduce((somma, una) => somma + una.minuti, 0)

  return (
    <>
      <table className="tabella tabella-report">
        <thead>
          <tr>
            <th scope="col">Lavoro</th>
            <th scope="col">Ore</th>
            <th scope="col">Quota</th>
          </tr>
        </thead>
        <tbody>
          {dettaglio.lavori.map((uno) => (
            <Lavoro key={uno.lavoroId} lavoro={uno} totale={totale} />
          ))}

          {dettaglio.senzaLavoro.length > 0 && (
            <>
              <tr className="riga-gruppo">
                <th scope="row">
                  Senza lavoro
                  {/* Non è un residuo da ignorare: è il contorno della
                      settimana, e vederlo crescere è un'informazione. */}
                  <span className="nota"> · riunioni, formazione, amministrazione</span>
                </th>
                <td>{oreMinuti(senzaLavoro)}</td>
                <td>{percentuale(senzaLavoro, totale)}</td>
              </tr>
              {dettaglio.senzaLavoro.map((una) => (
                <tr key={una.tipo} className="riga-figlia">
                  <td>{una.etichetta}</td>
                  <td>{oreMinuti(una.minuti)}</td>
                  <td>{percentuale(una.minuti, totale)}</td>
                </tr>
              ))}
            </>
          )}
        </tbody>
      </table>

      {dettaglio.lavori.length === 0 && dettaglio.senzaLavoro.length === 0 && (
        <p className="nota">Nessuna ora registrata in questo periodo.</p>
      )}
    </>
  )
}

/** Un lavoro con le sue task e le sue attività senza task. */
function Lavoro({ lavoro, totale }: { lavoro: VoceLavoro; totale: number }) {
  return (
    <>
      <tr className="riga-gruppo">
        <th scope="row">
          <span className={`punto-tinta ${tintaLavoro(lavoro.lavoroId)}`} aria-hidden="true" />
          {/* Il codice del lavoro resta scritto: due tinte vicine non devono
              far perdere l'informazione, ed è la stessa regola del calendario
              del tempo. Nulli solo se il lavoro non esiste più. */}
          {lavoro.codice ?? `#${lavoro.lavoroId}`} {lavoro.nome ?? ''}
        </th>
        <td>{oreMinuti(lavoro.minuti)}</td>
        <td>{percentuale(lavoro.minuti, totale)}</td>
      </tr>

      {lavoro.task.map((una) => (
        <tr key={una.taskId} className="riga-figlia">
          <td>{una.titolo}</td>
          <td>{oreMinuti(una.minuti)}</td>
          <td>{percentuale(una.minuti, totale)}</td>
        </tr>
      ))}

      {lavoro.attivitaDirette.map((una) => (
        <tr key={una.tipo} className="riga-figlia">
          {/* Ore su questo lavoro ma fuori da ogni task: stanno dentro il
              lavoro perché gli appartengono — una manutenzione per quel
              cliente è tempo di quel cliente. */}
          <td>
            {una.etichetta} <span className="nota">· senza task</span>
          </td>
          <td>{oreMinuti(una.minuti)}</td>
          <td>{percentuale(una.minuti, totale)}</td>
        </tr>
      ))}
    </>
  )
}

/**
 * I segmenti della barra di una persona: un lavoro per segmento, più uno
 * neutro per il tempo che non è di nessun lavoro.
 *
 * La tinta è quella di `tintaLavoro`, la stessa che il lavoro ha nel
 * calendario del tempo: un progetto che lì è verde è verde anche qui, e la
 * memoria visiva attraversa le schermate. «Senza lavoro» prende il tono neutro
 * e non una tinta della serie — non è un progetto, e dargli un colore da
 * progetto lo farebbe sembrare tale.
 */
function segmentiDi(dettaglio: ReportPersona): Segmento[] {
  const segmenti: Segmento[] = dettaglio.lavori.map((uno) => ({
    chiave: `lavoro-${uno.lavoroId}`,
    etichetta: uno.codice ?? `#${uno.lavoroId}`,
    minuti: uno.minuti,
    tinta: tintaLavoro(uno.lavoroId),
  }))

  const senzaLavoro = dettaglio.senzaLavoro.reduce((somma, una) => somma + una.minuti, 0)
  if (senzaLavoro > 0) {
    segmenti.push({ chiave: 'senza-lavoro', etichetta: 'Senza lavoro', minuti: senzaLavoro })
  }
  return segmenti
}

/**
 * Il primo del mese corrente e oggi: il periodo che si vuole quasi sempre, e
 * quello su cui si misura il lavoro di un'azienda.
 *
 * In ISO locale e non da `toISOString()`, che passa per UTC: in un fuso a est
 * di Greenwich il primo del mese diventerebbe l'ultimo del mese precedente.
 */
function primoDelMese(): string {
  const adesso = new Date()
  return iso(new Date(adesso.getFullYear(), adesso.getMonth(), 1))
}

function oggi(): string {
  return iso(new Date())
}

function iso(giorno: Date): string {
  const mese = String(giorno.getMonth() + 1).padStart(2, '0')
  const numero = String(giorno.getDate()).padStart(2, '0')
  return `${giorno.getFullYear()}-${mese}-${numero}`
}
