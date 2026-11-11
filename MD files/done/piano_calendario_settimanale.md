# Il calendario settimanale del tempo lavorato

## Il problema

La dashboard ha un solo calendario, e guarda avanti. `PaginaDashboard` disegna le
task per scadenza, le assenze approvate e le festività come eventi a giornata
intera, alimentati da `GET /api/mio-calendario`. Risponde alla domanda «che cosa
devo fare», e la risponde bene.

Nessuna schermata risponde a «che cosa ho fatto».

Il dato per rispondere c'è già, ed è buono: dalla V15 ogni avvia/pausa/completa
scrive una riga di `sessione_task` con i due istanti, quindi il sistema sa non
solo quante ore una persona ha lavorato, ma **in quali ore del giorno**. Di tutta
quell'informazione il dipendente vede una riga di testo — «Oggi hai registrato
3h 20m» — e l'avviso delle chiusure d'ufficio. La collocazione nella giornata non
la vede nessuno.

C'è poi un buco più concreto. Il timer misura solo se lo si preme, e chi si
dimentica non ha modo di rimediare: la V17 ha eliminato la dichiarazione
giornaliera, e la sola scrittura manuale rimasta è la correzione di una sessione
che **esiste già** (`PUT /api/mio-tempo/sessioni/{id}`), limitata alla giornata in
corso. Chi giovedì ha lavorato senza premere avvia, venerdì non può più dirlo.

Non si torna alla dichiarazione: quella era un adempimento serale su una
ricostruzione a memoria, e toglierla era il senso del cambiamento. Ma una rete di
sicurezza serve, e il posto naturale per metterla è lo stesso dove si legge il
consuntivo — un calendario a fasce orarie che mostra le sessioni dove sono
cadute, e che si può riempire cliccando una zona vuota.

**Il timer resta la strada normale. Il calendario è la rete.** È una differenza
che l'interfaccia deve far vedere, e la fa vedere con l'origine della sessione.

## Che cosa si vede

Una griglia settimanale **da lunedì a domenica**, con le ore **dalle 07:00 alle
21:00**, affiancata nella dashboard al calendario delle scadenze.

Le ore visibili sono più larghe dell'orario di lavoro di proposito. L'orario è
9–18, ma chi comincia alle sette e mezza o finisce alle otto di sera deve poter
vedere e scrivere quelle ore: una griglia che si fermasse alle 18 direbbe che
quel lavoro non è esistito. La fascia 9–18 è **evidenziata** invece che essere
l'unica disegnata — si vede dov'è l'orario senza che il resto diventi
inaccessibile.

Il sabato e la domenica ci sono per la stessa ragione. Non è un invito a
lavorarci: è che se è accaduto, il consuntivo del lavoro lo deve sapere.

### I colori

**Una tinta per lavoro**, non per task. Le task dello stesso lavoro condividono il
colore, e la domanda a cui la settimana risponde a colpo d'occhio diventa «su
quali progetti è andata questa settimana» — che è la domanda utile. Una tinta per
task darebbe un arcobaleno in cui due task dello stesso lavoro sembrano cose
lontane.

La tinta si ricava **dall'id del lavoro**, non dall'ordine in cui le sessioni
arrivano: così il lavoro che è verde questa settimana è verde anche la prossima, e
la memoria visiva serve a qualcosa. Otto tinte che si ripetono; con più di otto
lavori nella stessa settimana due possono coincidere, e si accetta — il codice del
lavoro è scritto dentro il blocco.

Il **tratteggio** dice l'origine: una sessione misurata dal timer è piena, una
scritta a mano o dedotta dal sistema è tratteggiata. È la stessa distinzione che
`OrigineSessione` esiste per custodire, e qui diventa visibile: guardando la
settimana si capisce quanta di quella griglia è misura e quanta è ricordo.

### Che cosa si può fare

- **Clic o trascinamento su una zona vuota** → si apre il pannello, già compilato
  con l'intervallo scelto; si seleziona la task e si salva.
- **Clic su una sessione esistente** → lo stesso pannello, con gli orari e la task
  modificabili, più «Annulla sessione».

Niente trascina-e-rilascia sulle sessioni già scritte. Sarebbe comodo, ma un clic
accidentale che riscrive ore di lavoro è un danno silenzioso, e il pannello
costa un clic in più in cambio della conferma.

## Le regole, e perché sono queste

### Si scrive dentro la settimana corrente

Da lunedì a domenica della settimana in corso. Prima di quel lunedì il tempo è
chiuso.

La finestra della giornata era giusta quando l'unica scrittura manuale era la
correzione di una misura appena presa: ci si accorge subito di aver lasciato
scorrere il timer. Qui lo scopo è diverso — recuperare un giorno che non è stato
registrato — e un giorno di finestra lo manca per definizione, perché di solito
se ne accorge il venerdì.

