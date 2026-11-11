import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import {
  chiediAssenza,
  ETICHETTA_MOTIVO,
  ETICHETTA_STATO,
  ETICHETTA_TIPO,
  mieAssenze,
  modificaAssenza,
  ritiraAssenza,
  SPIEGAZIONE_TIPO,
  TINTA_STATO,
} from './api'
import type { MotivoPermesso, NuovaAssenza, RigaAssenza, TipoAssenza } from './api'
import { testo } from '../api/http'
import Pannello from '../componenti/Pannello'
import { Pillola } from '../componenti/Stato'

const TIPI: TipoAssenza[] = ['FERIE', 'PERMESSO_ORARIO', 'PERMESSO_RETRIBUITO']

const MOTIVI: MotivoPermesso[] = [
  'LUTTO',
  'MATRIMONIO',
  'LEGGE_104',
  'DONAZIONE_SANGUE',
  'VISITA_MEDICA',
  'ALTRO',
]

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
}

const MODULO_CHIUSO: Modulo = { aperto: false, tipo: 'FERIE', modifica: null }

/** I campi del form, tutti insieme: quali contano lo dice la tipologia. */
type Campi = {
  dataInizio: string
  dataFine: string
  oraInizio: string
  oraFine: string
  motivo: MotivoPermesso
  note: string
  /** Solo per il permesso retribuito: giornata intera oppure fascia oraria. */
  aGiornate: boolean
}

const CAMPI_VUOTI: Campi = {
  dataInizio: '',
  dataFine: '',
  oraInizio: '',
  oraFine: '',
  motivo: 'VISITA_MEDICA',
  note: '',
  aGiornate: true,
}

