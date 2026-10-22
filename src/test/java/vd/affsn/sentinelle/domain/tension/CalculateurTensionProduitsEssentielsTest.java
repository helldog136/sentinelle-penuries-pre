package vd.affsn.sentinelle.domain.tension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static vd.affsn.sentinelle.domain.Fixtures.ACTEUR_A;
import static vd.affsn.sentinelle.domain.Fixtures.DISTRIBUTEUR_A;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.domain.model.StatutRupture;

/**
 * ADR-004 § 4 : une rupture d'un produit essentiel compte double dans l'indice de tension.
 * Cas d'acceptation AC-48 à AC-55, AC-59 et AC-60 au niveau du service de domaine pur.
 *
 * <p>Conventions de l'ADR : référentiel {@code VMP-100001} et {@code VMP-300003} essentiels,
 * {@code VMP-200002}, {@code VMP-400004} et {@code VMP-999999} absents ; date de référence
 * {@code D} = 2026-11-30 ; chaque rupture est {@code EN_COURS} et touche la seule région
 * {@code HAUTE_PAPERASSE} sauf mention contraire ; comparaison à 1e-9 près.
 */
@DisplayName("CalculateurTension, produits essentiels (ADR-004 AC-48..AC-55, AC-59, AC-60)")
class CalculateurTensionProduitsEssentielsTest {

    private static final Offset<Double> PRECISION = within(1e-9);

    private static final LocalDate D = LocalDate.of(2026, 11, 30);

    private static final CodeProduit VMP_100001 = CodeProduit.de("VMP-100001");
    private static final CodeProduit VMP_200002 = CodeProduit.de("VMP-200002");
    private static final CodeProduit VMP_300003 = CodeProduit.de("VMP-300003");
    private static final CodeProduit VMP_400004 = CodeProduit.de("VMP-400004");
    private static final CodeProduit VMP_999999 = CodeProduit.de("VMP-999999");

    /** Référentiel des conventions de l'ADR, résolu par l'application et fourni au domaine. */
    private static final Predicate<CodeProduit> REFERENTIEL = Set.of(VMP_100001, VMP_300003)::contains;

    private final CalculateurTension calculateur = new CalculateurTension();

    // ---- fixtures locales (Fixtures.ruptureEnCours impose un produit unique) ----

    private static Rupture enCours(CodeProduit produit, LocalDate debut, List<Alternative> alternatives,
                                   Region premiere, Region... autres) {
        return Rupture.declarer(RuptureId.generer(), produit, ACTEUR_A, Periode.aPartirDu(debut),
                Motif.PRODUCTION, EnumSet.of(premiere, autres), alternatives);
    }

    private static Rupture enCours(CodeProduit produit, LocalDate debut) {
        return enCours(produit, debut, List.of(), Region.HAUTE_PAPERASSE);
    }

    private static Rupture cloturee(CodeProduit produit, LocalDate debut, LocalDate finEffective) {
        return Rupture.reconstituer(RuptureId.generer(), produit, DISTRIBUTEUR_A, Periode.aPartirDu(debut),
                Motif.LOGISTIQUE, EnumSet.of(Region.HAUTE_PAPERASSE), List.of(),
                StatutRupture.CLOTUREE, finEffective);
    }

    private static void assertAutresRegionsNulles(IndiceTension indice, Region... touchees) {
        Set<Region> exclues = touchees.length == 0 ? EnumSet.noneOf(Region.class) : EnumSet.of(touchees[0], touchees);
        assertThat(indice.parRegion()).containsOnlyKeys(Region.values());
        for (Region region : Region.values()) {
            if (!exclues.contains(region)) {
                assertThat(indice.de(region)).as("région %s", region).isZero();
            }
        }
    }

    @Nested
    @DisplayName("calculer(ruptures, D, estEssentiel)")
    class CalculPondere {

