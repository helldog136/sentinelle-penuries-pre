package vd.affsn.sentinelle.domain.tension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import vd.affsn.sentinelle.domain.exception.ValidationMetierException;

import vd.affsn.sentinelle.domain.model.Region;

/**
 * Résultat du calcul de tension à une date de référence.
 *
 * <p>Invariants : date non nulle ; {@code parRegion} contient TOUTES les régions (les régions
 * absentes de la map fournie sont complétées à 0.0) ; valeurs finies et >= 0 ; la map exposée
 * est une copie non modifiable, itérée dans l'ordre de déclaration de {@link Region}.
 */
public record IndiceTension(LocalDate dateReference, Map<Region, Double> parRegion) {

    public IndiceTension {
        if (dateReference == null || parRegion == null) {
            throw new ValidationMetierException("Indice de tension : date et valeurs obligatoires");
        }
        Map<Region, Double> complet = new EnumMap<>(Region.class);
        for (Region region : Region.values()) {
            Double valeur = parRegion.getOrDefault(region, 0.0);
            if (valeur == null || !Double.isFinite(valeur) || valeur < 0) {
                throw new ValidationMetierException("Indice de tension : valeur finie >= 0 attendue");
            }
            complet.put(region, valeur);
        }
        parRegion = Collections.unmodifiableMap(complet);
    }

    public double de(Region region) {
        return parRegion.get(region);
    }

    /**
     * Régions dont l'indice dépasse STRICTEMENT le seuil, dans l'ordre de déclaration de
     * {@link Region}.
     */
    public List<Region> regionsAuDessusDe(SeuilTension seuil) {
        return parRegion.entrySet().stream()
                .filter(entree -> seuil.estDepassePar(entree.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }
}
