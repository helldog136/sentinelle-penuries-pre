package vd.affsn.sentinelle.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import vd.affsn.sentinelle.domain.exception.ValidationMetierException;

@DisplayName("RuptureId")
class RuptureIdTest {

    @Test
    @DisplayName("refuse un UUID null")
    void refuseNull() {
        assertThatThrownBy(() -> new RuptureId(null)).isInstanceOf(ValidationMetierException.class);
    }

    @Test
    @DisplayName("generer() produit des identifiants distincts")
    void genererProduitDesIdentifiantsDistincts() {
        assertThat(RuptureId.generer()).isNotEqualTo(RuptureId.generer());
    }

    @Test
    @DisplayName("depuis(texte) relit la forme canonique produite par toString()")
    void allerRetourTextuel() {
        UUID uuid = UUID.fromString("3f2b8c1e-5d4a-4e6f-9a7b-1c2d3e4f5a6b");
        RuptureId id = new RuptureId(uuid);

        assertThat(id).hasToString("3f2b8c1e-5d4a-4e6f-9a7b-1c2d3e4f5a6b");
        assertThat(RuptureId.depuis(id.toString())).isEqualTo(id);
    }

    @ParameterizedTest(name = "depuis(« {0} ») est refusé")
    @NullAndEmptySource
    @ValueSource(strings = {"pas-un-uuid", "3f2b8c1e-5d4a-4e6f-9a7b", "3f2b8c1e5d4a4e6f9a7b1c2d3e4f5a6b", " 3f2b8c1e-5d4a-4e6f-9a7b-1c2d3e4f5a6b"})
    void depuisRefuseUnTexteInvalide(String texte) {
        assertThatThrownBy(() -> RuptureId.depuis(texte)).isInstanceOf(ValidationMetierException.class);
    }
}
