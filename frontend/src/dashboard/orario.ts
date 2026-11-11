/**
 * Le date della dashboard, nel formato in cui le schermate le mostrano.
 *
 * Stavano dentro `PaginaDashboard`, che era l'unica a usarle; da quando la
 * pagina è divisa in quattro pannelli servono a più di uno, e una copia per
 * pannello sarebbe quattro definizioni di «oggi».
 *
 * La **scala oraria** non è qui: vive in `tempo/orario.ts`, perché è una
 * proprietà di come si misurano le ore e non di come questa pagina le
 * disegna.
 */

/** La data di oggi in formato ISO, nel fuso di chi guarda. */
export function oggiIso(): string {
  const adesso = new Date()
  const mese = String(adesso.getMonth() + 1).padStart(2, '0')
  const giorno = String(adesso.getDate()).padStart(2, '0')
  return `${adesso.getFullYear()}-${mese}-${giorno}`
}

/**
 * Il giorno dopo, in ISO. Serve a tradurre la fine *inclusiva* dell'API nella
 * fine *esclusiva* che FullCalendar vuole per gli eventi a giornata intera.
 *
 * Passa da `Date.UTC` e non dal costruttore locale: con quest'ultimo, in un
 * fuso a ovest di Greenwich, «2026-11-06» diventerebbe il 5 novembre sera e
 * l'evento slitterebbe di un giorno.
 */
export function piuUnGiorno(iso: string): string {
  const [anno, mese, giorno] = iso.split('-').map(Number)
  return new Date(Date.UTC(anno, mese - 1, giorno + 1)).toISOString().slice(0, 10)
}

/** Da «2026-11-06» a «06/11». */
export function breve(iso: string): string {
  const [, mese, giorno] = iso.split('-')
  return `${giorno}/${mese}`
}

/**
 * La scadenza in forma brevissima: «oggi», «ieri», «domani», «ven», «06/11».
 *
 * Serve nelle righe strette della colonna destra, dove una data per esteso
 * occupa lo spazio del titolo. I giorni vicini si dicono a parole perché è
 * così che li si pensa — nessuno conta i giorni per sapere se una cosa scade
 * domani — e da una settimana in avanti si torna alla data, che è l'unica
 * cosa che non si confonde.
 */
export function scadenzaBreve(iso: string, giorniAllaScadenza: number): string {
  if (giorniAllaScadenza === 0) {
    return 'oggi'
  }
  if (giorniAllaScadenza === -1) {
    return 'ieri'
  }
  if (giorniAllaScadenza === 1) {
    return 'domani'
  }
  // Dentro la settimana il nome del giorno basta e si legge più in fretta di
  // una data; oltre, due «ven» a sette giorni di distanza sarebbero
  // indistinguibili.
  if (giorniAllaScadenza > 1 && giorniAllaScadenza <= 6) {
    const [anno, mese, giorno] = iso.split('-').map(Number)
    return new Date(anno, mese - 1, giorno)
      .toLocaleDateString('it-IT', { weekday: 'short' })
      .replace('.', '')
  }
  return breve(iso)
}

/** «Buongiorno, Marco.», secondo l'ora. */
export function saluto(nome: string): string {
  const ora = new Date().getHours()
  if (ora < 13) {
    return `Buongiorno, ${nome}.`
  }
  return ora < 18 ? `Buon pomeriggio, ${nome}.` : `Buonasera, ${nome}.`
}

/** «mercoledì 11 novembre». */
export function dataDiOggi(): string {
  return new Date().toLocaleDateString('it-IT', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  })
}
