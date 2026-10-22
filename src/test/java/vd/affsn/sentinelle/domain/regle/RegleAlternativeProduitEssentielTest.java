package vd.affsn.sentinelle.domain.regle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import vd.affsn.sentinelle.domain.exception.ValidationMetierException;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.Periode;

/**
 * ADR-004, cas d'acceptation AC-01 à AC-14 au niveau de la règle pure.
 *
 * <p>La règle reçoit des faits déjà résolus (« le produit est essentiel ») : le référentiel
 * des conventions de l'ADR ({@code VMP-100001} et {@code VMP-300003} essentiels,
 * {@code VMP-200002}, {@code VMP-400004} et {@code VMP-999999} non essentiels) se traduit
 * donc ici par le booléen passé à {@link RegleAlternativeProduitEssentiel#verifier}.
 *
 * <p>« Refusé » au niveau du domaine = {@link ValidationMetierException} portant exactement
 * {@link RegleAlternativeProduitEssentiel#MESSAGE} (traduite en {@code INVALID_ARGUMENT} au bord).
 */
@DisplayName("RegleAlternativeProduitEssentiel (ADR-004 AC-01..AC-14)")
class RegleAlternativeProduitEssentielTest {

    /** {@code MSG} des cas d'acceptation, recopié littéralement de l'ADR. */
    private static final String MSG =
            "alternatives: au moins une alternative requise (produit essentiel, rupture de plus de 30 jours)";

    private static final boolean ESSENTIEL = true;
    private static final boolean NON_ESSENTIEL = false;

    private static final LocalDate DEBUT = LocalDate.of(2026, 11, 1);

    /** Alternative non essentielle des conventions (VMP-400004). */
    private static final Alternative ALT_NON_ESSENTIELLE =
            Alternative.sansCommentaire(CodeProduit.de("VMP-400004"));
    /** Alternative elle-même essentielle (VMP-300003). */
    private static final Alternative ALT_ESSENTIELLE =
            Alternative.sansCommentaire(CodeProduit.de("VMP-300003"));

    private final RegleAlternativeProduitEssentiel regle = new RegleAlternativeProduitEssentiel();

    private void assertAcceptee(boolean essentiel, Periode periode, List<Alternative> alternatives) {
        assertThatCode(() -> regle.verifier(essentiel, periode, alternatives))
                .doesNotThrowAnyException();
    }

    private void assertRefusee(boolean essentiel, Periode periode, List<Alternative> alternatives) {
        assertThatThrownBy(() -> regle.verifier(essentiel, periode, alternatives))
                .isExactlyInstanceOf(ValidationMetierException.class)
                .hasMessage(MSG);
    }

    @Nested
    @DisplayName("Contrat de la classe (non-régression du squelette)")
    class Contrat {

        @Test
        @DisplayName("MESSAGE est exactement le MSG de l'ADR (§ 3)")
        void messageExact() {
            assertThat(RegleAlternativeProduitEssentiel.MESSAGE).isEqualTo(MSG);
        }

        @Test
        @DisplayName("MESSAGE respecte le format « champ: raison » sur le champ alternatives")
        void messageAuFormatChampRaison() {
            assertThat(RegleAlternativeProduitEssentiel.MESSAGE).startsWith("alternatives: ");
        }

        @Test
        @DisplayName("le seuil est de 30 jours")
        void seuilTrenteJours() {
            assertThat(RegleAlternativeProduitEssentiel.SEUIL_JOURS).isEqualTo(30L);
        }
    }

    @Nested
    @DisplayName("Produit essentiel : borne des 30 jours")
    class Borne {

        @Test
        @DisplayName("AC-01 : début 2026-11-01, fin 2026-12-01 (30 j), 0 alternative -> acceptée")
        void ac01_trenteJoursAcceptee() {
            assertAcceptee(ESSENTIEL, Periode.entre(DEBUT, LocalDate.of(2026, 12, 1)), List.of());
        }

        @Test
        @DisplayName("AC-02 : début 2026-11-01, fin 2026-12-02 (31 j), 0 alternative -> refusée (MSG)")
        void ac02_trenteEtUnJoursRefusee() {
            assertRefusee(ESSENTIEL, Periode.entre(DEBUT, LocalDate.of(2026, 12, 2)), List.of());
        }

