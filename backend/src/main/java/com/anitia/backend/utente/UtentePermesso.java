package com.anitia.backend.utente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Uno scostamento di permesso rispetto ai ruoli di una persona.
 *
 * <p>Esiste una riga <strong>solo quando contraddice i ruoli</strong>: un
 * permesso aggiunto a chi non ce l'ha, o tolto a chi lo riceverebbe. Se i
 * ruoli già dicono la cosa giusta, la riga viene cancellata. È questa
 * l'invariante che tiene la tabella leggibile.
 *
 * <p>{@code utenteId} e {@code permessoCodice} sono mappati come colonne
 * semplici e non come relazioni: sono la chiave primaria, e tenerli tali evita
 * le complicazioni dell'identità derivata. La relazione serve solo per
 * {@code impostatoDa}, che non fa parte della chiave.
 */
@Entity
@Table(name = "utente_permesso")
@IdClass(UtentePermessoId.class)
public class UtentePermesso {

    @Id
    @Column(name = "utente_id")
    private Long utenteId;

    @Id
    @Column(name = "permesso_codice", length = 50)
    private String permessoCodice;

    /** Vero se il permesso è aggiunto, falso se è tolto. */
    @Column(nullable = false)
    private boolean concesso;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "impostato_il")
    private LocalDateTime impostatoIl;

    /** Chi ha deciso, come {@code assegnato_da_id} altrove nello schema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "impostato_da_id")
    private Utente impostatoDa;

    /** Richiesto da JPA. */
    protected UtentePermesso() {
    }

    public static UtentePermesso nuovo(Long utenteId, String permessoCodice,
                                       boolean concesso, Utente impostatoDa) {
        UtentePermesso scostamento = new UtentePermesso();
        scostamento.utenteId = utenteId;
        scostamento.permessoCodice = permessoCodice;
        scostamento.concesso = concesso;
        scostamento.impostatoDa = impostatoDa;
        return scostamento;
    }

    public Long getUtenteId() {
        return utenteId;
    }

    public String getPermessoCodice() {
        return permessoCodice;
    }

    public boolean isConcesso() {
        return concesso;
    }

    public LocalDateTime getImpostatoIl() {
        return impostatoIl;
    }
}
