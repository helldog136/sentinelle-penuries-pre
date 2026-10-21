package vd.affsn.sentinelle.domain.model;

/**
 * Identifiant d'un distributeur agréé (registre fictif) : format strict {@code PRO-} suivi de
 * exactement six chiffres ASCII (ex. {@code PRO-000042}). Aucune normalisation.
 *
 * <p>{@link #toString()} renvoie la valeur brute.
 *
 * <p><b>STUB TDD (T0.2)</b> : la validation et {@code toString} restent à implémenter.
 */
public record IdentifiantDistributeur(String valeur) {

    /** Expression régulière du format attendu. */
    public static final String FORMAT = "PRO-[0-9]{6}";

    public IdentifiantDistributeur {
        // TODO T0.2 : null ou format non conforme -> ValidationMetierException
    }

    public static IdentifiantDistributeur de(String valeur) {
        return new IdentifiantDistributeur(valeur);
    }
}