        @Test
        @DisplayName("AC-48 : VMP-200002 (non essentiel) début 2026-11-16 (14 j) -> 3.0")
        void ac48_nonEssentielInchange() {
            IndiceTension indice = calculateur.calculer(List.of(enCours(VMP_200002, LocalDate.of(2026, 11, 16))),
                    D, REFERENTIEL);

            assertThat(indice.dateReference()).isEqualTo(D);
            assertThat(indice.de(Region.HAUTE_PAPERASSE)).isCloseTo(3.0, PRECISION);
            assertAutresRegionsNulles(indice, Region.HAUTE_PAPERASSE);
        }

        @Test
        @DisplayName("AC-49 : VMP-100001 (essentiel) début 2026-11-16 (14 j) -> 6.0")
        void ac49_essentielQuatorzeJours() {
            IndiceTension indice = calculateur.calculer(List.of(enCours(VMP_100001, LocalDate.of(2026, 11, 16))),
                    D, REFERENTIEL);

            assertThat(indice.de(Region.HAUTE_PAPERASSE)).isCloseTo(6.0, PRECISION);
            assertAutresRegionsNulles(indice, Region.HAUTE_PAPERASSE);
        }

        @Test
        @DisplayName("AC-50 : VMP-100001 début 2026-11-30 (0 j) -> 2.0, pas 1.0 (× 2 sur le poids entier)")
        void ac50_essentielDuJour() {
            IndiceTension indice = calculateur.calculer(List.of(enCours(VMP_100001, D)), D, REFERENTIEL);

            assertThat(indice.de(Region.HAUTE_PAPERASSE)).isCloseTo(2.0, PRECISION);
        }

        @Test
        @DisplayName("AC-51 : VMP-100001 début 2026-11-23 (7 j) -> 4.0")
        void ac51_essentielSeptJours() {
            IndiceTension indice = calculateur.calculer(List.of(enCours(VMP_100001, LocalDate.of(2026, 11, 23))),
                    D, REFERENTIEL);

            assertThat(indice.de(Region.HAUTE_PAPERASSE)).isCloseTo(4.0, PRECISION);
        }

        @Test
        @DisplayName("AC-52 : VMP-100001 début 2026-11-20 (10 j) -> 34/7 = 2 × (1 + 10/7)")
        void ac52_essentielDixJours() {
            IndiceTension indice = calculateur.calculer(List.of(enCours(VMP_100001, LocalDate.of(2026, 11, 20))),
                    D, REFERENTIEL);

            assertThat(indice.de(Region.HAUTE_PAPERASSE))
                    .isCloseTo(34.0 / 7.0, PRECISION)
                    .isCloseTo(4.857142857142857, PRECISION);
        }

        @Test
        @DisplayName("AC-53 : VMP-100001 début 2026-12-01 (postérieure à D) -> 0.0")
        void ac53_essentielFuturIgnore() {
            IndiceTension indice = calculateur.calculer(List.of(enCours(VMP_100001, LocalDate.of(2026, 12, 1))),
                    D, REFERENTIEL);

            assertThat(indice.de(Region.HAUTE_PAPERASSE)).isZero();
            assertAutresRegionsNulles(indice);
        }

        @Test
        @DisplayName("AC-54 : VMP-100001 début 2026-11-16, clôturée le 2026-11-20 -> 0.0")
        void ac54_essentielClotureeIgnoree() {
            IndiceTension indice = calculateur.calculer(
                    List.of(cloturee(VMP_100001, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20))),
                    D, REFERENTIEL);

            assertThat(indice.de(Region.HAUTE_PAPERASSE)).isZero();
            assertAutresRegionsNulles(indice);
        }

