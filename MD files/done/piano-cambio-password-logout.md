# Piano: cambio password e conferma logout

## Ambito

Le modifiche riguardano **solo frontend (FE) e backend (BE)**.

I test (unitari, integrazione, e2e) **non vengono toccati in questa fase** e saranno aggiornati in seguito. Alcuni test esistenti potrebbero fallire finché non vengono aggiornati, ad esempio quelli e2e che cliccano su logout e ora troveranno la finestra di conferma.

---

## GOAL 1: Cambio password

Ogni utente può cambiare la propria password inserendo la password attuale.

### Regole della nuova password

> Nota: "carattere alfanumerico" è interpretato come *carattere speciale* (non alfanumerico), dato che un numero è già alfanumerico. Da confermare.

- Almeno 8 caratteri
- Almeno 1 numero
- Almeno 1 carattere speciale (es. `!`, `@`, `#`)
- Diversa dalla password attuale

Regex di riferimento:

```
^(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$
```

### Backend

#### Endpoint
Endpoint autenticato, ad esempio `POST /api/account/change-password`, con body:

```json
{
  "oldPassword": "string",
  "newPassword": "string"
}
```

L'utente si ricava dalla sessione o dal token, **mai dal body**, così nessuno può cambiare la password di un altro utente.

#### Flusso
1. Verificare la password attuale confrontandola con l'hash salvato.
2. Validare la nuova password con le regole sopra. Il BE è la fonte di verità, indipendentemente dai controlli del FE.
3. Controllare che la nuova password sia diversa dalla vecchia.
4. Calcolare l'hash della nuova password (stesso algoritmo già in uso, es. bcrypt) e salvarlo.
5. Opzionale ma consigliato: invalidare le altre sessioni attive dell'utente.
6. Registrare l'evento nell'audit log, senza mai loggare le password.

#### Errori
- Usare 400 o 422 con un codice specifico:
  - `WRONG_OLD_PASSWORD`: password attuale errata
  - `WEAK_PASSWORD`: la nuova password non rispetta le regole
  - `SAME_PASSWORD`: la nuova password coincide con la vecchia
- Evitare 401 per la password vecchia errata: molti interceptor del FE lo interpretano come sessione scaduta e fanno logout automatico.

#### Sicurezza
- **Rate limiting:** limitare i tentativi con password vecchia errata.
- **Validatore:** mettere la regola in una funzione dedicata e riusabile.

### Frontend

#### Sezione
Sezione "Cambia password" nel profilo utente, con tre campi:
- Password attuale
- Nuova password
- Conferma nuova password

#### Requisiti visibili
Una checklist che si aggiorna mentre l'utente scrive:
- ✓ Almeno 8 caratteri
- ✓ Almeno un numero
- ✓ Almeno un carattere speciale

#### Validazione
- Stesse regole del BE.
- Controllo che "nuova password" e "conferma" coincidano.
- Pulsante di invio disabilitato finché il form non è valido.

#### Usabilità
Un toggle per mostrare o nascondere la password in ogni campo.

#### Gestione risposte
- **Errore:** il messaggio va sotto il campo giusto (es. "Password attuale non corretta").
- **Successo:** si mostra una conferma e si svuota il form.

---

## GOAL 2: Conferma prima del logout

### Frontend

#### Finestra di conferma
Al click su "Esci" si apre una modale **"Sei sicuro di voler uscire?"** con i pulsanti:
- **Annulla**
- **Esci**

#### Comportamento
- "Esci" avvia il flusso di logout già esistente.
- "Annulla", il tasto Esc o un click fuori dalla modale la chiudono senza fare nulla.

#### Componenti
Usare il componente modale già presente nel progetto, se c'è.

#### Punti di accesso
Applicare la conferma a tutti i punti da cui si fa logout (menu utente, sidebar, versione mobile). Conviene centralizzare la logica in un'unica funzione.

### Backend

Nessuna modifica: l'endpoint di logout resta invariato.

---

## Ordine di lavoro

1. **BE:** validatore password ed endpoint di cambio password.
2. **FE:** form di cambio password collegato all'endpoint.
3. **FE:** modale di conferma logout.
4. Verifica manuale dei criteri di accettazione.

---

## Criteri di accettazione

- [ ] Con password attuale errata, il cambio viene rifiutato con un messaggio chiaro.
- [ ] Una password senza numero, senza carattere speciale o sotto gli 8 caratteri viene rifiutata sia dal FE sia dal BE.
- [ ] Con dati validi la password viene aggiornata e si accede con quella nuova.
- [ ] Il click su logout mostra sempre la conferma.
- [ ] "Annulla" mantiene la sessione attiva.
- [ ] "Esci" chiude la sessione.
- [ ] Nessun file di test modificato.

---

## Fuori ambito (da fare in seguito)

- Aggiornamento dei test unitari, di integrazione ed e2e per coprire:
  - il nuovo endpoint di cambio password e il validatore;
  - il form di cambio password;
  - la modale di conferma logout (inclusi i test e2e esistenti sul logout).
