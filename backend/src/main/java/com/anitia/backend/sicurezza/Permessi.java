package com.anitia.backend.sicurezza;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Lettura dei permessi di chi sta chiedendo.
 *
 * <p>Serve dove un {@code @PreAuthorize} non basta, cioè quando la regola
 * dipende anche dai dati: «può gestire le task <em>dei propri</em> lavori» non
 * si esprime in un'annotazione, perché richiede di sapere chi è il
 * responsabile di quel lavoro. L'annotazione fa il primo filtro, il servizio
 * lo rifinisce con questi controlli.
 */
public final class Permessi {

    private Permessi() {
    }

    /** Vero se ha almeno uno dei permessi indicati. */
    public static boolean ha(UserDetails utente, String... codici) {
        Set<String> suoi = utente.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return Arrays.stream(codici).anyMatch(suoi::contains);
    }
}
