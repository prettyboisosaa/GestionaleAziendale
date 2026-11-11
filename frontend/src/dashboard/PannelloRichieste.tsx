import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import Pannello from '../componenti/Pannello'
import { Pillola } from '../componenti/Stato'
import Icona from '../componenti/Icona'
import type { NomeIcona } from '../componenti/Icona'
import { mieRichieste, richiesteInAttesa } from '../ore/api'
import type { RigaRichiestaOre, StatoRichiesta } from '../ore/api'
import { mieAssenze, tutteLeAssenze } from '../assenza/api'
import type { RigaAssenza, StatoAssenza } from '../assenza/api'
import { testo } from '../api/http'

/**
 * Quanti giorni indietro si guardano le richieste già chiuse.
 *
 * Una richiesta decisa un mese fa non è più una notizia: sta nella sua
 * pagina. Qui resta finché serve a sapere com'è andata.
 */
const GIORNI_DI_MEMORIA = 30

/**
 * I colori degli stati di una richiesta, in un posto solo.
 *
 * Ore e assenze avevano due vocabolari visivi diversi — le prime `origine-*`,
 * le seconde `pillola-*` — per dire le stesse tre cose. In un pannello che le
 * mette nella **stessa lista** questo non si può tenere: due righe in attesa
 * di colore diverso si leggono come due stati diversi. Si unificano su
 * `Pillola`, con le tinte che questa interfaccia già usa per «in sospeso»,
 * «andata bene» e «andata male».
 */
const TINTA_ESITO: Record<StatoRichiesta | StatoAssenza, string> = {
  IN_ATTESA: 'attesa',
  APPROVATA: 'completato',
  // Approvata, ma non per tutto quello che si era chiesto: non è un verde
  // pieno né un rifiuto, ed è l'unico stato che riguarda solo le ore.
  APPROVATA_PARZIALE: 'in-corso',
  RIFIUTATA: 'annullato',
  // Ritirata da chi l'aveva chiesta: non è un esito, è un ripensamento, e il
  // grigio dice che non c'è niente da leggerci.
  RITIRATA: 'attivo',
}

const ETICHETTA_ESITO: Record<StatoRichiesta | StatoAssenza, string> = {
  IN_ATTESA: 'In attesa',
  APPROVATA: 'Approvata',
  APPROVATA_PARZIALE: 'Approvata in parte',
  RIFIUTATA: 'Rifiutata',
  RITIRATA: 'Ritirata',
}

/**
 * Una richiesta, da qualunque delle due parti venga.
 *
 * Ore e assenze sono due tabelle, due rotte e due forme: quello che hanno in
 * comune è esattamente questo — chi, cosa, quando, com'è andata — ed è il
 * minimo che serve a metterle in fila insieme. La conversione sta in due
 * funzioni qui sotto, non nelle righe del JSX, così la lista non sa da dove
 * arrivano le sue righe.
 */
type Richiesta = {
  chiave: string
  icona: NomeIcona
  /** Che genere di richiesta è, per chi non distingue le due icone. */
  genere: string
  titolo: string
  dettaglio: string
  stato: StatoRichiesta | StatoAssenza
  /** Chi l'ha chiesta: serve solo nella coda di chi approva. */
  richiedente: string | null
  /** L'istante su cui si ordina: ISO, quindi confrontabile come stringa. */
  quando: string
  /** Dove si va a vederla per intero. */
  percorso: string
}

/**
 * Le proprie richieste, ore e assenze in una lista sola.
 *
 * **In attesa in cima**, perché sono le uniche su cui si può ancora fare
 * qualcosa; sotto quelle chiuse di recente, per sapere com'è andata senza
 * aprire due pagine. Erano appunto due pagine, e per sapere se si aveva
 * qualcosa in sospeso bisognava visitarle entrambe.
 */
