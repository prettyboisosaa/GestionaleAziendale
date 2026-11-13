import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { creaLavoro, responsabiliPossibili } from './api'
import type { CandidatoResponsabile } from './api'
import { testo } from '../api/http'
import type { UtenteCorrente } from '../autenticazione/api'
import { ha } from '../autenticazione/permessi'

/**
 * La creazione di un lavoro, in tre passi numerati e con il riepilogo a
 * fianco.
 *
 * **Il riepilogo è la parte che conta.** Un modulo di sette campi si compila
 * guardando i campi; quello che non si vede, mentre lo si compila, è che cosa
 * si sta per creare — e soprattutto quante ore si stanno impegnando. La
 * colonna di destra lo dice mentre si scrive, e il pulsante che crea sta lì
 * dentro: si preme dopo aver letto il riepilogo, non prima.
 *
 * **Lo stato non è un campo.** Prima c'erano due pulsanti radio, bozza o
 * attivo, con la regola scritta accanto: un lavoro attivo vuole un
 * responsabile. È la stessa cosa che dice il vincolo del database, e invece di
 * farla scegliere la si ricava — con un responsabile il lavoro nasce attivo,
 * senza nasce bozza. Il riepilogo scrive quale dei due sarà, così la regola si
 * legge nel suo effetto e non come una nota.
 */
