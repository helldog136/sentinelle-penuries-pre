package vd.affsn.sentinelle.adapter.out.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Validation de {@code sentinelle.audit.*} : constructeur et liaison Spring réelle. */
class AuditPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(AuditConfiguration.class);

    @Test
    void defautsSurs() {
        AuditProperties p = new AuditProperties(null, null, null);
        assertThat(p.hashGenese()).isEqualTo("0".repeat(64));
        assertThat(p.delaiAttenteVerrou()).isEqualTo(Duration.ofSeconds(10));
        assertThat(p.taillePageVerification()).isEqualTo(500);
    }

    @Test
    void defautsQuandAucunePropriete() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            AuditProperties p = ctx.getBean(AuditProperties.class);
            assertThat(p.hashGenese()).isEqualTo(AuditProperties.HASH_GENESE_PAR_DEFAUT);
            assertThat(p.delaiAttenteVerrou()).isEqualTo(AuditProperties.DELAI_ATTENTE_VERROU_PAR_DEFAUT);
            assertThat(p.taillePageVerification()).isEqualTo(AuditProperties.TAILLE_PAGE_VERIFICATION_PAR_DEFAUT);
        });
    }

    @Test
    void liaisonDesValeursValides() {
        runner.withPropertyValues("sentinelle.audit.hash-genese=" + "0123456789abcdef".repeat(4),
                        "sentinelle.audit.delai-attente-verrou=250ms",
                        "sentinelle.audit.taille-page-verification=3")
                .run(ctx -> {
                    AuditProperties p = ctx.getBean(AuditProperties.class);
                    assertThat(p.hashGenese()).isEqualTo("0123456789abcdef".repeat(4));
                    assertThat(p.delaiAttenteVerrou()).isEqualTo(Duration.ofMillis(250));
                    assertThat(p.taillePageVerification()).isEqualTo(3);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "g000000000000000000000000000000000000000000000000000000000000000",
            "ABCDEF0000000000000000000000000000000000000000000000000000000000",
            "00000000000000000000000000000000000000000000000000000000000000000",
            " 000000000000000000000000000000000000000000000000000000000000000"})
    void geneseInvalideRefusee(String genese) {
        assertThatThrownBy(() -> new AuditProperties(genese, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hash-genese");
    }

    @Test
    void geneseInvalideFaitEchouerLeDemarrage() {
        runner.withPropertyValues("sentinelle.audit.hash-genese=pas-hexa").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void delaiHorsBornesRefuse() {
        for (Duration d : new Duration[] {Duration.ZERO, Duration.ofMillis(-1), Duration.ofMinutes(5).plusNanos(1)}) {
            assertThatThrownBy(() -> new AuditProperties(null, d, null))
                    .as("%s", d).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("delai-attente-verrou");
        }
        assertThat(new AuditProperties(null, Duration.ofMinutes(5), null).delaiAttenteVerrou())
                .isEqualTo(Duration.ofMinutes(5));
        assertThat(new AuditProperties(null, Duration.ofNanos(1), null).delaiAttenteVerrou())
                .isEqualTo(Duration.ofNanos(1));
        runner.withPropertyValues("sentinelle.audit.delai-attente-verrou=0s").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void taillePageHorsBornesRefusee() {
        for (int t : new int[] {0, -1, 10_001, Integer.MIN_VALUE}) {
            assertThatThrownBy(() -> new AuditProperties(null, null, t))
                    .as("%d", t).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("taille-page-verification");
        }
        assertThat(new AuditProperties(null, null, 1).taillePageVerification()).isEqualTo(1);
        assertThat(new AuditProperties(null, null, 10_000).taillePageVerification()).isEqualTo(10_000);
        runner.withPropertyValues("sentinelle.audit.taille-page-verification=0").run(ctx -> assertThat(ctx).hasFailed());
    }

    /**
     * Ambiguïté documentée : un délai inférieur à la milliseconde est accepté par la validation,
     * mais l'adaptateur le convertit en millisecondes ({@code toMillis()}), soit 0 : le verrou est
     * alors tenté sans aucune attente.
     */
    @Test
    void delaiSousLaMillisecondeAccepteMaisEquivautAZeroMs() {
        assertThat(new AuditProperties(null, Duration.ofNanos(999_999), null).delaiAttenteVerrou().toMillis())
                .isZero();
    }
}
