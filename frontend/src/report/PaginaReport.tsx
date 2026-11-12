import { useEffect, useState } from 'react'
import BarraRipartizione from './BarraRipartizione'
import type { Segmento } from './BarraRipartizione'
import { oreMinuti, percentuale, quota, reportPersona, reportPersone } from './api'
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
 * **Due colonne, non un elenco che si apre.** A sinistra le persone con la
 * loro barra, a destra la ripartizione di quella scelta: prima il dettaglio si
 * apriva dentro la riga, e confrontare due persone voleva dire aprire la
 * seconda — che chiudeva la prima. Con le due colonne la lista resta ferma e
 * cambia solo la metà destra.
 *
 * Le percentuali sono sempre **sul tempo della persona**: su cento ore di
 * Mario, quaranta sul lavoro A. Non sul budget del lavoro e non su una
 * giornata teorica — la domanda è come il tempo è stato speso, non se è
 * bastato. Dentro un lavoro, invece, le task si rapportano **al lavoro**: lì
 * la domanda è come si è spezzato quel lavoro, non quanto pesa sul mese.
 */

/**
 * L'esito di una chiamata, **con il periodo che aveva chiesto**.
 *
 * Non un `righe | null` accanto a un flag di caricamento: così lo stato della
 * pagina si *ricava* dal confronto fra ciò che si ha in mano e il periodo
 * scelto, e non esiste nessun istante in cui si leggono i numeri di marzo
 * sotto un'intestazione che dice aprile. Azzerare i dati dentro l'effetto
 * otterrebbe lo stesso, ma scrivendo stato durante un render — che è
 * esattamente ciò che React chiede di non fare.
 */
type Esito = { da: string; a: string; persone?: RiepilogoPersona[]; errore?: string }

type EsitoPersona = {
  utenteId: number
  da: string
  a: string
  dettaglio?: ReportPersona
  errore?: string
}

/**
 * I tre periodi che si guardano davvero: l'ultima settimana, le ultime due, il
 * mese in corso.
 *
 * Prima c'erano due campi data. Un intervallo libero serve una volta ogni
 * tanto e costa due scelte ogni volta che si apre la pagina; questi tre sono
 * la risposta alle domande che si fanno sempre — «come è andata la settimana»,
 * «come va il mese» — e sono un clic solo.
 */
type Periodo = 'SETTE' | 'QUATTORDICI' | 'MESE'

