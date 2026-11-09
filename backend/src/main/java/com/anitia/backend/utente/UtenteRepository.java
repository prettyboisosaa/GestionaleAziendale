package com.anitia.backend.utente;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UtenteRepository extends JpaRepository<Utente, Long> {

    /**
     * Cerca l'utente per email. La collation del database
     * ({@code utf8mb4_0900_ai_ci}) ignora già maiuscole e minuscole, quindi il
     * confronto è case insensitive senza bisogno di normalizzare l'email.
     */
    Optional<Utente> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Utente> findAllByOrderByCognomeAscNomeAsc();

    List<Utente> findByAttivoTrueOrderByCognomeAscNomeAsc();

    /**
     * I codici dei permessi effettivi dell'utente, letti dalla vista
     * {@code permesso_effettivo}: l'unione dei permessi di tutti i suoi ruoli.
     * Per un utente disattivato la lista è vuota, perché la vista li esclude.
     */
    @Query(value = "SELECT permesso_codice FROM permesso_effettivo WHERE utente_id = :utenteId",
            nativeQuery = true)
    List<String> trovaCodiciPermesso(@Param("utenteId") Long utenteId);

    /**
     * I permessi che arrivano <strong>solo dai ruoli</strong>, ignorando gli
     * scostamenti per utente. Serve alla schermata dei permessi per dire
     * «questo glielo dà il ruolo» e distinguerlo da «questo gliel'hai aggiunto
     * tu».
     *
     * <p>Qui non si filtra su {@code attivo}: la domanda è come sono
     * configurati i ruoli, non se la persona può accedere.
     */
    @Query(value = "SELECT DISTINCT rp.permesso_codice FROM utente_ruolo ur "
                 + "JOIN ruolo_permesso rp ON rp.ruolo_id = ur.ruolo_id "
                 + "WHERE ur.utente_id = :utenteId",
            nativeQuery = true)
    List<String> trovaCodiciPermessoDaiRuoli(@Param("utenteId") Long utenteId);

    /**
     * Vero se la persona ha una task <strong>attiva</strong> in almeno un
     * lavoro di cui l'altro è responsabile.
     *
     * <p>È il rimpiazzo di {@code eNelTeam}, caduto con la tabella
     * {@code membro_team}: una task è assegnabile a chiunque, quindi non c'è
     * nessun elenco di sottoposti da consultare. Quello che resta è il lavoro,
     * che è l'unità con cui si alloca davvero, e serve a far rispettare
     * {@code TEMPO_VISUALIZZA_LAVORO}.
     *
     * <p>Il filtro è {@code rimossa_il IS NULL} e non la colonna generata
     * {@code attiva}, per la stessa ragione annotata in
     * {@code LavoroRepository}: quella serve al vincolo di unicità e vale NULL
     * quando l'assegnazione è stata rimossa.
     */
    default boolean eNeiLavoriDi(Long responsabileId, Long dipendenteId) {
        return contaNeiLavoriDi(responsabileId, dipendenteId) > 0;
    }

    /**
     * Si conta e si confronta in Java invece di scrivere {@code COUNT(*) > 0}
     * nella query: MySQL restituirebbe quel confronto come {@code BIGINT}, e
     * Spring Data non sa convertire un numero in {@code Boolean} — solleva una
     * {@code ClassCastException} a tempo di esecuzione, che nessun test con il
     * servizio simulato può intercettare.
     */
    @Query(value = "SELECT COUNT(*) FROM lavoro l "
                 + "JOIN task t ON t.lavoro_id = l.id "
                 + "JOIN assegnazione_task a ON a.task_id = t.id "
                 + "WHERE l.responsabile_id = :responsabileId "
                 + "AND a.utente_id = :dipendenteId AND a.rimossa_il IS NULL",
            nativeQuery = true)
    long contaNeiLavoriDi(@Param("responsabileId") Long responsabileId,
                          @Param("dipendenteId") Long dipendenteId);

    /** Quanti permessi effettivi ha ciascun utente, in una query sola. */
    @Query(value = "SELECT utente_id AS utenteId, COUNT(*) AS quanti "
                 + "FROM permesso_effettivo GROUP BY utente_id",
            nativeQuery = true)
    List<ConteggioPermessi> contaPermessiEffettivi();

    /** Una riga di {@link #contaPermessiEffettivi()}. */
    interface ConteggioPermessi {
        Long getUtenteId();

        int getQuanti();
    }

    /**
     * Gli utenti che fra i loro permessi effettivi hanno quello indicato, in
     * ordine di cognome.
     *
     * <p>La vista {@code permesso_effettivo} esclude già gli utenti
     * disattivati, quindi il requisito «solo utenti attivi» arriva gratis.
     *
     * <p>Si interroga il permesso e non il nome del ruolo perché i ruoli sono
     * dati rinominabili dall'applicazione, mentre i permessi sono un catalogo
     * fisso che cambia solo con una migrazione.
     */
    @Query(value = "SELECT u.* FROM utente u "
                 + "JOIN permesso_effettivo pe ON pe.utente_id = u.id "
                 + "WHERE pe.permesso_codice = :permesso "
                 + "ORDER BY u.cognome, u.nome",
            nativeQuery = true)
    List<Utente> trovaConPermesso(@Param("permesso") String permesso);

    /**
     * I ruoli di tutti gli utenti in una query sola: l'elenco li raggruppa in
     * memoria, invece di interrogare il database una volta per utente.
     */
    @Query(value = "SELECT ur.utente_id AS utenteId, r.nome AS nome "
                 + "FROM utente_ruolo ur JOIN ruolo r ON r.id = ur.ruolo_id "
                 + "ORDER BY r.nome",
            nativeQuery = true)
    List<RuoloAssegnato> trovaRuoliAssegnati();


    /**
     * Assegna un ruolo scrivendo direttamente sulla tabella di collegamento.
     *
     * <p>Non c'è un {@code @ManyToMany} di proposito: {@code utente_ruolo} ha
     * anche {@code assegnato_il} e {@code assegnato_da_id}, cioè quando e da
     * chi quel ruolo è stato dato. Una @ManyToMany scriverebbe solo le due
     * chiavi e butterebbe via quell'informazione, che lo schema registra
     * deliberatamente.
     */
    @Modifying
    @Query(value = "INSERT INTO utente_ruolo (utente_id, ruolo_id, assegnato_da_id) "
                 + "VALUES (:utenteId, :ruoloId, :assegnatoDaId)",
            nativeQuery = true)
    void assegnaRuolo(@Param("utenteId") Long utenteId,
                      @Param("ruoloId") Long ruoloId,
                      @Param("assegnatoDaId") Long assegnatoDaId);

    /** Una coppia utente-ruolo restituita da {@link #trovaRuoliAssegnati()}. */
    interface RuoloAssegnato {
        Long getUtenteId();

        String getNome();
    }
}
