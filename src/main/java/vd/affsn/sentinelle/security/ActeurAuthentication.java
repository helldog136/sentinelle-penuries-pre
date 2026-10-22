package vd.affsn.sentinelle.security;

import java.util.List;
import java.util.Objects;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import vd.affsn.sentinelle.domain.model.Acteur;

/**
 * Authentification Spring Security issue d'un jeton vérifié : le principal est l'{@link Acteur},
 * l'unique autorité est {@code ROLE_<ROLE>}, et les credentials sont {@code null} (le jeton n'est
 * jamais conservé).
 */
public final class ActeurAuthentication extends AbstractAuthenticationToken {

    private final transient Acteur acteur;

    public ActeurAuthentication(Acteur acteur) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + Objects.requireNonNull(acteur, "acteur").role().name())));
        this.acteur = acteur;
        super.setAuthenticated(true);
    }

    @Override
    public Acteur getPrincipal() {
        return acteur;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public String getName() {
        return acteur.sujet();
    }
}
