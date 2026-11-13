import ConfermaAzione from './ConfermaAzione'

/**
 * «Vuoi uscire?»
 *
 * Il contenuto; la forma è {@link ConfermaAzione}, che è la finestra della
 * tavola 05 — pastiglia con l'icona, la domanda, una riga che dice cosa
 * succede davvero, e i due pulsanti con il principale a destra.
 *
 * <p>Sta in un componente suo e non dentro `Intestazione` perché la conferma
 * deve valere per **ogni** punto da cui si esce. Oggi il pulsante è uno solo,
 * nella barra laterale, ma l'uscita vive in `App`, che è l'unico posto che
 * possiede la sessione: mettendo la finestra lì, un secondo pulsante aggiunto
 * domani passa dalla conferma senza doversene ricordare.
 *
 * <p><strong>Non c'è «esci da tutti i dispositivi»</strong>, benché la tavola
 * del design system mostri una casella in questo punto. Il gestionale non sa
 * fare quella cosa: le sessioni vivono nella memoria del processo e non
 * esiste un elenco di quelle aperte altrove da poter chiudere. Una casella
 * che non fa niente è peggio della sua assenza — chi la segna crede di aver
 * chiuso una sessione che resta aperta. La casella tornerà il giorno che
 * esisterà un archivio delle sessioni.
 *
 * <p>La variante è quella normale e non `distruttiva`: uscire non porta via
 * niente, e tingere di corallo un'azione reversibile toglie peso al corallo
 * il giorno che compare su una cancellazione vera.
 */
export default function ConfermaUscita({
  onAnnulla,
  onEsci,
}: {
  onAnnulla: () => void
  onEsci: () => void
}) {
  return (
    <ConfermaAzione
      icona="esci"
      titolo="Vuoi uscire?"
      descrizione={
        <>
          La sessione si chiude e tornerai alla schermata di accesso. Il tempo
          già registrato resta dov'è; <strong>una task avviata continua a
          scorrere</strong>.
        </>
      }
      etichettaAnnulla="Resta"
      etichettaConferma="Esci"
      onConferma={onEsci}
      onChiudi={onAnnulla}
      // Nell'angolo in alto a destra: nasce dal pulsante «Esci» e compare
      // accanto al punto in cui si è premuto, invece di coprire la pagina al
      // centro per una domanda da un clic.
      angolo
    />
  )
}
