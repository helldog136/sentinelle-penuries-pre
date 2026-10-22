package vd.affsn.sentinelle.domain.tension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static vd.affsn.sentinelle.domain.Fixtures.DATE_REF;
import static vd.affsn.sentinelle.domain.Fixtures.ruptureCloturee;
import static vd.affsn.sentinelle.domain.Fixtures.ruptureEnCours;

import java.util.List;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;

/**
 * Formule : poids = 1 + joursEcoules / 7.0 ; indice(région) = somme des poids des ruptures
 * actives qui la touchent. Date de référence : 2026-10-21. Valeurs attendues calculées à la main.
 */
@DisplayName("CalculateurTension")
class CalculateurTensionTest {

    private static final Offset<Double> PRECISION = within(1e-9);

    private final CalculateurTension calculateur = new CalculateurTension();

    @Nested
    @DisplayName("Poids d'une rupture")
    class Poids {

        @ParameterizedTest(name = "début {0} jours avant la référence -> poids {1}")
        @CsvSource({
                "0,   1.0",                  // 1 + 0/7
                "1,   1.142857142857143",    // 1 + 1/7
                "3,   1.4285714285714286",   // 1 + 3/7
                "6,   1.8571428571428572",   // 1 + 6/7 (pas d'arrondi à la semaine)
                "7,   2.0",                  // 1 + 7/7
                "10,  2.4285714285714284",   // 1 + 10/7 = 17/7
                "14,  3.0",                  // 1 + 14/7
                "365, 53.142857142857146"    // 1 + 365/7
        })
        void poidsSelonAnciennete(int joursAvant, double poidsAttendu) {
            Rupture rupture = ruptureEnCours(DATE_REF.minusDays(joursAvant), Region.HAUTE_PAPERASSE);

            assertThat(calculateur.poids(rupture, DATE_REF)).isCloseTo(poidsAttendu, PRECISION);
        }

        @Test
        @DisplayName("jours négatifs (début futur) ramenés à 0 : poids 1.0")
        void joursNegatifsRamenesAZero() {
            Rupture future = ruptureEnCours(DATE_REF.plusDays(5), Region.HAUTE_PAPERASSE);

            assertThat(calculateur.poids(future, DATE_REF)).isCloseTo(1.0, PRECISION);
        }
    }

    @Nested
    @DisplayName("Indice par région")
    class Indice {

        @Test
        @DisplayName("aucune rupture : les six régions à 0.0")
        void aucuneRupture() {
            IndiceTension indice = calculateur.calculer(List.of(), DATE_REF);

            assertThat(indice.dateReference()).isEqualTo(DATE_REF);
            assertThat(indice.parRegion()).containsOnlyKeys(Region.values());
            assertThat(indice.parRegion().values()).containsOnly(0.0);
        }

        @Test
        @DisplayName("une rupture commencée le jour même : 1.0 dans sa région, 0.0 ailleurs")
        void uneRuptureDuJour() {
            IndiceTension indice = calculateur.calculer(
                    List.of(ruptureEnCours(DATE_REF, Region.COTE_DU_TIMEOUT)), DATE_REF);

            assertThat(indice.de(Region.COTE_DU_TIMEOUT)).isCloseTo(1.0, PRECISION);
            assertThat(indice.de(Region.HAUTE_PAPERASSE)).isZero();
            assertThat(indice.parRegion()).hasSize(6);
        }

        @Test
        @DisplayName("une rupture multi-régions compte avec son poids entier dans chacune")
        void multiRegions() {
            // début 7 jours avant -> poids 2.0 dans chacune des trois régions
            Rupture rupture = ruptureEnCours(DATE_REF.minusDays(7),
                    Region.HAUTE_PAPERASSE, Region.BASSE_VALIDATION, Region.PROVINCE_DU_TICKET_OUVERT);

            IndiceTension indice = calculateur.calculer(List.of(rupture), DATE_REF);

            assertThat(indice.de(Region.HAUTE_PAPERASSE)).isCloseTo(2.0, PRECISION);
            assertThat(indice.de(Region.BASSE_VALIDATION)).isCloseTo(2.0, PRECISION);
            assertThat(indice.de(Region.PROVINCE_DU_TICKET_OUVERT)).isCloseTo(2.0, PRECISION);
            assertThat(indice.de(Region.PLATEAU_DU_LEGACY)).isZero();
        }

