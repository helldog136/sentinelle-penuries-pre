package vd.affsn.sentinelle.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import vd.affsn.sentinelle.domain.model.Acteur;

/**
 * Authentification Spring Security issue d'un jeton vérifié : le principal est l'{@link Acteur},
 * l'unique autorité est {@code ROLE_<ROLE>}, et les credentials sont {@code null} (le jeton n'est
 * jamais conservé).
 *
 * <p>STUB (phase A).
 */
public final class ActeurAuthentication extends AbstractAuthenticationToken {

    private final transient Acteur acteur;

    public ActeurAuthentication(Acteur acteur) {
        super(null);
        this.acteur = acteur;
    }

    @Override
    public Acteur getPrincipal() {
        throw new UnsupportedOperationException("ActeurAuthentication : phase B");
    }

    @Override
    public Object getCredentials() {
        return null;
    }
}
