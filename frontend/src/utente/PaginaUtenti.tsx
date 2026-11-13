import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { cambiaAttivazione, elencoUtenti } from './api'
import type { RigaUtente } from './api'
import { elencoRuoli } from '../ruolo/api'
import type { RigaRuolo } from '../ruolo/api'
import { testo } from '../api/http'
import type { UtenteCorrente } from '../autenticazione/api'
import { ha } from '../autenticazione/permessi'
import ModaleResetPassword from './ModaleResetPassword'

/**
 * Il filtro dell'elenco. «Invitati» esiste nel design system e resta qui con
 * il suo contatore: finché non ci sono inviti vale zero, e il chip lo dice.
 */
type Filtro = 'TUTTI' | 'ATTIVI' | 'DISATTIVATI' | 'INVITATI'

const FILTRI: { valore: Filtro; nome: string }[] = [
  { valore: 'TUTTI', nome: 'Tutti' },
  { valore: 'ATTIVI', nome: 'Attivi' },
  { valore: 'DISATTIVATI', nome: 'Disattivati' },
  { valore: 'INVITATI', nome: 'Invitati' },
]

/** Da «2027-01-01T08:00:00» a «01/01/2027». */
function data(iso: string): string {
  return new Date(iso).toLocaleDateString('it-IT')
}

/**
 * Il modificatore di colore della pillola di un ruolo.
 *
 * Il ruolo più potente è il più scuro, e gli altri scendono di tono: la
 * gerarchia si legge dal colore prima che dalla parola. I nomi arrivano dal
 * database e possono essere qualunque cosa, quindi il confronto è sul nome in
 * minuscolo e c'è sempre un ripiego neutro.
 */
function tintaRuolo(nome: string): string {
  const chiave = nome.toLowerCase()
  if (chiave.includes('admin')) {
    return 'ruolo-admin'
  }
  if (chiave.includes('responsabile') || chiave.includes('manager')) {
    return 'ruolo-responsabile'
  }
  if (chiave.includes('ospite') || chiave.includes('esterno')) {
    return 'ruolo-ospite'
  }
  return 'ruolo-dipendente'
}

/** Le iniziali per la pastiglia della persona. */
function iniziali(riga: RigaUtente): string {
  return `${riga.nome.charAt(0)}${riga.cognome.charAt(0)}`.toUpperCase()
}

