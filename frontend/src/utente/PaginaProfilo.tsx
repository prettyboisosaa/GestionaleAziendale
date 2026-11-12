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
 *
 * **Due schede affiancate**: a sinistra quello che si è, a destra l'unica cosa
 * che si può cambiare. È la divisione che il design system fa di questa
 * pagina, e dice da sé una cosa che altrimenti andrebbe scritta: i dati del
 * profilo non si toccano da qui.
 */
export default function PaginaProfilo({ utente }: { utente: UtenteCorrente }) {
  return (
    <main className="pagina-larga">
      <p className="occhiello">Account</p>
      <div className="testata testata-profilo">
        <h1>Impostazioni utente</h1>
      </div>

      <div className="griglia-profilo">
        <SchedaIdentita utente={utente} />
        <CambioPassword />
      </div>
    </main>
  )
}

/** Chi si è: la scheda di sola lettura, a sinistra. */
function SchedaIdentita({ utente }: { utente: UtenteCorrente }) {
  return (
    <section className="scheda-profilo">
      <div className="profilo-capo">
        <span className="profilo-gettone" aria-hidden="true">
          {utente.nome.charAt(0)}
          {utente.cognome.charAt(0)}
        </span>
        <span className="profilo-capo-testo">
          <span className="profilo-nome">
            {utente.nome} {utente.cognome}
          </span>
          <span className="nota">{ruolo(utente)}</span>
        </span>
      </div>

      <dl className="elenco-dati">
        <Dato nome="Nome" valore={utente.nome} />
        <Dato nome="Cognome" valore={utente.cognome} />
        <Dato nome="Email" valore={utente.email} />
        {/* Reparto e ultimo accesso non esistono sul server: la riga c'è
            perché il disegno la prevede, e il trattino dice che il dato manca
            invece di inventarlo. */}
        <Dato nome="Reparto" valore="—" />
        <Dato nome="Ultimo accesso" valore="—" />
      </dl>

      {/* La frase che spiega perché nessuno di quei campi è un campo: i dati
          anagrafici li tiene l'amministratore, e cercare un pulsante «modifica»
          che non c'è è peggio che leggerlo scritto. */}
      <p className="nota">I dati del profilo sono gestiti dall'amministratore.</p>
    </section>
  )
}

/** Una riga dell'anagrafica: nome a sinistra, valore a destra. */
function Dato({ nome, valore }: { nome: string; valore: string }) {
  return (
    <div className="riga-dato">
      <dt>{nome}</dt>
      <dd>{valore}</dd>
    </div>
  )
}

/**
 * Come si chiama il livello di chi guarda.
 *
 * **Si ricava dai permessi**, perché il server non manda un nome di ruolo
 * insieme all'utente collegato: manda l'elenco di quello che quella persona
 * può fare. Sono le due caselle che distinguono davvero i tre livelli — chi
 * crea utenze amministra, chi crea lavori li dirige — e il ripiego è il
 * livello più basso, che è anche quello giusto quando non si sa.
 */
