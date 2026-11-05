package com.anitia.backend.ore;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimentoOreRepository extends JpaRepository<MovimentoOre, Long> {

    /**
     * Lo storico delle ore di un lavoro, dal più vecchio al più recente.
     *
     * <p>{@code LEFT JOIN FETCH} sulla task — che è nulla nei movimenti del
     * solo monte ore, e un join interno li farebbe sparire dallo storico — e
     * {@code JOIN FETCH} sull'autore, che c'è sempre. Il fetch esplicito evita
     * una query per riga: {@code open-in-view} è disattivato, quindi leggere
     * una relazione lazy fuori dalla transazione fallirebbe comunque.
     */
    @Query("SELECT m FROM MovimentoOre m "
         + "LEFT JOIN FETCH m.task "
         + "JOIN FETCH m.autore "
         + "WHERE m.lavoro.id = :lavoroId "
         + "ORDER BY m.creatoIl, m.id")
    List<MovimentoOre> trovaDelLavoro(@Param("lavoroId") Long lavoroId);

    /**
     * Il monte ore con cui il lavoro è partito, cioè le ore del movimento
     * {@code MONTE_INIZIALE}.
     *
     * <p>È vuoto per i lavori creati senza budget, e per quelli nati prima
     * della migrazione V8. Chi lo legge deve gestire l'assenza invece di
     * assumere uno zero, che direbbe «è partito senza ore» — un'affermazione
     * diversa da «non lo sappiamo».
     */
    @Query("SELECT m.ore FROM MovimentoOre m "
         + "WHERE m.lavoro.id = :lavoroId AND m.tipo = com.anitia.backend.ore.TipoMovimento.MONTE_INIZIALE")
    Optional<BigDecimal> monteIniziale(@Param("lavoroId") Long lavoroId);
}
