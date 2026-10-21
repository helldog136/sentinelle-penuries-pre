package vd.affsn.sentinelle.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersIncrementer;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRestartException;

/**
 * Tests unitaires (sans contexte Spring) de {@link LanceurCalculTension} et de
 * {@link PlanificationCalculTension}. Le comportement avec le vrai JobRepository est couvert par
 * {@link TensionBatchJobIntegrationTest}.
 */
class LanceurEtPlanificationTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 21);

    /** JobLauncher de test : mémorise les paramètres et renvoie une exécution au statut voulu. */
    private static final class LauncherEnregistreur implements JobLauncher {
        final List<JobParameters> recus = new ArrayList<>();
        BatchStatus statut = BatchStatus.COMPLETED;

        @Override
        public JobExecution run(Job job, JobParameters jobParameters) {
            recus.add(jobParameters);
            JobExecution execution = new JobExecution(new JobInstance(1L, job.getName()), 1L, jobParameters);
            execution.setStatus(statut);
            return execution;
        }
    }

    /** Job factice portant le nom et l'incrémenteur du vrai job ; jamais exécuté. */
    private static Job jobAvecIncrementeur() {
        return new Job() {
            @Override
            public String getName() {
                return TensionBatchConfiguration.NOM_JOB;
            }

            @Override
            public void execute(JobExecution execution) {
                throw new UnsupportedOperationException();
            }

            @Override
            public JobParametersIncrementer getJobParametersIncrementer() {
                return new RunIdIncrementer();
            }
        };
    }

    /** JobExplorer factice : ne connaît qu'une éventuelle dernière instance et sa dernière exécution. */
    private static final class FauxJobExplorer implements JobExplorer {
        JobInstance derniere;
        JobExecution derniereExecution;

        @Override
        public JobInstance getLastJobInstance(String jobName) {
            return derniere;
        }

        @Override
        public JobExecution getLastJobExecution(JobInstance jobInstance) {
            return derniereExecution;
        }

        @Override
        public List<JobInstance> getJobInstances(String jobName, int start, int count) {
            return derniere == null ? List.of() : List.of(derniere);
        }

        @Override
        public JobExecution getJobExecution(Long executionId) {
            return null;
        }

        @Override
        public StepExecution getStepExecution(Long jobExecutionId, Long stepExecutionId) {
            return null;
        }

        @Override
        public JobInstance getJobInstance(Long instanceId) {
            return null;
        }

        @Override
        public List<JobExecution> getJobExecutions(JobInstance jobInstance) {
            return derniereExecution == null ? List.of() : List.of(derniereExecution);
        }

        @Override
        public Set<JobExecution> findRunningJobExecutions(String jobName) {
            return Set.of();
        }

        @Override
        public List<String> getJobNames() {
            return List.of();
        }

        @Override
        public List<JobInstance> findJobInstancesByJobName(String jobName, int start, int count) {
            return getJobInstances(jobName, start, count);
        }

        @Override
        public long getJobInstanceCount(String jobName) {
            return derniere == null ? 0 : 1;
        }
    }

    @Nested
    class Lanceur {

        private final LauncherEnregistreur launcher = new LauncherEnregistreur();
        private final FauxJobExplorer explorer = new FauxJobExplorer();
        private final Job job = jobAvecIncrementeur();

        @Test
        void premierLancementDateEtRunIdUn() throws Exception {
            new LanceurCalculTension(launcher, explorer, job).lancer(D);

            JobParameters p = launcher.recus.get(0);
            assertThat(p.getString("dateReference")).isEqualTo("2026-10-21");
            assertThat(p.getLong("run.id")).isEqualTo(1L);
            assertThat(p.getParameters()).containsOnlyKeys("dateReference", "run.id");
            assertThat(p.getParameters().get("dateReference").isIdentifying()).isTrue();
        }

        @Test
        void lancementSuivantIncrementeRunIdEtRemplaceLaDatePrecedente() throws Exception {
            JobInstance derniere = new JobInstance(7L, TensionBatchConfiguration.NOM_JOB);
            JobParameters precedents = new JobParametersBuilder()
                    .addString("dateReference", "2026-10-20").addLong("run.id", 7L).toJobParameters();
            explorer.derniere = derniere;
            explorer.derniereExecution = new JobExecution(derniere, 9L, precedents);

            new LanceurCalculTension(launcher, explorer, job).lancer(D);

            JobParameters p = launcher.recus.get(0);
            assertThat(p.getString("dateReference")).isEqualTo("2026-10-21");
            assertThat(p.getLong("run.id")).isEqualTo(8L);
            assertThat(p.getParameters()).containsOnlyKeys("dateReference", "run.id");
        }

        @Test
        void parametresProduitsPassentLeValidateur() throws Exception {
            new LanceurCalculTension(launcher, explorer, job).lancer(LocalDate.of(1, 2, 3));

            assertThat(launcher.recus.get(0).getString("dateReference")).isEqualTo("0001-02-03");
            assertThatCode(() -> new DateReferenceJobParametersValidator().validate(launcher.recus.get(0)))
                    .doesNotThrowAnyException();
        }

        @Test
        void dateNulleRefusee() {
            var lanceur = new LanceurCalculTension(launcher, explorer, job);
            assertThatThrownBy(() -> lanceur.lancer(null)).isInstanceOf(NullPointerException.class);
            assertThat(launcher.recus).isEmpty();
        }

        @Test
        void dependancesObligatoires() {
            assertThatThrownBy(() -> new LanceurCalculTension(null, explorer, job)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new LanceurCalculTension(launcher, null, job)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new LanceurCalculTension(launcher, explorer, null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Planification {

        private final List<LocalDate> datesLancees = new ArrayList<>();
        private Exception echec;
        private BatchStatus statut = BatchStatus.COMPLETED;
        private final HorlogeFixe horloge = new HorlogeFixe(D);
        private ListAppender<ILoggingEvent> journal;
        private Logger logger;

        private final LanceurCalculTension lanceur = new LanceurCalculTension(
                new LauncherEnregistreur(), new FauxJobExplorer(), jobAvecIncrementeur()) {
            @Override
            public JobExecution lancer(LocalDate dateReference) throws Exception {
                datesLancees.add(dateReference);
                if (echec != null) {
                    throw echec;
                }
                JobExecution e = new JobExecution(1L);
                e.setStatus(statut);
                return e;
            }
        };

        @BeforeEach
        void brancherJournal() {
            logger = (Logger) LoggerFactory.getLogger(PlanificationCalculTension.class);
            journal = new ListAppender<>();
            journal.start();
            logger.addAppender(journal);
        }

        @AfterEach
        void debrancherJournal() {
            logger.detachAppender(journal);
        }

        @Test
        void lanceAvecLaDateDuJourFournieParLHorloge() {
            new PlanificationCalculTension(lanceur, horloge).declencher();

            assertThat(datesLancees).containsExactly(D);
            assertThat(horloge.appels()).isEqualTo(1);
            assertThat(journal.list).anySatisfy(e -> {
                assertThat(e.getLevel()).isEqualTo(Level.INFO);
                assertThat(e.getFormattedMessage()).contains("2026-10-21").contains("COMPLETED");
            });
        }

        @Test
        void dateRelueAChaqueDeclenchement() {
            var planification = new PlanificationCalculTension(lanceur, horloge);
            planification.declencher();
            horloge.fixer(D.plusDays(1));
            planification.declencher();

            assertThat(datesLancees).containsExactly(D, D.plusDays(1));
        }

        @Test
        void exceptionDuLancementJournaliseeEnErreurSansPropagation() {
            echec = new JobRestartException("refus");
            var planification = new PlanificationCalculTension(lanceur, horloge);

            assertThatCode(planification::declencher).doesNotThrowAnyException();
            assertThat(journal.list).anySatisfy(e -> {
                assertThat(e.getLevel()).isEqualTo(Level.ERROR);
                assertThat(e.getFormattedMessage()).contains("2026-10-21");
                assertThat(e.getThrowableProxy()).isNotNull();
                assertThat(e.getThrowableProxy().getClassName()).isEqualTo(JobRestartException.class.getName());
            });
        }

        @Test
        void exceptionNonVerifieeDuLancementNonPropagee() {
            echec = new IllegalStateException("inattendu");
            assertThatCode(new PlanificationCalculTension(lanceur, horloge)::declencher).doesNotThrowAnyException();
        }

        @Test
        void declenchementSuivantPossibleApresUnEchec() {
            var planification = new PlanificationCalculTension(lanceur, horloge);
            echec = new IllegalStateException("panne");
            planification.declencher();
            echec = null;
            horloge.fixer(D.plusDays(1));
            planification.declencher();

            assertThat(datesLancees).containsExactly(D, D.plusDays(1));
        }

        /**
         * Comportement actuel documenté : un job terminé en FAILED (sans exception du lanceur) est
         * journalisé au niveau INFO (« terminé : FAILED »), pas en ERROR. Spécification muette.
         */
        @Test
        void jobEnEchecJournaliseEnInfoAvecLeStatut() {
            statut = BatchStatus.FAILED;
            new PlanificationCalculTension(lanceur, horloge).declencher();

            assertThat(journal.list).singleElement().satisfies(e -> {
                assertThat(e.getLevel()).isEqualTo(Level.INFO);
                assertThat(e.getFormattedMessage()).contains("FAILED");
            });
        }

        /**
         * Comportement actuel documenté : l'appel à l'horloge est HORS du try ; une exception de
         * l'horloge se propage au planificateur (qui, par défaut, la journalise et conserve la
         * planification). La Javadoc ne promet la non-propagation que pour le lancement.
         */
        @Test
        void exceptionDeLHorlogePropageeEtAucunLancement() {
            horloge.tomberEnPanne(new IllegalStateException("horloge"));
            var planification = new PlanificationCalculTension(lanceur, horloge);

            assertThatThrownBy(planification::declencher).isInstanceOf(IllegalStateException.class);
            assertThat(datesLancees).isEmpty();
        }

        @Test
        void dependancesObligatoires() {
            assertThatThrownBy(() -> new PlanificationCalculTension(null, horloge))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new PlanificationCalculTension(lanceur, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
