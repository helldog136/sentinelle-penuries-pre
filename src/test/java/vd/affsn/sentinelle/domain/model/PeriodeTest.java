package vd.affsn.sentinelle.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import vd.affsn.sentinelle.domain.exception.ValidationMetierException;

@DisplayName("Periode")
class PeriodeTest {

    private static final LocalDate DEBUT = LocalDate.of(2026, 10, 1);

    @Test
    @DisplayName("sans fin estimée : la fin est absente")
    void sansFinEstimee() {
        Periode periode = Periode.aPartirDu(DEBUT);

        assertThat(periode.debut()).isEqualTo(DEBUT);
        assertThat(periode.finEstimeeOptionnelle()).isEmpty();
    }

    @Test
    @DisplayName("avec fin estimée postérieure : acceptée")
    void avecFinPosterieure() {
        Periode periode = Periode.entre(DEBUT, DEBUT.plusDays(30));

        assertThat(periode.finEstimeeOptionnelle()).contains(DEBUT.plusDays(30));
    }

    @Test
    @DisplayName("fin estimée le jour même du début : acceptée (cas limite)")
    void finLeJourMeme() {
        assertThat(Periode.entre(DEBUT, DEBUT).finEstimeeOptionnelle()).contains(DEBUT);
    }

    @Test
    @DisplayName("fin estimée la veille du début : refusée")
    void finAvantDebutRefusee() {
        assertThatThrownBy(() -> Periode.entre(DEBUT, DEBUT.minusDays(1)))
                .isInstanceOf(ValidationMetierException.class);
    }

    @Test
    @DisplayName("début null : refusé")
    void debutNullRefuse() {
        assertThatThrownBy(() -> Periode.aPartirDu(null)).isInstanceOf(ValidationMetierException.class);
        assertThatThrownBy(() -> new Periode(null, DEBUT)).isInstanceOf(ValidationMetierException.class);
    }

    @Test
    @DisplayName("aCommenceLe : vrai le jour du début et après, faux la veille")
    void aCommenceLe() {
        Periode periode = Periode.aPartirDu(DEBUT);

        assertThat(periode.aCommenceLe(DEBUT.minusDays(1))).isFalse();
        assertThat(periode.aCommenceLe(DEBUT)).isTrue();
        assertThat(periode.aCommenceLe(DEBUT.plusDays(1))).isTrue();
    }
}
