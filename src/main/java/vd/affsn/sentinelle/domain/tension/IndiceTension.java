package vd.affsn.sentinelle.domain.tension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import vd.affsn.sentinelle.domain.model.Region;

/**
 * Résultat du calcul de tension à une date de référence.
 *
 * <p>Invariants : date non nulle ; {@code parRegion} contient TOUTES les régions (les régions
 * absentes de la map fournie sont complétées à 0.0) ; valeurs finies et >= 0 ; la map exposée
 * est une copie non modifiable, itérée dans l'ordre de déclaration de {@link Region}.
 *
 * <p><b>STUB TDD (T0.2)</b> : validation et comportement à implémenter.
 */
public record IndiceTension(LocalDate dateReference, Map<Region, Double> parRegion) {

    public IndiceTension {
        // TODO T0.2 : validation, complétion à 0.0, copie EnumMap non modifiable
    }

    public double de(Region region) {
        throw new UnsupportedOperationException("T0.2 : à implémenter");
    }

    /**
     * Régions dont l'indice dépasse STRICTEMENT le seuil, dans l'ordre de déclaration de
     * {@link Region}.
     */
    public List<Region> regionsAuDessusDe(SeuilTension seuil) {
        throw new UnsupportedOperationException("T0.2 : à implémenter");
    }
}
