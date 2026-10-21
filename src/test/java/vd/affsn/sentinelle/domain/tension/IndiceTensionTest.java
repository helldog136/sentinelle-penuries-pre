package vd.affsn.sentinelle.domain.tension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import vd.affsn.sentinelle.domain.exception.ValidationMetierException;
import vd.affsn.sentinelle.domain.model.Region;

@DisplayName("IndiceTension")
class IndiceTensionTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 21);

    private static IndiceTension indice(Map<Region, Double> valeurs) {
        return new IndiceTension(DATE, valeurs);
    }

    @Test
    @DisplayName("les régions absentes sont complétées à 0.0 ; ordre de l'énumération")
    void completionAZero() {
        IndiceTension indice = indice(Map.of(Region.COTE_DU_TIMEOUT, 1.5));

        assertThat(indice.parRegion()).containsOnlyKeys(Region.values());
        assertThat(indice.parRegion().keySet()).containsExactly(Region.values());
        assertThat(indice.de(Region.COTE_DU_TIMEOUT)).isEqualTo(1.5);
        assertThat(indice.de(Region.HAUTE_PAPERASSE)).isZero();
    }

    @Test
    @DisplayName("la map exposée est une copie non modifiable")
    void copieNonModifiable() {
        Map<Region, Double> source = new HashMap<>(Map.of(Region.HAUTE_PAPERASSE, 3.0));
        IndiceTension indice = indice(source);

        source.put(Region.HAUTE_PAPERASSE, 99.0);

        assertThat(indice.de(Region.HAUTE_PAPERASSE)).isEqualTo(3.0);
        assertThatThrownBy(() -> indice.parRegion().put(Region.HAUTE_PAPERASSE, 0.0))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest(name = "valeur {0} refusée")
    @ValueSource(doubles = {-0.0001, -1.0, Double.NaN, Double.POSITIVE_INFINITY})
    void valeursInvalides(double valeur) {
        assertThatThrownBy(() -> indice(Map.of(Region.HAUTE_PAPERASSE, valeur)))
                .isInstanceOf(ValidationMetierException.class);
    }

    @Test
    @DisplayName("date ou map null : refusées")
    void nullsRefuses() {
        assertThatThrownBy(() -> new IndiceTension(null, Map.of())).isInstanceOf(ValidationMetierException.class);
        assertThatThrownBy(() -> new IndiceTension(DATE, null)).isInstanceOf(ValidationMetierException.class);
    }

    @Test
    @DisplayName("seuil STRICT : une région égale au seuil n'est pas retenue")
    void seuilStrict() {
        Map<Region, Double> valeurs = new EnumMap<>(Region.class);
        valeurs.put(Region.HAUTE_PAPERASSE, 5.0);
        valeurs.put(Region.BASSE_VALIDATION, 5.0000001);
        valeurs.put(Region.PLATEAU_DU_LEGACY, 4.9999999);
        valeurs.put(Region.PROVINCE_DU_TICKET_OUVERT, 12.0);

        assertThat(indice(valeurs).regionsAuDessusDe(new SeuilTension(5.0)))
                .containsExactly(Region.BASSE_VALIDATION, Region.PROVINCE_DU_TICKET_OUVERT);
    }

    @Test
    @DisplayName("seuil 0 : les régions à 0.0 ne sont pas retenues")
    void seuilZero() {
        assertThat(indice(Map.of(Region.MARAIS_DE_LA_RECETTE, 1.0)).regionsAuDessusDe(new SeuilTension(0.0)))
                .containsExactly(Region.MARAIS_DE_LA_RECETTE);
        assertThat(indice(Map.of()).regionsAuDessusDe(new SeuilTension(0.0))).isEmpty();
    }

    @Test
    @DisplayName("résultat dans l'ordre de l'énumération, pas par valeur décroissante")
    void ordreDeLEnumeration() {
        Map<Region, Double> valeurs = new EnumMap<>(Region.class);
        valeurs.put(Region.PROVINCE_DU_TICKET_OUVERT, 50.0);
        valeurs.put(Region.HAUTE_PAPERASSE, 3.0);
        valeurs.put(Region.COTE_DU_TIMEOUT, 10.0);

        assertThat(indice(valeurs).regionsAuDessusDe(new SeuilTension(1.0)))
                .containsExactly(Region.HAUTE_PAPERASSE, Region.COTE_DU_TIMEOUT, Region.PROVINCE_DU_TICKET_OUVERT);
    }
}