export default function PaginaReport() {
  const [periodo, setPeriodo] = useState<Periodo>('MESE')
  const [esito, setEsito] = useState<Esito | null>(null)
  /** Chi è aperto a destra, e che cosa si sa di lui. */
  const [scelta, setScelta] = useState<number | null>(null)
  const [esitoPersona, setEsitoPersona] = useState<EsitoPersona | null>(null)

  const { da, a } = intervallo(periodo)

  useEffect(() => {
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
  }, [da, a])

  /**
   * Il dettaglio si rilegge anche quando cambia solo il periodo, non soltanto
   * quando si sceglie una persona: lasciare a destra la ripartizione di marzo
   * accanto a un elenco che ormai mostra aprile sarebbe il modo più efficace
   * di far leggere il numero sbagliato.
   */
  useEffect(() => {
    if (scelta === null) {
      return
    }
    let annullato = false

    reportPersona(scelta, da, a)
      .then((arrivato) => {
        if (!annullato) {
          setEsitoPersona({ utenteId: scelta, da, a, dettaglio: arrivato })
        }
      })
      .catch((motivo: unknown) => {
        if (!annullato) {
          setEsitoPersona({ utenteId: scelta, da, a, errore: testo(motivo) })
        }
      })

    return () => {
      annullato = true
    }
  }, [scelta, da, a])

  // «Fresco» vuol dire: risponde al periodo che si vede adesso. Quello che
  // resta di una richiesta precedente non si mostra, si considera in arrivo.
  const fresco = esito !== null && esito.da === da && esito.a === a
  const elenco = fresco ? (esito.persone ?? []) : []
  const errore = fresco ? (esito.errore ?? null) : null
  const carico = !fresco

  const totale = elenco.reduce((somma, una) => somma + una.minutiTotali, 0)

  const suo =
    esitoPersona !== null
    && esitoPersona.utenteId === scelta
    && esitoPersona.da === da
    && esitoPersona.a === a
  const dettaglio = suo ? (esitoPersona.dettaglio ?? null) : null
  const erroreDettaglio = suo ? (esitoPersona.errore ?? null) : null

  return (
    <main className="pagina-larga">
      <div className="testata testata-report">
        <div>
          <h1>Ore per persona</h1>
          {/* Il periodo e il totale sotto il titolo e non in un riquadro:
              sono il contesto di ogni numero della pagina, e devono leggersi
              nello stesso colpo d'occhio del titolo. */}
          <p className="nota sottotitolo-report">
            Dal {breve(da)} al {breve(a)} ·{' '}
            {carico ? '…' : `${oreMinuti(totale)} registrate in totale`}
          </p>
        </div>

        <div className="gruppo-periodo" role="group" aria-label="Periodo">
          {PERIODI.map((uno) => (
            <button
              key={uno.valore}
              type="button"
              className={periodo === uno.valore ? 'periodo periodo-scelto' : 'periodo'}
              aria-pressed={periodo === uno.valore}
              onClick={() => setPeriodo(uno.valore)}
            >
              {uno.nome}
            </button>
          ))}
        </div>
      </div>

      {errore !== null && (
        <p className="errore" role="alert">
          {errore}
        </p>
      )}

      <div className="griglia-report">
        <section className="scheda-report">
          {carico && (
            <p className="sommario" role="status">
              Carico…
            </p>
          )}

          {!carico && elenco.length === 0 && (
            <p className="sommario">Nessuna persona da mostrare.</p>
          )}

          <ul className="elenco-persone">
            {elenco.map((una) => (
              <Persona
                key={una.utenteId}
                persona={una}
                scelta={scelta === una.utenteId}
                dettaglio={scelta === una.utenteId ? dettaglio : null}
                onScegli={() =>
                  setScelta(scelta === una.utenteId ? null : una.utenteId)
                }
              />
            ))}
          </ul>
        </section>

        <section className="scheda-report scheda-ripartizione">
          {scelta === null && (
            <p className="sommario">
              Scegli una persona per vedere su che cosa è andato il suo tempo.
            </p>
          )}
          {scelta !== null && erroreDettaglio !== null && (
            <p className="errore" role="alert">
              {erroreDettaglio}
            </p>
          )}
          {scelta !== null && erroreDettaglio === null && dettaglio === null && (
            <p className="sommario" role="status">
              Carico…
            </p>
          )}
          {dettaglio !== null && <Ripartizione dettaglio={dettaglio} />}
        </section>
      </div>
    </main>
  )
}

/**
 * Una persona nell'elenco: pastiglia, nome, ore, e sotto la sua barra.
 *
 * La barra c'è **solo quando si conosce la ripartizione**, cioè quando quella
 * persona è stata aperta almeno una volta: senza il dettaglio il server non ha
 * mandato su cosa sono andate quelle ore, e una barra di un colore solo
 * direbbe una cosa falsa — che tutto il tempo è andato su una cosa sola. Al
 * suo posto resta una vasca vuota, che tiene l'altezza della riga ferma.
 */