Non si va oltre, invece, perché le ore di una persona non sono un diario
personale: entrano nel consuntivo di un lavoro, su cui qualcuno decide. Una
settimana è il periodo che si chiude e si guarda; lasciar riscrivere un mese dopo
vorrebbe dire che nessun consuntivo è mai fermo.

La stessa finestra vale per correzione e annullamento: cliccare il martedì sul
calendario e sistemarlo il venerdì è esattamente il caso d'uso, e due finestre
diverse sulla stessa griglia sarebbero inspiegabili. Resta l'eccezione che c'era:
una **chiusura d'ufficio da confermare** si corregge sempre, perché quel numero
l'ha dedotto il sistema e chi c'era lo deve poter sistemare anche dopo.

Il server dice al frontend se la settimana è modificabile, invece di lasciargli
ricalcolare la regola: una griglia che si apre per ricevere un 409 è una griglia
che ha mentito.

### Le ore non si sovrappongono

Due sessioni della stessa persona non possono coprire lo stesso istante.

Era già vero, ma per caso. Il vincolo `uk_sessione_in_corso` garantisce una sola
sessione **aperta** per persona, e finora bastava: scriveva solo il timer, e il
timer chiude la sessione precedente quando ne apre un'altra. Delle sessioni
**chiuse** nessuno si è dovuto occupare, perché non c'era modo di crearne due
accavallate.

La scrittura manuale quel modo lo introduce, e romperebbe la proprietà su cui
`ServizioTempo` dichiara di poggiare tutto il resto: che le ore di una persona si
possano sommare senza chiedersi quale sia quella vera. Due ore scritte due volte
diventerebbero quattro nel consuntivo di un lavoro.

Quindi un inserimento che si sovrappone viene **respinto**, e il messaggio
**nomina la task e gli orari in conflitto**: «sovrapposto» senza dire a cosa è un
errore su cui non si può agire. Il controllo vale anche per la correzione, che
oggi potrebbe spostare una sessione sopra un'altra.

Il controllo sta nel servizio e non in un vincolo del database: un CHECK non
guarda le altre righe, e il vincolo di esclusione che servirebbe MySQL non lo ha.
È una differenza dichiarata rispetto a `uk_sessione_in_corso`, che invece è nel
database proprio perché due clic contemporanei passerebbero sotto un controllo
applicativo. Qui il rischio concreto — la stessa persona che inserisce due
sessioni accavallate nello stesso istante da due finestre — vale la semplicità.

### Si scrive solo sulle proprie task

Serve un'**assegnazione attiva** alla task, e non basta poterla gestire.

È la regola che `avvia` già applica, e per la ragione scritta là: il tempo è di
chi l'ha lavorato, e scriverlo su una task di un collega vorrebbe dire mettere le
proprie ore nel consuntivo del suo lavoro. Nemmeno un amministratore scrive il
tempo di qualcun altro — e per lo stesso motivo non esiste la correzione da parte
dell'amministratore: quando una sessione non torna, la strada è chiederlo alla
persona.

Un responsabile che davvero ci lavora ha già la sua strada: assegnarsi la task.

### Niente tempo nel futuro, e la durata non si manda

Una sessione che finisce dopo adesso viene respinta: non è un ricordo, è un
proposito.

Il pannello manda **due istanti e nient'altro**. La durata la ricava il database
dalla differenza, come fa per ogni sessione: mandare anche i minuti vorrebbe dire
poter mandare tre numeri in disaccordo, e poi scegliere a quale credere.

### Scrivere una sessione mette la task in corso

Se la task era ancora `ATTIVO`, passa a `IN_CORSO`. Averci lavorato *è* averla
messa in corso, e chiedere di dirlo anche con la tendina sarebbe l'adempimento
che questo cambiamento ha tolto. È quello che fa già l'avvio del timer.

## Il modello dei dati

Nessuna tabella nuova, nessuna colonna nuova, nessun totale salvato. Una sessione
scritta a mano è una riga di `sessione_task` come tutte le altre, e i conti la
leggono senza sapere da dove viene — è il principio di sempre: si salva la storia,
i totali si calcolano.

Cambia una cosa sola: **`origine` ha un quarto valore, `MANUALE`**.

```
TIMER       il dipendente ha premuto avvia e poi pausa o completa
AUTOMATICA  chiusa d'ufficio perché aperta oltre il tetto
IMPORTATA   convertita da una vecchia voce di dichiarazione (V16)
MANUALE     scritta a mano sul calendario settimanale
```

Non si riusa `TIMER`, che significa «misurata», e non si riusa `IMPORTATA`, che
significa «storia di un altro modello». L'enum esiste per non confondere quello
che una persona ha misurato con quello che il sistema ha dedotto, e un ricordo
dichiarato è una terza qualità di dato: l'interfaccia la mostra in modo diverso, e
chi legge un consuntivo ha diritto di sapere quanta parte è misura.

