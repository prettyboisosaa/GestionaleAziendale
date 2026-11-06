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

/** Lo stato del pannello: chiuso, aperto su una tipologia, o in modifica. */
type Pannello =
  | { fase: 'chiuso' }
  | { fase: 'scelta' }
  | { fase: 'compila'; tipo: TipoAssenza; modifica: number | null }

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

export default function PaginaMieAssenze() {
  const [righe, setRighe] = useState<RigaAssenza[] | null>(null)
  const [errore, setErrore] = useState<string | null>(null)
  const [pannello, setPannello] = useState<Pannello>({ fase: 'chiuso' })
  const [campi, setCampi] = useState<Campi>(CAMPI_VUOTI)
  const [inCorso, setInCorso] = useState(false)

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

  function apri(tipo: TipoAssenza) {
    setCampi(CAMPI_VUOTI)
    setErrore(null)
    setPannello({ fase: 'compila', tipo, modifica: null })
  }

  function apriModifica(riga: RigaAssenza) {
    setCampi(campiDa(riga))
    setErrore(null)
    setPannello({ fase: 'compila', tipo: riga.tipo, modifica: riga.id })
  }

  function chiudi() {
    setPannello({ fase: 'chiuso' })
    setCampi(CAMPI_VUOTI)
  }

  async function invia(evento: FormEvent) {
    evento.preventDefault()
    if (pannello.fase !== 'compila') {
      return
    }

    setInCorso(true)
    setErrore(null)
    try {
      const dati = corpo(pannello.tipo, campi)
      const salvata =
        pannello.modifica === null
          ? await chiediAssenza(dati)
          : await modificaAssenza(pannello.modifica, dati)

      setRighe((attuali) => sostituisci(attuali, salvata, pannello.modifica === null))
      chiudi()
    } catch (motivo) {
      // I messaggi del server spiegano già cosa manca o cosa si sovrappone:
      // CAMPI_MANCANTI, DATA_PASSATA, RICHIESTA_SOVRAPPOSTA. Il codice in
      // testa lo toglie `testo`, e resta la frase in italiano.
      setErrore(testo(motivo))
    } finally {
      setInCorso(false)
    }
  }

  async function ritira(id: number) {
    setInCorso(true)
    setErrore(null)
    try {
      const aggiornata = await ritiraAssenza(id)
      setRighe((attuali) => sostituisci(attuali, aggiornata, false))
    } catch (motivo) {
      setErrore(testo(motivo))
    } finally {
      setInCorso(false)
    }
  }

  const tipo = pannello.fase === 'compila' ? pannello.tipo : null
  // Il permesso retribuito è l'unico che sceglie: le ferie sono sempre a
  // giornate, il permesso orario sempre a fascia.
  const aFascia =
    tipo === 'PERMESSO_ORARIO' || (tipo === 'PERMESSO_RETRIBUITO' && !campi.aGiornate)

  return (
    <main className="pagina-larga">
      <div className="testata">
        <h1>Assenze</h1>
        {pannello.fase === 'chiuso' && (
          <button type="button" className="azione" onClick={() => setPannello({ fase: 'scelta' })}>
            Nuova richiesta
          </button>
        )}
      </div>

      {errore !== null && (
        <p className="errore" role="alert">
          {errore}
        </p>
      )}

      {/* Prima la tipologia, poi il form di quella tipologia: un form unico
          con i campi di tutte e tre mostrerebbe sempre due terzi di campi che
          non servono. */}
      {pannello.fase === 'scelta' && (
        <div className="modulo">
          <h2>Che richiesta vuoi fare?</h2>
          {TIPI.map((una) => (
            <button key={una} type="button" className="scelta" onClick={() => apri(una)}>
              {ETICHETTA_TIPO[una]}
              <em>{SPIEGAZIONE_TIPO[una]}</em>
            </button>
          ))}
          <div className="azioni">
            <button type="button" className="secondario" onClick={chiudi}>
              Annulla
            </button>
          </div>
        </div>
      )}

      {pannello.fase === 'compila' && tipo !== null && (
        <form className="modulo" onSubmit={(evento) => void invia(evento)}>
          <h2>
            {pannello.modifica === null ? ETICHETTA_TIPO[tipo] : `Modifica: ${ETICHETTA_TIPO[tipo]}`}
          </h2>

          {tipo === 'PERMESSO_RETRIBUITO' && (
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

          {tipo === 'PERMESSO_RETRIBUITO' && (
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
              Note{tipo === 'PERMESSO_RETRIBUITO' && campi.motivo === 'ALTRO' ? '' : ' (facoltative)'}
            </label>
            <textarea
              id="note"
              rows={3}
              maxLength={500}
              /* Col motivo «Altro» le note sono l'unica cosa che spieghi di
                 cosa si tratta, e il server le pretende. */
              required={tipo === 'PERMESSO_RETRIBUITO' && campi.motivo === 'ALTRO'}
              value={campi.note}
              onChange={(evento) => setCampi({ ...campi, note: evento.target.value })}
            />
          </div>

          <p className="sommario">
            {riepilogoLocale(aFascia, campi)}
          </p>

          <div className="azioni">
            <button type="submit" disabled={inCorso}>
              {pannello.modifica === null ? 'Invia richiesta' : 'Salva'}
            </button>
            <button type="button" className="secondario" onClick={chiudi} disabled={inCorso}>
              Annulla
            </button>
          </div>
        </form>
      )}

      {errore === null && righe === null && (
        <p className="sommario" role="status">
          Carico le richieste…
        </p>
      )}

      {righe !== null && righe.length === 0 && (
        <p className="sommario">Non hai ancora chiesto nessuna assenza.</p>
      )}

      {righe !== null && righe.length > 0 && (
        <div className="scheda-tabella">
          <table className="tabella">
            <thead>
              <tr>
                <th>Tipologia</th>
                <th>Quando</th>
                <th>Durata</th>
                <th>Stato</th>
                <th>Esito</th>
                <th>Azioni</th>
              </tr>
            </thead>
            <tbody>
              {righe.map((riga) => (
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
                  <td className="azioni">
                    {/* Solo finché è in attesa: una richiesta decisa non si
                        tocca più, e il server risponde 409. */}
                    {riga.stato === 'IN_ATTESA' && (
                      <>
                        <button
                          type="button"
                          onClick={() => apriModifica(riga)}
                          disabled={inCorso}
                        >
                          Modifica
                        </button>
                        <button
                          type="button"
                          onClick={() => void ritira(riga.id)}
                          disabled={inCorso}
                        >
                          Ritira
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
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
 * Il riepilogo mostrato <em>mentre</em> si compila.
 *
 * È un conto approssimato, e di proposito: i giorni lavorativi veri li sa
 * solo il server, che conosce le festività, e qui conta i giorni di calendario
 * togliendo i fine settimana. Il numero esatto compare nella tabella appena la
 * richiesta è salvata, nella colonna «Durata», che arriva dal server.
 */
function riepilogoLocale(aFascia: boolean, campi: Campi): string {
  if (aFascia) {
    if (campi.oraInizio === '' || campi.oraFine === '') {
      return 'Indica gli orari per vedere la durata.'
    }
    const minuti = minutiTra(campi.oraInizio, campi.oraFine)
    if (minuti <= 0) {
      return 'L’ora di fine deve venire dopo quella di inizio.'
    }
    const ore = Math.floor(minuti / 60)
    const resto = minuti % 60
    if (ore === 0) {
      return `${resto} min`
    }
    return resto === 0 ? `${ore} h` : `${ore} h ${resto} min`
  }

  if (campi.dataInizio === '' || campi.dataFine === '') {
    return 'Indica le date per vedere quanti giorni sono.'
  }
  const giorni = giorniFeriali(campi.dataInizio, campi.dataFine)
  if (giorni < 0) {
    return 'L’ultimo giorno non può precedere il primo.'
  }
  const coda = giorni === 1 ? 'giorno lavorativo' : 'giorni lavorativi'
  return `${giorni} ${coda} (festività escluse dal server)`
}

function minutiTra(inizio: string, fine: string): number {
  const [oreInizio, minutiInizio] = inizio.split(':').map(Number)
  const [oreFine, minutiFine] = fine.split(':').map(Number)
  return oreFine * 60 + minutiFine - (oreInizio * 60 + minutiInizio)
}

/** I giorni da lunedì a venerdì fra le due date, estremi compresi. */
function giorniFeriali(da: string, a: string): number {
  const inizio = new Date(da)
  const fine = new Date(a)
  if (fine < inizio) {
    return -1
  }

  let quanti = 0
  for (const giorno = new Date(inizio); giorno <= fine; giorno.setDate(giorno.getDate() + 1)) {
    const settimana = giorno.getDay()
    if (settimana !== 0 && settimana !== 6) {
      quanti++
    }
  }
  return quanti
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
