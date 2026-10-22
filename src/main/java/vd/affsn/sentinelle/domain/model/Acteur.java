package vd.affsn.sentinelle.domain.model;

import java.util.Optional;

import vd.affsn.sentinelle.domain.exception.ValidationMetierException;

/**
 * Acteur authentifié qui appelle un cas d'usage. Immuable.
 *
 * <ul>
 *   <li>{@code sujet} : identifiant opaque du professionnel (issu du jeton), non vide, au plus
 *       {@value #LONGUEUR_MAX_SUJET} caractères. Jamais un patient.</li>
 *   <li>{@code distributeurRattache} : obligatoire pour {@link Role#DISTRIBUTEUR}, interdit
 *       ({@code null}) pour les autres rôles.</li>
 * </ul>
 */
public record Acteur(String sujet, Role role, IdentifiantDistributeur distributeurRattache) {

    public static final int LONGUEUR_MAX_SUJET = 128;

    public Acteur {
        if (sujet == null || sujet.isBlank() || sujet.length() > LONGUEUR_MAX_SUJET) {
            throw new ValidationMetierException(
                    "Acteur : sujet obligatoire, au plus " + LONGUEUR_MAX_SUJET + " caractères");
        }
        if (role == null) {
            throw new ValidationMetierException("Acteur : rôle obligatoire");
        }
        if ((role == Role.DISTRIBUTEUR) != (distributeurRattache != null)) {
            throw new ValidationMetierException(
                    "Acteur : rattachement distributeur obligatoire pour DISTRIBUTEUR et interdit sinon");
        }
    }

    public static Acteur distributeur(String sujet, IdentifiantDistributeur identifiant) {
        return new Acteur(sujet, Role.DISTRIBUTEUR, identifiant);
    }

    public static Acteur pharmacien(String sujet) {
        return new Acteur(sujet, Role.PHARMACIEN, null);
    }

    public static Acteur prescripteur(String sujet) {
        return new Acteur(sujet, Role.PRESCRIPTEUR, null);
    }

    public Optional<IdentifiantDistributeur> identifiantDistributeur() {
        return Optional.ofNullable(distributeurRattache);
    }

    /** Vrai si l'acteur a le rôle DISTRIBUTEUR et est rattaché à cet identifiant précis. */
    public boolean agitPour(IdentifiantDistributeur distributeur) {
        return role == Role.DISTRIBUTEUR && distributeurRattache.equals(distributeur);
    }
}
