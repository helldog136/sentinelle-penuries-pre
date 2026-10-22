package vd.affsn.sentinelle.adapter.out.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests unitaires (sans Spring) du format canonique v1 et de l'assainissement du résumé.
 * Le vecteur de référence a été calculé HORS JVM (octets construits à la main, {@code sha256sum}).
 */
class CanonisationAuditTest {

    /** Vecteur de référence : octets de la Javadoc v1 construits hors Java, haché par sha256sum. */
    private static final String HASH_REFERENCE =
            "31a4a2c9b0a0e6dfafea9271a3f7f2e5b12e1583f7da3e9b9ee6710afc078dce";

    @Test
    void vecteurDeReferenceCalculeHorsJvm() {
        String h = CanonisationAudit.hacher("123e4567-e89b-12d3-a456-426614174000", "DECLARATION",
                "00000000-0000-0000-0000-000000000001", "agent-42", "DISTRIBUTEUR",
                "2026-10-21T08:15:30.123456Z", "produit VMP-123456, 2 régions", "0".repeat(64));
        assertThat(h).isEqualTo(HASH_REFERENCE);
        assertThat(AuditTestSupport.hashIndependant("123e4567-e89b-12d3-a456-426614174000", "DECLARATION",
                "00000000-0000-0000-0000-000000000001", "agent-42", "DISTRIBUTEUR",
                "2026-10-21T08:15:30.123456Z", "produit VMP-123456, 2 régions", "0".repeat(64)))
                .isEqualTo(HASH_REFERENCE);
    }

