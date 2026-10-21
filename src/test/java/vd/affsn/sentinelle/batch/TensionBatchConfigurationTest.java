package vd.affsn.sentinelle.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vd.affsn.sentinelle.application.port.out.HorlogePort;
import vd.affsn.sentinelle.application.port.out.NotificationRegionPort;
import vd.affsn.sentinelle.application.port.out.RuptureRepository;
import vd.affsn.sentinelle.domain.tension.SeuilTension;

/**
 * Garde {@code sentinelle.batch.enabled} et câblage de {@link TensionBatchConfiguration}, sur une
 * base H2 dédiée (mode PostgreSQL, migrations Flyway V1 à V3, initialiseur Spring Batch coupé
 * comme dans {@code config/batch.yml}).
 */
class TensionBatchConfigurationTest {

    @Configuration(proxyBeanMethods = false)
    static class PortsFactices {
        @Bean
        RuptureRepository ruptureRepository() {
            return new FauxRuptureRepository();
        }

        @Bean
        HorlogePort horloge() {
            return new HorlogeFixe(LocalDate.of(2026, 10, 21));
        }
    }

    @TempDir
    Path tmp;

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class,
                        DataSourceTransactionManagerAutoConfiguration.class,
                        FlywayAutoConfiguration.class,
                        BatchAutoConfiguration.class,
                        ValidationAutoConfiguration.class))
                .withUserConfiguration(PortsFactices.class, TensionBatchConfiguration.class)
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:batch-cfg-" + UUID.randomUUID()
                                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "spring.batch.jdbc.initialize-schema=never",
                        "spring.batch.job.enabled=false",
                        // Planification inerte pendant les tests (29 février).
                        "sentinelle.batch.cron=0 0 3 29 2 *",
                        "sentinelle.batch.notifications.repertoire=" + tmp);
    }

    @Test
    void desactiveParDefautAucunBeanDuJob() {
        runner().run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(TensionBatchConfiguration.class);
            assertThat(ctx).doesNotHaveBean(TensionBatchProperties.class);
            assertThat(ctx).doesNotHaveBean(Job.class);
            assertThat(ctx).doesNotHaveBean(CalculTensionTasklet.class);
            assertThat(ctx).doesNotHaveBean(LanceurCalculTension.class);
            assertThat(ctx).doesNotHaveBean(PlanificationCalculTension.class);
            assertThat(ctx).doesNotHaveBean(NotificationRegionPort.class);
            assertThat(ctx).doesNotHaveBean(SeuilTension.class);
        });
    }

    @Test
    void desactiveExplicitementAucunBeanDuJob() {
        runner().withPropertyValues("sentinelle.batch.enabled=false").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(Job.class);
            assertThat(ctx).doesNotHaveBean(PlanificationCalculTension.class);
        });
    }

    @Test
    void activeTousLesBeansPresentsEtCables() {
        runner().withPropertyValues("sentinelle.batch.enabled=true", "sentinelle.batch.seuil-tension=7.5").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(TensionBatchProperties.class);
            assertThat(ctx).hasSingleBean(CalculTensionTasklet.class);
            assertThat(ctx).hasSingleBean(LanceurCalculTension.class);
            assertThat(ctx).hasSingleBean(PlanificationCalculTension.class);
            assertThat(ctx).getBean(NotificationRegionPort.class).isInstanceOf(FichierNotificationRegionAdapter.class);
            assertThat(ctx.getBean(SeuilTension.class)).isEqualTo(new SeuilTension(7.5));

            Job job = ctx.getBean(TensionBatchConfiguration.NOM_JOB, Job.class);
            assertThat(job.getName()).isEqualTo("calculerTensionNocturne");
            assertThat(job.getJobParametersIncrementer()).isInstanceOf(RunIdIncrementer.class);
            assertThat(job.getJobParametersValidator()).isInstanceOf(DateReferenceJobParametersValidator.class);
            assertThat(job.isRestartable()).isTrue();
        });
    }

    @Test
    void activeLancementCompletSurBaseDedieeEcritLeFichier() {
        runner().withPropertyValues("sentinelle.batch.enabled=true").run(ctx -> {
            var execution = ctx.getBean(LanceurCalculTension.class).lancer(LocalDate.of(2026, 10, 21));
            assertThat(execution.getStatus().isUnsuccessful()).isFalse();
            assertThat(execution.getJobParameters().getLong("run.id")).isEqualTo(1L);
            assertThat(tmp.resolve("notifications-tension-2026-10-21.json")).hasContent(
                    "{\"dateReference\":\"2026-10-21\",\"seuil\":10.0,\"regions\":[]}");
        });
    }

    @Test
    void activeAvecValeurInvalideEchecAuDemarrage() {
        runner().withPropertyValues("sentinelle.batch.enabled=true", "sentinelle.batch.seuil-tension=-1")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner().withPropertyValues("sentinelle.batch.enabled=true", "sentinelle.batch.fuseau=Nulle/Part")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner().withPropertyValues("sentinelle.batch.enabled=true", "sentinelle.batch.notifications.repertoire=rel")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    /**
     * Comportement actuel documenté (ambiguïté) : les propriétés n'étant enregistrées que par la
     * configuration conditionnelle, une valeur invalide est IGNORÉE quand le job est désactivé.
     * La Javadoc dit « toutes les valeurs sont validées au démarrage ».
     */
    @Test
    void desactiveValeurInvalideNonValidee() {
        runner().withPropertyValues("sentinelle.batch.seuil-tension=-1", "sentinelle.batch.cron=nimporte")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void activeSansHorlogeEchecAuDemarrage() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class,
                        DataSourceTransactionManagerAutoConfiguration.class,
                        BatchAutoConfiguration.class))
                .withUserConfiguration(TensionBatchConfiguration.class)
                .withBean(RuptureRepository.class, FauxRuptureRepository::new)
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:batch-cfg-" + UUID.randomUUID(),
                        "spring.batch.jdbc.initialize-schema=never",
                        "sentinelle.batch.enabled=true",
                        "sentinelle.batch.notifications.repertoire=" + tmp)
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    /**
     * Comportement actuel documenté (ambiguïté) : la garde compare le TEXTE à « true » (casse
     * ignorée). Une valeur que Spring convertirait pourtant en booléen vrai (« 1 », « yes », « on »
     * passée par variable d'environnement) laisse le job silencieusement désactivé.
     */
    @Test
    void activationParUnAutreLibelleQueTrueLaisseLeJobDesactive() {
        runner().withPropertyValues("sentinelle.batch.enabled=1")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(LanceurCalculTension.class));
        runner().withPropertyValues("sentinelle.batch.enabled=TRUE")
                .run(ctx -> assertThat(ctx).hasSingleBean(LanceurCalculTension.class));
    }

    /**
     * Correction : le validateur exige {@code run.id} de type Long strictement positif. Un
     * lancement manuel avec {@code run.id} texte est REFUSÉ (aucune instance créée) et ne peut donc
     * plus empoisonner les lancements suivants (lanceur et planification).
     */
    @Test
    void runIdTexteRefuseEtNenpoisonnePasLesLancementsSuivants() {
        runner().withPropertyValues("sentinelle.batch.enabled=true").run(ctx -> {
            Job job = ctx.getBean(Job.class);
            JobExplorer explorer = ctx.getBean(JobExplorer.class);
            assertThatThrownBy(() -> ctx.getBean(JobLauncher.class).run(job,
                    new JobParametersBuilder()
                            .addString("dateReference", "2026-10-21")
                            .addString("run.id", "manuel")
                            .toJobParameters()))
                    .isInstanceOf(JobParametersInvalidException.class)
                    .hasMessageContaining("run.id")
                    .hasMessageNotContaining("manuel");
            assertThat(explorer.getJobInstanceCount(TensionBatchConfiguration.NOM_JOB)).isZero();

            var suivant = ctx.getBean(LanceurCalculTension.class).lancer(LocalDate.of(2026, 10, 22));
            assertThat(suivant.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(suivant.getJobParameters().getLong("run.id")).isEqualTo(1L);
        });
    }

    @Test
    void runIdLongNegatifOuNulRefusesPositifAccepteEtLancementSuivantNormal() {
        runner().withPropertyValues("sentinelle.batch.enabled=true").run(ctx -> {
            Job job = ctx.getBean(Job.class);
            JobLauncher launcher = ctx.getBean(JobLauncher.class);
            for (long invalide : new long[] {-1L, 0L}) {
                assertThatThrownBy(() -> launcher.run(job, new JobParametersBuilder()
                        .addString("dateReference", "2026-10-21").addLong("run.id", invalide).toJobParameters()))
                        .isInstanceOf(JobParametersInvalidException.class);
            }
            assertThat(ctx.getBean(JobExplorer.class).getJobInstanceCount(TensionBatchConfiguration.NOM_JOB)).isZero();

            var manuel = launcher.run(job, new JobParametersBuilder()
                    .addString("dateReference", "2026-10-21").addLong("run.id", 5L).toJobParameters());
            assertThat(manuel.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            var suivant = ctx.getBean(LanceurCalculTension.class).lancer(LocalDate.of(2026, 10, 22));
            assertThat(suivant.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(suivant.getJobParameters().getLong("run.id")).isEqualTo(6L);
        });
    }

    @Test
    void activeSansRepertoireOuAvecRepertoireVideOuBlancEchecAuDemarrage() {
        for (String valeur : new String[] {"", "   "}) {
            runner().withPropertyValues("sentinelle.batch.enabled=true",
                    "sentinelle.batch.notifications.repertoire=" + valeur)
                    .run(ctx -> assertThat(ctx).hasFailed());
        }
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class,
                        DataSourceTransactionManagerAutoConfiguration.class,
                        BatchAutoConfiguration.class,
                        ValidationAutoConfiguration.class))
                .withUserConfiguration(PortsFactices.class, TensionBatchConfiguration.class)
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:batch-cfg-" + UUID.randomUUID(),
                        "spring.batch.jdbc.initialize-schema=never",
                        "sentinelle.batch.enabled=true")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
