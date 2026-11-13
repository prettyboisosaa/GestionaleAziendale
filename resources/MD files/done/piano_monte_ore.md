# Gestione monte ore — piano di implementazione

## context

Siamo ingegneri software su un gestionale interno per l'assegnazione di lavori e task ai dipendenti. Al momento possiamo creare progetti con nome, descrizione, data inizio/fine prevista e un responsabile, e possiamo assegnare i dipendenti. Ora vogliamo aggiungere la gestione del monte ore.

## goal

Ogni progetto ha un monte ore che si divide tra le sue task. I dipendenti registrano ogni giorno le ore lavorate. Se una task richiede più ore del previsto, c'è un percorso chiaro per ottenerle, e ogni passaggio resta tracciato nel sistema.

## ruoli

| ruolo | cosa può fare |
|---|---|
| admin | crea i progetti, fissa il monte ore, è l'unico che può modificarlo, gestisce le richieste di ore |
| pm / responsabile | crea le task del suo progetto, stima e alloca le ore, apre richieste di ore all'admin |
| dipendente | registra ogni giorno su quali task ha lavorato, per quante ore e cosa ha fatto |

## regole

### progetto

- Il monte ore viene fissato dall'admin alla creazione del progetto.
- Solo l'admin può modificarlo, e mai sotto la somma delle ore già allocate alle task.
- Ogni modifica al monte ore viene registrata come movimento. Il valore iniziale non si perde, così a fine progetto possiamo confrontare stima e consuntivo.
- Ore libere del progetto = `monte_ore − somma(task.ore_allocate)`. Vanno calcolate sempre al volo, non salvate in un campo.

### task

- Ogni task ha due valori distinti: **ore stimate** (quante ne servono secondo il pm) e **ore allocate** (quante sono coperte dal budget).
- La somma delle ore allocate di tutte le task non può superare il monte ore del progetto.
- Le ore **non si prendono mai da altre task aperte**.
- Le task non si cancellano: si chiudono o si annullano. Le ore non consumate tornano nel pool del progetto e le registrazioni restano.

### registrazione ore

- Un dipendente registra ore solo sulle task aperte a cui è assegnato.
- Massimo 8 ore al giorno per dipendente.
- Ogni registrazione contiene ore e descrizione del lavoro svolto.
- Registrare ore oltre l'allocato è permesso, ma la task viene segnata **in sforamento**.

### allocazione

- Ogni allocazione avviene in una transazione con lock sulla riga del progetto (`SELECT ... FOR UPDATE`), per evitare che due operazioni allochino le stesse ore libere.
- Ogni allocazione genera un movimento con autore, data e motivo.

## workflow

1. L'admin crea il progetto con date e monte ore.
2. Il pm crea le task con le ore stimate e alloca le ore dal pool.
3. Ogni giorno il dipendente registra ore e descrizione sulle sue task.
4. Se una task sfora o servirà più tempo, il dipendente lo comunica a voce al pm.
5. Il pm alloca le ore dal pool. Se non bastano, apre una richiesta ore all'admin.
6. L'admin valuta la richiesta vedendo la situazione del progetto, poi approva, approva in parte o rifiuta.
7. Alla chiusura di una task, le ore non usate tornano nel pool.

## edge case

### un dipendente registra le ore della giornata

```
registra_ore(dipendente, task, data, ore, descrizione):
    if task.stato != aperta or dipendente non assegnato a task:
        rifiuta
    if somma_ore_giorno(dipendente, data) + ore > 8:
        rifiuta
    salva registrazione
    if task.ore_consumate > task.ore_allocate:
        segna task in sforamento    // il pm la vede subito
```

### un dipendente necessita di più ore per completare una task

```
ore_aggiuntive(task, ore_necessarie):    // richiesta fatta a voce al pm
    libere = progetto.monte_ore - somma(task.ore_allocate)
    if libere >= ore_necessarie:
        pm alloca dal pool alla task
    else:
        pm alloca le ore libere rimaste (se ce ne sono)
        pm apre richiesta_ore all'admin per la differenza (precompilata)
        // la task resta in sforamento, il dipendente continua a registrare
        // mai prendere ore da altre task aperte
```

### l'admin gestisce una richiesta

La schermata della richiesta mostra all'admin: monte ore attuale e iniziale, ore allocate, ore consumate, task in sforamento, storico delle richieste del progetto.

