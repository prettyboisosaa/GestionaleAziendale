import { useEffect, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import PaginaLogin from './autenticazione/PaginaLogin'
import { io, logout } from './autenticazione/api'
import { quandoLaSessioneScade } from './api/http'
import { mioTempo } from './tempo/api'
import type { SituazioneTempo } from './tempo/api'
import type { UtenteCorrente } from './autenticazione/api'
import Intestazione from './componenti/Intestazione'
import Protetta from './componenti/Protetta'
import PaginaUtenti from './utente/PaginaUtenti'
import PaginaProfilo from './utente/PaginaProfilo'
import ConfermaUscita from './componenti/ConfermaUscita'
import Sidebar from './componenti/Sidebar'
import { useSchermoStretto } from './componenti/schermo'
import PaginaNuovoUtente from './utente/PaginaNuovoUtente'
import PaginaPermessiUtente from './utente/PaginaPermessiUtente'
import PaginaLavori from './lavoro/PaginaLavori'
import PaginaNuovoLavoro from './lavoro/PaginaNuovoLavoro'
import PaginaLavoro from './lavoro/PaginaLavoro'
import PaginaRichiesteOre from './ore/PaginaRichiesteOre'
import PaginaDashboard from './dashboard/PaginaDashboard'
import PaginaMieAssenze from './assenza/PaginaMieAssenze'
import PaginaAssenze from './assenza/PaginaAssenze'
import PaginaReport from './report/PaginaReport'
import './App.css'

/**
 * Chi apre le richieste e chi le decide arrivano alla stessa pagina, che si
 * comporta in due modi: il primo vede le proprie con l'esito, il secondo la
 * coda da decidere. Fonderle evita due schermate che mostrerebbero le stesse
 * righe.
 */
const RICHIESTE_ORE = ['ORE_RICHIESTE_GESTISCI', 'ORE_RICHIEDI']

type Stato =
  | { fase: 'caricamento' }
  | { fase: 'anonimo'; avviso?: string }
  | { fase: 'collegato'; utente: UtenteCorrente }

const SESSIONE_SCADUTA = 'La sessione è scaduta per inattività. Accedi di nuovo.'

export default function App() {
  const [stato, setStato] = useState<Stato>({ fase: 'caricamento' })

  /**
   * Il tempo di chi è collegato, tenuto qui e non in ogni pagina.
   *
   * La barra in testata e le pagine con i pulsanti del timer devono dire la
   * stessa cosa: se la dashboard avvia una task, la barra lo deve mostrare
   * subito. Tenendolo in due stati separati divergerebbero al primo clic, e
   * non c'è un posto migliore dove metterlo — questo componente è già quello
   * che possiede l'utente collegato.
   */
  const [tempo, setTempo] = useState<SituazioneTempo | null>(null)

  /**
   * Vero mentre la conferma d'uscita è aperta.
   *
   * Sta qui e non in `Intestazione` perché l'uscita è di questo componente —
   * è lui che possiede la sessione — e perché così la conferma vale per ogni
   * punto da cui si esce, compresi quelli che non esistono ancora.
   */
  const [chiedoSeUscire, setChiedoSeUscire] = useState(false)

  /**
   * Da mobile la navigazione è un menù a scomparsa, e lo stato di «aperto»
   * vive qui: il pulsante che lo apre sta in testata e la barra che si apre è
   * un altro componente, quindi nessuno dei due può possederlo.
   */
  const [menuAperto, setMenuAperto] = useState(false)
  const stretto = useSchermoStretto()

  /**
   * La sessione scade per inattività dopo trenta minuti, e il server lo dice
   * rispondendo 401 alla prima richiesta successiva. Senza questo, ogni
   * azione dopo una pausa lunga falliva con «Richiesta non riuscita. Riprova.»
   * e riprovare non serviva: l'applicazione restava aperta su schermate che
   * sembravano a posto, dentro una sessione che non esisteva più.
   *
   * Si registra una volta sola: `quandoLaSessioneScade` tiene l'ultima
   * callback, e l'array di dipendenze vuoto fa sì che sia sempre questa.
   * `setStato` di React è stabile, quindi non c'è niente che possa invecchiare.
   */
  useEffect(() => {
    quandoLaSessioneScade(() => setStato({ fase: 'anonimo', avviso: SESSIONE_SCADUTA }))
  }, [])

  // Il cookie di sessione è HttpOnly, quindi invisibile a JavaScript: l'unico
  // modo di sapere se la sessione è ancora viva è chiederlo al server.
  useEffect(() => {
    let annullato = false

    io()
      .then((utente) => {
        if (annullato) {
          return
        }
        setStato(utente === null ? { fase: 'anonimo' } : { fase: 'collegato', utente })
      })
      .catch(() => {
        // Server irraggiungibile: si mostra il login, dove un tentativo darà
        // un messaggio d'errore leggibile.
        if (!annullato) {
          setStato({ fase: 'anonimo' })
        }
      })

    return () => {
      annullato = true
    }
  }, [])

  // Il tempo si carica appena si sa chi è collegato. Un errore qui non si
  // mostra: la barra del timer è un comodo, e non deve poter impedire di
  // usare il gestionale.
  const collegato = stato.fase === 'collegato'
  useEffect(() => {
    if (!collegato) {
      return
    }

    let annullato = false
    mioTempo()
      .then((situazione) => {
        if (!annullato) {
          setTempo(situazione)
        }
      })
      .catch(() => undefined)

    return () => {
      annullato = true
    }
  }, [collegato])

  async function esci() {
    // La modale si chiude prima della chiamata: lasciarla aperta mentre il
    // server risponde la farebbe sembrare bloccata, e non c'è niente da
    // annullare — a questo punto la decisione è presa.
    setChiedoSeUscire(false)
    await logout()
    setStato({ fase: 'anonimo' })
    // Il tempo si azzera qui e non nell'effetto che lo carica: e' l'uscita a
    // renderlo obsoleto, e azzerarlo dentro l'effetto farebbe partire un
    // secondo render per una cosa che sappiamo gia'.
    setTempo(null)
  }

  if (stato.fase === 'caricamento') {
    return (
      <main className="pagina">
        <div className="colonna">
          <p className="azienda">Anitia</p>
          <h1>Gestionale</h1>
          <p className="sommario" role="status">
            Verifico la sessione…
          </p>
        </div>
      </main>
    )
  }

  // Due mondi separati, anonimo e collegato: così nelle pagine interne
  // l'utente non è mai nullo e non serve controllarlo a ogni passo.
  if (stato.fase === 'anonimo') {
    return (
      <BrowserRouter>
        <Routes>
          <Route
            path="/login"
            element={
              <PaginaLogin
                avviso={stato.avviso}
                onAccesso={(utente) => setStato({ fase: 'collegato', utente })}
              />
            }
          />
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </BrowserRouter>
    )
  }

  const utente = stato.utente
  // Dove si atterra: sempre la dashboard. Risponde alla domanda con cui si
  // apre la giornata — cosa devo fare oggi — e da quando TASK_VISUALIZZA_ASSEGNATE
  // è un permesso di base ce l'hanno tutti, quindi non c'è più nessuno da
  // dirottare altrove. Prima era una cascata a tre rami, e i due rami di
  // riserva sono diventati irraggiungibili.
  const iniziale = '/dashboard'

  return (
    <BrowserRouter>
      {/* La navigazione è una barra laterale comune a tutta l'app, non più
          una riga di voci in testata: da chiusa è una colonna di icone, da
          aperta **spinge** il contenuto, che si stringe per farle posto. Da
          schermo stretto non spinge niente e torna a essere un menù
          sovrapposto, perché lì non c'è spazio da spartire. */}
      <Sidebar
        utente={utente}
        tempo={tempo}
        onTempo={setTempo}
        // «Esci» non esce: chiede. Chi lo preme per sbaglio con una task
        // avviata perde la pagina su cui stava lavorando, e un clic non è un
        // motivo sufficiente per chiudere una sessione.
        onEsci={() => setChiedoSeUscire(true)}
        mobile={stretto}
        aperta={menuAperto}
        onChiudi={() => setMenuAperto(false)}
      />

      <div className="corpo-app">
        <Intestazione
          mostraMenu={stretto}
          onMenu={() => setMenuAperto((prima) => !prima)}
        />

        {chiedoSeUscire && (
          <ConfermaUscita
            onAnnulla={() => setChiedoSeUscire(false)}
            onEsci={() => void esci()}
          />
        )}

        <Routes>
          <Route
            path="/dashboard"
            element={<PaginaDashboard utente={utente} tempo={tempo} onTempo={setTempo} />}
          />
          <Route path="/lavori" element={<PaginaLavori utente={utente} />} />
          <Route
            path="/lavori/nuovo"
            element={
              <Protetta utente={utente} permesso="LAVORI_CREA">
                <PaginaNuovoLavoro utente={utente} />
              </Protetta>
            }
          />
          <Route
            path="/lavori/:id"
            element={<PaginaLavoro utente={utente} tempo={tempo} onTempo={setTempo} />}
          />
          <Route
            path="/richieste-ore"
            element={
              <Protetta utente={utente} permesso={RICHIESTE_ORE}>
                <PaginaRichiesteOre utente={utente} />
              </Protetta>
            }
          />
          {/* Nessuna `Protetta` attorno: il profilo è di chi è collegato, e
              cambiarsi la password è parte dell'avere un accesso — non un
              permesso da concedere. */}
          <Route path="/profilo" element={<PaginaProfilo utente={utente} />} />
          <Route path="/assenze" element={<PaginaMieAssenze />} />
          <Route
            path="/richieste-assenza"
            element={
              <Protetta utente={utente} permesso="ASSENZE_GESTISCI">
                <PaginaAssenze />
              </Protetta>
            }
          />
          <Route
            path="/report"
            element={
              <Protetta utente={utente} permesso="TEMPO_VISUALIZZA_TUTTI">
                <PaginaReport />
              </Protetta>
            }
          />
          <Route
            path="/utenti"
            element={
              <Protetta utente={utente} permesso="UTENTI_VISUALIZZA">
                <PaginaUtenti utente={utente} />
              </Protetta>
            }
          />
          <Route
            path="/utenti/nuovo"
            element={
              <Protetta utente={utente} modo="tutti" permesso={['UTENTI_CREA', 'RUOLI_ASSEGNA']}>
                <PaginaNuovoUtente />
              </Protetta>
            }
          />
          <Route
            path="/utenti/:id/permessi"
            element={
              <Protetta utente={utente} modo="tutti" permesso={['UTENTI_VISUALIZZA', 'RUOLI_ASSEGNA']}>
                <PaginaPermessiUtente />
              </Protetta>
            }
          />
          <Route path="*" element={<Navigate to={iniziale} replace />} />
        </Routes>
      </div>
    </BrowserRouter>
  )
}
