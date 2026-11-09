import { useEffect, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import PaginaLogin from './autenticazione/PaginaLogin'
import { io, logout } from './autenticazione/api'
import { mioTempo } from './tempo/api'
import type { SituazioneTempo } from './tempo/api'
import type { UtenteCorrente } from './autenticazione/api'
import Intestazione from './componenti/Intestazione'
import Protetta from './componenti/Protetta'
import PaginaUtenti from './utente/PaginaUtenti'
import PaginaNuovoUtente from './utente/PaginaNuovoUtente'
import PaginaPermessiUtente from './utente/PaginaPermessiUtente'
import PaginaLavori from './lavoro/PaginaLavori'
import PaginaNuovoLavoro from './lavoro/PaginaNuovoLavoro'
import PaginaLavoro from './lavoro/PaginaLavoro'
import PaginaRichiesteOre from './ore/PaginaRichiesteOre'
import PaginaDashboard from './dashboard/PaginaDashboard'
import PaginaMieAssenze from './assenza/PaginaMieAssenze'
import PaginaAssenze from './assenza/PaginaAssenze'
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
  | { fase: 'anonimo' }
  | { fase: 'collegato'; utente: UtenteCorrente }

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
              <PaginaLogin onAccesso={(utente) => setStato({ fase: 'collegato', utente })} />
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
      <Intestazione utente={utente} tempo={tempo} onTempo={setTempo} onEsci={esci} />
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
    </BrowserRouter>
  )
}
