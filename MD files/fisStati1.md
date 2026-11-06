# Fix: stati, annullamento, utenti e assenze

Correzioni da applicare al gestionale, in aggiunta a `sezione-dipendente.md`. SQL in sintassi PostgreSQL; i nomi di tabelle e colonne vanno adattati a quelli reali.

| Richiesta | Sezione |
|---|---|
| Un lavoro annullato viene tolto dalla vista dei lavori | 2 |
| Un lavoro annullato non appare più nel calendario dei dipendenti | 2 |
| Annullando un lavoro, tutte le sue task diventano `annullato` | 2 |
| Completati in verde, annullati in rosso | 3 |
| In registrazione si scelgono solo task attive o in corso | 4 |
| Attivazione e disattivazione degli utenti | 5 |
| Un form per ogni tipologia di richiesta di ferie o permessi | 6 |

---

## 1. Stati

Lavori e task usano gli stessi quattro stati.

| Stato | Significato |
|---|---|
| `attivo` | creato, non ancora iniziato |
| `in_corso` | iniziato |
| `completato` | concluso |
| `annullato` | interrotto; stato finale, in sola lettura |

Questi stati sostituiscono `da_fare` e `completata` di `sezione-dipendente.md`.

```sql
ALTER TABLE task DROP CONSTRAINT IF EXISTS task_stato_check;
UPDATE task SET stato = 'attivo'     WHERE stato = 'da_fare';
UPDATE task SET stato = 'completato' WHERE stato = 'completata';
ALTER TABLE task
  ALTER COLUMN stato SET DEFAULT 'attivo',
  ADD CONSTRAINT task_stato_check
    CHECK (stato IN ('attivo', 'in_corso', 'completato', 'annullato'));

ALTER TABLE lavori ADD COLUMN annullato_il TIMESTAMPTZ NULL;
```

`lavori.stato` va portato sugli stessi quattro valori, con lo stesso vincolo. Se oggi usa valori diversi, vanno mappati come sopra.

## 2. Annullamento di un lavoro

`POST /lavori/:id/annulla`, consentito agli stessi ruoli che oggi possono modificare il lavoro. Tutto in un'unica transazione:

```sql
UPDATE lavori SET stato = 'annullato', annullato_il = now() WHERE id = :id;

UPDATE task SET stato = 'annullato' WHERE lavoro_id = :id;   -- tutte, anche le completate

DELETE FROM allocazioni WHERE lavoro_id = :id AND data_inizio > current_date;
UPDATE allocazioni SET data_fine = current_date
WHERE lavoro_id = :id AND data_fine > current_date;
```

Dopo l'annullamento:

- il lavoro e le sue task sono in sola lettura e non si possono riaprire;
- le registrazioni già fatte restano nello storico e nei report;
- il lavoro non appare più nel calendario dei dipendenti.

**Frontend.** Le liste dei lavori, sia quella dell'admin sia "I miei lavori" del dipendente, non mostrano i lavori annullati. Un interruttore "Mostra annullati" li fa ricomparire in fondo alla lista, in rosso, per consultarli. Le task annullate restano visibili nel dettaglio del lavoro e in "Le mie task", sotto quelle aperte e in rosso.

**Calendario.** `/me/calendario` esclude i lavori annullati e le scadenze delle loro task, anche nei giorni passati. Le ore già registrate su quei lavori restano nel totale del giorno, perché sono lavoro svolto davvero. I giorni coperti solo da un lavoro annullato non vengono segnalati come giorni da completare.

**API.** `GET /lavori` e `GET /me/lavori` escludono gli annullati, salvo `?includi_annullati=true`.

## 3. Colori

| Stato | Chiaro | Scuro |
|---|---|---|
| `attivo` | `#6E6E73` | `#98989D` |
| `in_corso` | `#0071E3` | `#0A84FF` |
| `completato` | `#34C759` | `#30D158` |
| `annullato` | `#FF3B30` | `#FF453A` |

