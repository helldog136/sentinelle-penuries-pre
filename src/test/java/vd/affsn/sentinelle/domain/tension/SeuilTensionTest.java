package vd.affsn.sentinelle.domain.tension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import vd.affsn.sentinelle.domain.exception.ValidationMetierException;

@DisplayName("SeuilTension")
class SeuilTensionTest {

    @ParameterizedTest(name = "seuil {0} refusé")
    @ValueSource(doubles = {-0.5, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void seuilsInvalides(double valeur) {
        assertThatThrownBy(() -> new SeuilTension(valeur)).isInstanceOf(ValidationMetierException.class);
    }

    @Test
    @DisplayName("seuil 0 accepté")
    void seuilZeroAccepte() {
        assertThat(new SeuilTension(0.0).valeur()).isZero();
    }

    @Test
    @DisplayName("dépassement strict : égal au seuil ne dépasse pas")
    void depassementStrict() {
        SeuilTension seuil = new SeuilTension(3.0);

        assertThat(seuil.estDepassePar(3.0)).isFalse();
        assertThat(seuil.estDepassePar(2.999)).isFalse();
        assertThat(seuil.estDepassePar(3.0001)).isTrue();
    }
}