Una sessione manuale nasce **confermata**: l'ha scritta la persona, non c'è niente
da far confermare a nessuno. Quindi `ck_sessione_conferma` — `confermata = TRUE OR
origine = 'AUTOMATICA'` — resta valido così com'è, e `AUTOMATICA` resta l'unica
origine che può stare in attesa.

`uk_sessione_in_corso` non entra in gioco: una sessione manuale ha sempre
`chiusa_il`, quindi la sua colonna generata `in_corso` è NULL.

Aggiungere un valore vuole una **migrazione**, perché `ck_sessione_origine`
rifiuterebbe la scrittura. Il CHECK non si modifica: si elimina e si rifà con la
lista copiata dalla V15.

## Le rotte

Due, entrambe su `TASK_AGGIORNA_STATO`.

```
GET  /api/mio-tempo/settimana?giorno=YYYY-MM-DD
POST /api/lavori/{idLavoro}/task/{idTask}/sessioni
```

**Nessun permesso nuovo.** `TASK_AGGIORNA_STATO` è nel pavimento e dice già
«aggiornare lo stato delle task a cui è assegnato»: registrare il proprio tempo è
quello, come lo sono avvia, pausa e completa. Un permesso che si concederebbe
sempre insieme a un altro è un permesso che non esiste. Il confine vero non è
nell'annotazione ma nei dati, e lo verifica il servizio.

La scrittura sta sotto `/api/lavori/{l}/task/{t}/` perché **nomina una task**, come
`avvia` e `completa`; `/api/mio-tempo/...` resta per ciò che riguarda la sessione
corrente, dove la task non serve dirla perché ce n'è una sola.

La lettura è una rotta nuova e non un riuso di `GET /api/tempo/utente/{id}`, che
pure restituirebbe le stesse sessioni: quella chiede
`TEMPO_VISUALIZZA_TUTTI`/`_LAVORO`, permessi che un dipendente non ha e non deve
avere per guardare le proprie ore. Aggiungerci un'eccezione «oppure sono io»
metterebbe due significati in una rotta sola.

La risposta della lettura porta la settimana, il totale dei minuti, le sessioni e
il flag `modificabile`. La scrittura risponde con la **situazione intera** di chi
chiede, come ogni scrittura del tempo: è quello che la barra in testata e la
dashboard ridisegnano, e averlo nella risposta evita una seconda chiamata dopo
ogni clic.

La tendina delle task **non chiede una rotta**: le task assegnate e aperte sono
già in `GET /api/mia-dashboard`, che la pagina carica comunque.

## Quello che si è deciso di non fare

- **Trascina e ridimensiona** sulle sessioni esistenti. Comodo, ma un clic
  accidentale che riscrive le ore è un danno che non si vede.
- **Settimane passate.** Si veda la finestra: un consuntivo deve potersi fermare.
- **Correzione da parte dell'amministratore.** Le ore sono di chi le ha lavorate.
  Quando un numero non torna si chiede alla persona; quello che l'amministratore
  ha è l'elenco di chi ha sessioni in attesa.
- **Una pagina del tempo.** Il calendario sta nella dashboard, dove il dipendente
  passa comunque. Una schermata in più da visitare sarebbe l'adempimento tornato
  dalla finestra.
- **Notifiche** a chi ha una settimana vuota. In questo gestionale non ci sono
  notifiche: ciò che una notifica annuncerebbe si mette in evidenza nelle
  schermate.
- **Un vincolo di non sovrapposizione nel database.** MySQL non ha il vincolo di
  esclusione che servirebbe.

## Come si verifica

1. Login come dipendente; la griglia mostra lunedì–domenica, 07:00–21:00, con
   09:00–18:00 evidenziato.
2. **Avvia** su una task → il blocco compare all'ora giusta, con la tinta del suo
   lavoro, pieno; **Pausa** lo chiude.
3. Trascinare una zona vuota di ieri → il pannello si apre precompilato; scelta la
   task e salvato, il blocco appare **tratteggiato**, e il totale della settimana,
   «Oggi hai registrato…» e le ore consumate della task si aggiornano.
4. Tentare una sovrapposizione → rifiutata, con la task in conflitto nominata.
5. Settimana precedente → sola lettura, e la testata lo dice.
6. Clic su una sessione di un altro giorno della settimana → si corregge e si
   annulla senza errore.
7. In MySQL, `minuti` e `in_corso` coerenti con gli istanti scritti a mano:
   `SELECT id, avviata_il, chiusa_il, minuti, in_corso, origine FROM sessione_task
   WHERE utente_id = ? ORDER BY avviata_il DESC;`

---

## Che cosa cambia rispetto all'originale

