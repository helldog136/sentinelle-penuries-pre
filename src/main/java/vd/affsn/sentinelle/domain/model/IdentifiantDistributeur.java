package vd.affsn.sentinelle.domain.model;

import vd.affsn.sentinelle.domain.exception.ValidationMetierException;

/**
 * Identifiant d'un distributeur agréé (registre fictif) : format strict {@code PRO-} suivi de
 * exactement six chiffres ASCII (ex. {@code PRO-000042}). Aucune normalisation.
 *
 * <p>{@link #toString()} renvoie la valeur brute.
 */
public record IdentifiantDistributeur(String valeur) {

    /** Expression régulière du format attendu. */
    public static final String FORMAT = "PRO-[0-9]{6}";

    public IdentifiantDistributeur {
        if (valeur == null || !valeur.matches(FORMAT)) {
            throw new ValidationMetierException("Identifiant distributeur invalide : format attendu PRO-NNNNNN");
        }
    }

    public static IdentifiantDistributeur de(String valeur) {
        return new IdentifiantDistributeur(valeur);
    }

    @Override
    public String toString() {
        return valeur;
    }
}
