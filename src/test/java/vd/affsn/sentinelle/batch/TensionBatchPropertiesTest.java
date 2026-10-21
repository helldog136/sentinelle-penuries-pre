package vd.affsn.sentinelle.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.ZoneId;

import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * {@link TensionBatchProperties} : liaison Spring Boot (valeurs par défaut, échec au démarrage
 * sur valeur invalide) et constructeur du record.
 */
class TensionBatchPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TensionBatchProperties.class)
    static class ProprietesSeules {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(ProprietesSeules.class);

    /** Vrai si l'une des causes de la chaîne a un message contenant le fragment. */
    private static boolean chaineContient(Throwable t, String fragment) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c.getMessage() != null && c.getMessage().contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    @Nested
    class LiaisonSpringBoot {

        @Test
        void valeursParDefaut() {
            runner.run(ctx -> {
                assertThat(ctx).hasNotFailed();
                TensionBatchProperties p = ctx.getBean(TensionBatchProperties.class);
                assertThat(p.enabled()).isFalse();
                assertThat(p.cron()).isEqualTo("0 0 2 * * *");
                assertThat(p.fuseau()).isEqualTo("UTC");
                assertThat(p.zoneId()).isEqualTo(ZoneId.of("UTC"));
                assertThat(p.seuilTension()).isEqualTo(10.0);
                // Aucun défaut pour le répertoire : il est obligatoire seulement quand le job est activé.
                assertThat(p.notifications().repertoire()).isNull();
            });
        }

        @Test
        void activeSansRepertoireEmpecheLeDemarrage() {
            runner.withPropertyValues("sentinelle.batch.enabled=true").run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(chaineContient(ctx.getStartupFailure(), "notifications.repertoire : obligatoire")).isTrue();
            });
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   "})
        void activeAvecRepertoireVideOuBlancEmpecheLeDemarrage(String repertoire) {
            runner.withPropertyValues("sentinelle.batch.enabled=true",
                    "sentinelle.batch.notifications.repertoire=" + repertoire).run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(chaineContient(ctx.getStartupFailure(), "sentinelle.batch.notifications.repertoire")).isTrue();
            });
        }

        @Test
        void valeursPersonnaliseesLiees(@TempDir Path tmp) {
            runner.withPropertyValues(
                    "sentinelle.batch.enabled=true",
                    "sentinelle.batch.cron=0 30 1 * * MON-FRI",
                    "sentinelle.batch.fuseau=Europe/Paris",
                    "sentinelle.batch.seuil-tension=0",
                    "sentinelle.batch.notifications.repertoire=" + tmp.resolve("x").resolve("..").resolve("notif"))
                    .run(ctx -> {
                        TensionBatchProperties p = ctx.getBean(TensionBatchProperties.class);
                        assertThat(p.enabled()).isTrue();
                        assertThat(p.cron()).isEqualTo("0 30 1 * * MON-FRI");
                        assertThat(p.zoneId()).isEqualTo(ZoneId.of("Europe/Paris"));
                        assertThat(p.seuilTension()).isZero();
                        assertThat(p.notifications().repertoire()).isEqualTo(tmp.resolve("notif"));
                    });
        }

        @ParameterizedTest
        @ValueSource(strings = {"-1", "-0.0001", "NaN", "Infinity", "-Infinity"})
        void seuilInvalideEmpecheLeDemarrage(String seuil) {
            runner.withPropertyValues("sentinelle.batch.seuil-tension=" + seuil).run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(chaineContient(ctx.getStartupFailure(), "sentinelle.batch.seuil-tension")).isTrue();
            });
        }

        @Test
        void seuilNonNumeriqueEmpecheLeDemarrage() {
            runner.withPropertyValues("sentinelle.batch.seuil-tension=dix").run(ctx -> assertThat(ctx).hasFailed());
        }

        @ParameterizedTest
        @ValueSource(strings = {"0 2 * * *", "tous les jours", "0 0 25 * * *", "0 0 2 * * * *", "-", " "})
        void cronInvalideEmpecheLeDemarrage(String cron) {
            runner.withPropertyValues("sentinelle.batch.cron=" + cron).run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(chaineContient(ctx.getStartupFailure(), "sentinelle.batch.cron")).isTrue();
            });
        }

        @ParameterizedTest
        @ValueSource(strings = {"Mars/Olympus", "UTC+25", "Europe/", "  "})
        void fuseauInvalideEmpecheLeDemarrage(String fuseau) {
            runner.withPropertyValues("sentinelle.batch.fuseau=" + fuseau).run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(chaineContient(ctx.getStartupFailure(), "sentinelle.batch.fuseau")).isTrue();
            });
        }

        @ParameterizedTest
        @ValueSource(strings = {"notifications", "./notifications", "../etc"})
        void repertoireRelatifEmpecheLeDemarrage(String repertoire) {
            runner.withPropertyValues("sentinelle.batch.notifications.repertoire=" + repertoire).run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(chaineContient(ctx.getStartupFailure(), "chemin absolu attendu")).isTrue();
            });
        }

        /** Job désactivé : un répertoire vide reste sans effet (pas de repli sur un défaut sous /tmp). */
        @Test
        void desactiveRepertoireVideNeRetombePasSurUnDefaut() {
            runner.withPropertyValues("sentinelle.batch.notifications.repertoire=").run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(ctx.getBean(TensionBatchProperties.class).notifications().repertoire()).isNull();
            });
        }
    }

    @Nested
    class Constructeur {

        private final TensionBatchProperties.Notifications notif =
                new TensionBatchProperties.Notifications(Path.of("/srv/notif"));

        private void echoue(ThrowableAssert.ThrowingCallable appel, String fragment) {
            assertThatThrownBy(appel).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(fragment);
        }

        @Test
        void seuilNegatifNanInfiniOuNulRefuses() {
            echoue(() -> new TensionBatchProperties(true, "0 0 2 * * *", "UTC", -0.5, notif), "seuil-tension");
            echoue(() -> new TensionBatchProperties(true, "0 0 2 * * *", "UTC", Double.NaN, notif), "seuil-tension");
            echoue(() -> new TensionBatchProperties(true, "0 0 2 * * *", "UTC", Double.POSITIVE_INFINITY, notif),
                    "seuil-tension");
            echoue(() -> new TensionBatchProperties(true, "0 0 2 * * *", "UTC", null, notif), "seuil-tension");
        }

        @Test
        void cronNulOuInvalideRefuse() {
            echoue(() -> new TensionBatchProperties(true, null, "UTC", 1.0, notif), "cron");
            echoue(() -> new TensionBatchProperties(true, "0 0 2 * *", "UTC", 1.0, notif), "cron");
        }

        @Test
        void fuseauNulOuInvalideRefuse() {
            echoue(() -> new TensionBatchProperties(true, "0 0 2 * * *", null, 1.0, notif), "fuseau");
            echoue(() -> new TensionBatchProperties(true, "0 0 2 * * *", "Nulle/Part", 1.0, notif), "fuseau");
        }

        @Test
        void seuilZeroEtMacroCronAcceptes() {
            var p = new TensionBatchProperties(false, "@daily", "Europe/Zurich", 0.0, notif);
            assertThat(p.seuilTension()).isZero();
            assertThat(p.zoneId()).isEqualTo(ZoneId.of("Europe/Zurich"));
        }

        @Test
        void repertoireRelatifRefuseEtAbsoluNormalise() {
            echoue(() -> new TensionBatchProperties.Notifications(Path.of("relatif")), "chemin absolu attendu");
            assertThat(new TensionBatchProperties.Notifications(Path.of("/srv/a/../b/./c")).repertoire())
                    .isEqualTo(Path.of("/srv/b/c"));
        }

        @Test
        void repertoireNulResteNulSansDefautEtBlancRefuse() {
            assertThat(new TensionBatchProperties.Notifications(null).repertoire()).isNull();
            echoue(() -> new TensionBatchProperties.Notifications(Path.of(" ")), "valeur vide ou blanche refusée");
        }

        @Test
        void activeSansRepertoireRefuseParLeConstructeurSansRecopierDeContenu() {
            echoue(() -> new TensionBatchProperties(true, "0 0 2 * * *", "UTC", 10.0,
                    new TensionBatchProperties.Notifications(null)), "notifications.repertoire : obligatoire");
            echoue(() -> new TensionBatchProperties(true, "0 0 2 * * *", "UTC", 10.0, null),
                    "notifications.repertoire : obligatoire");
            // Désactivé : accepté.
            new TensionBatchProperties(false, "0 0 2 * * *", "UTC", 10.0,
                    new TensionBatchProperties.Notifications(null));
        }

        /**
         * Ambiguïté documentée : {@code -0.0} passe la garde {@code >= 0} ; le fichier écrira
         * alors {@code "seuil":-0.0}.
         */
        @Test
        void moinsZeroAccepte() {
            var p = new TensionBatchProperties(true, "0 0 2 * * *", "UTC", -0.0, notif);
            assertThat(Double.compare(p.seuilTension(), -0.0)).isZero();
        }
    }
}
