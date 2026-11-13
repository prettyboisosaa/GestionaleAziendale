import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import {
  chiediAssenza,
  ETICHETTA_MOTIVO,
  ETICHETTA_STATO,
  mieAssenze,
  modificaAssenza,
  ritiraAssenza,
  TINTA_STATO,
} from './api'
import type { MotivoPermesso, NuovaAssenza, RigaAssenza, TipoAssenza } from './api'
import { testo } from '../api/http'
import Pannello from '../componenti/Pannello'
import { Pillola } from '../componenti/Stato'

/** Da «2027-01-01» a «01/01/2027». */
function data(iso: string): string {
  return new Date(iso).toLocaleDateString('it-IT')
}

/** Da «09:30:00» a «09:30»: i secondi non servono a nessuno. */
function ora(iso: string): string {
  return iso.slice(0, 5)
}

/** Il periodo in una riga: date, oppure data più fascia oraria. */
function periodo(riga: RigaAssenza): string {
  if (riga.oraInizio !== null && riga.oraFine !== null) {
    return `${data(riga.dataInizio)}, ${ora(riga.oraInizio)}–${ora(riga.oraFine)}`
  }
  if (riga.dataInizio === riga.dataFine) {
    return data(riga.dataInizio)
  }
  return `${data(riga.dataInizio)} – ${data(riga.dataFine)}`
}

/** Oggi in formato ISO, per il minimo dei campi data: non si chiede il passato. */
function oggi(): string {
  return new Date().toISOString().slice(0, 10)
}

/**
 * Lo stato del modulo.
 *
 * **Una fase sola, non tre.** Prima erano `chiuso` → `scelta` → `compila`, e
 * con `modifica` che raddoppiava ciascuna facevano sei combinazioni di
 * rendering nella stessa pagina: per arrivare al modulo si attraversavano tre
 * schermate, e il pulsante «Nuova richiesta» spariva per strada. La tipologia
 * ora si scegli *dentro* il modulo, come un campo qualunque — perché è un
 * campo qualunque.
 */
type Modulo = {
  aperto: boolean
  tipo: TipoAssenza
  /** Null per una richiesta nuova, l'id di quella che si sta correggendo. */
  modifica: number | null
  /**
   * Solo in modifica: la richiesta che si sta correggendo ha una fascia
   * oraria.
   *
   * Il modulo di richiesta non chiede orari — è la schermata del design
   * system — ma nel database esistono permessi orari nati prima. Correggerne
   * uno senza questo interruttore vorrebbe dire riscriverlo a giornata
   * intera senza dirlo a nessuno: le due ore compaiono solo per quelle righe.
   */
  conOrario: boolean
  /**
   * Solo in modifica: la richiesta dura più di un giorno **pur non essendo
   * ferie**. Le ferie l'intervallo ce l'hanno sempre, e non ha bisogno di
   * essere ricordato qui: lo dice la tipologia.
   */
  piuGiorni: boolean
}

const MODULO_CHIUSO: Modulo = {
  aperto: false,
  tipo: 'FERIE',
  modifica: null,
  conOrario: false,
  piuGiorni: false,
}

/** I campi del form, tutti insieme: quali contano lo dice la tipologia. */
type Campi = {
  dataInizio: string
  dataFine: string
  oraInizio: string
  oraFine: string
  motivo: MotivoPermesso
  note: string
}

/**
 * Il motivo di una richiesta nuova è «Altro»: il modulo del design system non
 * ha la tendina dei motivi, e lo si legge dalle note — che per «Altro» il
 * server pretende, ed è il motivo per cui su un permesso smettono di essere
 * facoltative.
 */
const CAMPI_VUOTI: Campi = {
  dataInizio: '',
  dataFine: '',
  oraInizio: '',
  oraFine: '',
  motivo: 'ALTRO',
  note: '',
}