        @Test
        @DisplayName("AC-03 : comme AC-02 avec 1 alternative VMP-400004 -> acceptée")
        void ac03_trenteEtUnJoursAvecAlternativeAcceptee() {
            assertAcceptee(ESSENTIEL, Periode.entre(DEBUT, LocalDate.of(2026, 12, 2)),
                    List.of(ALT_NON_ESSENTIELLE));
        }

        @Test
        @DisplayName("AC-06 : début = fin = 2026-11-01 (durée nulle), 0 alternative -> acceptée")
        void ac06_dureeNulleAcceptee() {
            assertAcceptee(ESSENTIEL, Periode.entre(DEBUT, DEBUT), List.of());
        }

        @ParameterizedTest(name = "écart de {0} jour(s) -> acceptée")
        @CsvSource({"0", "1", "15", "29", "30"})
        @DisplayName("AC-01/AC-06 : tout écart de 0 à 30 jours est accepté sans alternative")
        void ecartsCourtsAcceptes(int jours) {
            assertAcceptee(ESSENTIEL, Periode.entre(DEBUT, DEBUT.plusDays(jours)), List.of());
        }

        @ParameterizedTest(name = "écart de {0} jours -> refusée")
        @CsvSource({"31", "32", "60", "365"})
        @DisplayName("AC-02 : tout écart strictement supérieur à 30 jours est refusé sans alternative")
        void ecartsLongsRefuses(int jours) {
            assertRefusee(ESSENTIEL, Periode.entre(DEBUT, DEBUT.plusDays(jours)), List.of());
        }
    }

    @Nested
    @DisplayName("Produit essentiel : fin estimée absente")
    class SansFin {

        @Test
        @DisplayName("AC-04 : début 2026-11-01, sans fin estimée, 0 alternative -> refusée (MSG)")
        void ac04_sansFinRefusee() {
            assertRefusee(ESSENTIEL, Periode.aPartirDu(DEBUT), List.of());
        }

        @Test
        @DisplayName("AC-05 : comme AC-04 avec 1 alternative -> acceptée")
        void ac05_sansFinAvecAlternativeAcceptee() {
            assertAcceptee(ESSENTIEL, Periode.aPartirDu(DEBUT), List.of(ALT_NON_ESSENTIELLE));
        }

        @Test
        @DisplayName("AC-10 : début passé 2026-09-01, sans fin estimée, 0 alternative -> refusée (durée indépendante du jour)")
        void ac10_debutPasseSansFinRefusee() {
            assertRefusee(ESSENTIEL, Periode.aPartirDu(LocalDate.of(2026, 9, 1)), List.of());
        }
    }

    @Nested
    @DisplayName("Arithmétique calendaire (plusDays(30))")
    class Calendrier {

        @Test
        @DisplayName("AC-08 : février 2027, fin 2027-03-03 (début + 30 j) -> acceptée")
        void ac08_fevrier2027BorneAcceptee() {
            assertAcceptee(ESSENTIEL,
                    Periode.entre(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 3)), List.of());
        }