function ruolo(utente: UtenteCorrente): string {
  if (utente.permessi.includes('UTENTI_CREA') || utente.permessi.includes('RUOLI_ASSEGNA')) {
    return 'Amministratore'
  }
  if (utente.permessi.includes('LAVORI_CREA')) {
    return 'Responsabile'
  }
  return 'Dipendente'
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
   * Il quarto requisito non sta in `regolePassword`, e non è una
   * dimenticanza: quello è l'elenco di che cos'è una password valida, e una
   * password uguale alla precedente è valida — semplicemente non è un
   * cambio. Il server la rifiuta con `SAME_PASSWORD`, e qui lo si dice prima.
   */
  const diversa = nuova !== '' && nuova !== attuale
  const requisiti = [
    ...REQUISITI.map((requisito) => ({
      id: requisito.id,
      etichetta: requisito.etichetta,
      soddisfatto: requisito.soddisfatto(nuova),
    })),
    {
      id: 'diversa',
      etichetta: 'Diversa dalla password attuale',
      soddisfatto: diversa,
    },
  ]

  const forza = requisiti.filter((requisito) => requisito.soddisfatto).length

  /**
   * Il pulsante resta spento finché il modulo non sta in piedi: chiedere al
   * server di rifiutare una password di quattro lettere è un giro di rete per
   * sapere una cosa che si vede guardando il campo.
   */
  const puoInviare = attuale !== '' && passwordValida(nuova) && diversa && coincidono

  function svuota() {
    setAttuale('')
    setNuova('')
    setConferma('')
    setErroreAttuale(null)
    setErroreNuova(null)
    setErrore(null)
  }

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
      <h2 className="profilo-titolo">Cambia password</h2>
      <p className="nota">Usa una password che non usi altrove.</p>

      <form className="modulo modulo-password" onSubmit={invia}>
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

        {/* La misura della forza: quattro tacche, una per requisito
            soddisfatto. Non è un punteggio inventato da un algoritmo — è il
            conto delle stesse regole scritte qui sotto, e per questo la
            tacca che si accende corrisponde sempre a una riga che diventa
            verde. */}
        <div className="forza-password">
          <div className="tacche" role="presentation">
            {[1, 2, 3, 4].map((tacca) => (
              <span
                key={tacca}
                className={tacca <= forza ? `tacca tacca-${forza}` : 'tacca'}
              />
            ))}
          </div>
          <p className="nota">{descrizioneForza(nuova, forza)}</p>
        </div>

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

        {/* La spunta si aggiorna mentre si scrive, e c'e' sempre: comparire
            solo quando qualcosa manca direbbe le regole dopo che si e' gia'
            sbagliato. */}
        <ul className="requisiti">
          {requisiti.map((requisito) => (
            <li
              key={requisito.id}
              className={requisito.soddisfatto ? 'requisito-ok' : undefined}
            >
              {/* Il segno e' decorativo: quello che conta lo dice il testo,
                  e un lettore di schermo che legge «spunta» a ogni riga
                  aggiunge rumore, non informazione. */}
              <span className="requisito-segno" aria-hidden="true">
                {requisito.soddisfatto ? '✓' : '·'}
              </span>
              {requisito.etichetta}
            </li>
          ))}
        </ul>

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

        <div className="azioni azioni-password">
          {/* «Annulla» non naviga da nessuna parte: svuota i tre campi. In una
              pagina che non ha altro da fare, uscire dal modulo vuol dire
              lasciarlo come lo si è trovato. */}
          <button type="button" className="secondario" onClick={svuota} disabled={inCorso}>
            Annulla
          </button>
          <button type="submit" disabled={!puoInviare || inCorso}>
            {inCorso ? 'Salvo…' : 'Aggiorna password'}
          </button>
        </div>
      </form>
    </section>
  )
}

/** Che cosa dice la riga sotto le tacche, a seconda di quanto manca. */
function descrizioneForza(nuova: string, forza: number): string {
  if (nuova === '') {
    return 'Inserisci una nuova password'
  }
  if (forza <= 1) {
    return 'Troppo debole'
  }
  if (forza === 2) {
    return 'Ancora debole'
  }
  if (forza === 3) {
    return 'Quasi: manca un requisito'
  }
  return 'Va bene'
}

/**
 * Un campo password con il suo errore sotto.
 *
 * Un componente e non tre blocchi copiati: sono tre campi identici in tutto
 * tranne l'etichetta, e tre copie dello stesso markup sono tre occasioni di
 * cambiarne due su tre.
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
  return (
    <div className="campo">
      <label htmlFor={id}>{etichetta}</label>
      <input
        id={id}
        type="password"
        className="campo-password"
        value={valore}
        onChange={(evento) => onCambia(evento.target.value)}
        autoComplete={autoComplete}
        required
      />
      {errore !== null && (
        <p className="errore" role="alert">
          {errore}
        </p>
      )}
    </div>
  )
}
