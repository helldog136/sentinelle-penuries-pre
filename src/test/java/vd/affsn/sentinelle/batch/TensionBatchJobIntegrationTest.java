package vd.affsn.sentinelle.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vd.affsn.sentinelle.domain.Fixtures.ruptureCloturee;
import static vd.affsn.sentinelle.domain.Fixtures.ruptureEnCours;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import vd.affsn.sentinelle.application.port.out.HorlogePort;
import vd.affsn.sentinelle.application.port.out.RuptureRepository;
import vd.affsn.sentinelle.domain.model.Region;

/**
 * Job complet avec le VRAI JobRepository Spring Batch sur H2 (profil test, schéma Flyway V3),
 * dépôt de ruptures et horloge factices. Couvre lancement, idempotence (incrémenteur), reprise
 * après échec, contexte d'exécution persisté et planification.
 *
 * <p>La base H2 du profil test est partagée entre contextes de la JVM : les assertions portent
 * sur les instances créées par chaque test, jamais sur le nombre absolu de lignes.
 */
@SpringBootTest(properties = {
    "sentinelle.batch.enabled=true",
    "sentinelle.batch.seuil-tension=2.5",
    // Planification inerte pendant les tests (29 février) : déclenchement appelé explicitement.
    "sentinelle.batch.cron=0 0 3 29 2 *"})
