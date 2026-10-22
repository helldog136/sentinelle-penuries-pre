package vd.affsn.sentinelle.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import vd.affsn.sentinelle.domain.exception.ValidationMetierException;

/**
 * Contrat commun des identifiants à format strict « PRÉFIXE-six chiffres » :
 * {@link CodeProduit} (VMP-) et {@link IdentifiantDistributeur} (PRO-).
 */
@DisplayName("Contrat des identifiants à format strict")
class IdentifiantsContratTest {

    record Cas(String nom, String prefixe, Function<String, Object> fabrique, Function<Object, String> valeur) {
        @Override
        public String toString() {
            return nom;
        }
    }

    static final Cas CODE_PRODUIT = new Cas("CodeProduit", "VMP",
            CodeProduit::new, o -> ((CodeProduit) o).valeur());
    static final Cas IDENTIFIANT_DISTRIBUTEUR = new Cas("IdentifiantDistributeur", "PRO",
            IdentifiantDistributeur::new, o -> ((IdentifiantDistributeur) o).valeur());

    static Stream<Cas> types() {
        return Stream.of(CODE_PRODUIT, IDENTIFIANT_DISTRIBUTEUR);
    }

    static Stream<Arguments> valeursValides() {
        return types().flatMap(cas -> Stream.of("000000", "123456", "999999")
                .map(chiffres -> Arguments.of(cas, cas.prefixe() + "-" + chiffres)));
    }

    /** Suffixes invalides, combinés à chaque préfixe. */
    static Stream<Arguments> valeursInvalides() {
        return types().flatMap(cas -> Stream.of(
                        "",                                    // vide
                        "   ",                                 // blanc
                        cas.prefixe(),                         // préfixe seul
                        cas.prefixe() + "-",                   // sans chiffres
                        cas.prefixe() + "-12345",              // 5 chiffres
                        cas.prefixe() + "-1234567",            // 7 chiffres
                        cas.prefixe() + "123456",              // tiret manquant
                        cas.prefixe() + "_123456",             // mauvais séparateur
                        cas.prefixe() + "-12345a",             // lettre
                        cas.prefixe() + "-12 456",             // espace interne
                        " " + cas.prefixe() + "-123456",       // espace en tête (pas de trim)
                        cas.prefixe() + "-123456 ",            // espace en fin (pas de trim)
                        cas.prefixe() + "-123456\n",           // saut de ligne final
                        cas.prefixe().toLowerCase() + "-123456", // minuscules (pas de normalisation)
                        cas.prefixe() + "-١٢٣٤٥٦", // chiffres non ASCII
                        cas.prefixe() + "-１２３４５６", // chiffres pleine chasse
                        "XXX-123456")                          // autre préfixe
                .map(v -> Arguments.of(cas, v)));
    }

    /** Le préfixe de l'un n'est pas accepté par l'autre. */
    static Stream<Arguments> prefixesCroises() {
        return Stream.of(
                Arguments.of(CODE_PRODUIT, "PRO-123456"),
                Arguments.of(IDENTIFIANT_DISTRIBUTEUR, "VMP-123456"));
    }

    @ParameterizedTest(name = "{0} accepte « {1} »")
    @MethodSource("valeursValides")
    void accepteUneValeurConforme(Cas cas, String valeur) {
        Object identifiant = cas.fabrique().apply(valeur);

        assertThat(cas.valeur().apply(identifiant)).isEqualTo(valeur);
        assertThat(identifiant).hasToString(valeur);
    }

    @ParameterizedTest(name = "{0} est une valeur : égalité par contenu")
    @MethodSource("types")
    void egaliteParValeur(Cas cas) {
        String valeur = cas.prefixe() + "-424242";

        assertThat(cas.fabrique().apply(valeur))
                .isEqualTo(cas.fabrique().apply(valeur))
                .hasSameHashCodeAs(cas.fabrique().apply(valeur))
                .isNotEqualTo(cas.fabrique().apply(cas.prefixe() + "-424243"));
    }

    @ParameterizedTest(name = "{0} refuse « {1} »")
    @MethodSource("valeursInvalides")
    void refuseUneValeurNonConforme(Cas cas, String valeur) {
        assertThatThrownBy(() -> cas.fabrique().apply(valeur))
                .isInstanceOf(ValidationMetierException.class);
    }

    @ParameterizedTest(name = "{0} refuse « {1} » (préfixe de l'autre type)")
    @MethodSource("prefixesCroises")
    void refuseLePrefixeDeLAutreType(Cas cas, String valeur) {
        assertThatThrownBy(() -> cas.fabrique().apply(valeur))
                .isInstanceOf(ValidationMetierException.class);
    }

    @ParameterizedTest(name = "{0} refuse null")
    @MethodSource("types")
    void refuseNull(Cas cas) {
        assertThatThrownBy(() -> cas.fabrique().apply(null))
                .isInstanceOf(ValidationMetierException.class);
    }

    @Test
    @DisplayName("Les fabriques statiques de() valident aussi")
    void fabriquesStatiquesValidentAussi() {
        assertThatThrownBy(() -> CodeProduit.de("VMP-1")).isInstanceOf(ValidationMetierException.class);
        assertThatThrownBy(() -> IdentifiantDistributeur.de("PRO-1")).isInstanceOf(ValidationMetierException.class);
    }

    @Test
    @DisplayName("Le message d'erreur cite le format attendu sans dépasser une ligne")
    void messageDErreurExploitable() {
        assertThatThrownBy(() -> new CodeProduit("VMP-12"))
                .isInstanceOf(ValidationMetierException.class)
                .hasMessageContaining("VMP-")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("\n"));
    }
}
