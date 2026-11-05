package com.anitia.backend.ruolo;

/** Un ruolo come lo vede il frontend, per riempire la scelta dei ruoli. */
public record RigaRuolo(Long id, String nome, String descrizione, boolean protetto) {

    static RigaRuolo da(Ruolo ruolo) {
        return new RigaRuolo(ruolo.getId(), ruolo.getNome(), ruolo.getDescrizione(),
                ruolo.isProtetto());
    }
}
