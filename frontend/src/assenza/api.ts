import { chiedi, postJson, putJson } from '../api/http'

export type TipoAssenza = 'FERIE' | 'PERMESSO_ORARIO' | 'PERMESSO_RETRIBUITO'

export type StatoAssenza = 'IN_ATTESA' | 'APPROVATA' | 'RIFIUTATA' | 'RITIRATA'

export type MotivoPermesso =
  | 'LUTTO'
  | 'MATRIMONIO'
  | 'LEGGE_104'
  | 'DONAZIONE_SANGUE'
  | 'VISITA_MEDICA'
  | 'ALTRO'

/**
 * Le etichette delle tipologie. Il server le manda già pronte dentro ogni
 * riga, ma qui servono anche **prima** che una riga esista: sono le voci della
 * lista da cui si scegli la tipologia per aprire il form.
 */
export const ETICHETTA_TIPO: Record<TipoAssenza, string> = {
  FERIE: 'Ferie',
  PERMESSO_ORARIO: 'Permesso orario',
  PERMESSO_RETRIBUITO: 'Permesso retribuito',
}

/** Cosa si chiede, per ciascuna tipologia: è il sottotitolo della scelta. */
export const SPIEGAZIONE_TIPO: Record<TipoAssenza, string> = {
  FERIE: 'Giornate intere, dal primo all’ultimo giorno.',
  PERMESSO_ORARIO: 'Un giorno solo, con una fascia oraria.',
  PERMESSO_RETRIBUITO: 'Lutto, matrimonio, legge 104, donazione, visita medica o altro.',
}

export const ETICHETTA_MOTIVO: Record<MotivoPermesso, string> = {
  LUTTO: 'Lutto',
  MATRIMONIO: 'Matrimonio',
  LEGGE_104: 'Legge 104',
  DONAZIONE_SANGUE: 'Donazione di sangue',
  VISITA_MEDICA: 'Visita medica',
  ALTRO: 'Altro',
}

export const ETICHETTA_STATO: Record<StatoAssenza, string> = {
  IN_ATTESA: 'In attesa',
  APPROVATA: 'Approvata',
  RIFIUTATA: 'Rifiutata',
  RITIRATA: 'Ritirata',
}

/**
 * La tinta del badge, come i modificatori CSS di `componenti/Stato`.
 * «In attesa» è arancione, approvata verde, rifiutata rossa, ritirata grigia.
 */
export const TINTA_STATO: Record<StatoAssenza, string> = {
  IN_ATTESA: 'attesa',
  APPROVATA: 'completato',
  RIFIUTATA: 'annullato',
  RITIRATA: 'attivo',
}

export type RigaAssenza = {
  id: number
  tipo: TipoAssenza
  tipoEtichetta: string
  dataInizio: string
  dataFine: string
  /** Null per le giornate intere. */
  oraInizio: string | null
  oraFine: string | null
  motivo: MotivoPermesso | null
  motivoEtichetta: string | null
  note: string | null
  stato: StatoAssenza
  statoEtichetta: string
  /** Riempito solo nell'elenco dell'amministratore. */
  richiedente: string | null
  gestitaDa: string | null
  gestitaIl: string | null
  notaEsito: string | null
  creataIl: string
  /** Null quando l'assenza è una fascia oraria. */
  giorniLavorativi: number | null
  /** Null per le giornate intere. */
  minuti: number | null
  /**
   * Il riepilogo già scritto: «3 giorni lavorativi», «2 h 30 min». Lo calcola
   * il server, che conosce le festività e i giorni lavorativi configurati:
   * rifarlo qui vorrebbe dire una seconda definizione di giorno lavorativo.
   */
  riepilogo: string | null
}

/**
 * Il corpo di una richiesta. I campi di ciascuna tipologia sono facoltativi:
 * è `tipo` a dire quali sono obbligatori, e il server risponde
 * `422 CAMPI_MANCANTI` quando ne manca uno.
 */
export type NuovaAssenza = {
  tipo: TipoAssenza
  dataInizio: string
  /** Si può omettere per un giorno solo: il server la fa coincidere con l'inizio. */
  dataFine: string | null
  oraInizio: string | null
  oraFine: string | null
  motivo: MotivoPermesso | null
  note: string | null
}

// --- le mie ----------------------------------------------------------------

export function mieAssenze(): Promise<RigaAssenza[]> {
  return chiedi<RigaAssenza[]>('/api/mie-assenze')
}

export function chiediAssenza(dati: NuovaAssenza): Promise<RigaAssenza> {
  return chiedi<RigaAssenza>('/api/mie-assenze', postJson(dati))
}

/** Modifica, consentita solo finché la richiesta è in attesa. */
export function modificaAssenza(id: number, dati: NuovaAssenza): Promise<RigaAssenza> {
  return chiedi<RigaAssenza>(`/api/mie-assenze/${id}`, putJson(dati))
}

/**
 * Ritira la richiesta. È l'unico modo che il dipendente ha di disfarla:
 * cancellarla porterebbe via la traccia di averla chiesta.
 */
export function ritiraAssenza(id: number): Promise<RigaAssenza> {
  return chiedi<RigaAssenza>(`/api/mie-assenze/${id}/ritira`, { method: 'POST' })
}

// --- tutte, per l'amministratore -------------------------------------------

export function tutteLeAssenze(
  stato: StatoAssenza | null,
  utenteId: number | null,
): Promise<RigaAssenza[]> {
  const filtri = new URLSearchParams()
  if (stato !== null) {
    filtri.set('stato', stato)
  }
  if (utenteId !== null) {
    filtri.set('utente_id', String(utenteId))
  }
  const query = filtri.size === 0 ? '' : '?' + filtri.toString()
  return chiedi<RigaAssenza[]>('/api/assenze' + query)
}

export function approvaAssenza(id: number, nota: string | null): Promise<RigaAssenza> {
  return chiedi<RigaAssenza>(`/api/assenze/${id}/approva`, postJson({ nota }))
}

/**
 * Rifiuta la richiesta. La nota è obbligatoria: senza, il server risponde
 * `422 NOTA_OBBLIGATORIA`. Un rifiuto senza motivazione è il modo più sicuro
 * di far riaprire la stessa richiesta il giorno dopo.
 */
export function rifiutaAssenza(id: number, nota: string): Promise<RigaAssenza> {
  return chiedi<RigaAssenza>(`/api/assenze/${id}/rifiuta`, postJson({ nota }))
}
