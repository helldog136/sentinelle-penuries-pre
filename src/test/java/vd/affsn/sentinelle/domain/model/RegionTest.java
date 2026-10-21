package vd.affsn.sentinelle.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Region")
class RegionTest {

    @Test
    @DisplayName("six régions, dans l'ordre officiel, avec leur libellé d'affichage")
    void libelles() {
        assertThat(Region.values())
                .extracting(Region::libelle)
                .containsExactly(
                        "Haute-Paperasse",
                        "Basse-Validation",
                        "Plateau du Legacy",
                        "Côte du Timeout",
                        "Marais de la Recette",
                        "Province du Ticket Ouvert");
    }
}
