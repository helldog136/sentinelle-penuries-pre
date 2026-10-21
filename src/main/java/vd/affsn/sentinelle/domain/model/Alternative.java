package vd.affsn.sentinelle.domain.model;

import vd.affsn.sentinelle.domain.exception.ValidationMetierException;

/**
 * Produit alternatif proposé pour une rupture, avec un commentaire professionnel court.
 *
 * <p>Le commentaire est destiné aux professionnels : il ne doit contenir AUCUNE donnée patient
 * (règle documentée, non vérifiable automatiquement ; d'où la borne de longueur et le refus des
 * sauts de ligne et caractères de contrôle, qui découragent le texte libre). Un commentaire
 * {@code null} est normalisé en chaîne vide ; les espaces de bord sont retirés.
 */
public record Alternative(CodeProduit produit, String commentaire) {

    public static final int LONGUEUR_MAX_COMMENTAIRE = 280;

    public Alternative {
        if (produit == null) {
            throw new ValidationMetierException("Alternative : produit obligatoire");
        }
        commentaire = commentaire == null ? "" : commentaire;
        if (commentaire.chars().anyMatch(Character::isISOControl)) {
            throw new ValidationMetierException("Alternative : caractère de contrôle interdit dans le commentaire");
        }
        commentaire = commentaire.strip();
        if (commentaire.length() > LONGUEUR_MAX_COMMENTAIRE) {
            throw new ValidationMetierException(
                    "Alternative : commentaire limité à " + LONGUEUR_MAX_COMMENTAIRE + " caractères");
        }
    }

    public static Alternative sansCommentaire(CodeProduit produit) {
        return new Alternative(produit, null);
    }
}