function Persona({
  persona,
  scelta,
  dettaglio,
  onScegli,
}: {
  persona: RiepilogoPersona
  scelta: boolean
  dettaglio: ReportPersona | null
  onScegli: () => void
}) {
  // Una persona a zero ore non si apre: non c'è niente da ripartire, e una
  // colonna destra vuota è peggio di una riga che non risponde.
  const vuota = persona.minutiTotali === 0

  return (
    <li>
      <button
        type="button"
        className={scelta ? 'riga-persona riga-persona-scelta' : 'riga-persona'}
        onClick={onScegli}
        disabled={vuota}
        aria-pressed={scelta}
      >
        <span className="riga-persona-capo">
          <span className={`gettone gettone-${persona.utenteId % 8}`}>
            <span aria-hidden="true">
              {persona.nome.charAt(0)}
              {persona.cognome.charAt(0)}
            </span>
          </span>
          <span className="riga-persona-nome">
            {persona.nome} {persona.cognome}
            {!persona.attivo && (
              // Un disattivato è in elenco solo perché ha ore nel periodo: il
              // suo lavoro è stato fatto, ma chi legge non deve cercarlo fra
              // chi è in forza oggi.
              <span className="nota"> · disattivato</span>
            )}
          </span>
          <span className="riga-persona-ore">{oreMinuti(persona.minutiTotali)}</span>
        </span>

        {dettaglio !== null ? (
          <BarraRipartizione
            segmenti={segmentiDi(dettaglio)}
            totale={dettaglio.minutiTotali}
          />
        ) : (
          <span className="barra-ripartizione-vuota" />
        )}

        {persona.minutiNonConfermati > 0 && (
          <span className="nota riga-persona-avviso">
            {oreMinuti(persona.minutiNonConfermati)} da confermare, già nel totale.
          </span>
        )}
      </button>
    </li>
  )
}

/**
 * La ripartizione di una persona: i suoi lavori, e dentro ciascuno le task.
 *
 * Un elenco e non una tabella: le colonne erano tre — nome, ore, quota — e due
 * di queste stanno in fondo alla riga senza bisogno di essere incolonnate,
 * mentre la barra sotto ogni lavoro dice il peso meglio di qualunque cifra
 * allineata.
 */
function Ripartizione({ dettaglio }: { dettaglio: ReportPersona }) {
  const totale = dettaglio.minutiTotali
  const senzaLavoro = dettaglio.senzaLavoro.reduce((somma, una) => somma + una.minuti, 0)

  return (
    <>
      <div className="testa-ripartizione">
        <span className={`gettone gettone-${dettaglio.utenteId % 8}`}>
          <span aria-hidden="true">
            {dettaglio.nome.charAt(0)}
            {dettaglio.cognome.charAt(0)}
          </span>
        </span>
        <span className="testa-ripartizione-nome">
          {dettaglio.nome} {dettaglio.cognome}
        </span>
        <span className="testa-ripartizione-ore">{oreMinuti(totale)}</span>
      </div>

      {dettaglio.lavori.map((uno) => (
        <Lavoro key={uno.lavoroId} lavoro={uno} totale={totale} />
      ))}

      {dettaglio.senzaLavoro.length > 0 && (
        <div className="voce-report">
          <div className="voce-report-capo">
            <span className="punto-tinta segmento-neutro" aria-hidden="true" />
            <span className="voce-report-nome">
              Senza lavoro
              {/* Non è un residuo da ignorare: è il contorno della settimana,
                  e vederlo crescere è un'informazione. */}
              <span className="nota"> · riunioni, formazione, amministrazione</span>
            </span>
            <span className="voce-report-ore">
              {oreMinuti(senzaLavoro)} · {percentuale(senzaLavoro, totale)}
            </span>
          </div>
          <Vasca parte={senzaLavoro} totale={totale} tinta="segmento-neutro" />
          {dettaglio.senzaLavoro.map((una) => (
            <div key={una.tipo} className="voce-figlia">
              <span>{una.etichetta}</span>
              <span className="voce-figlia-ore">{oreMinuti(una.minuti)}</span>
              <span className="voce-figlia-quota">{percentuale(una.minuti, senzaLavoro)}</span>
            </div>
          ))}
        </div>
      )}

      {dettaglio.lavori.length === 0 && dettaglio.senzaLavoro.length === 0 && (
        <p className="nota">Nessuna ora registrata in questo periodo.</p>
      )}
    </>
  )
}