        @Test
        @DisplayName("AC-55 : VMP-100001 début 2026-11-16 sur HAUTE_PAPERASSE et COTE_DU_TIMEOUT -> 6.0 chacune, 0.0 ailleurs")
        void ac55_essentielMultiRegions() {
            Rupture rupture = enCours(VMP_100001, LocalDate.of(2026, 11, 16), List.of(),
                    Region.HAUTE_PAPERASSE, Region.COTE_DU_TIMEOUT);

            IndiceTension indice = calculateur.calculer(List.of(rupture), D, REFERENTIEL);

            assertThat(indice.de(Region.HAUTE_PAPERASSE)).isCloseTo(6.0, PRECISION);
            assertThat(indice.de(Region.COTE_DU_TIMEOUT)).isCloseTo(6.0, PRECISION);
            assertAutresRegionsNulles(indice, Region.HAUTE_PAPERASSE, Region.COTE_DU_TIMEOUT);
        }

        @Test
        @DisplayName("AC-59 : VMP-999999 (absent du référentiel) début 2026-11-16 -> 3.0")
        void ac59_produitInconnuNonEssentiel() {
            IndiceTension indice = calculateur.calculer(List.of(enCours(VMP_999999, LocalDate.of(2026, 11, 16))),
                    D, REFERENTIEL);

            assertThat(indice.de(Region.HAUTE_PAPERASSE)).isCloseTo(3.0, PRECISION);
        }

        @Test
        @DisplayName("AC-60 : deux VMP-100001 début 2026-11-16, avec et sans alternative -> 12.0")
        void ac60_alternativesSansEffetSurLePoids() {
            Rupture avecAlternative = enCours(VMP_100001, LocalDate.of(2026, 11, 16),
                    List.of(Alternative.sansCommentaire(VMP_400004)), Region.HAUTE_PAPERASSE);
            Rupture sansAlternative = enCours(VMP_100001, LocalDate.of(2026, 11, 16));

            IndiceTension indice = calculateur.calculer(List.of(avecAlternative, sansAlternative), D, REFERENTIEL);

            assertThat(indice.de(Region.HAUTE_PAPERASSE)).isCloseTo(12.0, PRECISION);
        }

        @Test
        @DisplayName("référentiel vide : identique à l'ancien calcul sur les mêmes données")
        void referentielVideEquivautAuCalculHistorique() {
            List<Rupture> ruptures = List.of(
                    enCours(VMP_200002, LocalDate.of(2026, 11, 16)),
                    enCours(VMP_100001, LocalDate.of(2026, 11, 16), List.of(),
                            Region.HAUTE_PAPERASSE, Region.BASSE_VALIDATION),
                    enCours(VMP_300003, D));

            IndiceTension pondere = calculateur.calculer(ruptures, D, code -> false);

            assertThat(pondere).isEqualTo(calculateur.calculer(ruptures, D));
            assertThat(pondere.de(Region.HAUTE_PAPERASSE)).isCloseTo(7.0, PRECISION); // 3 + 3 + 1
        }