/** I campi già riempiti con una richiesta esistente, per la modifica. */
function campiDa(riga: RigaAssenza): Campi {
  return {
    dataInizio: riga.dataInizio,
    dataFine: riga.dataFine,
    oraInizio: riga.oraInizio === null ? '' : ora(riga.oraInizio),
    oraFine: riga.oraFine === null ? '' : ora(riga.oraFine),
    // Il motivo scelto a suo tempo si conserva: la tendina non c'è più, e
    // sovrascriverlo con «Altro» cambierebbe la richiesta mentre la si
    // corregge.
    motivo: riga.motivo ?? 'ALTRO',
    note: riga.note ?? '',
  }
}

/**
 * Le proprie ferie e i propri permessi: si chiedono, e si guarda come sono
 * andati.
 *
 * **Due elenchi, non uno.** «In attesa» porta le richieste su cui c'è ancora
 * qualcosa da fare — modificarle, ritirarle — e «Storico» quelle decise. Prima
 * stavano nella stessa tabella, distinte solo dall'ordine che arriva dal
 * server: per sapere se qualcosa era in sospeso bisognava leggere la colonna
 * «Stato» riga per riga.
 *
 * **Il modulo prende la pagina intera.** È la schermata «Richiedi ferie o
 * permesso» del design system: occhiello, titolo, una scheda sola con il tipo,
 * il giorno e le note. Gli elenchi si fanno da parte mentre è aperta — non
 * c'è niente, lì sotto, che serva a compilarla, e «Annulla» li riporta.
 */