Stessa linea minimal della sezione dipendente: nessuna riga o scheda colorata per intero. Il colore va su:

- un badge a pillola con fondo al 12–15% del colore e testo pieno ("Completato", "Annullato");
- un punto accanto al titolo nelle liste;
- gli eventi del calendario.

L'etichetta di testo c'è sempre, così lo stato si capisce anche senza distinguere i colori.

## 4. Registrazione delle ore

Nel form di registrazione:

- il selettore delle task mostra solo task `attivo` o `in_corso`, caricate da `GET /me/task?stato=attivo,in_corso`;
- il selettore dei lavori mostra solo lavori `attivo` o `in_corso`.

Il server applica la stessa regola:

| Caso | Esito |
|---|---|
| Task `completato` o `annullato` | `422 TASK_NON_SELEZIONABILE` |
| Lavoro `completato` o `annullato` | `422 LAVORO_CHIUSO` |

La regola vale per le nuove registrazioni e quando si cambia la task o il lavoro di una registrazione esistente. Le bozze già salvate su task chiuse nel frattempo si possono ancora inviare, perché descrivono lavoro svolto davvero.

## 5. Attivazione e disattivazione degli utenti

```sql
ALTER TABLE dipendenti
  ADD COLUMN attivo         BOOLEAN NOT NULL DEFAULT true,
  ADD COLUMN disattivato_il TIMESTAMPTZ NULL;
```

`PATCH /utenti/:id` con `{ "attivo": false }` per disattivare e `{ "attivo": true }` per riattivare. Solo admin; un admin non può disattivare se stesso.

Un utente disattivato:

- **resta visibile nel frontend**: nella lista Utenti con il badge grigio "Disattivato" e un interruttore per riattivarlo, e con il suo nome nello storico di lavori, task e registrazioni;
- **non può accedere**: il login verifica prima le credenziali e, solo se sono corrette, risponde `403 UTENTE_DISATTIVATO` con il messaggio "Account disattivato. Contatta l'amministratore." Così chi non conosce la password non scopre quali account esistono;
- **perde subito le sessioni aperte**: il backend controlla `attivo` a ogni richiesta autenticata, perché un token già emesso resterebbe altrimenti valido fino alla scadenza;
- **non compare nei selettori di assegnazione** di lavori e task; le assegnazioni esistenti restano.

## 6. Ferie e permessi

Ogni tipologia di richiesta ha il suo form. Le richieste le approva o le rifiuta l'admin.

### Tipologie e form

| Tipologia | Campi del form |
|---|---|
| **Ferie** | dal, al (giornate intere), note facoltative. Il form mostra i giorni lavorativi calcolati. |
| **Permesso orario** | giorno, dalle, alle (a passi di 15 minuti), motivo facoltativo. Il form mostra la durata calcolata. |
| **Permesso retribuito** | motivo (lutto, matrimonio, legge 104, donazione di sangue, visita medica, altro), giornata intera oppure fascia oraria, date o orari di conseguenza, note obbligatorie se il motivo è "altro". |

### Dati

```sql
CREATE TABLE richieste_assenza (
  id            BIGSERIAL PRIMARY KEY,
  dipendente_id BIGINT NOT NULL REFERENCES dipendenti(id),
  tipo          TEXT NOT NULL CHECK (tipo IN ('ferie', 'permesso_orario', 'permesso_retribuito')),
  data_inizio   DATE NOT NULL,
  data_fine     DATE NOT NULL,
  ora_inizio    TIME NULL,                    -- NULL = giornate intere
  ora_fine      TIME NULL,
  dettagli      JSONB NOT NULL DEFAULT '{}',  -- campi propri della tipologia (motivo, note)
  stato         TEXT NOT NULL DEFAULT 'in_attesa'
                  CHECK (stato IN ('in_attesa', 'approvata', 'rifiutata', 'ritirata')),
  esito_da      BIGINT NULL REFERENCES dipendenti(id),
  esito_il      TIMESTAMPTZ NULL,
  esito_nota    TEXT NULL,
  creata_il     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (data_fine >= data_inizio),
  CHECK ((ora_inizio IS NULL) = (ora_fine IS NULL)),
  CHECK (ora_inizio IS NULL OR (data_inizio = data_fine AND ora_fine > ora_inizio))
);
CREATE INDEX ON richieste_assenza (dipendente_id, data_inizio, data_fine);
```

