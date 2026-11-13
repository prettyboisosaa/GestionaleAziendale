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
import type {
  MotivoPermesso,
  NuovaAssenza,
  RigaAssenza,
  StatoAssenza,
  TipoAssenza,
} from './api'
import { testo } from '../api/http'
import Icona from '../componenti/Icona'
import { Pillola } from '../componenti/Stato'
import SelettoreData from '../componenti/SelettoreData'

const MESI = [
  'gen',
  'feb',
  'mar',
  'apr',
  'mag',
  'giu',
  'lug',
  'ago',
  'set',
  'ott',
  'nov',
  'dic',
]

/**
 * Da «2027-12-22» a «22 dic»: la data come la scrive il foglio del design.
 *
 * **L'anno compare solo quando non è quello corrente.** Uno storico è fatto di
 * richieste di anni diversi, e «3 nov» senza anno in mezzo a due righe del
 * 2024 sarebbe una data che non si sa leggere. Nell'anno in corso invece
 * l'anno è rumore: è lo stesso su ogni riga.
 *
 * La stringa si taglia invece di passare da `Date`: un ISO di sola data viene
 * letto come mezzanotte UTC, e a ovest di Greenwich `toLocaleDateString`
 * restituirebbe il giorno prima.
 */
function dataBreve(iso: string): string {
  const anno = iso.slice(0, 4)
  const giorno = Number(iso.slice(8, 10))
  const mese = MESI[Number(iso.slice(5, 7)) - 1]
  if (anno === String(new Date().getFullYear())) {
    return `${giorno} ${mese}`
  }
  return `${giorno} ${mese} ${anno}`
}

/** Da «09:30:00» a «09:30»: i secondi non servono a nessuno. */
function ora(iso: string): string {
  return iso.slice(0, 5)
}

/** Il periodo in una riga: date, oppure data più fascia oraria. */
function periodo(riga: RigaAssenza): string {
  if (riga.oraInizio !== null && riga.oraFine !== null) {
    return `${dataBreve(riga.dataInizio)}, ${ora(riga.oraInizio)}–${ora(riga.oraFine)}`
  }
  if (riga.dataInizio === riga.dataFine) {
    return dataBreve(riga.dataInizio)
  }
  return `${dataBreve(riga.dataInizio)} – ${dataBreve(riga.dataFine)}`
}

/**
 * La riga sotto il nome della richiesta, nello storico: il motivo, le note di
 * chi l'ha chiesta e la nota di chi ha deciso, separate da un punto medio.
 *
 * Tutte e tre in una riga sola e non in tre colonne: sono testo libero, e una
 * colonna «nota» larga quanto serve al caso peggiore lascerebbe vuoto metà
 * dell'elenco. Quella dell'esito porta davanti lo stato — «rifiutata: …» —
 * perché altrimenti non si distinguerebbe da quello che ha scritto chi
 * chiedeva.
 */
function nota(riga: RigaAssenza): string | null {
  const pezzi: string[] = []
  if (riga.motivoEtichetta !== null) {
    pezzi.push(riga.motivoEtichetta)
  }
  if (riga.note !== null && riga.note.trim() !== '') {
    pezzi.push(riga.note.trim())
  }
  if (riga.notaEsito !== null && riga.notaEsito.trim() !== '') {
    pezzi.push(`${riga.statoEtichetta.toLowerCase()}: ${riga.notaEsito.trim()}`)
  }
  return pezzi.length === 0 ? null : pezzi.join(' · ')
}

/**
 * I segmenti del filtro dello storico.
 *
 * «Ritirate» non sta nel foglio del design, e compare solo quando ce n'è
 * almeno una: una richiesta ritirata è una che si è disfatta da sé, e nella
 * maggior parte degli storici non ne esiste nemmeno una. Il segmento in più
 * sarebbe un filtro che non filtra niente — ma nasconderlo a chi invece ne ha
 * vorrebbe dire righe raggiungibili solo da «Tutte».
 */
