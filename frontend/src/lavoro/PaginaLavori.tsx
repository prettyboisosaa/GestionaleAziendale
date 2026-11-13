import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { annullaLavoro, elencoLavori, eliminaLavoro } from './api'
import type { RigaLavoro } from './api'
import { testo } from '../api/http'
import type { UtenteCorrente } from '../autenticazione/api'
import { ha } from '../autenticazione/permessi'
import { Pillola } from '../componenti/Stato'
import ConfermaAzione from '../componenti/ConfermaAzione'
import { useNotifiche } from '../componenti/Notifiche'
import { chiusoLavoro, ETICHETTA_LAVORO, tinta } from '../stato/stati'

/**
 * L'azione in attesa di conferma.
 *
 * Porta con sé codice e nome del lavoro, e non il solo `id`: la finestra di
 * conferma deve poter **nominare** ciò che sta per toccare. «Cancellare?» non
 * dice niente a chi ha premuto per sbaglio sulla scheda accanto a quella che
 * voleva; «Cancellare GEST-04?» sì. Cercarli nell'elenco al momento di
 * disegnare la finestra funzionerebbe quasi sempre — ma non subito dopo una
 * ricarica, quando la riga potrebbe non esserci più.
 */
type Azione = {
  id: number
  tipo: 'annulla' | 'elimina'
  codice: string
  nome: string
  /** Vero se il lavoro ha delle task: cambia quello che la conferma promette. */
  conTask: boolean
}

/**
 * Il filtro dell'elenco: non è uno stato, è un **gruppo** di stati.
 *
 * Prima qui c'era una tendina con i cinque stati dell'enum, uno per uno. Le
 * schede del design system mostrano invece tre scelte sole, e non è una
 * semplificazione: chi apre l'elenco sta decidendo dove entrare, e la
 * differenza fra «attivo» e «in corso» non cambia quella decisione — mentre la
 * differenza fra «c'è ancora da fare» e «è chiuso» sì.
 */
type Filtro = 'TUTTI' | 'APERTI' | 'CHIUSI' | 'ANNULLATI'

/** Da «2027-01-15» a «15 gen»: la scadenza su una scheda non ha bisogno dell'anno. */
function scadenza(iso: string): string {
  return new Date(iso).toLocaleDateString('it-IT', { day: 'numeric', month: 'short' })
}

/** Le ore senza decimali inutili: «12 h», «116 h», «7,5 h». */
function ore(valore: number): string {
  return `${valore.toLocaleString('it-IT', { maximumFractionDigits: 1 })} h`
}

/**
 * A che punto è un lavoro, in percentuale di task chiuse.
 *
 * **Null quando non ci sono task**, e non zero: un lavoro appena creato non è
 * «allo 0%», è un lavoro su cui non c'è ancora niente da misurare. È la stessa
 * regola dei KPI del server, dove un denominatore a zero risponde «n/d».
 */
function avanzamento(riga: RigaLavoro): number | null {
  if (riga.taskTotali === 0) {
    return null
  }
  return Math.round((riga.taskCompletate / riga.taskTotali) * 100)
}

/**
 * Le ore che restano da consumare: monte ore meno quelle registrate.
 *
 * Non è `oreLibere`, che è un'altra domanda — quelle sono le ore non ancora
 * distribuite alle task. Qui serve quanto resta del budget vero, perché è il
 * numero che dice se un lavoro arriverà in fondo.
 */
function oreRimaste(riga: RigaLavoro): number | null {
  if (riga.monteOre === null) {
    return null
  }
  return riga.monteOre - riga.oreConsumate
}

