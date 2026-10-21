package vd.affsn.sentinelle.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtre REST : lit {@code Authorization: Bearer <jwt>}, le fait vérifier par
 * {@link VerificateurJwt} et place une {@link ActeurAuthentication} dans le contexte de sécurité.
 * Sans en-tête, laisse passer en anonyme (l'autorisation tranche) ; en-tête présent mais refusé,
 * répond 401 via le point d'entrée.
 *
 * <p>Volontairement PAS un bean Spring (sinon Boot l'enregistrerait aussi comme filtre servlet
 * global) : instancié par {@link ConfigurationSecuriteWeb}.
 *
 * <p>STUB (phase A).
 */
public final class FiltreAuthentificationJwt extends OncePerRequestFilter {

    private final VerificateurJwt verificateur;
    private final AuthenticationEntryPoint pointEntree;

    public FiltreAuthentificationJwt(VerificateurJwt verificateur, AuthenticationEntryPoint pointEntree) {
        this.verificateur = verificateur;
        this.pointEntree = pointEntree;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        throw new UnsupportedOperationException("FiltreAuthentificationJwt : phase B");
    }
}