export default function PaginaNuovoLavoro({ utente }: { utente: UtenteCorrente }) {
  const naviga = useNavigate()

  // Il menu dei responsabili si può caricare solo con il permesso di
  // assegnarli: senza, si crea una bozza e il responsabile si mette dopo.
  const puoAssegnare = ha(utente, 'LAVORI_ASSEGNA_RESPONSABILE')

  // Il monte ore lo fissa solo l'amministratore: il server risponde 403 a chi
  // lo invia senza il permesso, quindi il campo si mostra soltanto a chi puo'
  // compilarlo invece di far scoprire il divieto dopo l'invio.
  const puoFissareMonteOre = ha(utente, 'ORE_MONTE_GESTISCI')

  const [candidati, setCandidati] = useState<CandidatoResponsabile[]>([])
  const [nome, setNome] = useState('')
  const [descrizione, setDescrizione] = useState('')
  const [dataInizio, setDataInizio] = useState('')
  const [dataFinePrevista, setDataFinePrevista] = useState('')
  const [responsabileId, setResponsabileId] = useState('')
  const [monteOre, setMonteOre] = useState('')
  const [errore, setErrore] = useState<string | null>(null)
  const [inCorso, setInCorso] = useState(false)

  useEffect(() => {
    if (!puoAssegnare) {
      return
    }

    let annullato = false

    responsabiliPossibili()
      .then((elenco) => {
        if (!annullato) {
          setCandidati(elenco)
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
  }, [puoAssegnare])

  /**
   * Con un responsabile il lavoro nasce attivo, senza nasce bozza.
   *
   * È la stessa condizione del vincolo `ck_lavoro_responsabile`: scritta così
   * non si può violare, e il modulo non ha più bisogno di rifiutare una
   * combinazione che l'utente poteva comporre.
   */
  const stato = responsabileId === '' ? 'BOZZA' : 'ATTIVO'

  const ore = monteOre.trim() === '' ? null : Number(monteOre)
  const completo = nome.trim() !== '' && dataInizio !== '' && dataFinePrevista !== ''
  const rovescio =
    dataInizio !== '' && dataFinePrevista !== '' && dataFinePrevista < dataInizio

  function svuota() {
    setNome('')
    setDescrizione('')
    setDataInizio('')
    setDataFinePrevista('')
    setResponsabileId('')
    setMonteOre('')
    setErrore(null)
  }

  async function invia(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()

    setErrore(null)
    setInCorso(true)
    try {
      await creaLavoro({
        nome,
        descrizione: descrizione.trim() === '' ? null : descrizione,
        dataInizio,
        dataFinePrevista,
        stato,
        responsabileId: responsabileId === '' ? null : Number(responsabileId),
        // Vuoto vuol dire «non ancora deciso», non zero: un budget a zero
        // direbbe che le ore sono esaurite.
        monteOre: !puoFissareMonteOre || ore === null ? null : ore,
      })
      naviga('/lavori')
    } catch (motivo) {
      setErrore(testo(motivo))
      setInCorso(false)
    }
  }

  return (
    <main className="pagina-larga">
      <Link to="/lavori" className="ritorno">
        <span aria-hidden="true">‹</span> Tutti i lavori
      </Link>

      <div className="testata testata-nuovo">
        <h1>Nuovo lavoro</h1>
      </div>
      <p className="nota sottotitolo-nuovo">
        Stima il monte ore, distribuiscilo tra le task: le ore vengono scalate dal pool
        aziendale.
      </p>

      <form className="griglia-nuovo" onSubmit={invia}>
        <div className="colonna-nuovo">
          <section className="scheda-passo">
            <h2 className="passo-titolo">1 · Dati del lavoro</h2>

            <div className="campo">
              <label htmlFor="nome">Nome</label>
              <input
                id="nome"
                className="campo-grande"
                required
                maxLength={200}
                placeholder="Impianto fotovoltaico capannone Ferri"
                value={nome}
                onChange={(evento) => setNome(evento.target.value)}
                disabled={inCorso}
              />
            </div>

            <div className="campo">
              <label htmlFor="descrizione">Descrizione</label>
              <textarea
                id="descrizione"
                className="campo-grande"
                rows={3}
                placeholder="Che cosa comprende il lavoro"
                value={descrizione}
                onChange={(evento) => setDescrizione(evento.target.value)}
                disabled={inCorso}
              />
            </div>
          </section>

          <section className="scheda-passo">
            <h2 className="passo-titolo">2 · Tempi e responsabile</h2>

            <div className="riga-campi">
              <div className="campo">
                <label htmlFor="dataInizio">Inizio</label>
                <input
                  id="dataInizio"
                  className="campo-grande"
                  type="date"
                  required
                  value={dataInizio}
                  onChange={(evento) => setDataInizio(evento.target.value)}
                  disabled={inCorso}
                />
              </div>

              <div className="campo">
                <label htmlFor="dataFinePrevista">Fine prevista</label>
                <input
                  id="dataFinePrevista"
                  className="campo-grande"
                  type="date"
                  required
                  min={dataInizio === '' ? undefined : dataInizio}
                  value={dataFinePrevista}
                  onChange={(evento) => setDataFinePrevista(evento.target.value)}
                  disabled={inCorso}
                />
              </div>

              {puoAssegnare && (
                <div className="campo">
                  <label htmlFor="responsabile">Responsabile</label>
                  <select
                    id="responsabile"
                    className="campo-grande"
                    value={responsabileId}
                    onChange={(evento) => setResponsabileId(evento.target.value)}
                    disabled={inCorso}
                  >
                    <option value="">Nessuno</option>
                    {candidati.map((candidato) => (
                      <option key={candidato.id} value={candidato.id}>
                        {candidato.cognome} {candidato.nome}
                      </option>
                    ))}
                  </select>
                </div>
              )}
            </div>

            {/* La durata scritta sotto le due date: due date non dicono quanto
                dura un lavoro — bisogna contarle — e la stima del monte ore si
                fa proprio su quel numero. */}
            <p className="nota riga-durata">{durata(dataInizio, dataFinePrevista)}</p>
          </section>

          {puoFissareMonteOre && (
            <section className="scheda-passo scheda-monte">
              <div>
                <h2 className="passo-titolo">3 · Monte ore stimato</h2>
                <p className="nota">La tua stima iniziale per l'intero lavoro</p>
              </div>

              <div className="campo-monte">
                <input
                  id="monteOre"
                  type="number"
                  min="0"
                  step="0.25"
                  aria-label="Monte ore stimato"
                  placeholder="0"
                  value={monteOre}
                  onChange={(evento) => setMonteOre(evento.target.value)}
                  disabled={inCorso}
                />
                <span className="unita">h</span>
              </div>
            </section>
          )}
        </div>

        <aside className="scheda-riepilogo">
          <h2 className="passo-titolo">Riepilogo</h2>

          <dl className="elenco-riepilogo">
            <div className="riga-riepilogo">
              <dt>Monte ore</dt>
              <dd>{ore === null ? '—' : `${numero(ore)} h`}</dd>
            </div>
            <div className="riga-riepilogo">
              <dt>Stato alla creazione</dt>
              <dd>{stato === 'ATTIVO' ? 'Attivo' : 'Bozza'}</dd>
            </div>
          </dl>

          {/* Si scrive solo quando c'è qualcosa che manca: a modulo finito il
              riepilogo non ha niente da aggiungere, e un pulsante acceso dice
              già da sé che si può premere. Quando invece è spento la ragione
              va detta, o si cerca il campo mancante a occhio. */}
          {!completo && (
            <p className="nota nota-manca">Mancano il nome o le date.</p>
          )}
          {completo && rovescio && (
            <p className="nota nota-manca">La fine prevista viene prima dell’inizio.</p>
          )}

          {errore !== null && (
            <p className="errore" role="alert">
              {errore}
            </p>
          )}

          <div className="azioni-riepilogo">
            <button type="submit" disabled={inCorso || !completo || rovescio}>
              {inCorso ? 'Creo…' : 'Crea lavoro'}
            </button>
            <button
              type="button"
              className="secondario"
              onClick={svuota}
              disabled={inCorso}
            >
              Svuota modulo
            </button>
          </div>
        </aside>
      </form>
    </main>
  )
}

/**
 * «Durata: 16 nov 2026 → 18 dic 2026 · 5 settimane».
 *
 * Le settimane e non i giorni: un lavoro si pianifica a settimane, e «32
 * giorni» chiede un conto in testa per diventare il numero su cui si decide.
 */
function durata(inizio: string, fine: string): string {
  if (inizio === '' || fine === '') {
    return 'Scegli le due date per vedere la durata.'
  }
  const da = new Date(`${inizio}T00:00:00`)
  const a = new Date(`${fine}T00:00:00`)
  if (a < da) {
    return 'La fine prevista viene prima dell’inizio.'
  }

  const giorni = Math.round((a.getTime() - da.getTime()) / 86400000) + 1
  const settimane = Math.max(1, Math.round(giorni / 7))
  return `Durata: ${esteso(da)} → ${esteso(a)} · ${
    settimane === 1 ? '1 settimana' : `${settimane} settimane`
  }`
}

/** «16 nov 2026». */
function esteso(giorno: Date): string {
  return giorno.toLocaleDateString('it-IT', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

/** Le ore senza decimali inutili: «120», «7,5». */
function numero(valore: number): string {
  return valore.toLocaleString('it-IT', { maximumFractionDigits: 2 })
}
