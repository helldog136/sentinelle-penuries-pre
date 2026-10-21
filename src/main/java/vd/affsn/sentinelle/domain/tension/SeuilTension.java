package vd.affsn.sentinelle.domain.tension;

/**
 * Seuil d'alerte de l'indice de tension : nombre fini, supérieur ou égal à 0.
 * Une région est signalée si son indice dépasse <em>strictement</em> le seuil.
 *
 * <p><b>STUB TDD (T0.2)</b> : validation et comportement à implémenter.
 */
public record SeuilTension(double valeur) {

    public SeuilTension {
        // TODO T0.2 : valeur < 0, NaN ou infinie -> ValidationMetierException
    }

    /** Vrai si l'indice est strictement supérieur au seuil. */
    public boolean estDepassePar(double indice) {
        throw new UnsupportedOperationException("T0.2 : à implémenter");
    }
}