export default function PannelloMieRichieste({
  /**
   * Vero se chi guarda può leggere le proprie richieste di ore.
   *
   * ⚠️ Serve davvero: `GET /api/richieste-ore` vuole `ORE_RICHIEDI`,
   * `ORE_RICHIESTE_GESTISCI` o `LAVORI_VISUALIZZA_TUTTI`, e **nessuno dei tre
   * è un permesso di base**. Un dipendente che non chiede ore prenderebbe un
   * 403, e con `Promise.all` quel rifiuto farebbe fallire anche il ramo delle
   * assenze: il pannello mostrerebbe un errore a tutti quelli che hanno solo
   * il pavimento. Le assenze invece passano da `ASSENZE_RICHIEDI`, che è
   * base, e si chiedono sempre.
   */
  puoOre,
}: {
  puoOre: boolean
}) {
  const [righe, setRighe] = useState<Richiesta[] | null>(null)
  const [errore, setErrore] = useState<string | null>(null)

  useEffect(() => {
    let annullato = false

    // Le due rotte partono insieme: sono indipendenti, e in fila costerebbero
    // il doppio dell'attesa per nessun motivo.
    Promise.all([
      puoOre ? mieRichieste() : Promise.resolve<RigaRichiestaOre[]>([]),
      mieAssenze(),
    ])
      .then(([ore, assenze]) => {
        if (!annullato) {
          setRighe(
            ordina([
              ...ore.map((una) => daOre(una, '/richieste-ore')),
              ...assenze.map((una) => daAssenza(una, '/assenze')),
            ]),
          )
          setErrore(null)
        }
      })
      .catch((motivo: unknown) => {
        if (!annullato) {
          setErrore(testo(motivo))
        }
      })

    return () => {
      annullato = true
    }
  }, [puoOre])

  const inAttesa = (righe ?? []).filter((una) => una.stato === 'IN_ATTESA').length

  return (
    <Pannello
      titolo="Le mie richieste"
      sottotitolo="ore e assenze insieme"
      azioni={
        inAttesa > 0 ? (
          <Pillola tinta="attesa">
            {inAttesa === 1 ? '1 in attesa' : `${inAttesa} in attesa`}
          </Pillola>
        ) : undefined
      }
      carico={righe === null && errore === null}
      errore={errore}
      vuoto={
        righe !== null && righe.length === 0
          ? 'Non hai richieste degli ultimi 30 giorni.'
          : null
      }
      classe="pannello-richieste"
    >
      <Elenco righe={righe ?? []} conRichiedente={false} />
    </Pannello>
  )
}

/**
 * La coda di chi approva: le richieste degli altri, ancora in attesa.
 *
 * **Non si approva da qui.** La riga porta alla pagina che decide, dove ci
 * sono il contesto e la nota dell'esito: approvare con un clic da un elenco
 * che non mostra quante ore restano nel monte o chi altro è in ferie quel
 * giorno vorrebbe dire decidere senza guardare.
 */
export function PannelloDaApprovare({
  puoOre,
  puoAssenze,
}: {
  puoOre: boolean
  puoAssenze: boolean
}) {
  const [righe, setRighe] = useState<Richiesta[] | null>(null)
  const [errore, setErrore] = useState<string | null>(null)

  useEffect(() => {
    let annullato = false

    // Si chiede solo quello che si ha il permesso di vedere: chiedere l'altra
    // rotta per poi ignorare il 403 riempirebbe la console di errori che non
    // sono errori.
    Promise.all([
      puoOre ? richiesteInAttesa() : Promise.resolve<RigaRichiestaOre[]>([]),
      puoAssenze ? tutteLeAssenze('IN_ATTESA', null) : Promise.resolve<RigaAssenza[]>([]),
    ])
      .then(([ore, assenze]) => {
        if (!annullato) {
          setRighe(
            ordina([
              ...ore.map((una) => daOre(una, '/richieste-ore')),
              ...assenze.map((una) => daAssenza(una, '/richieste-assenza')),
            ]),
          )
          setErrore(null)
        }
      })
      .catch((motivo: unknown) => {
        if (!annullato) {
          setErrore(testo(motivo))
        }
      })

    return () => {
      annullato = true
    }
  }, [puoOre, puoAssenze])

  return (
    <Pannello
      titolo="Richieste da approvare"
      sottotitolo="di chi aspetta una risposta"
      azioni={
        righe !== null && righe.length > 0 ? (
          <Pillola tinta="attesa">{String(righe.length)}</Pillola>
        ) : undefined
      }
      carico={righe === null && errore === null}
      errore={errore}
      vuoto={righe !== null && righe.length === 0 ? 'Nessuna richiesta in attesa.' : null}
      classe="pannello-richieste"
    >
      <Elenco righe={righe ?? []} conRichiedente />
    </Pannello>
  )
}

