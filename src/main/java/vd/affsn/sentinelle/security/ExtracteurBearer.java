package vd.affsn.sentinelle.security;

import java.util.Optional;

/**
 * Extrait le jeton d'une valeur {@code Authorization: Bearer <jeton>} (en-tête HTTP ou metadata
 * gRPC). Schéma insensible à la casse, exactement un espace, jeton non vide sans espace.
 * Toute autre forme (absente, autre schéma, jeton vide) donne {@link Optional#empty()}.
 *
 * <p>STUB (phase A).
 */
public final class ExtracteurBearer {

    private ExtracteurBearer() {
    }

    public static Optional<String> extraire(String valeur) {
        return Optional.empty();
    }
}
