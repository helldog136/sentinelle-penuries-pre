package vd.affsn.sentinelle.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Réponse 401 : en-tête {@code WWW-Authenticate: Bearer}, corps {@code application/problem+json}
 * (RFC 7807) générique, sans raison du refus ni écho du jeton.
 *
 * <p>STUB (phase A).
 */
public final class PointEntreeBearer implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        throw new UnsupportedOperationException("PointEntreeBearer : phase B");
    }
}