/** Le righe, uguali nei due pannelli: cambia solo se si nomina chi ha chiesto. */
function Elenco({
  righe,
  conRichiedente,
}: {
  righe: Richiesta[]
  conRichiedente: boolean
}) {
  return (
    <ul className="righe-richiesta">
      {righe.map((una) => (
        <li key={una.chiave} className="riga-richiesta">
          {/* L'icona distingue ore e assenze a colpo d'occhio; il genere
              scritto accanto al titolo la rende leggibile a chi la vede come
              un quadratino. */}
          <span className="riga-richiesta-genere" title={una.genere}>
            <Icona nome={una.icona} />
            <span className="solo-lettori">{una.genere}</span>
          </span>

          <span className="riga-richiesta-corpo">
            <Link to={una.percorso} className="riga-richiesta-titolo">
              {una.titolo}
            </Link>
            <span className="nota">
              {conRichiedente && una.richiedente !== null && `${una.richiedente} · `}
              {una.dettaglio}
            </span>
          </span>

          <Pillola tinta={TINTA_ESITO[una.stato]}>{ETICHETTA_ESITO[una.stato]}</Pillola>
        </li>
      ))}
    </ul>
  )
}

/**
 * In attesa prima, poi le chiuse dalla più recente, e si butta via quello che
 * è più vecchio della memoria del pannello.
 *
 * Le in attesa non si scartano mai per data: una richiesta di due mesi fa che
 * nessuno ha ancora deciso è esattamente la cosa che questo pannello esiste
 * per far vedere.
 */
function ordina(righe: Richiesta[]): Richiesta[] {
  const limite = new Date(Date.now() - GIORNI_DI_MEMORIA * 86_400_000)
    .toISOString()
    .slice(0, 10)

  return righe
    .filter((una) => una.stato === 'IN_ATTESA' || una.quando.slice(0, 10) >= limite)
    .sort((una, altra) => {
      const unaAttesa = una.stato === 'IN_ATTESA' ? 0 : 1
      const altraAttesa = altra.stato === 'IN_ATTESA' ? 0 : 1
      if (unaAttesa !== altraAttesa) {
        return unaAttesa - altraAttesa
      }
      return altra.quando.localeCompare(una.quando)
    })
}

function daOre(riga: RigaRichiestaOre, percorso: string): Richiesta {
  const quante = riga.oreRichieste.toLocaleString('it-IT', { maximumFractionDigits: 2 })
  return {
    chiave: `ore-${riga.id}`,
    icona: 'ore',
    genere: 'Richiesta di ore',
    titolo: `${quante} h su ${riga.codiceLavoro}`,
    dettaglio: riga.task ?? riga.nomeLavoro,
    stato: riga.stato,
    richiedente: riga.richiedente,
    // Si ordina sulla data della **decisione** quando c'è, altrimenti su
    // quella della domanda: di una richiesta chiusa interessa quando è stata
    // chiusa, non quando era stata aperta.
    quando: riga.gestitaIl ?? riga.creataIl,
    percorso,
  }
}

function daAssenza(riga: RigaAssenza, percorso: string): Richiesta {
  return {
    chiave: `assenza-${riga.id}`,
    icona: 'assenze',
    genere: 'Richiesta di assenza',
    titolo: riga.tipoEtichetta,
    // Il riepilogo lo scrive il server, che conosce festività e giorni
    // lavorativi: rifarlo qui vorrebbe dire una seconda definizione di
    // giorno lavorativo.
    dettaglio: riga.riepilogo ?? riga.dataInizio,
    stato: riga.stato,
    richiedente: riga.richiedente,
    quando: riga.gestitaIl ?? riga.creataIl,
    percorso,
  }
}
