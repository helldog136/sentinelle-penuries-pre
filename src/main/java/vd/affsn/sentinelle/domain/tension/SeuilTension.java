package vd.affsn.sentinelle.domain.tension;

import vd.affsn.sentinelle.domain.exception.ValidationMetierException;

/**
 * Seuil d'alerte de l'indice de tension : nombre fini, supérieur ou égal à 0.
 * Une région est signalée si son indice dépasse <em>strictement</em> le seuil.
 */
public record SeuilTension(double valeur) {

    public SeuilTension {
        if (!Double.isFinite(valeur) || valeur < 0) {
            throw new ValidationMetierException("Seuil de tension : nombre fini >= 0 attendu");
        }
    }

    /** Vrai si l'indice est strictement supérieur au seuil. */
    public boolean estDepassePar(double indice) {
        return indice > valeur;
    }
}
