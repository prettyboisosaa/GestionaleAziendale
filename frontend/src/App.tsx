import { useEffect, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import PaginaLogin from './autenticazione/PaginaLogin'
import { io, logout } from './autenticazione/api'
import type { UtenteCorrente } from './autenticazione/api'
import { ha } from './autenticazione/permessi'
import Intestazione from './componenti/Intestazione'
import Protetta from './componenti/Protetta'
import PaginaUtenti from './utente/PaginaUtenti'
import PaginaNuovoUtente from './utente/PaginaNuovoUtente'
import PaginaPermessiUtente from './utente/PaginaPermessiUtente'
import PaginaLavori from './lavoro/PaginaLavori'
import PaginaNuovoLavoro from './lavoro/PaginaNuovoLavoro'
import PaginaLavoro from './lavoro/PaginaLavoro'
import PaginaGiornata from './dichiarazione/PaginaGiornata'
import PaginaRichiesteOre from './ore/PaginaRichiesteOre'
import PaginaDashboard from './dashboard/PaginaDashboard'
import PaginaMieAssenze from './assenza/PaginaMieAssenze'
import PaginaAssenze from './assenza/PaginaAssenze'
import './App.css'

const VISUALIZZA_LAVORI = ['LAVORI_VISUALIZZA_TUTTI', 'LAVORI_VISUALIZZA_PROPRI']
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

  async function esci() {
    await logout()
    setStato({ fase: 'anonimo' })
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
  // Dove si atterra: i lavori sono la pagina di lavoro quotidiana, ma chi non
  // li vede non deve finire su una schermata di «non autorizzato».
  // Dove si atterra. La dashboard viene prima di tutto, perché risponde alla
  // domanda con cui si apre la giornata — cosa devo fare oggi — mentre
  // l'elenco dei lavori risponde a una domanda che ci si pone dopo. Chi non ha
  // task assegnate non ha una dashboard da guardare, e per quelli l'ordine di
  // prima resta intatto.
  const iniziale = ha(utente, 'TASK_VISUALIZZA_ASSEGNATE')
    ? '/dashboard'
    : ha(utente, ...VISUALIZZA_LAVORI)
      ? '/lavori'
      : '/utenti'

  return (
    <BrowserRouter>
      <Intestazione utente={utente} onEsci={esci} />
      <Routes>
        <Route
          path="/dashboard"
          element={
            <Protetta utente={utente} permesso="TASK_VISUALIZZA_ASSEGNATE">
              <PaginaDashboard utente={utente} />
            </Protetta>
          }
        />
        <Route
          path="/lavori"
          element={
            <Protetta utente={utente} permesso={VISUALIZZA_LAVORI}>
              <PaginaLavori utente={utente} />
            </Protetta>
          }
        />
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
          element={
            <Protetta utente={utente} permesso={VISUALIZZA_LAVORI}>
              <PaginaLavoro utente={utente} />
            </Protetta>
          }
        />
        <Route
          path="/giornata"
          element={
            <Protetta utente={utente} permesso="DICHIARAZIONI_COMPILA">
              <PaginaGiornata />
            </Protetta>
          }
        />
        <Route
          path="/richieste-ore"
          element={
            <Protetta utente={utente} permesso={RICHIESTE_ORE}>
              <PaginaRichiesteOre utente={utente} />
            </Protetta>
          }
        />
        <Route
          path="/assenze"
          element={
            <Protetta utente={utente} permesso="ASSENZE_RICHIEDI">
              <PaginaMieAssenze />
            </Protetta>
          }
        />
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
            <Protetta utente={utente} permesso="UTENTI_CREA">
              <PaginaNuovoUtente />
            </Protetta>
          }
        />
        <Route
          path="/utenti/:id/permessi"
          element={
            <Protetta utente={utente} permesso="RUOLI_ASSEGNA">
              <PaginaPermessiUtente />
            </Protetta>
          }
        />
        <Route path="*" element={<Navigate to={iniziale} replace />} />
      </Routes>
    </BrowserRouter>
  )
}
