package vd.affsn.sentinelle.domain.model;

/**
 * Code produit de la nomenclature fictive valdarienne : format strict {@code VMP-} suivi de
 * exactement six chiffres ASCII (ex. {@code VMP-123456}). Aucune normalisation (pas de trim,
 * pas de passage en majuscules) : une valeur non conforme est refusée.
 *
 * <p>{@link #toString()} renvoie la valeur brute.
 *
 * <p><b>STUB TDD (T0.2)</b> : la validation et {@code toString} restent à implémenter.
 */
public record CodeProduit(String valeur) {

    /** Expression régulière du format attendu. */
    public static final String FORMAT = "VMP-[0-9]{6}";

    public CodeProduit {
        // TODO T0.2 : null ou format non conforme -> ValidationMetierException
    }

    public static CodeProduit de(String valeur) {
        return new CodeProduit(valeur);
    }
}