        @Test
        @DisplayName("aucune rupture : les six régions à 0.0")
        void aucuneRupture() {
            IndiceTension indice = calculateur.calculer(List.of(), D, REFERENTIEL);

            assertThat(indice.dateReference()).isEqualTo(D);
            assertAutresRegionsNulles(indice);
        }
    }

    @Nested
    @DisplayName("poids(rupture, D, essentiel)")
    class PoidsPondere {

        @ParameterizedTest(name = "AC-{0} : début {1} -> poids essentiel {2}")
        @CsvSource({
                "49, 2026-11-16, 6.0",
                "50, 2026-11-30, 2.0",
                "51, 2026-11-23, 4.0",
                "52, 2026-11-20, 4.857142857142857"
        })
        @DisplayName("essentiel = true : (1 + jours / 7) × 2")
        void poidsEssentiel(String ac, LocalDate debut, double attendu) {
            assertThat(calculateur.poids(enCours(VMP_100001, debut), D, true)).isCloseTo(attendu, PRECISION);
        }

        @Test
        @DisplayName("AC-52 : poids essentiel à 10 jours = 34/7 exactement à 1e-9")
        void poidsEssentielDixJours() {
            assertThat(calculateur.poids(enCours(VMP_100001, LocalDate.of(2026, 11, 20)), D, true))
                    .isCloseTo(34.0 / 7.0, PRECISION);
        }

        @ParameterizedTest(name = "AC-{0} : début {1} -> poids non essentiel {2}")
        @CsvSource({
                "48, 2026-11-16, 3.0",
                "--, 2026-11-30, 1.0",
                "--, 2026-11-23, 2.0",
                "--, 2026-11-20, 2.4285714285714284"
        })
        @DisplayName("essentiel = false : 1 + jours / 7 (inchangé)")
        void poidsNonEssentiel(String ac, LocalDate debut, double attendu) {
            assertThat(calculateur.poids(enCours(VMP_200002, debut), D, false)).isCloseTo(attendu, PRECISION);
        }
    }

    @Nested
    @DisplayName("Non-régression des méthodes existantes")
    class NonRegression {

        @ParameterizedTest(name = "début {0} -> poids {1}")
        @CsvSource({
                "2026-11-16, 3.0",
                "2026-11-30, 1.0",
                "2026-11-23, 2.0",
                "2026-11-20, 2.4285714285714284"
        })
        @DisplayName("poids(rupture, D) ignore le caractère essentiel (1 + jours / 7)")
        void poidsHistoriqueInchange(LocalDate debut, double attendu) {
            assertThat(calculateur.poids(enCours(VMP_100001, debut), D)).isCloseTo(attendu, PRECISION);
        }

        @Test
        @DisplayName("poids(rupture, D, false) == poids(rupture, D)")
        void poidsNonEssentielEgalPoidsHistorique() {
            Rupture rupture = enCours(VMP_100001, LocalDate.of(2026, 11, 20));

            assertThat(calculateur.poids(rupture, D, false)).isEqualTo(calculateur.poids(rupture, D));
        }

        @Test
        @DisplayName("calculer(ruptures, D) ne pondère pas : VMP-100001 à 14 j -> 3.0")
        void calculHistoriqueSansPonderation() {
            IndiceTension indice = calculateur.calculer(List.of(enCours(VMP_100001, LocalDate.of(2026, 11, 16))), D);

            assertThat(indice.de(Region.HAUTE_PAPERASSE)).isCloseTo(3.0, PRECISION);
        }

        @Test
        @DisplayName("calculer(ruptures, D) sur les données d'AC-56 -> 7.0 (3 + 3 + 1)")
        void calculHistoriqueDonneesAc56() {
            List<Rupture> ruptures = List.of(
                    enCours(VMP_200002, LocalDate.of(2026, 11, 16)),
                    enCours(VMP_100001, LocalDate.of(2026, 11, 16)),
                    enCours(VMP_300003, D));

            IndiceTension indice = calculateur.calculer(ruptures, D);

            assertThat(indice.de(Region.HAUTE_PAPERASSE)).isCloseTo(7.0, PRECISION);
            assertAutresRegionsNulles(indice, Region.HAUTE_PAPERASSE);
        }

        @Test
        @DisplayName("calculer(ruptures, D) : clôturée et future toujours ignorées, multi-régions entier")
        void calculHistoriqueFiltrageEtMultiRegions() {
            List<Rupture> ruptures = List.of(
                    cloturee(VMP_100001, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20)),
                    enCours(VMP_100001, LocalDate.of(2026, 12, 1)),
                    enCours(VMP_100001, LocalDate.of(2026, 11, 16), List.of(),
                            Region.HAUTE_PAPERASSE, Region.COTE_DU_TIMEOUT));

            IndiceTension indice = calculateur.calculer(ruptures, D);

            assertThat(indice.de(Region.HAUTE_PAPERASSE)).isCloseTo(3.0, PRECISION);
            assertThat(indice.de(Region.COTE_DU_TIMEOUT)).isCloseTo(3.0, PRECISION);
            assertAutresRegionsNulles(indice, Region.HAUTE_PAPERASSE, Region.COTE_DU_TIMEOUT);
        }
    }
}
