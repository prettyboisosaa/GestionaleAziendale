import { useState } from 'react'
import type { FormEvent } from 'react'
import { cambiaPassword } from '../autenticazione/api'
import type { UtenteCorrente } from '../autenticazione/api'
import { REQUISITI, passwordValida } from '../autenticazione/regolePassword'
import { codice, senzaCodice } from '../api/http'

/**
 * Il proprio profilo: chi si è, e il cambio della propria password.
 *
 * Non è `/utenti/:id`, che è l'amministrazione delle persone e chiede
 * `UTENTI_VISUALIZZA`: qui non serve nessun permesso, perché cambiarsi la
 * password è parte dell'avere un accesso e non una cosa che si concede.
 */
export default function PaginaProfilo({ utente }: { utente: UtenteCorrente }) {
  return (
    <main className="pagina-larga">
      <div className="testata">
        <h1>Il mio profilo</h1>
      </div>

      <section className="scheda-profilo">
        <div className="testata">
          <h2>
            {utente.nome} {utente.cognome}
          </h2>
          <span className="nota">{utente.email}</span>
        </div>
      </section>

      <CambioPassword />
    </main>
  )
}

/**
 * Dove si cambia la password, con la password attuale a dimostrare di essere
 * il padrone dell'utenza.
 *
 * I tre campi stanno impilati e non in riga: sono tre cose diverse — quello
 * che si sa, quello che si vuole, la prova di non aver battuto male — e
 * affiancarle farebbe sembrare «nuova» e «conferma» due metà di un campo solo.
 */
function CambioPassword() {
  const [attuale, setAttuale] = useState('')
  const [nuova, setNuova] = useState('')
  const [conferma, setConferma] = useState('')

  /**
   * L'errore, smistato per campo.
   *
   * Un errore solo in fondo al modulo costringe a cercare a quale dei tre
   * campi si riferisca. Il backend manda un codice in testa al messaggio
   * proprio per questo: `WRONG_OLD_PASSWORD` riguarda il primo campo,
   * `WEAK_PASSWORD` e `SAME_PASSWORD` il secondo, e quello che non si
   * riconosce resta in fondo — meglio in un posto generico che nascosto.
   */
  const [erroreAttuale, setErroreAttuale] = useState<string | null>(null)
  const [erroreNuova, setErroreNuova] = useState<string | null>(null)
  const [errore, setErrore] = useState<string | null>(null)

  const [fatto, setFatto] = useState(false)
  const [inCorso, setInCorso] = useState(false)

  const coincidono = nuova !== '' && nuova === conferma
  /**
   * Il pulsante resta spento finché il modulo non sta in piedi: chiedere al
   * server di rifiutare una password di quattro lettere è un giro di rete per
   * sapere una cosa che si vede guardando il campo.
   */
  const puoInviare = attuale !== '' && passwordValida(nuova) && coincidono

  async function invia(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()

    setErroreAttuale(null)
    setErroreNuova(null)
    setErrore(null)
    setFatto(false)
    setInCorso(true)

    try {
      await cambiaPassword(attuale, nuova)
      // Il modulo si svuota: lasciare dentro le password dopo un cambio
      // riuscito le tiene in pagina senza nessuna ragione.
      setAttuale('')
      setNuova('')
      setConferma('')
      setFatto(true)
    } catch (motivo) {
      smista(motivo)
    } finally {
      setInCorso(false)
    }
  }

  /** Mette il messaggio sotto il campo che riguarda. */
  function smista(motivo: unknown) {
    const messaggio = motivo instanceof Error ? motivo.message : ''
    const leggibile = senzaCodice(messaggio)

    switch (codice(messaggio)) {
      case 'WRONG_OLD_PASSWORD':
        setErroreAttuale(leggibile)
        return
      case 'WEAK_PASSWORD':
      case 'SAME_PASSWORD':
        setErroreNuova(leggibile)
        return
      default:
        // Sessione scaduta, server muto, o il limite dei tentativi: nessuno
        // dei tre parla di un campo in particolare.
        setErrore(leggibile === '' ? 'Richiesta non riuscita. Riprova.' : leggibile)
    }
  }

  return (
    <section className="scheda-profilo">
      <div className="testata">
        <h2>Cambia password</h2>
      </div>

      <form className="modulo" onSubmit={invia}>
        <CampoPassword
          id="password-attuale"
          etichetta="Password attuale"
          valore={attuale}
          onCambia={setAttuale}
          errore={erroreAttuale}
          autoComplete="current-password"
        />

        <CampoPassword
          id="password-nuova"
          etichetta="Nuova password"
          valore={nuova}
          onCambia={setNuova}
          errore={erroreNuova}
          autoComplete="new-password"
        />

        {/* La spunta si aggiorna mentre si scrive, e c'e' sempre: comparire
            solo quando qualcosa manca direbbe le regole dopo che si e' gia'
            sbagliato. */}
        <ul className="requisiti">
          {REQUISITI.map((requisito) => {
            const soddisfatto = requisito.soddisfatto(nuova)
            return (
              <li
                key={requisito.id}
                className={soddisfatto ? 'requisito-ok' : undefined}
              >
                {/* Il segno e' decorativo: quello che conta lo dice il testo,
                    e un lettore di schermo che legge «spunta» a ogni riga
                    aggiunge rumore, non informazione. */}
                <span aria-hidden="true">{soddisfatto ? '✓' : '○'}</span>
                {requisito.etichetta}
              </li>
            )
          })}
        </ul>

        <CampoPassword
          id="password-conferma"
          etichetta="Conferma nuova password"
          valore={conferma}
          onCambia={setConferma}
          // Si dice solo quando c'e' qualcosa da dire: segnalare che non
          // coincidono alla prima lettera battuta e' un rimprovero a chi sta
          // ancora scrivendo.
          errore={
            conferma !== '' && !coincidono ? 'Le due password non coincidono.' : null
          }
          autoComplete="new-password"
        />

        {errore !== null && (
          <p className="errore" role="alert">
            {errore}
          </p>
        )}

        {fatto && (
          <p className="sommario" role="status">
            <strong>Password aggiornata.</strong> Dal prossimo accesso usa
            quella nuova.
          </p>
        )}

        <div className="azioni">
          <button type="submit" disabled={!puoInviare || inCorso}>
            {inCorso ? 'Salvo…' : 'Cambia password'}
          </button>
        </div>
      </form>
    </section>
  )
}