    @Test
    void prefixeDeLongueurRendLaSerialisationNonAmbigue() {
        // Déplacer un séparateur d'un champ à l'autre ne doit jamais produire le même hash.
        String a = CanonisationAudit.hacher("e", "T", "r", "s\nrole=1:x", "R", "i", "m", "p");
        String b = CanonisationAudit.hacher("e", "T", "r", "s", "\nrole=1:xR", "i", "m", "p");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void champNullRefuse() {
        assertThatThrownBy(() -> CanonisationAudit.hacher("e", "T", "r", "s", "R", "i", null, "p"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void formatInstantSixDecimalesUtcApresTroncature() {
        Instant i = Instant.parse("2026-02-03T04:05:06.987654321Z");
        assertThat(CanonisationAudit.formaterInstant(CanonisationAudit.tronquer(i)))
                .isEqualTo("2026-02-03T04:05:06.987654Z")
                .isEqualTo(AuditTestSupport.instantCanonique(i));
        assertThat(CanonisationAudit.formaterInstant(Instant.parse("2026-02-03T04:05:06Z")))
                .isEqualTo("2026-02-03T04:05:06.000000Z");
    }

    @Nested
    class Assainissement {

        @Test
        void jwtMasque() {
            assertThat(CanonisationAudit.assainirResume(
                    "a eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abc_DEF-1 b")).isEqualTo("a [masqué] b");
        }

        @Test
        void jwtNonSigneMasque() {
            assertThat(CanonisationAudit.assainirResume("eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0."))
                    .isEqualTo("[masqué]");
        }

        @ParameterizedTest
        @ValueSource(strings = {"Bearer abc", "bearer abc", "BEARER\tabc", "x Bearer   tok.en y"})
        void bearerMasque(String s) {
            assertThat(CanonisationAudit.assainirResume(s)).contains("[masqué]").doesNotContain("abc", "tok.en");
        }

        @Test
        void texteOrdinaireIntact() {
            assertThat(CanonisationAudit.assainirResume("produit VMP-123456, 2 régions"))
                    .isEqualTo("produit VMP-123456, 2 régions");
        }

        @Test
        void controleFormatBidiEtSeparateursRemplacesParEspace() {
            assertThat(CanonisationAudit.assainirResume("a\nb\rc\td\u0000e\u0085f‮g⁦h​i j k­l"))
                    .isEqualTo("a b c d e f g h i j k l");
        }

        @Test
        void substitutIsoleRemplaceParEspace() {
            assertThat(CanonisationAudit.assainirResume("a\uD800b\uDC00c")).isEqualTo("a b c");
        }

        @Test
        void espacesDeBordRetires() {
            assertThat(CanonisationAudit.assainirResume("   x ‮ ")).isEqualTo("x");
            assertThat(CanonisationAudit.assainirResume("   ")).isEmpty();
        }

        @Test
        void tronqueA500() {
            assertThat(CanonisationAudit.assainirResume("a".repeat(800))).hasSize(500);
        }

        @Test
        void troncatureNeCoupePasUnePaireDeSubstitution() {
            String s = "a".repeat(499) + "😀" + "zzz";
            String r = CanonisationAudit.assainirResume(s);
            assertThat(r).isEqualTo("a".repeat(499));
            assertThat(Character.isHighSurrogate(r.charAt(r.length() - 1))).isFalse();
        }

        @Test
        void paireCompleteEnFinDeLimiteConservee() {
            String s = "a".repeat(498) + "😀" + "zzz";
            assertThat(CanonisationAudit.assainirResume(s)).isEqualTo("a".repeat(498) + "😀");
        }

        @Test
        void troncatureSuivieDUnRetraitDesEspaces() {
            String s = "a".repeat(495) + "     " + "bbbbb";
            assertThat(CanonisationAudit.assainirResume(s)).isEqualTo("a".repeat(495));
        }

        /**
         * L'espace insécable (U+00A0) est traitée comme une espace : « Bearer » + U+00A0 + jeton est
         * masqué, et les U+00A0 de bord sont retirées.
         */
        @Test
        void espaceInsecableTraiteeCommeSeparateur_jetonMasque() {
            assertThat(CanonisationAudit.assainirResume("Bearer\u00A0s3cr3t")).isEqualTo(CanonisationAudit.MASQUE);
            assertThat(CanonisationAudit.assainirResume("\u00A0x\u00A0")).isEqualTo("x");
            assertThat(CanonisationAudit.assainirResume("\u3000x\u3000")).isEqualTo("x");
        }

        @Test
        void nullRefuse() {
            assertThatThrownBy(() -> CanonisationAudit.assainirResume(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void resultatToujoursEncodableEtBorne() {
            String r = CanonisationAudit.assainirResume("\uDBFF".repeat(300) + "é".repeat(400));
            assertThat(r.length()).isLessThanOrEqualTo(500);
            assertThat(r.codePoints().noneMatch(cp -> Character.getType(cp) == Character.SURROGATE)).isTrue();
        }
    }

    /**
     * Défauts constatés : tests volontairement rouges (le code n'est pas corrigé ici).
     */
    @Nested
    class BugsDecouverts {

        /**
         * Le masquage des jetons est appliqué AVANT le remplacement des séparateurs Unicode par une
         * espace. « Bearer » suivi d'un séparateur de ligne U+2028 (ou d'une espace de largeur nulle
         * U+200B), que {@code \s} ne reconnaît pas et que {@link vd.affsn.sentinelle.application.port.out.EvenementAudit}
         * accepte, devient « Bearer secret » APRÈS masquage : le jeton est stocké en clair.
         */
        @ParameterizedTest
        @ValueSource(strings = {"Bearer s3cr3t-token", "Bearer​s3cr3t-token", "Bearer‮s3cr3t-token"})
        void bearerSepareParUnCaractereRemplaceNEstPasMasque(String brut) {
            String r = CanonisationAudit.assainirResume(brut);
            assertThat(r).doesNotContain("s3cr3t-token");
        }

        /** Conséquence du même défaut : l'assainissement n'est pas idempotent. */
        @Test
        void assainissementIdempotent() {
            String une = CanonisationAudit.assainirResume("x Bearer s3cr3t");
            assertThat(CanonisationAudit.assainirResume(une)).isEqualTo(une);
        }
    }
}