/** Un lavoro con le sue task e le sue attività senza task. */
function Lavoro({ lavoro, totale }: { lavoro: VoceLavoro; totale: number }) {
  const tinta = tintaLavoro(lavoro.lavoroId)

  return (
    <div className="voce-report">
      <div className="voce-report-capo">
        <span className={`punto-tinta ${tinta}`} aria-hidden="true" />
        <span className="voce-report-nome">
          {/* Il codice del lavoro resta scritto: due tinte vicine non devono
              far perdere l'informazione, ed è la stessa regola del calendario
              del tempo. Nulli solo se il lavoro non esiste più. */}
          {lavoro.codice ?? `#${lavoro.lavoroId}`}
          {lavoro.nome !== null && ` · ${lavoro.nome}`}
        </span>
        <span className="voce-report-ore">
          {oreMinuti(lavoro.minuti)} · <span className="quota">{percentuale(lavoro.minuti, totale)}</span>
        </span>
      </div>

      <Vasca parte={lavoro.minuti} totale={totale} tinta={tinta} />

      {/* Le percentuali delle task sono **sul lavoro**, non sul mese: qui la
          domanda è come si è spezzato questo lavoro. Due denominatori nella
          stessa schermata si confonderebbero, se non fosse che stanno a due
          livelli di rientro diversi — ed è per questo che il rientro c'è. */}
      {lavoro.task.map((una) => (
        <div key={una.taskId} className="voce-figlia">
          <span>{una.titolo}</span>
          <span className="voce-figlia-ore">{oreMinuti(una.minuti)}</span>
          <span className="voce-figlia-quota">{percentuale(una.minuti, lavoro.minuti)}</span>
        </div>
      ))}

      {lavoro.attivitaDirette.map((una) => (
        <div key={una.tipo} className="voce-figlia">
          {/* Ore su questo lavoro ma fuori da ogni task: stanno dentro il
              lavoro perché gli appartengono — una manutenzione per quel
              cliente è tempo di quel cliente. */}
          <span>
            {una.etichetta} <span className="nota">· senza task</span>
          </span>
          <span className="voce-figlia-ore">{oreMinuti(una.minuti)}</span>
          <span className="voce-figlia-quota">{percentuale(una.minuti, lavoro.minuti)}</span>
        </div>
      ))}
    </div>
  )
}

/** La vasca di un lavoro: quanto pesa sul tempo della persona. */
function Vasca({
  parte,
  totale,
  tinta,
}: {
  parte: number
  totale: number
  tinta: string
}) {
  const sua = quota(parte, totale)
  return (
    <div className="vasca-report">
      <span
        className={`vasca-piena ${tinta}`}
        style={{ width: `${(sua ?? 0) * 100}%` }}
      />
    </div>
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

const PERIODI: { valore: Periodo; nome: string }[] = [
  { valore: 'SETTE', nome: '7 giorni' },
  { valore: 'QUATTORDICI', nome: '14 giorni' },
  { valore: 'MESE', nome: meseCorrente() },
]

/**
 * Le due date di un periodo.
 *
 * In ISO locale e non da `toISOString()`, che passa per UTC: in un fuso a est
 * di Greenwich il primo del mese diventerebbe l'ultimo del mese precedente.
 */
function intervallo(periodo: Periodo): { da: string; a: string } {
  const adesso = new Date()
  const a = iso(adesso)

  if (periodo === 'MESE') {
    return { da: iso(new Date(adesso.getFullYear(), adesso.getMonth(), 1)), a }
  }

  // Sette giorni vuol dire oggi e i sei precedenti: un intervallo che
  // comprendesse anche il settimo indietro sarebbe lungo otto.
  const indietro = periodo === 'SETTE' ? 6 : 13
  const da = new Date(adesso.getFullYear(), adesso.getMonth(), adesso.getDate() - indietro)
  return { da: iso(da), a }
}

/** «Novembre»: il nome del mese in corso, per l'etichetta del terzo periodo. */
function meseCorrente(): string {
  const nome = new Date().toLocaleDateString('it-IT', { month: 'long' })
  return nome.charAt(0).toUpperCase() + nome.slice(1)
}

/** Da «2026-11-01» a «1 nov». */
function breve(iso: string): string {
  return new Date(iso).toLocaleDateString('it-IT', { day: 'numeric', month: 'short' })
}

function iso(giorno: Date): string {
  const mese = String(giorno.getMonth() + 1).padStart(2, '0')
  const numero = String(giorno.getDate()).padStart(2, '0')
  return `${giorno.getFullYear()}-${mese}-${numero}`
}
