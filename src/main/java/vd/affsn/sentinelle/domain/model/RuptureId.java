package vd.affsn.sentinelle.domain.model;

import java.util.UUID;

import vd.affsn.sentinelle.domain.exception.ValidationMetierException;

/**
 * Identifiant technique d'une rupture (UUID). {@link #toString()} renvoie la forme canonique
 * de l'UUID.
 */
public record RuptureId(UUID valeur) {

    public RuptureId {
        if (valeur == null) {
            throw new ValidationMetierException("Identifiant de rupture obligatoire");
        }
    }

    public static RuptureId generer() {
        return new RuptureId(UUID.randomUUID());
    }

    /** Analyse la forme textuelle canonique ; toute valeur invalide -> ValidationMetierException. */
    public static RuptureId depuis(String texte) {
        if (texte != null) {
            try {
                UUID uuid = UUID.fromString(texte);
                if (uuid.toString().equalsIgnoreCase(texte)) {
                    return new RuptureId(uuid);
                }
            } catch (IllegalArgumentException e) {
                // traité ci-dessous
            }
        }
        throw new ValidationMetierException("Identifiant de rupture invalide : UUID canonique attendu");
    }

    @Override
    public String toString() {
        return valeur.toString();
    }
}
