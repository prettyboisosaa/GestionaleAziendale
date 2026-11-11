import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { creaLavoro, responsabiliPossibili } from './api'
import type { CandidatoResponsabile, StatoLavoro } from './api'
import { testo } from '../api/http'
import type { UtenteCorrente } from '../autenticazione/api'
import { ha } from '../autenticazione/permessi'

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
  const [stato, setStato] = useState<StatoLavoro>('BOZZA')
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

  async function invia(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()

    // Lo stesso vincolo che ha il database (ck_lavoro_responsabile): dirlo
    // qui risparmia un viaggio e spiega la scelta.
    if (stato === 'ATTIVO' && responsabileId === '') {
      setErrore('Un lavoro attivo deve avere un responsabile: scegline uno, oppure salvalo come bozza.')
      return
    }

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
        monteOre: !puoFissareMonteOre || monteOre.trim() === '' ? null : Number(monteOre),
      })
      naviga('/lavori')
    } catch (motivo) {
      setErrore(testo(motivo))
      setInCorso(false)
    }
  }

  return (
    <main className="pagina-larga pagina-modulo">
      <div className="testata">
        <h1>Nuovo lavoro</h1>
        <Link to="/lavori">Torna all'elenco</Link>
      </div>

      <form className="modulo" onSubmit={invia}>
        <div className="campo">
          <label htmlFor="nome">Nome</label>
          <input
            id="nome"
            required
            maxLength={200}
            value={nome}
            onChange={(evento) => setNome(evento.target.value)}
            disabled={inCorso}
          />
          <p className="nota">Il codice viene generato dal sistema.</p>
        </div>

        <div className="campo">
          <label htmlFor="descrizione">Descrizione</label>
          <textarea
            id="descrizione"
            rows={3}
            value={descrizione}
            onChange={(evento) => setDescrizione(evento.target.value)}
            disabled={inCorso}
          />
        </div>

        <div className="campo">
          <label htmlFor="dataInizio">Inizio</label>
          <input
            id="dataInizio"
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
            type="date"
            required
            min={dataInizio === '' ? undefined : dataInizio}
            value={dataFinePrevista}
            onChange={(evento) => setDataFinePrevista(evento.target.value)}
            disabled={inCorso}
          />
        </div>

        <fieldset className="campo">
          <legend>Stato</legend>
          <label className="scelta">
            <input
              type="radio"
              name="stato"
              checked={stato === 'BOZZA'}
              onChange={() => setStato('BOZZA')}
              disabled={inCorso}
            />
            <span>
              Bozza<em> — il responsabile si può scegliere dopo</em>
            </span>
          </label>
          <label className="scelta">
            <input
              type="radio"
              name="stato"
              checked={stato === 'ATTIVO'}
              onChange={() => setStato('ATTIVO')}
              disabled={inCorso}
            />
            <span>
              Attivo<em> — richiede un responsabile</em>
            </span>
          </label>
        </fieldset>

        {puoFissareMonteOre && (
          <div className="campo">
            <label htmlFor="monteOre">Monte ore</label>
            <input
              id="monteOre"
              type="number"
              min="0"
              step="0.25"
              value={monteOre}
              onChange={(evento) => setMonteOre(evento.target.value)}
              disabled={inCorso}
            />
            <p className="nota">
              Il budget da distribuire fra le task. Si può lasciare vuoto e fissarlo dopo:
              vuoto vuol dire «da decidere», non zero ore.
            </p>
          </div>
        )}

        {puoAssegnare && (
          <div className="campo">
            <label htmlFor="responsabile">Responsabile</label>
            <select
              id="responsabile"
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
            <p className="nota">Solo chi ha il permesso di gestire lavori.</p>
          </div>
        )}

        {errore !== null && (
          <p className="errore" role="alert">
            {errore}
          </p>
        )}

        <button type="submit" disabled={inCorso}>
          {inCorso ? 'Creo…' : 'Crea lavoro'}
        </button>
      </form>
    </main>
  )
}