type Filtro = 'TUTTE' | 'APPROVATA' | 'RIFIUTATA' | 'RITIRATA'

const SEGMENTI: { valore: Filtro; nome: string }[] = [
  { valore: 'TUTTE', nome: 'Tutte' },
  { valore: 'APPROVATA', nome: 'Approvate' },
  { valore: 'RIFIUTATA', nome: 'Rifiutate' },
  { valore: 'RITIRATA', nome: 'Ritirate' },
]

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
  /** Quale segmento dello storico è premuto. */
  const [filtro, setFiltro] = useState<Filtro>('TUTTE')

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

  /** Quante righe c'è dietro ciascun segmento, da scrivere accanto al nome. */
  function quante(valore: Filtro): number {
    if (valore === 'TUTTE') {
      return decise.length
    }
    return decise.filter((una) => una.stato === (valore as StatoAssenza)).length
  }

  const segmenti = SEGMENTI.filter(
    (uno) => uno.valore !== 'RITIRATA' || quante('RITIRATA') > 0,
  )
  const storico =
    filtro === 'TUTTE' ? decise : decise.filter((una) => una.stato === filtro)

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
                  {/* Il mese aperto davanti, e non un campo a cifre: chiedere
                      le ferie è il gesto in cui si guarda **dove cadono i fine
                      settimana** prima di decidere. Il campo nativo lo fa
                      digitare, e per vedere il calendario bisogna aprirlo. */}
                  <SelettoreData
                    id="dataInizio"
                    obbligatorio
                    min={oggi()}
                    valore={campi.dataInizio}
                    disabilitato={inInvio}
                    onCambia={(scelta) =>
                      setCampi({
                        ...campi,
                        dataInizio: scelta,
                        // Spostando l'inizio oltre la fine, la fine si
                        // sposta con lui: `min` impedisce di scriverne una
                        // precedente, ma non di renderla tale cambiando
                        // l'altro campo dopo.
                        dataFine:
                          campi.dataFine !== '' && campi.dataFine < scelta
                            ? scelta
                            : campi.dataFine,
                      })
                    }
                  />
                </div>
                <div className="campo">
                  <label htmlFor="dataFine">Al</label>
                  <SelettoreData
                    id="dataFine"
                    obbligatorio
                    min={campi.dataInizio === '' ? oggi() : campi.dataInizio}
                    valore={campi.dataFine}
                    disabilitato={inInvio}
                    onCambia={(scelta) => setCampi({ ...campi, dataFine: scelta })}
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
              <SelettoreData
                id="dataInizio"
                obbligatorio
                min={oggi()}
                valore={campi.dataInizio}
                disabilitato={inInvio}
                onCambia={(scelta) => setCampi({ ...campi, dataInizio: scelta })}
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
    <main className="pagina-larga pagina-mie-assenze">
      {/* Occhiello e titolo a sinistra, l'azione piena a destra: è la testata
          del foglio «Le mie ferie». Il titolo dice di chi sono le richieste —
          «Assenze» diceva di che cosa parla la sezione, che è già scritto
          nell'occhiello e nella barra laterale. */}
      <p className="occhiello">Ferie e permessi</p>
      <div className="testata testata-mie-assenze">
        <h1>Le mie richieste</h1>
        <button type="button" className="azione-piena" onClick={apri}>
          Richiedi ferie o permesso
        </button>
      </div>

      {errore !== null && (
        <p className="errore" role="alert">
          {errore}
        </p>
      )}

      {/* La scheda d'attesa esiste solo quando c'è qualcosa che aspetta. Un
          riquadro che dice «niente in sospeso» è una riga di interfaccia per
          dire che non c'è interfaccia da mostrare: senza richieste aperte la
          pagina è lo storico, e basta. */}
      {(carico || inAttesa.length > 0) && (
        <section className="scheda-assenze">
          <div className="scheda-assenze-testata">
            <h2>In attesa di approvazione</h2>
            {inAttesa.length > 0 && (
              <span className="scheda-assenze-conto">
                {inAttesa.length} in attesa
              </span>
            )}
          </div>

          {carico ? (
            <p className="sommario" role="status">
              Carico…
            </p>
          ) : (
            <ul className="righe-attesa">
              {inAttesa.map((riga) => (
                <li key={riga.id} className="riga-attesa">
                  <span className="riga-attesa-icona">
                    <Icona nome="attesa" />
                  </span>

                  <span className="riga-attesa-corpo">
                    <span className="riga-attesa-titolo">
                      {riga.tipoEtichetta} · {periodo(riga)}
                    </span>
                    <span className="riga-attesa-nota">
                      {riga.riepilogo !== null && `${riga.riepilogo} · `}
                      inviata il {dataBreve(riga.creataIl)}
                    </span>
                  </span>

                  <span className="riga-attesa-azioni">
                    {daRitirare === riga.id ? (
                      // Ritirare è definitivo e non si disfa: prima partiva al
                      // primo clic, senza nemmeno una domanda.
                      <span className="riga-attesa-conferma">
                        Ritirare?
                        <button
                          type="button"
                          disabled={inRitiro !== null}
                          onClick={() => void ritira(riga.id)}
                        >
                          Sì
                        </button>
                        <button
                          type="button"
                          disabled={inRitiro !== null}
                          onClick={() => setDaRitirare(null)}
                        >
                          No
                        </button>
                      </span>
                    ) : (
                      <>
                        {/* «Modifica» non sta nel foglio del design, che
                            disegna il solo «Ritira». Resta perché correggere
                            una data senza disfare la richiesta è l'unica cosa
                            che si può ancora fare da qui, e toglierla
                            vorrebbe dire ritirare e riscrivere da capo. */}
                        <button
                          type="button"
                          onClick={() => apriModifica(riga)}
                          disabled={inRitiro !== null}
                        >
                          Modifica
                        </button>
                        <button
                          type="button"
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
          )}
        </section>
      )}

      <section className="scheda-assenze">
        <div className="scheda-assenze-testata">
          <h2>Storico</h2>
          {decise.length > 0 && (
            <div className="segmenti" role="group" aria-label="Filtra lo storico">
              {segmenti.map((uno) => (
                <button
                  key={uno.valore}
                  type="button"
                  className={
                    filtro === uno.valore ? 'segmento segmento-scelto' : 'segmento'
                  }
                  aria-pressed={filtro === uno.valore}
                  onClick={() => setFiltro(uno.valore)}
                >
                  {uno.nome} · {quante(uno.valore)}
                </button>
              ))}
            </div>
          )}
        </div>

        {carico ? (
          <p className="sommario" role="status">
            Carico…
          </p>
        ) : storico.length === 0 ? (
          <p className="sommario">
            {decise.length === 0
              ? 'Non hai ancora richieste chiuse.'
              : 'Nessuna richiesta con questo esito.'}
          </p>
        ) : (
          <table className="tabella-storico">
            <thead>
              <tr>
                <th>Richiesta</th>
                <th>Periodo</th>
                <th className="colonna-destra">Giorni</th>
                <th className="colonna-destra">Esito</th>
              </tr>
            </thead>
            <tbody>
              {storico.map((riga) => (
                <tr key={riga.id}>
                  <td>
                    <span className="cella-richiesta">
                      <span className="cella-richiesta-titolo">
                        {riga.tipoEtichetta}
                      </span>
                      {nota(riga) !== null && (
                        <span className="cella-richiesta-nota">{nota(riga)}</span>
                      )}
                    </span>
                  </td>
                  <td>{periodo(riga)}</td>
                  <td className="colonna-destra colonna-giorni">
                    {riga.riepilogo ?? '—'}
                  </td>
                  <td className="colonna-destra colonna-esito">
                    <Pillola tinta={TINTA_STATO[riga.stato]}>
                      {ETICHETTA_STATO[riga.stato]}
                    </Pillola>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
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
