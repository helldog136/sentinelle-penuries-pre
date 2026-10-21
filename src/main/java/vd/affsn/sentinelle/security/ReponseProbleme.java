package vd.affsn.sentinelle.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

/**
 * Écrit une réponse {@code application/problem+json} (RFC 7807) fixe pour la sécurité REST.
 * {@code instance} est le chemin de la requête, SANS la chaîne de requête (qui pourrait porter un
 * jeton) ; aucune donnée interne n'est incluse.
 */
final class ReponseProbleme {

    private static final ObjectMapper JSON = new ObjectMapper();
    static final String TYPE_BASE = "https://sentinelle.affsn.example/problemes/";

    private ReponseProbleme() {
    }

    static void ecrire(HttpServletRequest requete, HttpServletResponse reponse,
                       int statut, String type, String titre, String detail) throws IOException {
        Map<String, Object> probleme = new LinkedHashMap<>();
        probleme.put("type", TYPE_BASE + type);
        probleme.put("title", titre);
        probleme.put("status", statut);
        probleme.put("detail", detail);
        probleme.put("instance", requete.getRequestURI());

        reponse.setStatus(statut);
        reponse.setCharacterEncoding("UTF-8");
        reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        reponse.getOutputStream().write(JSON.writeValueAsBytes(probleme));
        reponse.flushBuffer();
    }
}