Elenco dei punti in cui questo documento si discosta dal sistema come è oggi, con
il motivo in breve. Tutto il resto resta com'era.

### Regole modificate

| Cosa | Prima | Adesso | Perché |
|---|---|---|---|
| Finestra di scrittura | **La giornata in corso.** `ServizioTempo.vietaSeFuoriDallaFinestra` risponde 409 su una sessione di ieri | **La settimana corrente, lunedì–domenica** | Un giorno di finestra manca per definizione il caso d'uso: di un giorno non registrato ci si accorge il venerdì |
| Messaggio del 409 | «non si corregge più: si sistema entro la giornata» | «…entro la settimana» | Segue la finestra |
| Correzione e annullamento | Entro la giornata | Entro la settimana | Due finestre diverse sulla stessa griglia sarebbero inspiegabili. L'eccezione per le chiusure d'ufficio da confermare resta |
| Sovrapposizione fra sessioni chiuse | **Non controllata**, perché impossibile: scriveva solo il timer | **Respinta** su inserimento manuale *e* su correzione | La scrittura manuale la rende possibile, e romperebbe la somma delle ore |
| Scritture manuali possibili | Solo correggere una sessione **esistente** | Anche **crearne** una | Chi si dimentica il timer non aveva alcun rimedio dopo la V17 |

### Aggiunte

- **`origine = MANUALE`**, quarto valore dell'enum e della sua CHECK. Vuole una
  migrazione, perché `ck_sessione_origine` rifiuterebbe la scrittura: si elimina
  il CHECK e si rifà con la lista copiata dalla V15.
- **`GET /api/mio-tempo/settimana`** — la settimana di chi chiede, con il totale e
  il flag `modificabile`. Su `TASK_AGGIORNA_STATO`.
- **`POST /api/lavori/{idLavoro}/task/{idTask}/sessioni`** — l'inserimento
  manuale. Su `TASK_AGGIORNA_STATO`.
- **Query delle sovrapposizioni** su `SessioneTaskRepository`. Una sessione aperta
  è trattata come se si estendesse all'infinito: volutamente un filo severo, per
  non dover decidere «fino a adesso» dentro una query.
- **Due componenti nuovi** nel frontend: la griglia settimanale e il pannello
  della sessione. Il pannello è un `<dialog>` nativo — in progetto non c'è nessuna
  libreria di componenti e non se ne introduce una.
- **`@fullcalendar/timegrid`**, dipendenza nuova: in `package.json` ci sono core,
  daygrid, interaction, list e react, ma non timegrid, e la vista a fasce orarie
  sta lì. Alla versione degli altri plugin, `^6.1.22`.
- **Una tavolozza di otto tinte per lavoro** in `App.css`, più il tratteggio per
  origine. In TypeScript solo il nome della classe, i valori nel CSS: è la regola
  scritta in testa a `stato/stati.ts`.

### Codice esistente che si riorganizza senza cambiare comportamento

- I controlli di `avvia` — assegnazione attiva, lavoro non chiuso, task aperta —
  si **estraggono in due metodi privati** per riusarli nell'inserimento manuale,
  invece di duplicarli.
- `PaginaDashboard` riceve una seconda scheda e nient'altro: la logica nuova vive
  nei componenti nuovi, perché il file è già a 570 righe.

### Quello che non cambia, benché si potesse pensare il contrario

- **Nessun permesso nuovo**, quindi nessuna migrazione di permessi e nessuna
  concessione al ruolo protetto.
- **Nessuna tabella e nessuna colonna nuova.** Nessun totale salvato.
- **`ck_sessione_conferma` e `uk_sessione_in_corso`** restano identici.
- **`RigaSessione`** resta identico: ha già `lavoroId` e `codiceLavoro`, cioè
  quanto serve alla tinta.
- **`GET /api/mio-calendario`** e il calendario delle scadenze restano come sono:
  il nuovo si affianca, non lo sostituisce.
- Il **tetto delle 8 ore** e la chiusura pigra restano come sono.

### Scelte di presentazione da rivedere se non convincono

- Le due sezioni sono **impilate a larghezza piena**, non in due colonne: una
  griglia 7 giorni × 14 ore sotto i ~700px diventa illeggibile, e metterla a
  fianco del calendario del mese le darebbe metà schermo. È un cambio di CSS e di
  una `<div>`.

### Debito preesistente che va chiuso prima, e non appartiene a questa feature

`frontend/src/utente/api.ts` contiene un rename `TOGLIATO → TOLTO` lasciato a
metà: il backend emette ancora `TOGLIATO` e `PaginaPermessiUtente` usa la chiave
vecchia, quindi **`tsc -b` non compila** e nulla di quanto sopra è verificabile.
Si ripristina `TOGLIATO` — una riga. Portare il rename a termine toccherebbe
enum, test e pagina: è un lavoro a sé.
