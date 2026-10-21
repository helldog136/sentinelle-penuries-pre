package vd.affsn.sentinelle.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Réponse 403 au format {@code application/problem+json} (RFC 7807), générique, sans détail
 * interne ni écho du jeton ou de l'identité.
 */
public final class GestionnaireAccesRefuse implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        ReponseProbleme.ecrire(request, response, HttpServletResponse.SC_FORBIDDEN,
                "acces-refuse", "Accès refusé",
                "Le rôle de l'appelant ne permet pas d'accéder à cette ressource.");
    }
}