@ActiveProfiles("test")
@Import(TensionBatchJobIntegrationTest.PortsFactices.class)
class TensionBatchJobIntegrationTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 21);
    private static final String NOM_D = "notifications-tension-2026-10-21.json";

    @TempDir
    static Path repertoire;

    @DynamicPropertySource
    static void repertoireNotifications(DynamicPropertyRegistry registry) {
        registry.add("sentinelle.batch.notifications.repertoire", () -> repertoire.toString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PortsFactices {
        @Bean
        @Primary
        FauxRuptureRepository fauxRuptureRepository() {
            return new FauxRuptureRepository();
        }

        @Bean
        HorlogeFixe horlogeFixe() {
            return new HorlogeFixe(D);
        }
    }

    @Autowired
    private FauxRuptureRepository depot;
    @Autowired
    private HorlogeFixe horloge;
    @Autowired
    private RuptureRepository depotInjecte;
    @Autowired
    private HorlogePort horlogeInjectee;
    @Autowired
    private LanceurCalculTension lanceur;
    @Autowired
    private PlanificationCalculTension planification;
    @Autowired
    private JobLauncher jobLauncher;
    @Autowired
    private JobExplorer jobExplorer;
    @Autowired
    private Job job;
    @Autowired
    private JobRepository jobRepository;

    @BeforeEach
    void reinitialiser() throws IOException {
        depot.reinitialiser();
        horloge.fixer(D);
        try (Stream<Path> s = Files.list(repertoire)) {
            for (Path p : s.toList()) {
                Files.delete(p);
            }
        }
    }

    private List<String> fichiers() throws IOException {
        try (Stream<Path> s = Files.list(repertoire)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private String contenu(String nom) throws IOException {
        return Files.readString(repertoire.resolve(nom));
    }

    private StepExecution stepRelu(JobExecution execution) {
        JobExecution relue = jobExplorer.getJobExecution(execution.getId());
        assertThat(relue).isNotNull();
        assertThat(relue.getStepExecutions()).hasSize(1);
        StepExecution step = relue.getStepExecutions().iterator().next();
        return jobExplorer.getStepExecution(relue.getId(), step.getId());
    }

    private void donneesNominales() {
        depot.avec(
                ruptureEnCours(D.minusDays(14), Region.HAUTE_PAPERASSE, Region.COTE_DU_TIMEOUT), // 3.0 chacune
                ruptureEnCours(D.minusDays(7), Region.COTE_DU_TIMEOUT), // +2.0 -> 5.0
                ruptureEnCours(D.minusDays(7), Region.BASSE_VALIDATION)); // 2.0 < 2.5
    }

    private static final String JSON_NOMINAL = "{\"dateReference\":\"2026-10-21\",\"seuil\":2.5,\"regions\":["
            + "{\"region\":\"HAUTE_PAPERASSE\",\"indice\":3.0},"
            + "{\"region\":\"COTE_DU_TIMEOUT\",\"indice\":5.0}]}";

    @Test
    void lesFauxPortsSontCeuxInjectesDansLeJob() {
        assertThat(depotInjecte).isSameAs(depot);
        assertThat(horlogeInjectee).isSameAs(horloge);
        assertThat(job.getName()).isEqualTo(TensionBatchConfiguration.NOM_JOB);
    }

    @Nested
    class Nominal {

        @Test
        void lancementEcritLeFichierEtTermineCompleted() throws Exception {
            donneesNominales();

            JobExecution execution = lanceur.lancer(D);

            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
            assertThat(fichiers()).containsExactly(NOM_D);
            assertThat(contenu(NOM_D)).isEqualTo(JSON_NOMINAL);
            assertThat(depot.datesDemandees()).containsExactly(D);
        }

        @Test
        void contexteDExecutionDuStepPersisteEtRelu() throws Exception {
            donneesNominales();
            depot.avec(ruptureCloturee(D.minusDays(30), D.minusDays(1), Region.MARAIS_DE_LA_RECETTE));

            StepExecution step = stepRelu(lanceur.lancer(D));

            assertThat(step.getStepName()).isEqualTo(TensionBatchConfiguration.NOM_STEP);
            assertThat(step.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            var ctx = step.getExecutionContext();
            assertThat(ctx.getInt("nombreRupturesActives")).isEqualTo(3);
            assertThat(ctx.getInt("nombreRegionsSignalees")).isEqualTo(2);
            assertThat(ctx.getDouble("indice.HAUTE_PAPERASSE")).isEqualTo(3.0);
            assertThat(ctx.getDouble("indice.BASSE_VALIDATION")).isEqualTo(2.0);
            assertThat(ctx.getDouble("indice.PLATEAU_DU_LEGACY")).isZero();
            assertThat(ctx.getDouble("indice.COTE_DU_TIMEOUT")).isEqualTo(5.0);
            assertThat(ctx.getDouble("indice.MARAIS_DE_LA_RECETTE")).isZero();
            assertThat(ctx.getDouble("indice.PROVINCE_DU_TICKET_OUVERT")).isZero();
        }

        @Test
        void parametresPersistesDateEtRunId() throws Exception {
            JobExecution execution = lanceur.lancer(D);
            JobParameters relus = jobExplorer.getJobExecution(execution.getId()).getJobParameters();

            assertThat(relus.getString("dateReference")).isEqualTo("2026-10-21");
            assertThat(relus.getLong("run.id")).isNotNull().isPositive();
            assertThat(relus.getParameters()).containsOnlyKeys("dateReference", "run.id");
        }

        @Test
        void aucuneRuptureFichierAvecListeVide() throws Exception {
            assertThat(lanceur.lancer(D).getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(contenu(NOM_D)).isEqualTo("{\"dateReference\":\"2026-10-21\",\"seuil\":2.5,\"regions\":[]}");
        }

        @Test
        void deuxLancementsSuccessifsPourDeuxDatesDeuxFichiers() throws Exception {
            depot.avec(ruptureEnCours(D.minusDays(14), Region.HAUTE_PAPERASSE));

            JobExecution premier = lanceur.lancer(D);
            JobExecution second = lanceur.lancer(D.plusDays(7)); // 21 jours -> 4.0

            assertThat(premier.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(fichiers()).containsExactly(NOM_D, "notifications-tension-2026-10-28.json");
            assertThat(contenu("notifications-tension-2026-10-28.json")).isEqualTo(
                    "{\"dateReference\":\"2026-10-28\",\"seuil\":2.5,"
                            + "\"regions\":[{\"region\":\"HAUTE_PAPERASSE\",\"indice\":4.0}]}");
            assertThat(second.getJobParameters().getLong("run.id"))
                    .isEqualTo(premier.getJobParameters().getLong("run.id") + 1);
        }
    }

    @Nested
    class IdempotenceEtRelance {

        @Test
        void relanceMemeJourNouvelleInstanceMemeContenuFichierRemplace() throws Exception {
            donneesNominales();
            JobExecution premiere = lanceur.lancer(D);
            Object inodeAvant = Files.getAttribute(repertoire.resolve(NOM_D), "unix:ino");

            JobExecution seconde = lanceur.lancer(D);

            assertThat(seconde.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(seconde.getJobInstance().getInstanceId()).isNotEqualTo(premiere.getJobInstance().getInstanceId());
            assertThat(seconde.getJobParameters().getLong("run.id"))
                    .isEqualTo(premiere.getJobParameters().getLong("run.id") + 1);
            assertThat(seconde.getJobParameters().getString("dateReference")).isEqualTo("2026-10-21");
            assertThat(contenu(NOM_D)).isEqualTo(JSON_NOMINAL);
            assertThat(Files.getAttribute(repertoire.resolve(NOM_D), "unix:ino")).isNotEqualTo(inodeAvant);
            assertThat(fichiers()).containsExactly(NOM_D);
        }

        @Test
        void relanceApresChangementDesDonneesContenuDifferent() throws Exception {
            donneesNominales();
            lanceur.lancer(D);
            String avant = contenu(NOM_D);

            depot.avec(ruptureEnCours(D, Region.PROVINCE_DU_TICKET_OUVERT, Region.BASSE_VALIDATION)); // BV 3.0, PTO 1.0
            lanceur.lancer(D);

            assertThat(contenu(NOM_D)).isNotEqualTo(avant).isEqualTo(
                    "{\"dateReference\":\"2026-10-21\",\"seuil\":2.5,\"regions\":["
                            + "{\"region\":\"HAUTE_PAPERASSE\",\"indice\":3.0},"
                            + "{\"region\":\"BASSE_VALIDATION\",\"indice\":3.0},"
                            + "{\"region\":\"COTE_DU_TIMEOUT\",\"indice\":5.0}]}");
        }

        @Test
        void relancerUneInstanceTermineeAvecLesMemesParametresEstRefuse() throws Exception {
            JobExecution execution = lanceur.lancer(D);

            assertThatThrownBy(() -> jobLauncher.run(job, execution.getJobParameters()))
                    .isInstanceOf(JobInstanceAlreadyCompleteException.class);
        }

        @Test
        void parametresInvalidesRefusesSansCreerDInstance() throws Exception {
            long avant = jobExplorer.getJobInstanceCount(TensionBatchConfiguration.NOM_JOB);

            assertThatThrownBy(() -> jobLauncher.run(job,
                    new JobParametersBuilder().addString("dateReference", "2026-02-30").toJobParameters()))
                    .isInstanceOf(JobParametersInvalidException.class);
            assertThatThrownBy(() -> jobLauncher.run(job, new JobParametersBuilder()
                    .addString("dateReference", "2026-10-21").addString("intrus", "x").toJobParameters()))
                    .isInstanceOf(JobParametersInvalidException.class);
            assertThatThrownBy(() -> jobLauncher.run(job, new JobParameters()))
                    .isInstanceOf(JobParametersInvalidException.class);

            assertThat(jobExplorer.getJobInstanceCount(TensionBatchConfiguration.NOM_JOB)).isEqualTo(avant);
            assertThat(fichiers()).isEmpty();
        }
    }

    @Nested
    class EchecEtReprise {

        @Test
        void echecDuDepotJobFailedSansFichierNiTemporaire() throws Exception {
            depot.tomberEnPanne(new IllegalStateException("base indisponible"));

            JobExecution execution = lanceur.lancer(D);

            assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
            assertThat(execution.getAllFailureExceptions())
                    .anySatisfy(e -> assertThat(e).isInstanceOf(IllegalStateException.class));
            assertThat(stepRelu(execution).getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(fichiers()).isEmpty();
        }

        @Test
        void echecDuDepotConserveLeFichierPrecedentDuJour() throws Exception {
            donneesNominales();
            lanceur.lancer(D);
            depot.tomberEnPanne(new IllegalStateException("base indisponible"));

            assertThat(lanceur.lancer(D).getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(contenu(NOM_D)).isEqualTo(JSON_NOMINAL);
            assertThat(fichiers()).containsExactly(NOM_D);
        }

        @Test
        void repriseDeLInstanceEchoueeAvecLesMemesParametres() throws Exception {
            donneesNominales();
            depot.tomberEnPanne(new IllegalStateException("base indisponible"));
            JobExecution echouee = lanceur.lancer(D);
            assertThat(echouee.getStatus()).isEqualTo(BatchStatus.FAILED);

            depot.reparer();
            JobExecution reprise = jobLauncher.run(job, echouee.getJobParameters());

            assertThat(reprise.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(reprise.getId()).isNotEqualTo(echouee.getId());
            assertThat(reprise.getJobInstance().getInstanceId()).isEqualTo(echouee.getJobInstance().getInstanceId());
            assertThat(jobExplorer.getJobExecutions(reprise.getJobInstance())).hasSize(2);
            assertThat(contenu(NOM_D)).isEqualTo(JSON_NOMINAL);
            assertThat(fichiers()).containsExactly(NOM_D);
        }

        @Test
        void executionInterrompueBrutalementPuisRelanceeMemeDateProduitLeFichier() throws Exception {
            // Simule un arrêt brutal : une exécution restée STARTED pour la date D.
            JobParameters parametres = new JobParametersBuilder(jobExplorer)
                    .addString("dateReference", "2026-10-21").getNextJobParameters(job).toJobParameters();
            JobExecution interrompue = jobRepository.createJobExecution(job.getName(), parametres);
            interrompue.setStatus(BatchStatus.STARTED);
            interrompue.setStartTime(LocalDateTime.of(2026, 10, 21, 2, 0));
            jobRepository.update(interrompue);
            donneesNominales();

            // La relance de la MÊME instance est refusée tant qu'elle paraît en cours...
            assertThatThrownBy(() -> jobLauncher.run(job, parametres))
                    .isInstanceOf(JobExecutionAlreadyRunningException.class);
            // ... mais le lanceur crée une nouvelle instance et produit le fichier attendu.
            JobExecution relance = lanceur.lancer(D);

            assertThat(relance.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(relance.getJobInstance().getInstanceId()).isNotEqualTo(interrompue.getJobInstance().getInstanceId());
            assertThat(contenu(NOM_D)).isEqualTo(JSON_NOMINAL);

            // Nettoyage : l'exécution simulée est marquée abandonnée.
            interrompue.setStatus(BatchStatus.ABANDONED);
            interrompue.setEndTime(LocalDateTime.of(2026, 10, 21, 3, 0));
            jobRepository.update(interrompue);
        }

        @Test
        void relanceParLeLanceurApresEchecNouvelleInstanceMemeFichierQuUneExecutionSansPanne() throws Exception {
            donneesNominales();
            depot.tomberEnPanne(new IllegalStateException("base indisponible"));
            JobExecution echouee = lanceur.lancer(D);

            depot.reparer();
            JobExecution relance = lanceur.lancer(D);

            assertThat(relance.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(relance.getJobInstance().getInstanceId()).isNotEqualTo(echouee.getJobInstance().getInstanceId());
            assertThat(contenu(NOM_D)).isEqualTo(JSON_NOMINAL);
        }
    }

    @Nested
    class Planification {

        @Test
        void declenchementUtiliseLaDateDeLHorloge() throws Exception {
            horloge.fixer(LocalDate.of(2027, 1, 2));

            planification.declencher();

            assertThat(depot.datesDemandees()).containsExactly(LocalDate.of(2027, 1, 2));
            assertThat(fichiers()).containsExactly("notifications-tension-2027-01-02.json");
            JobExecution derniere = jobExplorer.getLastJobExecution(
                    jobExplorer.getLastJobInstance(TensionBatchConfiguration.NOM_JOB));
            assertThat(derniere.getJobParameters().getString("dateReference")).isEqualTo("2027-01-02");
            assertThat(derniere.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        }

        @Test
        void declenchementAvecDepotEnPanneNeLevePasEtEnregistreUnEchec() throws Exception {
            depot.tomberEnPanne(new IllegalStateException("base indisponible"));

            planification.declencher();

            JobExecution derniere = jobExplorer.getLastJobExecution(
                    jobExplorer.getLastJobInstance(TensionBatchConfiguration.NOM_JOB));
            assertThat(derniere.getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(fichiers()).isEmpty();
        }
    }
}
