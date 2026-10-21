package vd.affsn.sentinelle.domain.model;

import java.util.Optional;

/**
 * Acteur authentifié qui appelle un cas d'usage. Immuable.
 *
 * <ul>
 *   <li>{@code sujet} : identifiant opaque du professionnel (issu du jeton), non vide, au plus
 *       {@value #LONGUEUR_MAX_SUJET} caractères. Jamais un patient.</li>
 *   <li>{@code distributeurRattache} : obligatoire pour {@link Role#DISTRIBUTEUR}, interdit
 *       ({@code null}) pour les autres rôles.</li>
 * </ul>
 *
 * <p><b>STUB TDD (T0.2)</b> : validation et comportement à implémenter.
 */
public record Acteur(String sujet, Role role, IdentifiantDistributeur distributeurRattache) {

    public static final int LONGUEUR_MAX_SUJET = 128;

    public Acteur {
        // TODO T0.2 : sujet null/blanc/trop long, role null, cohérence rôle/rattachement
        //             -> ValidationMetierException
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
        throw new UnsupportedOperationException("T0.2 : à implémenter");
    }

    /** Vrai si l'acteur a le rôle DISTRIBUTEUR et est rattaché à cet identifiant précis. */
    public boolean agitPour(IdentifiantDistributeur distributeur) {
        throw new UnsupportedOperationException("T0.2 : à implémenter");
    }
}