I campi comuni a tutte le tipologie (date e orari) sono colonne. Quelli propri di ciascuna stanno in `dettagli`, che il server valida in base a `tipo`.

### Regole

| Regola | Esito |
|---|---|
| Campi obbligatori della tipologia mancanti | `422 CAMPI_MANCANTI` |
| Date nel passato | `422 DATA_PASSATA` |
| Sovrapposizione con un'altra richiesta in attesa o approvata | `409 RICHIESTA_SOVRAPPOSTA` |
| Il dipendente modifica o ritira solo richieste `in_attesa` | `409 RICHIESTA_CHIUSA` |
| Solo l'admin approva o rifiuta; il rifiuto richiede una nota | `403` / `422 NOTA_OBBLIGATORIA` |
| Registrazione ore in un giorno di assenza approvata | avviso, salvataggio consentito |

### API

| Metodo | Percorso | Uso |
|---|---|---|
| `GET` | `/me/richieste` | le mie richieste |
| `POST` | `/me/richieste` | nuova richiesta; `tipo` decide quali campi sono obbligatori |
| `PUT` | `/me/richieste/:id` | modifica, solo `in_attesa` |
| `POST` | `/me/richieste/:id/ritira` | ritiro, solo `in_attesa` |
| `GET` | `/richieste?stato=&dipendente_id=` | admin: tutte le richieste |
| `POST` | `/richieste/:id/approva` | admin |
| `POST` | `/richieste/:id/rifiuta` | admin, con nota obbligatoria |

### Frontend

**Dipendente.** Nuova voce "Assenze" nella barra laterale, con l'elenco delle proprie richieste e il loro stato. "Nuova richiesta" apre il pannello: prima si sceglie la tipologia da una lista raggruppata con le tre voci, poi compare il form di quella tipologia. In fondo al form c'è il riepilogo calcolato ("3 giorni lavorativi", "2 h 30 min") e il pulsante "Invia richiesta".

**Admin.** Pagina "Richieste" con quelle in attesa in cima. Approvazione e rifiuto direttamente dalla riga, con nota obbligatoria per il rifiuto.

**Stati.** Badge come nella sezione 3: in attesa arancione (`#FF9500`, scuro `#FF9F0A`), approvata verde, rifiutata rossa, ritirata grigia.

**Calendario.** Nuovo tipo di evento `assenza`:

- le richieste approvate compaiono in viola (`#AF52DE`, scuro `#BF5AF2`);
- quelle in attesa nello stesso colore più tenue, con l'etichetta "In attesa";
- quelle rifiutate e ritirate non compaiono.

I giorni coperti per intero da un'assenza approvata non vengono segnalati come giorni da completare.

**Notifiche.** Se il sistema di notifiche è già presente, una nuova richiesta genera una notifica all'admin e l'esito una notifica al dipendente.

**Non incluso:** saldo di ferie e permessi residui, malattia, allegati.

## 7. Consegna

- Non scrivere né eseguire test di alcun tipo: unitari, di integrazione, end-to-end o verifiche manuali.
- Completa tutto quanto descritto in questo documento e consegna il lavoro finito. La valutazione la faccio io.
- Dove un dettaglio non è specificato, scegli la soluzione più semplice coerente con il documento e prosegui senza fermarti.
- Da consegnare: migrazioni, modifiche agli endpoint, modifiche al frontend e una breve nota con le scelte fatte.
