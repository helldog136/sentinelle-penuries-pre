package vd.affsn.sentinelle.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Réponse 401 : en-tête {@code WWW-Authenticate: Bearer}, corps {@code application/problem+json}
 * (RFC 7807) générique et identique quelle que soit la cause (jeton absent, expiré, mal signé...),
 * sans écho du jeton.
 */
public final class PointEntreeBearer implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        ReponseProbleme.ecrire(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                "non-authentifie", JetonInvalideException.MESSAGE_GENERIQUE,
                "Authentification requise ou jeton invalide.");
    }
}