/**
 * Un campo password con il suo interruttore mostra/nascondi e il suo errore.
 *
 * Un componente e non tre blocchi copiati: sono tre campi identici in tutto
 * tranne l'etichetta, e tre copie dello stesso `useState` per il mostra sono
 * tre occasioni di dimenticarne una.
 *
 * L'interruttore serve perché una password che non si vede si sbaglia, e qui
 * si sbaglia tre volte: chi la batte male in «nuova» e bene in «conferma»
 * cambia la password in una cosa che non sa di aver scritto.
 */
function CampoPassword({
  id,
  etichetta,
  valore,
  onCambia,
  errore,
  autoComplete,
}: {
  id: string
  etichetta: string
  valore: string
  onCambia: (valore: string) => void
  errore: string | null
  autoComplete: string
}) {
  const [visibile, setVisibile] = useState(false)

  return (
    <div className="campo">
      <label htmlFor={id}>{etichetta}</label>
      <div className="campo-con-azione">
        <input
          id={id}
          type={visibile ? 'text' : 'password'}
          value={valore}
          onChange={(evento) => onCambia(evento.target.value)}
          autoComplete={autoComplete}
          required
        />
        <button
          type="button"
          className="minuscolo"
          // Il nome del pulsante dice che cosa fara', non lo stato in cui si
          // trova: e' quello che serve a chi lo preme.
          aria-label={visibile ? `Nascondi ${etichetta}` : `Mostra ${etichetta}`}
          onClick={() => setVisibile((prima) => !prima)}
        >
          {visibile ? 'Nascondi' : 'Mostra'}
        </button>
      </div>
      {errore !== null && (
        <p className="errore" role="alert">
          {errore}
        </p>
      )}
    </div>
  )
}