export default function PaginaLavori({ utente }: { utente: UtenteCorrente }) {
  const [righe, setRighe] = useState<RigaLavoro[] | null>(null)
  const [errore, setErrore] = useState<string | null>(null)
  const [azione, setAzione] = useState<Azione | null>(null)
  const [inCorso, setInCorso] = useState(false)
  const [filtro, setFiltro] = useState<Filtro>('TUTTI')
  const notifiche = useNotifiche()

  // Due permessi, due portate. `LAVORI_MODIFICA_TUTTI` annulla e cancella
  // qualunque lavoro; `LAVORI_MODIFICA_PROPRI` annulla solo quelli di cui si è
  // responsabili, e non cancella niente — la cancellazione è definitiva e resta
  // all'amministratore.
  const puoModificareTutti = ha(utente, 'LAVORI_MODIFICA_TUTTI')
  const puoModificarePropri = ha(utente, 'LAVORI_MODIFICA_PROPRI')

  /**
   * Chi ha solo il permesso sui propri annulla il lavoro di cui è
   * responsabile, e il confronto è sull'id: il nome formattato sarebbe
   * ambiguo fra due omonimi. È la stessa condizione che il servizio verifica
   * con `eResponsabile`, quindi il pulsante compare dove non prenderà 403.
   */
  const puoAnnullare = (riga: RigaLavoro) =>
    puoModificareTutti || (puoModificarePropri && riga.responsabileId === utente.id)

  /**
   * Si chiedono **sempre** anche gli annullati, e poi si filtra qui.
   *
   * Prima era un interruttore che rifaceva la richiesta: i chip devono poter
   * scrivere accanto al proprio nome quanti lavori contengono, e un contatore
   * non si può scrivere su righe che non si hanno. Gli annullati restano
   * comunque fuori dalla vista di partenza — il loro chip compare solo se ce
   * ne sono, e non è mai quello selezionato all'apertura.
   */
  useEffect(() => {
    let annullato = false

    elencoLavori(true)
      .then((elenco) => {
        if (!annullato) {
          setRighe(elenco)
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
  }, [])

  async function esegui(scelta: Azione) {
    setInCorso(true)
    setErrore(null)
    try {
      if (scelta.tipo === 'annulla') {
        await annullaLavoro(scelta.id)
      } else {
        await eliminaLavoro(scelta.id)
      }
      // Ricarica dopo l'azione: lo stato è cambiato, o la riga non c'è più.
      setRighe(await elencoLavori(true))

      notifiche.successo(
        scelta.tipo === 'annulla'
          ? `${scelta.codice} annullato.`
          : `${scelta.codice} cancellato.`,
      )
    } catch (motivo) {
      // Il caso tipico è il 409 su una bozza che ha delle task: il messaggio
      // arriva dal server e spiega di annullare invece di cancellare.
      //
      // In una pastiglia e non più in cima alla pagina: l'elenco dei lavori è
      // una griglia che scorre, e chi preme su una scheda in fondo non vedeva
      // mai il messaggio che spiegava perché non era successo niente. Con
      // «Riprova» accanto, perché il 409 può dipendere da una task chiusa nel
      // frattempo da qualcun altro.
      notifiche.errore(testo(motivo), {
        azione: { etichetta: 'Riprova', onPremi: () => void esegui(scelta) },
      })
    } finally {
      setAzione(null)
      setInCorso(false)
    }
  }

  const tutte = righe ?? []
  const aperti = tutte.filter((riga) => !chiusoLavoro(riga.stato))
  const chiusi = tutte.filter((riga) => riga.stato === 'COMPLETATO')
  const annullati = tutte.filter((riga) => riga.stato === 'ANNULLATO')

  // «Tutti» non comprende gli annullati, ed è voluto: un lavoro che non si farà
  // non è uno dei lavori. Si guarda dal suo chip, che compare solo se ce n'è
  // almeno uno.
  const vivi = tutte.filter((riga) => riga.stato !== 'ANNULLATO')
  const gruppi: Record<Filtro, RigaLavoro[]> = {
    TUTTI: vivi,
    APERTI: aperti,
    CHIUSI: chiusi,
    ANNULLATI: annullati,
  }
  const visibili = gruppi[filtro]

  const chip: { valore: Filtro; nome: string }[] = [
    { valore: 'TUTTI', nome: 'Tutti' },
    { valore: 'APERTI', nome: 'In corso' },
    { valore: 'CHIUSI', nome: 'Completati' },
    ...(annullati.length > 0
      ? [{ valore: 'ANNULLATI' as Filtro, nome: 'Annullati' }]
      : []),
  ]

  return (
    <main className="pagina-larga">
      {/* L'occhiello sopra il titolo: il design system intesta così ogni
          schermata, e qui dice in che sezione si è entrati mentre il titolo
          dice che cosa ci si trova. */}
      <p className="occhiello">Lavori</p>
      <div className="testata testata-lavori">
        <h1>Lavori attivi</h1>
        {ha(utente, 'LAVORI_CREA') && (
          <Link to="/lavori/nuovo" className="azione">
            Nuovo lavoro
          </Link>
        )}
      </div>

      {errore !== null && (
        <p className="errore" role="alert">
          {errore}
        </p>
      )}

      {/* I chip con il loro contatore. Il numero accanto al nome non è
          decorazione: dice quanto c'è dietro un filtro **prima** di premerlo,
          e evita il giro «premo, è vuoto, torno indietro». */}
      {righe !== null && righe.length > 0 && (
        <div className="chip-filtri" role="group" aria-label="Filtra i lavori">
          {chip.map((uno) => (
            <button
              key={uno.valore}
              type="button"
              className={filtro === uno.valore ? 'chip chip-scelto' : 'chip'}
              aria-pressed={filtro === uno.valore}
              onClick={() => setFiltro(uno.valore)}
            >
              {uno.nome} · {gruppi[uno.valore].length}
            </button>
          ))}
        </div>
      )}

      {errore === null && righe === null && (
        <p className="sommario" role="status">
          Carico l'elenco…
        </p>
      )}

      {righe !== null && righe.length === 0 && (
        <p className="sommario">Nessun lavoro da mostrare.</p>
      )}

      {/* Nessun risultato è una cosa diversa da nessun lavoro: qui c'è un
          rimedio da offrire, perché è un chip a nascondere tutto. */}
      {righe !== null && righe.length > 0 && visibili.length === 0 && (
        <p className="sommario">
          Nessun lavoro in questo gruppo.{' '}
          <button type="button" className="minuscolo" onClick={() => setFiltro('TUTTI')}>
            Mostra tutti
          </button>
        </p>
      )}

      {visibili.length > 0 && (
        <div className="griglia-lavori">
          {visibili.map((riga) => (
            <CardLavoro
              key={riga.id}
              riga={riga}
              inCorso={inCorso}
              puoAnnullare={puoAnnullare(riga)}
              puoEliminare={puoModificareTutti}
              onChiedi={setAzione}
            />
          ))}
        </div>
      )}

      {/* La conferma vive qui e non nella scheda: una sola finestra per la
          pagina, qualunque riga l'abbia chiesta.

          Le due varianti dicono cose diverse perché **sono** cose diverse, ed
          è la distinzione su cui questo gestionale è costruito: annullare
          lascia tutto dov'è e cambia uno stato, cancellare porta via la riga.
          Solo la seconda è `distruttiva`. Se il corallo comparisse anche su un
          annullamento, il giorno che compare su una cancellazione vera non
          direbbe più niente. */}
      {azione !== null && (
        <ConfermaAzione
          icona={azione.tipo === 'annulla' ? 'chiudi' : 'cestino'}
          variante={azione.tipo === 'annulla' ? 'normale' : 'distruttiva'}
          titolo={
            azione.tipo === 'annulla'
              ? `Annullare ${azione.codice}?`
              : `Cancellare ${azione.codice}?`
          }
          descrizione={
            azione.tipo === 'annulla' ? (
              <>
                «{azione.nome}» passa fra gli annullati e smette di comparire
                fra i lavori aperti. <strong>Le ore già registrate restano</strong>,
                e la scheda resta consultabile: niente va perso.
              </>
            ) : (
              <>
                «{azione.nome}» sparisce dall'elenco e non si può recuperare.
                {azione.conTask
                  ? ' Questo lavoro ha delle task: quasi certamente il server rifiuterà, e la cosa giusta è annullarlo invece di cancellarlo.'
                  : ' È una bozza senza task, quindi non porta via nessuna ora registrata.'}
              </>
            )
          }
          etichettaConferma={azione.tipo === 'annulla' ? 'Annulla il lavoro' : 'Cancella'}
          // «Annulla» come etichetta del pulsante che desiste, accanto a un'azione
          // che si chiama anch'essa «annulla», sarebbe una domanda invece che una
          // risposta: qui chi desiste «lascia stare».
          etichettaAnnulla="Lascia stare"
          inCorso={inCorso}
          onConferma={() => void esegui(azione)}
          onChiudi={() => setAzione(null)}
        />
      )}
    </main>
  )
}

/**
 * Un lavoro come scheda, nella forma del design system: in alto codice e
 * stato, poi il nome, poi **i tre numeri che fanno decidere dove entrare** —
 * a che punto sono le task, quante ore restano, quando scade.
 *
 * Prima al loro posto c'era la descrizione del lavoro. Una descrizione su una
 * scheda si legge una volta sola, la prima: dalla seconda in poi chi guarda
 * l'elenco sta cercando un lavoro che ha bisogno di lui, e per quello servono
 * dei numeri.
 *
 * **L'intera scheda è un collegamento vero**, non un `onClick` su un `div`: il
 * nome è dentro un `<a>` che si allarga a coprire la scheda con un `::after`,
 * così si apre in una nuova finestra con il tasto centrale, si copia
 * l'indirizzo col destro e la tastiera ci arriva una volta sola invece che su
 * ogni riga. I pulsanti delle azioni stanno **sopra** quel velo, altrimenti
 * premerli aprirebbe il dettaglio.
 */
function CardLavoro({
  riga,
  inCorso,
  puoAnnullare,
  puoEliminare,
  onChiedi,
}: {
  riga: RigaLavoro
  /** Vero mentre un'azione è in volo: i pulsanti di tutte le schede si spengono. */
  inCorso: boolean
  puoAnnullare: boolean
  puoEliminare: boolean
  onChiedi: (azione: Azione) => void
}) {
  // Un lavoro completato non si annulla, e uno già annullato nemmeno: il
  // server rifiuta entrambi, e offrire il pulsante sarebbe una promessa falsa.
  // La cancellazione si offre solo sulle bozze, e solo a chi ha il permesso su
  // tutti i lavori: cancellare è definitivo, e `LAVORI_MODIFICA_PROPRI` non lo
  // concede. Se poi quella bozza ha delle task, il server risponde 409 e il
  // messaggio spiega di annullarla.
  const annullabile = !chiusoLavoro(riga.stato) && puoAnnullare
  const eliminabile = riga.stato === 'BOZZA' && puoEliminare
  const chiuso = chiusoLavoro(riga.stato)

  const quota = avanzamento(riga)
  const rimaste = oreRimaste(riga)

  // Le ore rimaste diventano corallo quando sono poche: sotto un decimo del
  // monte ore. È l'unico colore acceso della scheda, e sta sul solo numero
  // che chiede di fare qualcosa — su un lavoro chiuso non chiede niente, e
  // resta inchiostro.
  const poche =
    !chiuso
    && rimaste !== null
    && riga.monteOre !== null
    && riga.monteOre > 0
    && rimaste <= riga.monteOre / 10

  return (
    <article
      className={
        riga.stato === 'ANNULLATO' ? 'card-lavoro card-lavoro-spenta' : 'card-lavoro'
      }
    >
      <div className="card-lavoro-capo">
        <span className="card-lavoro-codice">
          {riga.codice}
          {riga.responsabile !== null && ` · ${riga.responsabile}`}
        </span>
        <Pillola tinta={tinta(riga.stato)}>{ETICHETTA_LAVORO[riga.stato]}</Pillola>
      </div>

      <h2 className="card-lavoro-nome">
        <Link to={`/lavori/${riga.id}`}>{riga.nome}</Link>
      </h2>

      {/* L'avanzamento: la frase a sinistra, la percentuale a destra, la barra
          sotto. La frase e la barra dicono la stessa cosa in due modi, e non è
          una ripetizione: la barra si legge a colpo d'occhio su quattro schede
          in fila, la frase dice su quante task è calcolata. */}
      <div className="card-lavoro-avanzamento">
        <div className="card-lavoro-riga">
          <span className="nota nota-piena">
            {riga.taskTotali === 0
              ? 'Nessuna task'
              : `${riga.taskCompletate} di ${riga.taskTotali} task completate`}
          </span>
          <span className="card-lavoro-quota">{quota === null ? '—' : `${quota}%`}</span>
        </div>
        <Barra quota={quota ?? 0} completo={quota === 100} />
      </div>

      <div className="card-lavoro-piede">
        <div className="card-lavoro-colonna">
          <span className="card-lavoro-etichetta">Ore rimaste</span>
          <span className={poche ? 'card-lavoro-ore card-lavoro-poche' : 'card-lavoro-ore'}>
            {rimaste === null ? '—' : ore(rimaste)}
          </span>
        </div>

        <div className="card-lavoro-colonna card-lavoro-colonna-destra">
          <span className="nota">
            {chiuso ? 'Chiuso' : `Scade ${scadenza(riga.dataFinePrevista)}`}
          </span>
          {/* Le priorità alte si scrivono solo dove ce ne sono: uno «0» su
              ogni scheda sarebbe rumore, e la sua assenza dice già la stessa
              cosa. Su un lavoro chiuso non si scrivono affatto — le sue task
              aperte, se ce ne fossero, non sono più un problema di oggi. */}
          {!chiuso && riga.taskUrgenti > 0 && (
            <span className="segnale-urgenza">
              {riga.taskUrgenti === 1 ? '1 priorità alta' : `${riga.taskUrgenti} priorità alte`}
            </span>
          )}
        </div>
      </div>

      {/* I due pulsanti restano sempre quelli che sono: la domanda non si apre
          più **dentro** la scheda.

          Prima «Annullare? Sì / No» sostituiva le azioni al loro posto, e due
          cose non andavano. La scheda cambiava altezza, quindi la griglia si
          riassestava sotto il dito proprio mentre si stava per premere; e un
          «Sì» grande come un «No», a mezzo centimetro l'uno dall'altro, è la
          disposizione che fa cancellare un lavoro per sbaglio. La finestra
          della tavola 05 chiede la stessa cosa fermando tutto il resto, e
          mette la conferma dalla parte opposta rispetto al pulsante che la ha
          aperta. */}
      {(annullabile || eliminabile) && (
        <div className="card-lavoro-azioni">
          {annullabile && (
            <button
              type="button"
              className="minuscolo"
              disabled={inCorso}
              onClick={() =>
                onChiedi({
                  id: riga.id,
                  tipo: 'annulla',
                  codice: riga.codice,
                  nome: riga.nome,
                  conTask: riga.taskTotali > 0,
                })
              }
            >
              Annulla
            </button>
          )}
          {eliminabile && (
            <button
              type="button"
              className="minuscolo"
              disabled={inCorso}
              onClick={() =>
                onChiedi({
                  id: riga.id,
                  tipo: 'elimina',
                  codice: riga.codice,
                  nome: riga.nome,
                  conTask: riga.taskTotali > 0,
                })
              }
            >
              Elimina
            </button>
          )}
        </div>
      )}
    </article>
  )
}

/**
 * La barra di avanzamento di una scheda.
 *
 * `role="progressbar"` con i suoi valori, non un `div` colorato: la
 * percentuale accanto si legge, la barra no, e senza i ruoli chi usa un
 * lettore di schermo sentirebbe soltanto che lì c'è qualcosa.
 */
function Barra({ quota, completo }: { quota: number; completo: boolean }) {
  return (
    <div
      className="barra-avanzamento"
      role="progressbar"
      aria-valuenow={quota}
      aria-valuemin={0}
      aria-valuemax={100}
    >
      <span
        className={completo ? 'barra-piena barra-completa' : 'barra-piena'}
        style={{ width: `${quota}%` }}
      />
    </div>
  )
}
