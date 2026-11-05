import { chiedi } from '../api/http'

export type RigaRuolo = {
  id: number
  nome: string
  descrizione: string | null
  protetto: boolean
}

export function elencoRuoli(): Promise<RigaRuolo[]> {
  return chiedi<RigaRuolo[]>('/api/ruoli')
}
