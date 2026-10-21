package vd.affsn.sentinelle.domain.model;

import java.util.UUID;

/**
 * Identifiant technique d'une rupture (UUID). {@link #toString()} renvoie la forme canonique
 * de l'UUID.
 *
 * <p><b>STUB TDD (T0.2)</b> : validation, {@link #depuis(String)} et {@code toString} à implémenter.
 */
public record RuptureId(UUID valeur) {

    public RuptureId {
        // TODO T0.2 : null -> ValidationMetierException
    }

    public static RuptureId generer() {
        return new RuptureId(UUID.randomUUID());
    }

    /** Analyse la forme textuelle canonique ; toute valeur invalide -> ValidationMetierException. */
    public static RuptureId depuis(String texte) {
        throw new UnsupportedOperationException("T0.2 : à implémenter");
    }
}
