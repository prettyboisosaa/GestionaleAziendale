/**
 * La scala oraria delle griglie e le ore di una giornata piena.
 *
 * Vive qui, nel dominio del tempo, e non nella dashboard che la usa: è una
 * proprietà di come questo gestionale misura le ore, e la dashboard ne è un
 * lettore. Il verso della dipendenza conta — la dashboard già importa da
 * `tempo/api`, e il contrario farebbe dipendere il registro del tempo dalla
 * forma di una schermata.
 *
 * I due calendari affiancati devono avere **la stessa scala**: se quello del
 * pianificato partisse dalle otto e quello del registrato dalle sette, due
 * blocchi alla stessa altezza sarebbero due ore diverse, e leggerli insieme
 * ingannerebbe invece di aiutare. Due costanti in due file avrebbero
 * esattamente quell'effetto la prima volta che una delle due cambia.
 */

/**
 * Gli estremi della griglia, più larghi dell'orario di lavoro.
 *
 * Il piano chiede ore «limitate all'orario lavorativo, con scorrimento
 * interno»: le due cose insieme vogliono dire che la fascia **visibile** è
 * quella di lavoro, non che la scala si fermi lì. La differenza conta: chi ha
 * lavorato alle venti deve poterlo vedere, e una griglia che si fermasse alle
 * diciannove gli direbbe che quell'ora non è esistita, facendo sparire ore
 * vere dal consuntivo di un lavoro. Si scorre per arrivarci, ma c'è.
 */
export const ORA_SCALA_INIZIO = 7
export const ORA_SCALA_FINE = 21

/**
 * L'orario di lavoro, quello che si vede all'apertura e che la griglia
 * evidenzia. Non limita niente: dice dov'è.
 */
export const ORA_LAVORO_INIZIO = 9
export const ORA_LAVORO_FINE = 18

/**
 * Le ore che si contano in una giornata piena.
 *
 * Una costante e non un dato del server: non c'è nessuna impostazione che le
 * dichiari, e inventarne una — con la sua migrazione e la sua rotta — per un
 * numero che serve a colorare un contatore sarebbe un'infrastruttura più
 * grande della cosa che misura. Il giorno in cui le ore dipendessero dal
 * contratto, questo è il punto da cui partire.
 */
export const ORE_PREVISTE = 8

/** I minuti di una giornata piena: il contatore lavora in minuti. */
export const MINUTI_PREVISTI = ORE_PREVISTE * 60

/** Un'ora, nel formato che FullCalendar vuole per le sue fasce. */
export function fascia(ora: number): string {
  return `${String(ora).padStart(2, '0')}:00:00`
}

/**
 * Dove parte la griglia all'apertura: un'ora prima di adesso, dentro la fascia
 * di lavoro.
 *
 * Alle cinque del pomeriggio la parte della giornata che interessa è quella
 * che resta, non quella passata: arrivare alle sette del mattino costringe a
 * scorrere ogni volta. Un'ora indietro per vedere da dove si viene. Fuori
 * dall'orario non si inventa niente — prima dell'apertura si parte
 * dall'apertura, dopo la chiusura si resta sulle ultime ore — perché una
 * griglia posizionata oltre la fine si mostrerebbe vuota.
 */
export function scrollIniziale(): string {
  const adesso = new Date().getHours()
  return fascia(Math.min(Math.max(adesso - 1, ORA_LAVORO_INIZIO), ORA_LAVORO_FINE))
}