/** I campi già riempiti con una richiesta esistente, per la modifica. */
function campiDa(riga: RigaAssenza): Campi {
  return {
    dataInizio: riga.dataInizio,
    dataFine: riga.dataFine,
    oraInizio: riga.oraInizio === null ? '' : ora(riga.oraInizio),
    oraFine: riga.oraFine === null ? '' : ora(riga.oraFine),
    motivo: riga.motivo ?? 'VISITA_MEDICA',
    note: riga.note ?? '',
    aGiornate: riga.oraInizio === null,
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
    setModulo({ aperto: true, tipo: 'FERIE', modifica: null })
  }

  function apriModifica(riga: RigaAssenza) {
    setCampi(campiDa(riga))
    setErroreModulo(null)
    setDaRitirare(null)
    setModulo({ aperto: true, tipo: riga.tipo, modifica: riga.id })
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
    setCampi(CAMPI_VUOTI)
    setErroreModulo(null)
    setModulo((prima) => ({ ...prima, tipo }))
  }

  async function invia(evento: FormEvent) {
    evento.preventDefault()

    setInInvio(true)
    setErroreModulo(null)
    try {
      const dati = corpo(modulo.tipo, campi)
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

  // Il permesso retribuito è l'unico che sceglie: le ferie sono sempre a
  // giornate, il permesso orario sempre a fascia.
  const aFascia =
    modulo.tipo === 'PERMESSO_ORARIO'
    || (modulo.tipo === 'PERMESSO_RETRIBUITO' && !campi.aGiornate)

  const tutte = righe ?? []
  const inAttesa = tutte.filter((una) => una.stato === 'IN_ATTESA')
  const decise = tutte.filter((una) => una.stato !== 'IN_ATTESA')
  const carico = righe === null && errore === null

  return (
    <main className="pagina-larga pagina-modulo">
      <div className="testata">
        <h1>Assenze</h1>
        {/* Il pulsante **resta** anche col modulo aperto: prima spariva, e
            chi lo cercava per tornare indietro non lo trovava più. */}
        {!modulo.aperto && (
          <button type="button" className="azione" onClick={apri}>
            Nuova richiesta
          </button>
        )}
      </div>

      {errore !== null && (
        <p className="errore" role="alert">
          {errore}
        </p>
      )}

      {modulo.aperto && (
        <form className="modulo" onSubmit={(evento) => void invia(evento)}>
          <h2>
            {modulo.modifica === null
              ? 'Nuova richiesta'
              : `Modifica: ${ETICHETTA_TIPO[modulo.tipo]}`}
          </h2>

          {/* La tipologia è il primo campo del modulo, non una schermata
              prima: decide quali campi compaiono sotto, e tenerla qui fa
              vedere subito l'effetto di un cambio d'idea.

              Correggendo non si cambia: una ferie che diventa un permesso
              orario non è la stessa richiesta corretta, è un'altra. */}
          {modulo.modifica === null && (
            <fieldset className="campo">
              <legend>Che richiesta vuoi fare?</legend>
              {TIPI.map((una) => (
                <label key={una} className="scelta-riga">
                  <input
                    type="radio"
                    name="tipo"
                    checked={modulo.tipo === una}
                    onChange={() => cambiaTipo(una)}
                  />
                  <span>
                    {ETICHETTA_TIPO[una]}
                    <em>{SPIEGAZIONE_TIPO[una]}</em>
                  </span>
                </label>
              ))}
            </fieldset>
          )}

          {modulo.tipo === 'PERMESSO_RETRIBUITO' && (
            <fieldset className="campo">
              <legend>Durata</legend>
              <div className="scelte-riga">
                <label className="campo-inline">
                  <input
                    type="radio"
                    name="durata"
                    checked={campi.aGiornate}
                    onChange={() => setCampi({ ...campi, aGiornate: true })}
                  />
                  Giornata intera
                </label>
                <label className="campo-inline">
                  <input
                    type="radio"
                    name="durata"
                    checked={!campi.aGiornate}
                    onChange={() => setCampi({ ...campi, aGiornate: false })}
                  />
                  Fascia oraria
                </label>
              </div>
            </fieldset>
          )}

          <div className="campo">
            <label htmlFor="dataInizio">{aFascia ? 'Giorno' : 'Dal'}</label>
            <input
              id="dataInizio"
              type="date"
              required
              min={oggi()}
              value={campi.dataInizio}
              onChange={(evento) => setCampi({ ...campi, dataInizio: evento.target.value })}
            />
          </div>

          {/* La data di fine non si chiede per una fascia oraria: un permesso
              a ore riguarda un giorno solo, e il server lo rifiuta altrimenti. */}
          {!aFascia && (
            <div className="campo">
              <label htmlFor="dataFine">Al</label>
              <input
                id="dataFine"
                type="date"
                required
                min={campi.dataInizio === '' ? oggi() : campi.dataInizio}
                value={campi.dataFine}
                onChange={(evento) => setCampi({ ...campi, dataFine: evento.target.value })}
              />
            </div>
          )}

          {aFascia && (
            <div className="modulo-riga">
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
                  onChange={(evento) => setCampi({ ...campi, oraFine: evento.target.value })}
                />
              </div>
            </div>
          )}

          {modulo.tipo === 'PERMESSO_RETRIBUITO' && (
            <div className="campo">
              <label htmlFor="motivo">Motivo</label>
              <select
                id="motivo"
                value={campi.motivo}
                onChange={(evento) =>
                  setCampi({ ...campi, motivo: evento.target.value as MotivoPermesso })
                }
              >
                {MOTIVI.map((uno) => (
                  <option key={uno} value={uno}>
                    {ETICHETTA_MOTIVO[uno]}
                  </option>
                ))}
              </select>
            </div>
          )}

          <div className="campo">
            <label htmlFor="note">
              Note
              {modulo.tipo === 'PERMESSO_RETRIBUITO' && campi.motivo === 'ALTRO'
                ? ''
                : ' (facoltative)'}
            </label>
            <textarea
              id="note"
              rows={3}
              maxLength={500}
              /* Col motivo «Altro» le note sono l'unica cosa che spieghi di
                 cosa si tratta, e il server le pretende. */
              required={modulo.tipo === 'PERMESSO_RETRIBUITO' && campi.motivo === 'ALTRO'}
              value={campi.note}
              onChange={(evento) => setCampi({ ...campi, note: evento.target.value })}
            />
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

          <div className="azioni">
            <button type="submit" disabled={inInvio}>
              {modulo.modifica === null ? 'Invia richiesta' : 'Salva'}
            </button>
            <button type="button" className="secondario" onClick={chiudi} disabled={inInvio}>
              Annulla
            </button>
          </div>
        </form>
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
function corpo(tipo: TipoAssenza, campi: Campi): NuovaAssenza {
  const aFascia =
    tipo === 'PERMESSO_ORARIO' || (tipo === 'PERMESSO_RETRIBUITO' && !campi.aGiornate)

  return {
    tipo,
    dataInizio: campi.dataInizio,
    // Per una fascia oraria la fine coincide con l'inizio: il server la
    // risolve da sé quando è null, ma dirlo qui rende la richiesta leggibile.
    dataFine: aFascia ? campi.dataInizio : campi.dataFine,
    oraInizio: aFascia ? campi.oraInizio : null,
    oraFine: aFascia ? campi.oraFine : null,
    motivo: tipo === 'PERMESSO_RETRIBUITO' ? campi.motivo : null,
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
