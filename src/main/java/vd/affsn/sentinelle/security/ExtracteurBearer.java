package vd.affsn.sentinelle.security;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Extrait le jeton d'une valeur {@code Authorization: Bearer <jeton>} (en-tête HTTP ou metadata
 * gRPC). Schéma insensible à la casse, exactement un espace, jeton non vide au format
 * {@code b64token} de la RFC 6750. Toute autre forme (absente, autre schéma, jeton vide ou avec
 * espace) donne {@link Optional#empty()}.
 */
public final class ExtracteurBearer {

    private static final String SCHEMA = "Bearer ";
    private static final Pattern B64TOKEN = Pattern.compile("[A-Za-z0-9\\-._~+/]+=*");

    private ExtracteurBearer() {
    }

    public static Optional<String> extraire(String valeur) {
        if (valeur == null || valeur.length() <= SCHEMA.length()
                || !valeur.regionMatches(true, 0, SCHEMA, 0, SCHEMA.length())) {
            return Optional.empty();
        }
        String jeton = valeur.substring(SCHEMA.length());
        return B64TOKEN.matcher(jeton).matches() ? Optional.of(jeton) : Optional.empty();
    }
}