        @Test
        @DisplayName("scénario complet calculé à la main")
        void scenarioComplet() {
            List<Rupture> ruptures = List.of(
                    // R1 : début 2026-10-14 (7 j) -> 2.0, Haute-Paperasse + Basse-Validation
                    ruptureEnCours(DATE_REF.minusDays(7), Region.HAUTE_PAPERASSE, Region.BASSE_VALIDATION),
                    // R2 : début 2026-10-11 (10 j) -> 17/7, Haute-Paperasse
                    ruptureEnCours(DATE_REF.minusDays(10), Region.HAUTE_PAPERASSE),
                    // R3 : début 2026-10-21 (0 j) -> 1.0, Côte du Timeout
                    ruptureEnCours(DATE_REF, Region.COTE_DU_TIMEOUT),
                    // R4 : clôturée -> ignorée, Plateau du Legacy
                    ruptureCloturee(DATE_REF.minusDays(30), DATE_REF.minusDays(1), Region.PLATEAU_DU_LEGACY),
                    // R5 : commence demain -> ignorée, Marais de la Recette
                    ruptureEnCours(DATE_REF.plusDays(1), Region.MARAIS_DE_LA_RECETTE));

            IndiceTension indice = calculateur.calculer(ruptures, DATE_REF);

            assertThat(indice.de(Region.HAUTE_PAPERASSE)).isCloseTo(31.0 / 7.0, PRECISION); // 2 + 17/7 = 4.428571...
            assertThat(indice.de(Region.BASSE_VALIDATION)).isCloseTo(2.0, PRECISION);
            assertThat(indice.de(Region.COTE_DU_TIMEOUT)).isCloseTo(1.0, PRECISION);
            assertThat(indice.de(Region.PLATEAU_DU_LEGACY)).isZero();
            assertThat(indice.de(Region.MARAIS_DE_LA_RECETTE)).isZero();
            assertThat(indice.de(Region.PROVINCE_DU_TICKET_OUVERT)).isZero();
        }

        @Test
        @DisplayName("rupture clôturée ignorée même si la référence précède sa fin effective")
        void clotureeIgnoreeMemeAvantSaFin() {
            Rupture cloturee = ruptureCloturee(DATE_REF.minusDays(10), DATE_REF.plusDays(10), Region.HAUTE_PAPERASSE);

            IndiceTension indice = calculateur.calculer(List.of(cloturee), DATE_REF);

            assertThat(indice.de(Region.HAUTE_PAPERASSE)).isZero();
        }

        @Test
        @DisplayName("rupture non encore commencée ignorée")
        void futureIgnoree() {
            IndiceTension indice = calculateur.calculer(
                    List.of(ruptureEnCours(DATE_REF.plusDays(1), Region.HAUTE_PAPERASSE)), DATE_REF);

            assertThat(indice.de(Region.HAUTE_PAPERASSE)).isZero();
        }

        @Test
        @DisplayName("le calcul est pur : même entrée, même résultat ; l'entrée n'est pas modifiée")
        void purete() {
            List<Rupture> ruptures = List.of(
                    ruptureEnCours(DATE_REF.minusDays(3), Region.HAUTE_PAPERASSE),
                    ruptureEnCours(DATE_REF.minusDays(14), Region.HAUTE_PAPERASSE, Region.COTE_DU_TIMEOUT));

            IndiceTension premier = calculateur.calculer(ruptures, DATE_REF);
            IndiceTension second = calculateur.calculer(ruptures, DATE_REF);

            assertThat(premier).isEqualTo(second);
            assertThat(premier.de(Region.HAUTE_PAPERASSE)).isCloseTo(1 + 3 / 7.0 + 3.0, PRECISION); // 31/7
            assertThat(premier.de(Region.COTE_DU_TIMEOUT)).isCloseTo(3.0, PRECISION);
            assertThat(ruptures).hasSize(2);
        }

        @Test
        @DisplayName("la même date de référence plus tard alourdit l'indice")
        void lIndiceSAlourditAvecLeTemps() {
            List<Rupture> ruptures = List.of(ruptureEnCours(DATE_REF, Region.HAUTE_PAPERASSE));

            double aujourdhui = calculateur.calculer(ruptures, DATE_REF).de(Region.HAUTE_PAPERASSE);
            double dansUneSemaine = calculateur.calculer(ruptures, DATE_REF.plusDays(7)).de(Region.HAUTE_PAPERASSE);

            assertThat(aujourdhui).isCloseTo(1.0, PRECISION);
            assertThat(dansUneSemaine).isCloseTo(2.0, PRECISION);
        }
    }

    @Nested
    @DisplayName("Régions au-dessus du seuil")
    class AuDessusDuSeuil {

        @Test
        @DisplayName("bout en bout : seuil 2.0 exclut la région à exactement 2.0")
        void seuilStrictDeBoutEnBout() {
            List<Rupture> ruptures = List.of(
                    ruptureEnCours(DATE_REF.minusDays(7), Region.HAUTE_PAPERASSE, Region.BASSE_VALIDATION), // 2.0
                    ruptureEnCours(DATE_REF.minusDays(10), Region.HAUTE_PAPERASSE),                         // 17/7
                    ruptureEnCours(DATE_REF, Region.COTE_DU_TIMEOUT));                                      // 1.0

            IndiceTension indice = calculateur.calculer(ruptures, DATE_REF);

            assertThat(indice.regionsAuDessusDe(new SeuilTension(2.0)))
                    .containsExactly(Region.HAUTE_PAPERASSE);
            assertThat(indice.regionsAuDessusDe(new SeuilTension(0.0)))
                    .containsExactly(Region.HAUTE_PAPERASSE, Region.BASSE_VALIDATION, Region.COTE_DU_TIMEOUT);
        }
    }
}