```
gestisci_richiesta(richiesta, ore_concesse):
    if ore_concesse == 0:
        stato = rifiutata
    else:
        in transazione con lock sul progetto:
            aumenta monte ore (movimento collegato alla richiesta)
            if richiesta.task: alloca direttamente alla task
            else: le ore restano nel pool
        stato = approvata oppure approvata_parziale
    notifica il pm
```

### l'admin modifica direttamente il monte ore

```
modifica_monte_ore(progetto, nuovo_valore, motivo):
    if nuovo_valore < somma(task.ore_allocate):
        rifiuta    // prima vanno chiuse o ridotte delle task
    registra movimento (modifica_monte, differenza, motivo)
```

### una task viene chiusa o annullata

```
chiudi_task(task):
    residuo = task.ore_allocate - task.ore_consumate
    if residuo > 0:
        rilascia residuo nel pool (movimento rilascio)
    // se il residuo è negativo la task ha sforato: resta registrato così
    // le registrazioni ore non vengono mai cancellate
```

## modello dati

```sql
progetto(id, nome, descrizione, data_inizio, data_fine, responsabile_id, monte_ore)

task(id, progetto_id, nome, ore_stimate, ore_allocate, stato)
    -- stato: aperta / chiusa / annullata

assegnazione(task_id, dipendente_id)

registro_ore(id, task_id, dipendente_id, data, ore, descrizione)

richiesta_ore(id, progetto_id, task_id NULL, richiedente_id,
              ore_richieste, ore_concesse NULL, motivo,
              stato, gestita_da NULL, gestita_il NULL, creata_il)
    -- stato: in_attesa / approvata / approvata_parziale / rifiutata

movimento_ore(id, progetto_id, task_id NULL, richiesta_id NULL,
              ore, tipo, motivo, autore_id, creato_il)
    -- tipo: monte_iniziale / modifica_monte / allocazione / rilascio
```

Note:

- Le ore vanno salvate come `DECIMAL(4,2)` oppure in minuti interi, mai come float.
- `ore_consumate` di una task si calcola come `SUM(registro_ore.ore)` sulla task.
- Il vincolo delle 8 ore si controlla sommando `registro_ore` per dipendente e data, nella stessa transazione dell'inserimento.
- Il monte ore iniziale si ricava dal movimento di tipo `monte_iniziale`.

## notifiche

- L'admin riceve una notifica (badge o email) per ogni nuova richiesta di ore.
- Promemoria all'admin se una richiesta resta in attesa per più di N giorni.
- Il pm riceve una notifica quando una sua richiesta viene gestita.
- Il pm vede in evidenza le task in sforamento del suo progetto.

## fix applicate rispetto al piano iniziale

| problema del piano iniziale | fix |
|---|---|
| prendere ore da altre task aperte spostava lo sforamento e lo nascondeva | non si prendono mai ore da task aperte. Le ore tornano nel pool solo da task chiuse o annullate |
| nessun percorso se il pool non bastava | richiesta ore all'admin, che può aumentare il monte ore |
| caso "pool insufficiente ma non vuoto" non gestito | il pm alloca le ore libere rimaste e chiede all'admin solo la differenza |
| richieste tramite messaggi liberi, scollegate dall'azione | `richiesta_ore` strutturata con stato. L'approvazione aggiorna il monte ore in automatico |
| l'admin decideva senza contesto | la schermata della richiesta mostra lo stato completo del progetto |
| sovrascrivere il monte ore cancellava la stima iniziale | ogni modifica è un movimento, e il valore iniziale resta |
| stima e allocazione confuse in un unico numero | `ore_stimate` e `ore_allocate` separate. La differenza precompila la richiesta |
| le ore concesse potevano finire su un'altra task | se la richiesta è legata a una task, le ore vanno direttamente lì |
| registrazioni bloccate oltre l'allocato | la registrazione è accettata e la task viene segnata in sforamento |
| ore residue bloccate nelle task completate | alla chiusura il residuo torna nel pool |
| cancellazione di task con ore registrate | stato `annullata`, nessuna cancellazione fisica |
| doppie allocazioni concorrenti dal pool | transazione con lock sulla riga del progetto |

## da decidere

- Le 8 ore sono un tetto rigido, oppure servono straordinari o un limite diverso per i part-time? Nel secondo caso il limite diventa un parametro per dipendente.
- Ogni progetto ha un solo pm o possono essercene di più? Con più pm conviene legare sempre le ore concesse a una task.
- Se l'admin è assente, chi può approvare le richieste al suo posto?
- Il dipendente può registrare ore su giorni passati? Se sì, entro quanti giorni?