export default function PaginaMieAssenze() {
  const [righe, setRighe] = useState<RigaAssenza[] | null>(null)
  /**
   * Due errori, non uno.
   *
   * Prima un solo banner in cima raccoglieva il caricamento, l'invio del
   * modulo e il ritiro: un errore nato su una riga in fondo alla tabella
   * compariva fuori dalla vista, dove nessuno lo leggeva.
   */
  const [errore, setErrore] = useState<string | null>(null)
  const [erroreModulo, setErroreModulo] = useState<string | null>(null)
  const [modulo, setModulo] = useState<Modulo>(MODULO_CHIUSO)
  const [campi, setCampi] = useState<Campi>(CAMPI_VUOTI)
  const [inInvio, setInInvio] = useState(false)
  /**
   * La riga su cui si sta ritirando, per id.
   *
   * Non un booleano globale: con quello, ritirare la richiesta in fondo
   * spegneva i pulsanti di tutte le righe e del modulo.
   */
  const [inRitiro, setInRitiro] = useState<number | null>(null)
  /** La richiesta per cui si sta chiedendo conferma del ritiro. */
  const [daRitirare, setDaRitirare] = useState<number | null>(null)

  useEffect(() => {
    let annullato = false

    mieAssenze()
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

  function apri() {
    setCampi(CAMPI_VUOTI)
    setErroreModulo(null)
    setModulo({ ...MODULO_CHIUSO, aperto: true })
  }

  function apriModifica(riga: RigaAssenza) {
    setCampi(campiDa(riga))
    setErroreModulo(null)
    setDaRitirare(null)
    setModulo({
      aperto: true,
      tipo: riga.tipo,
      modifica: riga.id,
      conOrario: riga.oraInizio !== null,
      piuGiorni: riga.dataInizio !== riga.dataFine,
    })
  }

  function chiudi() {
    setModulo(MODULO_CHIUSO)
    setCampi(CAMPI_VUOTI)
    setErroreModulo(null)
  }

  /**
   * Cambiare tipologia azzera i campi, tranne quando si sta correggendo: lì
   * la tipologia non si cambia, perché cambiarla vorrebbe dire un'altra
   * richiesta.
   */
  function cambiaTipo(tipo: TipoAssenza) {
    if (modulo.modifica !== null || modulo.tipo === tipo) {
      return
    }
    setCampi(CAMPI_VUOTI)
    setErroreModulo(null)
    setModulo((prima) => ({ ...prima, tipo }))
  }

  async function invia(evento: FormEvent) {
    evento.preventDefault()

    setInInvio(true)
    setErroreModulo(null)
    try {
      const dati = corpo(modulo, campi)
      const salvata =
        modulo.modifica === null
          ? await chiediAssenza(dati)
          : await modificaAssenza(modulo.modifica, dati)

      setRighe((attuali) => sostituisci(attuali, salvata, modulo.modifica === null))
      chiudi()
    } catch (motivo) {
      // I messaggi del server spiegano già cosa manca o cosa si sovrappone:
      // CAMPI_MANCANTI, DATA_PASSATA, RICHIESTA_SOVRAPPOSTA. Il codice in
      // testa lo toglie `testo`, e resta la frase in italiano.
      setErroreModulo(testo(motivo))
    } finally {
      setInInvio(false)
    }
  }

  async function ritira(id: number) {
    setInRitiro(id)
    setErrore(null)
    try {
      const aggiornata = await ritiraAssenza(id)
      setRighe((attuali) => sostituisci(attuali, aggiornata, false))
      setDaRitirare(null)
    } catch (motivo) {
      setErrore(testo(motivo))
    } finally {
      setInRitiro(null)
    }
  }

  const permesso = modulo.tipo !== 'FERIE'

  /**
   * Le ferie si chiedono **dal … al …**, il permesso per un giorno solo.
   *
   * Il foglio del design disegna un campo «Giorno» soltanto, e per le ferie
   * non basta: una settimana di ferie è una richiesta sola, e chiederla un
   * giorno per volta vorrebbe dire cinque righe da approvare una a una — oltre
   * a far scattare il controllo delle sovrapposizioni alla seconda. Il campo
   * singolo resta dov'è giusto: sul permesso, che riguarda un giorno e basta.
   *
   * In modifica l'intervallo resta anche su un permesso che già durava più
   * giorni: quelle richieste esistono, e correggerne la data non è il momento
   * di accorciarle.
   */
  const intervallo = modulo.tipo === 'FERIE' || modulo.piuGiorni

  /**
   * Col motivo «Altro» le note sono l'unica cosa che spieghi di cosa si
   * tratta, e il server le pretende: su una richiesta di permesso nuova —
   * che nasce sempre «Altro» — smettono quindi di essere facoltative.
   */
  const noteObbligatorie = modulo.tipo === 'PERMESSO_RETRIBUITO' && campi.motivo === 'ALTRO'

  /**
   * Il pulsante si accende quando c'è abbastanza per inviare.
   *
   * Spento non è un divieto scoperto dopo: è la stessa condizione che il
   * server controllerebbe, detta prima di premere.
   */
  const completo =
    campi.dataInizio !== ''
    && (!intervallo || campi.dataFine !== '')
    && (!modulo.conOrario || (campi.oraInizio !== '' && campi.oraFine !== ''))
    && (!noteObbligatorie || campi.note.trim() !== '')

  const tutte = righe ?? []
  const inAttesa = tutte.filter((una) => una.stato === 'IN_ATTESA')
  const decise = tutte.filter((una) => una.stato !== 'IN_ATTESA')
  const carico = righe === null && errore === null

  if (modulo.aperto) {
    return (
      <main className="pagina-larga pagina-richiesta">
        <p className="occhiello">Ferie e permessi</p>
        <div className="testata testata-richiesta">
          <h1>
            {modulo.modifica === null ? 'Richiedi ferie o permesso' : 'Modifica la richiesta'}
          </h1>
        </div>

        <form className="scheda-richiesta" onSubmit={(evento) => void invia(evento)}>
          {/* Il tipo è il primo campo, non una schermata prima: è un
              interruttore a due posizioni, e la posizione scelta è piena di
              verde profondo — in Fluida l'elemento attivo è la tinta più
              scura della pagina.

              Correggendo non si cambia: una ferie che diventa un permesso
              non è la stessa richiesta corretta, è un'altra. */}
          <fieldset className="campo">
            <legend>Tipo</legend>
            <div className="scelta-tipo">
              <label className={permesso ? 'scelta-tipo-voce' : 'scelta-tipo-voce attiva'}>
                <input
                  type="radio"
                  name="tipo"
                  checked={!permesso}
                  disabled={modulo.modifica !== null || inInvio}
                  onChange={() => cambiaTipo('FERIE')}
                />
                <span>Ferie</span>
              </label>
              <label className={permesso ? 'scelta-tipo-voce attiva' : 'scelta-tipo-voce'}>
                <input
                  type="radio"
                  name="tipo"
                  checked={permesso}
                  disabled={modulo.modifica !== null || inInvio}
                  onChange={() => cambiaTipo('PERMESSO_RETRIBUITO')}
                />
                <span>Permesso</span>
              </label>
            </div>
          </fieldset>

          {/* Il motivo scelto a suo tempo, quando non è «Altro»: la tendina
              non c'è più, ma il dato sì, e correggendo le date lo si deve
              poter leggere invece di scoprirlo cambiato. */}
          {modulo.modifica !== null
            && modulo.tipo === 'PERMESSO_RETRIBUITO'
            && campi.motivo !== 'ALTRO' && (
              <p className="campo-aiuto">Motivo: {ETICHETTA_MOTIVO[campi.motivo]}</p>
            )}

          {/* Le due date stanno sulla stessa riga, non una sotto l'altra: un
              periodo si legge come una cosa sola, e separarle farebbe
              scegliere la fine dopo aver perso di vista l'inizio. */}
          {intervallo ? (
            <div className="campo-periodo">
              <div className="riga-richiesta">
                <div className="campo">
                  <label htmlFor="dataInizio">Dal</label>
                  <input
                    id="dataInizio"
                    type="date"
                    required
                    min={oggi()}
                    value={campi.dataInizio}
                    disabled={inInvio}
                    onChange={(evento) =>
                      setCampi({
                        ...campi,
                        dataInizio: evento.target.value,
                        // Spostando l'inizio oltre la fine, la fine si
                        // sposta con lui: `min` impedisce di scriverne una
                        // precedente, ma non di renderla tale cambiando
                        // l'altro campo dopo.
                        dataFine:
                          campi.dataFine !== '' && campi.dataFine < evento.target.value
                            ? evento.target.value
                            : campi.dataFine,
                      })
                    }
                  />
                </div>
                <div className="campo">
                  <label htmlFor="dataFine">Al</label>
                  <input
                    id="dataFine"
                    type="date"
                    required
                    min={campi.dataInizio === '' ? oggi() : campi.dataInizio}
                    value={campi.dataFine}
                    disabled={inInvio}
                    onChange={(evento) => setCampi({ ...campi, dataFine: evento.target.value })}
                  />
                </div>
              </div>
              {/* Compreso, e non «fino a»: è il giorno in cui si è ancora
                  via, ed è la domanda che si fa chi compila. I giorni
                  lavorativi veri li conta il server, che conosce le
                  festività: compaiono nella riga appena è salvata. */}
              <p className="campo-aiuto">
                Primo e ultimo giorno di assenza, compresi
              </p>
            </div>
          ) : (
            <div className="campo">
              <label htmlFor="dataInizio">Giorno</label>
              <input
                id="dataInizio"
                type="date"
                required
                min={oggi()}
                value={campi.dataInizio}
                disabled={inInvio}
                onChange={(evento) => setCampi({ ...campi, dataInizio: evento.target.value })}
              />
              <p className="campo-aiuto">Scegli il giorno richiesto</p>
            </div>
          )}

          {modulo.conOrario && (
            <div className="riga-richiesta">
              <div className="campo">
                <label htmlFor="oraInizio">Dalle</label>
                {/* A passi di 15 minuti, come dice il documento: step è in
                    secondi, quindi 900. */}
                <input
                  id="oraInizio"
                  type="time"
                  required
                  step={900}
                  value={campi.oraInizio}
                  disabled={inInvio}
                  onChange={(evento) => setCampi({ ...campi, oraInizio: evento.target.value })}
                />
              </div>
              <div className="campo">
                <label htmlFor="oraFine">Alle</label>
                <input
                  id="oraFine"
                  type="time"
                  required
                  step={900}
                  value={campi.oraFine}
                  disabled={inInvio}
                  onChange={(evento) => setCampi({ ...campi, oraFine: evento.target.value })}
                />
              </div>
            </div>
          )}

          <div className="campo">
            <label htmlFor="note">
              Note
              {!noteObbligatorie && <span className="etichetta-tenue"> (facoltative)</span>}
            </label>
            <textarea
              id="note"
              rows={3}
              maxLength={500}
              required={noteObbligatorie}
              placeholder="Es. visita medica, motivo del permesso…"
              value={campi.note}
              disabled={inInvio}
              onChange={(evento) => setCampi({ ...campi, note: evento.target.value })}
            />
            {noteObbligatorie && (
              <p className="campo-aiuto">Scrivi il motivo: è quello che si legge per decidere</p>
            )}
          </div>

          {/* Qui stava un conteggio dei giorni calcolato nel browser, che
              saltava le festività e finiva accanto al numero esatto del server
              nella tabella sotto: lo stesso dato con due definizioni nella
              stessa pagina. È stato tolto. La durata vera compare nella riga
              appena la richiesta è salvata, calcolata da chi conosce le
              festività e i giorni lavorativi configurati. */}

          {erroreModulo !== null && (
            <p className="errore" role="alert">
              {erroreModulo}
            </p>
          )}

          {/* «Annulla» a sinistra e l'invio a destra, ai due capi della
              scheda: l'azione che costa sta dalla parte da cui si esce, non
              accanto a quella che desiste. */}
          <div className="azioni-richiesta">
            <button type="button" className="secondario" onClick={chiudi} disabled={inInvio}>
              Annulla
            </button>
            <button type="submit" disabled={inInvio || !completo}>
              {modulo.modifica === null ? 'Invia richiesta' : 'Salva'}
            </button>
          </div>
        </form>
      </main>
    )
  }

  return (
    <main className="pagina-larga pagina-modulo">
      <div className="testata">
        <h1>Assenze</h1>
        <button type="button" className="azione" onClick={apri}>
          Nuova richiesta
        </button>
      </div>

      {errore !== null && (
        <p className="errore" role="alert">
          {errore}
        </p>
      )}

      <Pannello
        titolo="In attesa"
        sottotitolo="le puoi ancora correggere o ritirare"
        azioni={
          inAttesa.length > 0 ? (
            <Pillola tinta="attesa">{String(inAttesa.length)}</Pillola>
          ) : undefined
        }
        carico={carico}
        vuoto={!carico && inAttesa.length === 0 ? 'Nessuna richiesta in sospeso.' : null}
      >
        <ul className="righe-assenza">
          {inAttesa.map((riga) => (
            <li key={riga.id} className="riga-assenza">
              <span className="riga-assenza-corpo">
                <span className="riga-assenza-titolo">
                  {riga.tipoEtichetta}
                  {riga.motivoEtichetta !== null && (
                    <span className="nota"> · {riga.motivoEtichetta}</span>
                  )}
                </span>
                <span className="nota">
                  {periodo(riga)} · {riga.riepilogo}
                </span>
              </span>

              <span className="riga-assenza-azioni">
                {daRitirare === riga.id ? (
                  // Ritirare è definitivo e non si disfa: prima partiva al
                  // primo clic, senza nemmeno una domanda.
                  <span className="conferma">
                    Ritirare?
                    <button
                      type="button"
                      className="minuscolo"
                      disabled={inRitiro !== null}
                      onClick={() => void ritira(riga.id)}
                    >
                      Sì
                    </button>
                    <button
                      type="button"
                      className="minuscolo"
                      disabled={inRitiro !== null}
                      onClick={() => setDaRitirare(null)}
                    >
                      No
                    </button>
                  </span>
                ) : (
                  <>
                    <button
                      type="button"
                      className="minuscolo"
                      onClick={() => apriModifica(riga)}
                      disabled={inRitiro !== null}
                    >
                      Modifica
                    </button>
                    <button
                      type="button"
                      className="minuscolo"
                      onClick={() => setDaRitirare(riga.id)}
                      disabled={inRitiro !== null}
                    >
                      Ritira
                    </button>
                  </>
                )}
              </span>
            </li>
          ))}
        </ul>
      </Pannello>

      <Pannello
        titolo="Storico"
        sottotitolo="richieste già decise o ritirate"
        carico={carico}
        vuoto={
          !carico && decise.length === 0 ? 'Non hai ancora richieste chiuse.' : null
        }
      >
        <div className="scheda-tabella scheda-tabella-nuda">
          <table className="tabella">
            <thead>
              <tr>
                <th>Tipologia</th>
                <th>Quando</th>
                <th>Durata</th>
                <th>Stato</th>
                <th>Esito</th>
              </tr>
            </thead>
            <tbody>
              {decise.map((riga) => (
                <tr key={riga.id}>
                  <td>
                    {riga.tipoEtichetta}
                    {riga.motivoEtichetta !== null && (
                      <span className="nota"> · {riga.motivoEtichetta}</span>
                    )}
                  </td>
                  <td>{periodo(riga)}</td>
                  <td>{riga.riepilogo}</td>
                  <td>
                    <Pillola tinta={TINTA_STATO[riga.stato]}>
                      {ETICHETTA_STATO[riga.stato]}
                    </Pillola>
                  </td>
                  <td>{riga.notaEsito ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Pannello>
    </main>
  )
}

/** Il corpo da mandare, costruito in base alla tipologia. */
function corpo(modulo: Modulo, campi: Campi): NuovaAssenza {
  // La stessa regola che decide i campi: le ferie sono un periodo, il
  // permesso un giorno. Scritta qui una seconda volta e non passata come
  // argomento perché è il tipo a determinarla, e il tipo ce l'abbiamo.
  const intervallo = modulo.tipo === 'FERIE' || modulo.piuGiorni

  return {
    tipo: modulo.tipo,
    dataInizio: campi.dataInizio,
    // Un giorno solo: la fine coincide con l'inizio. Il server la risolve da
    // sé quando è null, ma dirlo qui rende la richiesta leggibile.
    dataFine: intervallo ? campi.dataFine : campi.dataInizio,
    oraInizio: modulo.conOrario ? campi.oraInizio : null,
    oraFine: modulo.conOrario ? campi.oraFine : null,
    // Il motivo lo ammette solo il permesso retribuito: sulle ferie e sul
    // permesso orario il server rifiuta anche un valore corretto.
    motivo: modulo.tipo === 'PERMESSO_RETRIBUITO' ? campi.motivo : null,
    note: campi.note.trim() === '' ? null : campi.note.trim(),
  }
}

/**
 * Rimpiazza la riga aggiornata, o mette in cima quella nuova.
 *
 * In cima e non in fondo: il server ordina le richieste in attesa per prime, e
 * una appena inviata è in attesa per definizione.
 */
function sostituisci(
  attuali: RigaAssenza[] | null,
  riga: RigaAssenza,
  nuova: boolean,
): RigaAssenza[] | null {
  if (attuali === null) {
    return null
  }
  if (nuova) {
    return [riga, ...attuali]
  }
  return attuali.map((una) => (una.id === riga.id ? riga : una))
}
