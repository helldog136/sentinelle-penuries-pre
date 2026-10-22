package vd.affsn.sentinelle.domain.model;

import vd.affsn.sentinelle.domain.exception.ValidationMetierException;

/**
 * Code produit de la nomenclature fictive valdarienne : format strict {@code VMP-} suivi de
 * exactement six chiffres ASCII (ex. {@code VMP-123456}). Aucune normalisation (pas de trim,
 * pas de passage en majuscules) : une valeur non conforme est refusée.
 *
 * <p>{@link #toString()} renvoie la valeur brute.
 */
public record CodeProduit(String valeur) {

    /** Expression régulière du format attendu. */
    public static final String FORMAT = "VMP-[0-9]{6}";

    public CodeProduit {
        if (valeur == null || !valeur.matches(FORMAT)) {
            throw new ValidationMetierException("Code produit invalide : format attendu VMP-NNNNNN");
        }
    }

    public static CodeProduit de(String valeur) {
        return new CodeProduit(valeur);
    }

    @Override
    public String toString() {
        return valeur;
    }
}
