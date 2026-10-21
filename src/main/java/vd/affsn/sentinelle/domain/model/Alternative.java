package vd.affsn.sentinelle.domain.model;

/**
 * Produit alternatif proposé pour une rupture, avec un commentaire professionnel court.
 *
 * <p>Le commentaire est destiné aux professionnels : il ne doit contenir AUCUNE donnée patient
 * (règle documentée, non vérifiable automatiquement ; d'où la borne de longueur et le refus des
 * sauts de ligne et caractères de contrôle, qui découragent le texte libre). Un commentaire
 * {@code null} est normalisé en chaîne vide ; les espaces de bord sont retirés.
 *
 * <p><b>STUB TDD (T0.2)</b> : validation et normalisation à implémenter.
 */
public record Alternative(CodeProduit produit, String commentaire) {

    public static final int LONGUEUR_MAX_COMMENTAIRE = 280;

    public Alternative {
        // TODO T0.2 : produit null, commentaire > 280 (après strip) ou contenant un caractère
        //             de contrôle (dont \n, \r, \t) -> ValidationMetierException ; null -> "".
    }

    public static Alternative sansCommentaire(CodeProduit produit) {
        return new Alternative(produit, null);
    }
}