export default function PaginaUtenti({ utente }: { utente: UtenteCorrente }) {
  const [righe, setRighe] = useState<RigaUtente[] | null>(null)
  const [ruoli, setRuoli] = useState<RigaRuolo[]>([])
  const [errore, setErrore] = useState<string | null>(null)
  /** L'id su cui è in corso il cambio di stato, per spegnere il suo interruttore. */
  const [inCorso, setInCorso] = useState<number | null>(null)
  const [cerca, setCerca] = useState('')
  const [filtro, setFiltro] = useState<Filtro>('TUTTI')
  const [ruoloScelto, setRuoloScelto] = useState('')
  const [scelti, setScelti] = useState<number[]>([])

  /** La persona di cui si sta assegnando una password nuova, se c'è. */
  const [resetDi, setResetDi] = useState<RigaUtente | null>(null)
  /** La conferma dopo un reset riuscito: resta finché non si fa altro. */
  const [conferma, setConferma] = useState<string | null>(null)

  const puoAssegnare = ha(utente, 'RUOLI_ASSEGNA')
  const puoDisattivare = ha(utente, 'UTENTI_DISATTIVA')
  const puoAzzerarePassword = ha(utente, 'UTENTI_RESET_PASSWORD')

  useEffect(() => {
    let annullato = false

    elencoUtenti()
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

    // I ruoli servono solo a riempire la tendina del filtro: se non arrivano,
    // la tendina resta con «Tutti i ruoli» e l'elenco funziona lo stesso.
    elencoRuoli()
      .then((elenco) => {
        if (!annullato) {
          setRuoli(elenco)
        }
      })
      .catch(() => {
        // Niente da mostrare: è un filtro, non un dato della pagina.
      })

    return () => {
      annullato = true
    }
  }, [])

  async function cambia(riga: RigaUtente) {
    setInCorso(riga.id)
    setErrore(null)
    try {
      const aggiornato = await cambiaAttivazione(riga.id, !riga.attivo)
      // Si sostituisce la riga invece di ricaricare tutto: il server
      // restituisce l'utente aggiornato, e una seconda chiamata per leggere
      // quello che si ha già in mano sarebbe sprecata.
      setRighe((attuali) =>
        attuali === null
          ? null
          : attuali.map((una) => (una.id === aggiornato.id ? aggiornato : una)),
      )
    } catch (motivo) {
      // Il caso tipico è il 409 su se stessi: il messaggio arriva dal server
      // e dice di chiedere a un altro amministratore.
      setErrore(testo(motivo))
    } finally {
      setInCorso(null)
    }
  }

  const tutte = righe ?? []
  const attivi = tutte.filter((riga) => riga.attivo)
  const disattivati = tutte.filter((riga) => !riga.attivo)
  // Gli inviti non sono ancora modellati sul server: il riquadro e il chip
  // esistono con il loro zero invece di sparire, perché la domanda «quanti
  // inviti sono in sospeso» ha una risposta, ed è nessuno.
  const invitati: RigaUtente[] = []

  const gruppi: Record<Filtro, RigaUtente[]> = {
    TUTTI: tutte,
    ATTIVI: attivi,
    DISATTIVATI: disattivati,
    INVITATI: invitati,
  }

  const cercato = cerca.trim().toLowerCase()
  const visibili = gruppi[filtro].filter((riga) => {
    if (ruoloScelto !== '' && !riga.ruoli.includes(ruoloScelto)) {
      return false
    }
    if (cercato === '') {
      return true
    }
    return (
      `${riga.nome} ${riga.cognome}`.toLowerCase().includes(cercato)
      || riga.email.toLowerCase().includes(cercato)
      || riga.ruoli.join(' ').toLowerCase().includes(cercato)
    )
  })

  /** Le righe che si possono spuntare: la propria no, il server la rifiuta. */
  const spuntabili = visibili.filter((riga) => riga.email !== utente.email)
  const tutteScelte = spuntabili.length > 0 && scelti.length === spuntabili.length

  function spunta(id: number, scelto: boolean) {
    setScelti((attuali) =>
      scelto ? [...attuali, id] : attuali.filter((uno) => uno !== id),
    )
  }

  return (
    <main className="pagina-larga">
      <p className="occhiello">Area amministratore</p>
      <div className="testata testata-utenti">
        <h1>Utenti e permessi</h1>
        {ha(utente, 'UTENTI_CREA') && (
          <Link to="/utenti/nuovo" className="azione">
            Invita utente
          </Link>
        )}
      </div>

      {errore !== null && (
        <p className="errore" role="alert">
          {errore}
        </p>
      )}

      {/* La conferma del cambio password. Resta in pagina invece di
          svanire da sé: contiene l'unica istruzione che conta — che la
          password va comunicata a voce — e chi la legge deve poterla
          rileggere mentre compone il numero. */}
      {conferma !== null && (
        <p className="avviso" role="status">
          {conferma}{' '}
          <button type="button" className="bottone-riga" onClick={() => setConferma(null)}>
            Ho capito
          </button>
        </p>
      )}

      {/* I quattro conteggi. I primi due sono pieni perché sono la coppia che
          si guarda — quante persone ci sono e quante lavorano davvero — e gli
          altri due restano bianchi: sono eccezioni, e contano quando non sono
          zero. */}
      <div className="riquadri-utenti">
        <div className="riquadro-kpi riquadro-pieno">
          <span className="riquadro-etichetta">Utenti totali</span>
          <span className="riquadro-valore">{tutte.length}</span>
        </div>
        <div className="riquadro-kpi riquadro-acqua">
          <span className="riquadro-etichetta">Attivi</span>
          <span className="riquadro-valore">{attivi.length}</span>
        </div>
        <div className="riquadro-kpi">
          <span className="riquadro-etichetta">Disattivati</span>
          <span className="riquadro-valore">{disattivati.length}</span>
        </div>
        <div className="riquadro-kpi">
          <span className="riquadro-etichetta">Inviti in sospeso</span>
          <span className="riquadro-valore">{invitati.length}</span>
        </div>
      </div>

      <section className="scheda-utenti">
        <div className="barra-utenti">
          <div className="campo-ricerca">
            <span className="lente" aria-hidden="true">
              ⌕
            </span>
            <input
              type="search"
              aria-label="Cerca un utente"
              placeholder="Cerca per nome, email o reparto…"
              value={cerca}
              onChange={(evento) => setCerca(evento.target.value)}
            />
          </div>

          <div className="chip-filtri" role="group" aria-label="Filtra gli utenti">
            {FILTRI.map((uno) => (
              <button
                key={uno.valore}
                type="button"
                className={filtro === uno.valore ? 'chip chip-scelto' : 'chip'}
                aria-pressed={filtro === uno.valore}
                onClick={() => setFiltro(uno.valore)}
              >
                {uno.nome}
              </button>
            ))}
          </div>

          <select
            aria-label="Filtra per ruolo"
            className="scelta-ruolo"
            value={ruoloScelto}
            onChange={(evento) => setRuoloScelto(evento.target.value)}
          >
            <option value="">Tutti i ruoli</option>
            {ruoli.map((ruolo) => (
              <option key={ruolo.id} value={ruolo.nome}>
                {ruolo.nome}
              </option>
            ))}
          </select>
        </div>

        {errore === null && righe === null && (
          <p className="sommario" role="status">
            Carico l'elenco…
          </p>
        )}

        {righe !== null && (
          <>
            <table className="tabella tabella-utenti">
              <thead>
                <tr>
                  <th className="colonna-spunta">
                    <input
                      type="checkbox"
                      aria-label="Seleziona tutti"
                      checked={tutteScelte}
                      onChange={(evento) =>
                        setScelti(
                          evento.target.checked ? spuntabili.map((riga) => riga.id) : [],
                        )
                      }
                    />
                  </th>
                  <th>Utente</th>
                  <th>Ruolo</th>
                  <th>Reparto</th>
                  <th>Ultimo accesso</th>
                  <th>Stato</th>
                  <th className="colonna-azioni">Azioni</th>
                </tr>
              </thead>
              <tbody>
                {/* Gli utenti disattivati restano nell'elenco: il loro nome
                    compare nello storico di lavori, task e registrazioni, e
                    nasconderli renderebbe quello storico illeggibile. */}
                {visibili.map((riga) => {
                  const io = riga.email === utente.email
                  return (
                    <tr key={riga.id} className={riga.attivo ? undefined : 'riga-spenta'}>
                      <td className="colonna-spunta">
                        <input
                          type="checkbox"
                          aria-label={`Seleziona ${riga.nome} ${riga.cognome}`}
                          disabled={io}
                          checked={scelti.includes(riga.id)}
                          onChange={(evento) => spunta(riga.id, evento.target.checked)}
                        />
                      </td>

                      <td>
                        <div className="cella-utente">
                          <span className={`gettone gettone-${riga.id % 5}`}>
                            <span aria-hidden="true">{iniziali(riga)}</span>
                          </span>
                          <span className="cella-utente-testo">
                            <span className="cella-utente-nome">
                              {riga.nome} {riga.cognome}
                              {io && <span className="segno-io">Tu</span>}
                            </span>
                            <span className="cella-utente-email">{riga.email}</span>
                          </span>
                        </div>
                      </td>

                      <td>
                        {/* Il ruolo come pillola e non come tendina: cambiarlo
                            è una cosa che il server non espone da qui, e un
                            menu che non scrive niente sarebbe una promessa
                            falsa. I ruoli si assegnano dalla pagina dei
                            permessi. */}
                        {riga.ruoli.length === 0 ? (
                          <span className="nota">—</span>
                        ) : (
                          <span className={`pillola-ruolo ${tintaRuolo(riga.ruoli[0])}`}>
                            {riga.ruoli.join(', ')}
                          </span>
                        )}
                      </td>

                      {/* Reparto e ultimo accesso non esistono sul server: la
                          colonna c'è perché il disegno la prevede, e il
                          trattino dice che il dato manca invece di
                          inventarlo. */}
                      <td className="nota">—</td>
                      <td className="nota">—</td>

                      <td>
                        <div className="cella-stato">
                          {/* L'interruttore nei due versi: è quello che rende
                              la riattivazione ovvia a chi ha appena
                              disattivato qualcuno per sbaglio. Sulla propria
                              riga è spento — il server risponde 409 — ma
                              resta visibile, perché lo stato si deve leggere
                              anche lì. */}
                          <label className="interruttore">
                            <input
                              type="checkbox"
                              checked={riga.attivo}
                              disabled={!puoDisattivare || io || inCorso === riga.id}
                              aria-label={
                                riga.attivo
                                  ? `Disattiva ${riga.nome} ${riga.cognome}`
                                  : `Riattiva ${riga.nome} ${riga.cognome}`
                              }
                              onChange={() => void cambia(riga)}
                            />
                            <span className="interruttore-vasca" aria-hidden="true" />
                          </label>
                          <span className="cella-stato-testo">
                            {riga.attivo ? 'Attivo' : 'Disattivato'}
                            {io && ' · tu'}
                            {!riga.attivo && riga.disattivatoIl !== null && (
                              <span className="nota">dal {data(riga.disattivatoIl)}</span>
                            )}
                          </span>
                        </div>
                      </td>

                      <td className="colonna-azioni">
                        {puoAssegnare && (
                          <Link to={`/utenti/${riga.id}/permessi`} className="bottone-riga">
                            Permessi
                          </Link>
                        )}
                        {/* Non sulla propria riga: il server la rifiuta
                            comunque con RESET_DI_SE_STESSI, e offrire un
                            pulsante che non può funzionare è peggio che non
                            offrirlo. La propria password si cambia da
                            «Profilo», indicando quella attuale. */}
                        {puoAzzerarePassword && riga.id !== utente.id && (
                          <button
                            type="button"
                            className="bottone-riga"
                            onClick={() => {
                              setConferma(null)
                              setResetDi(riga)
                            }}
                          >
                            Password
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>

            <p className="nota piede-utenti">
              Mostrati {visibili.length} di {tutte.length} utenti
              {scelti.length > 0 && ` · ${scelti.length} selezionati`}
            </p>
          </>
        )}
      </section>

      {resetDi !== null && (
        <ModaleResetPassword
          persona={resetDi}
          onChiudi={() => setResetDi(null)}
          onFatto={(messaggio) => {
            setResetDi(null)
            setConferma(messaggio)
          }}
        />
      )}
    </main>
  )
}
