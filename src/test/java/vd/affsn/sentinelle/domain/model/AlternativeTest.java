package vd.affsn.sentinelle.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import vd.affsn.sentinelle.domain.exception.ValidationMetierException;

@DisplayName("Alternative")
class AlternativeTest {

    private static final CodeProduit ALT = new CodeProduit("VMP-654321");

    @Test
    @DisplayName("produit et commentaire court : acceptés, espaces de bord retirés")
    void casNominal() {
        Alternative alternative = new Alternative(ALT, "  Même principe actif, boîte de 30  ");

        assertThat(alternative.produit()).isEqualTo(ALT);
        assertThat(alternative.commentaire()).isEqualTo("Même principe actif, boîte de 30");
    }

    @Test
    @DisplayName("commentaire null : normalisé en chaîne vide")
    void commentaireNullNormalise() {
        assertThat(Alternative.sansCommentaire(ALT).commentaire()).isEmpty();
        assertThat(new Alternative(ALT, null).commentaire()).isEmpty();
    }

    @Test
    @DisplayName("commentaire de exactement 280 caractères : accepté (borne)")
    void commentaireALaBorne() {
        String commentaire = "a".repeat(Alternative.LONGUEUR_MAX_COMMENTAIRE);

        assertThat(new Alternative(ALT, commentaire).commentaire()).hasSize(280);
    }

    @Test
    @DisplayName("commentaire de 281 caractères : refusé")
    void commentaireTropLong() {
        String commentaire = "a".repeat(Alternative.LONGUEUR_MAX_COMMENTAIRE + 1);

        assertThatThrownBy(() -> new Alternative(ALT, commentaire))
                .isInstanceOf(ValidationMetierException.class);
    }

    @Test
    @DisplayName("la borne s'applique après retrait des espaces de bord")
    void borneApresStrip() {
        String commentaire = "  " + "a".repeat(Alternative.LONGUEUR_MAX_COMMENTAIRE) + "  ";

        assertThat(new Alternative(ALT, commentaire).commentaire()).hasSize(280);
    }

    @ParameterizedTest(name = "caractère de contrôle refusé : {0}")
    @ValueSource(strings = {"ligne 1\nligne 2", "avec\rretour", "avec\ttabulation", "nul\u0000"})
    void caracteresDeControleRefuses(String commentaire) {
        assertThatThrownBy(() -> new Alternative(ALT, commentaire))
                .isInstanceOf(ValidationMetierException.class);
    }

    @Test
    @DisplayName("produit null : refusé")
    void produitNullRefuse() {
        assertThatThrownBy(() -> new Alternative(null, "commentaire"))
                .isInstanceOf(ValidationMetierException.class);
    }
}
