package vd.affsn.sentinelle.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import vd.affsn.sentinelle.domain.exception.ValidationMetierException;

@DisplayName("Acteur")
class ActeurTest {

    private static final IdentifiantDistributeur PRO_A = new IdentifiantDistributeur("PRO-000001");
    private static final IdentifiantDistributeur PRO_B = new IdentifiantDistributeur("PRO-000002");

    @Test
    @DisplayName("un distributeur porte son identifiant de rattachement")
    void distributeurRattache() {
        Acteur acteur = Acteur.distributeur("sujet-1", PRO_A);

        assertThat(acteur.role()).isEqualTo(Role.DISTRIBUTEUR);
        assertThat(acteur.identifiantDistributeur()).contains(PRO_A);
    }

    @Test
    @DisplayName("un pharmacien ou un prescripteur n'a pas d'identifiant distributeur")
    void nonDistributeurSansRattachement() {
        assertThat(Acteur.pharmacien("sujet-2").identifiantDistributeur()).isEmpty();
        assertThat(Acteur.prescripteur("sujet-3").identifiantDistributeur()).isEmpty();
    }

    @Test
    @DisplayName("DISTRIBUTEUR sans identifiant : refusé")
    void distributeurSansIdentifiantRefuse() {
        assertThatThrownBy(() -> Acteur.distributeur("sujet-1", null))
                .isInstanceOf(ValidationMetierException.class);
    }

    @ParameterizedTest(name = "{0} avec identifiant distributeur : refusé")
    @EnumSource(value = Role.class, names = {"PHARMACIEN", "PRESCRIPTEUR"})
    void nonDistributeurAvecIdentifiantRefuse(Role role) {
        assertThatThrownBy(() -> new Acteur("sujet-1", role, PRO_A))
                .isInstanceOf(ValidationMetierException.class);
    }

    @ParameterizedTest(name = "sujet invalide « {0} » : refusé")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void sujetInvalideRefuse(String sujet) {
        assertThatThrownBy(() -> Acteur.pharmacien(sujet)).isInstanceOf(ValidationMetierException.class);
    }

    @Test
    @DisplayName("sujet de 128 caractères accepté, 129 refusé")
    void borneSujet() {
        assertThat(Acteur.pharmacien("s".repeat(Acteur.LONGUEUR_MAX_SUJET)).sujet()).hasSize(128);
        assertThatThrownBy(() -> Acteur.pharmacien("s".repeat(Acteur.LONGUEUR_MAX_SUJET + 1)))
                .isInstanceOf(ValidationMetierException.class);
    }

    @Test
    @DisplayName("rôle null : refusé")
    void roleNullRefuse() {
        assertThatThrownBy(() -> new Acteur("sujet-1", null, null)).isInstanceOf(ValidationMetierException.class);
    }

    @Test
    @DisplayName("agitPour : vrai seulement pour le distributeur de rattachement")
    void agitPour() {
        assertThat(Acteur.distributeur("sujet-1", PRO_A).agitPour(PRO_A)).isTrue();
        assertThat(Acteur.distributeur("sujet-1", PRO_A).agitPour(PRO_B)).isFalse();
        assertThat(Acteur.pharmacien("sujet-2").agitPour(PRO_A)).isFalse();
    }
}
