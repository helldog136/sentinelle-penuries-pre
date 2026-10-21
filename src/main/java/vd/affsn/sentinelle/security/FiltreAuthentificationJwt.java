package vd.affsn.sentinelle.security;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import vd.affsn.sentinelle.domain.model.Acteur;

/**
 * Filtre REST : lit {@code Authorization: Bearer <jwt>}, le fait vérifier par
 * {@link VerificateurJwt} et place une {@link ActeurAuthentication} dans le contexte de sécurité.
 * Sans en-tête, laisse passer en anonyme (l'autorisation tranche) ; en-tête présent mais refusé
 * (autre schéma, jeton invalide, plusieurs en-têtes), répond 401 via le point d'entrée.
 *
 * <p>Volontairement PAS un bean Spring (sinon Boot l'enregistrerait aussi comme filtre servlet
 * global) : instancié par {@link ConfigurationSecuriteWeb}. Ne journalise jamais le jeton.
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
        List<String> valeurs = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
        if (valeurs.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        Optional<String> jeton = valeurs.size() == 1 ? ExtracteurBearer.extraire(valeurs.get(0)) : Optional.empty();
        Acteur acteur;
        try {
            acteur = verificateur.verifier(jeton.orElseThrow(JetonInvalideException::new));
        } catch (JetonInvalideException refus) {
            SecurityContextHolder.clearContext();
            pointEntree.commence(request, response,
                    new BadCredentialsException(JetonInvalideException.MESSAGE_GENERIQUE));
            return;
        }
        SecurityContext contexte = SecurityContextHolder.createEmptyContext();
        contexte.setAuthentication(new ActeurAuthentication(acteur));
        SecurityContextHolder.setContext(contexte);
        chain.doFilter(request, response);
    }
}