        @Test
        @DisplayName("AC-08 : février 2027, fin 2027-03-04 (début + 31 j) -> refusée (MSG)")
        void ac08_fevrier2027AuDelaRefusee() {
            assertRefusee(ESSENTIEL,
                    Periode.entre(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 4)), List.of());
        }

        @Test
        @DisplayName("AC-09 : février 2028 (bissextile), fin 2028-03-02 (début + 30 j) -> acceptée")
        void ac09_fevrier2028BorneAcceptee() {
            assertAcceptee(ESSENTIEL,
                    Periode.entre(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 3, 2)), List.of());
        }

        @Test
        @DisplayName("AC-09 : février 2028 (bissextile), fin 2028-03-03 (début + 31 j) -> refusée (MSG)")
        void ac09_fevrier2028AuDelaRefusee() {
            assertRefusee(ESSENTIEL,
                    Periode.entre(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 3, 3)), List.of());
        }

        @Test
        @DisplayName("garde-fou des données : les fins choisies sont bien début + 30 et début + 31 jours")
        void donneesCalendairesCoherentes() {
            assertThat(LocalDate.of(2026, 11, 1).plusDays(30)).isEqualTo(LocalDate.of(2026, 12, 1));
            assertThat(LocalDate.of(2027, 2, 1).plusDays(30)).isEqualTo(LocalDate.of(2027, 3, 3));
            assertThat(LocalDate.of(2028, 2, 1).plusDays(30)).isEqualTo(LocalDate.of(2028, 3, 2));
        }
    }

    @Nested
    @DisplayName("Nature des alternatives et message")
    class AlternativesEtMessage {

        @Test
        @DisplayName("AC-11 : rupture longue, 1 seule alternative elle-même essentielle (VMP-300003) -> acceptée")
        void ac11_alternativeEssentielleSatisfaitLaRegle() {
            assertAcceptee(ESSENTIEL, Periode.entre(DEBUT, LocalDate.of(2026, 12, 2)), List.of(ALT_ESSENTIELLE));
        }

        @Test
        @DisplayName("AC-12 : rupture longue refusée -> description égale à MSG, une seule fois")
        void ac12_messageUneSeuleFois() {
            assertThatThrownBy(() -> regle.verifier(ESSENTIEL, Periode.aPartirDu(DEBUT), List.of()))
                    .isExactlyInstanceOf(ValidationMetierException.class)
                    .satisfies(e -> {
                        assertThat(e.getMessage()).isEqualTo(MSG);
                        assertThat(e.getMessage().split("alternatives:", -1)).hasSize(2);
                    });
        }

        @Test
        @DisplayName("AC-12 : la même rupture longue avec 1 alternative -> acceptée")
        void ac12_uneAlternativeSuffit() {
            assertAcceptee(ESSENTIEL, Periode.aPartirDu(DEBUT), List.of(ALT_NON_ESSENTIELLE));
        }

        @Test
        @DisplayName("plusieurs alternatives (jusqu'à la borne v1 de 20) -> acceptée")
        void plusieursAlternativesAcceptees() {
            List<Alternative> vingt = java.util.stream.IntStream.rangeClosed(1, 20)
                    .mapToObj(i -> Alternative.sansCommentaire(CodeProduit.de(String.format("VMP-5%05d", i))))
                    .toList();

            assertAcceptee(ESSENTIEL, Periode.aPartirDu(DEBUT), vingt);
        }

        @Test
        @DisplayName("le message ne reprend aucune valeur saisie (dates)")
        void messageSansValeurSaisie() {
            assertThatThrownBy(() -> regle.verifier(ESSENTIEL,
                    Periode.entre(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 4)), List.of()))
                    .isExactlyInstanceOf(ValidationMetierException.class)
                    .hasMessage(MSG)
                    .hasMessageNotContaining("2027");
        }
    }

    @Nested
    @DisplayName("Produit non essentiel : jamais refusé")
    class NonEssentiel {

        @Test
        @DisplayName("AC-07 : VMP-200002 sans fin estimée, 0 alternative -> acceptée")
        void ac07_sansFinAcceptee() {
            assertAcceptee(NON_ESSENTIEL, Periode.aPartirDu(DEBUT), List.of());
        }

        @Test
        @DisplayName("AC-07 : VMP-200002 fin 2026-12-02 (31 j), 0 alternative -> acceptée")
        void ac07_trenteEtUnJoursAcceptee() {
            assertAcceptee(NON_ESSENTIEL, Periode.entre(DEBUT, LocalDate.of(2026, 12, 2)), List.of());
        }

        @Test
        @DisplayName("AC-14 : VMP-999999 inconnu du référentiel (donc non essentiel), sans fin, 0 alternative -> acceptée")
        void ac14_produitInconnuAccepte() {
            assertAcceptee(NON_ESSENTIEL, Periode.aPartirDu(DEBUT), List.of());
        }

        @ParameterizedTest(name = "début {0}, fin {1} -> acceptée")
        @CsvSource(nullValues = "-", value = {
                "2026-11-01, 2026-11-01",
                "2026-11-01, 2026-12-01",
                "2026-11-01, 2026-12-02",
                "2026-11-01, 2027-11-01",
                "2026-09-01, -",
                "2027-02-01, 2027-03-04",
                "2028-02-01, 2028-03-03"
        })
        @DisplayName("aucune période ne fait refuser un produit non essentiel sans alternative")
        void jamaisRefuse(LocalDate debut, LocalDate fin) {
            assertAcceptee(NON_ESSENTIEL, new Periode(debut, fin), List.of());
        }
    }
}
