package vd.affsn.sentinelle.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import vd.affsn.sentinelle.application.port.out.ReferentielProduitsPort;
import vd.affsn.sentinelle.domain.Fixtures;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;

/**
 * Job complet (vrai JobRepository sur H2) avec un faux référentiel de produits essentiels
 * (ADR-004 : AC-56, AC-61, AC-62, AC-64, AC-65). D = 2026-11-30, seuil 10.0, et une
 * {@code entree-en-vigueur} FUTURE (2027-01-01) pour vérifier que la pondération l'ignore.
 */
@SpringBootTest(properties = {
    "sentinelle.batch.enabled=true",
    "sentinelle.batch.seuil-tension=10.0",
    "sentinelle.batch.cron=0 0 3 29 2 *",
    "sentinelle.regles.alternative-obligatoire.entree-en-vigueur=2027-01-01"})
@ActiveProfiles("test")
@Import(TensionBatchEssentielsJobIntegrationTest.PortsFactices.class)
class TensionBatchEssentielsJobIntegrationTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 30);
    private static final LocalDate IL_Y_A_14_JOURS = LocalDate.of(2026, 11, 16);
    private static final String NOM_D = "notifications-tension-2026-11-30.json";
    private static final String ESS_1 = "VMP-100001";
    private static final String NON_ESS = "VMP-200002";
    private static final String ESS_3 = "VMP-300003";

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

        @Bean
        @Primary
        FauxReferentielProduits fauxReferentielProduits() {
            return new FauxReferentielProduits();
        }
    }

    @Autowired
    private FauxRuptureRepository depot;
    @Autowired
    private FauxReferentielProduits referentiel;
    @Autowired
    private ReferentielProduitsPort referentielInjecte;
    @Autowired
    private HorlogeFixe horloge;
    @Autowired
    private LanceurCalculTension lanceur;
    @Autowired
    private PlanificationCalculTension planification;
    @Autowired
    private JobExplorer jobExplorer;

    @BeforeEach
    void reinitialiser() throws IOException {
        depot.reinitialiser();
        referentiel.reinitialiser();
        referentiel.avec(ESS_1, ESS_3);
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
        StepExecution step = relue.getStepExecutions().iterator().next();
        return jobExplorer.getStepExecution(relue.getId(), step.getId());
    }

    private static Rupture rupture(String produit, LocalDate debut, Region region) {
        return Rupture.declarer(RuptureId.generer(), new CodeProduit(produit), Fixtures.ACTEUR_A,
                Periode.aPartirDu(debut), Motif.PRODUCTION, EnumSet.of(region), List.of());
    }

    private void donneesAc56() {
        depot.avec(
                rupture(NON_ESS, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE), // 3.0
                rupture(ESS_1, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE), // 6.0
                rupture(ESS_3, D, Region.HAUTE_PAPERASSE)); // 2.0
    }

    @Test
    void lePortFactifEstCeluiInjecteDansLeContexte() {
        assertThat(referentielInjecte).isSameAs(referentiel);
    }

    @Nested
    class Ponderation {

        @Test
        void ac56FichierExactEtEntreeEnVigueurFutureIgnoree() throws Exception {
            // AC-61 : entree-en-vigueur = 2027-01-01 (futur) n'empêche pas le x 2.
            donneesAc56();

            JobExecution execution = lanceur.lancer(D);

            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(contenu(NOM_D)).isEqualTo(
                    "{\"dateReference\":\"2026-11-30\",\"seuil\":10.0,\"regions\":["
                            + "{\"region\":\"HAUTE_PAPERASSE\",\"indice\":11.0}]}");
        }

        @Test
        void ac61UneRuptureEssentielleDeQuatorzeJoursPese6MalgreEntreeEnVigueurFuture() throws Exception {
            depot.avec(rupture(ESS_1, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE));

            StepExecution step = stepRelu(lanceur.lancer(D));

            assertThat(step.getExecutionContext().getDouble("indice.HAUTE_PAPERASSE")).isEqualTo(6.0);
        }

        @Test
        void ac64ContexteDExecutionPersisteEtRelu() throws Exception {
            donneesAc56();

            StepExecution step = stepRelu(lanceur.lancer(D));

            var ctx = step.getExecutionContext();
            assertThat(ctx.getDouble("indice.HAUTE_PAPERASSE")).isEqualTo(11.0);
            assertThat(ctx.getInt("nombreRupturesActives")).isEqualTo(3);
            assertThat(ctx.getInt("nombreRegionsSignalees")).isEqualTo(1);
        }
    }

    @Nested
    class Rejeu {

        @Test
        void ac62RejeuApresRetraitDuReferentielNouvelleInstanceFichierRemplace() throws Exception {
            depot.avec(rupture(ESS_1, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE));
            JobExecution premiere = lanceur.lancer(D);
            assertThat(premiere.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(stepRelu(premiere).getExecutionContext().getDouble("indice.HAUTE_PAPERASSE")).isEqualTo(6.0);

            referentiel.retirer(ESS_1);
            JobExecution seconde = lanceur.lancer(D);

            assertThat(seconde.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(seconde.getJobInstance().getInstanceId()).isNotEqualTo(premiere.getJobInstance().getInstanceId());
            assertThat(stepRelu(seconde).getExecutionContext().getDouble("indice.HAUTE_PAPERASSE")).isEqualTo(3.0);
            assertThat(contenu(NOM_D)).isEqualTo("{\"dateReference\":\"2026-11-30\",\"seuil\":10.0,\"regions\":[]}");
            assertThat(fichiers()).containsExactly(NOM_D);
        }
    }

    @Nested
    class PanneDuReferentiel {

        private ListAppender<ILoggingEvent> journal;
        private Logger logger;

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
        void ac65JobFailedCausePortAucunFichierPuisPlanificationSuivanteActive() throws Exception {
            donneesAc56();
            referentiel.tomberEnPanne(new IllegalStateException("referentiel indisponible"));

            planification.declencher();

            JobExecution derniere = jobExplorer.getLastJobExecution(
                    jobExplorer.getLastJobInstance(TensionBatchConfiguration.NOM_JOB));
            assertThat(derniere.getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(derniere.getAllFailureExceptions()).anySatisfy(e -> assertThat(e)
                    .isInstanceOf(IllegalStateException.class).hasMessage("referentiel indisponible"));
            assertThat(referentiel.interrogations()).isNotEmpty();
            assertThat(fichiers()).isEmpty();
            assertThat(journal.list).anySatisfy(e -> {
                assertThat(e.getLevel()).isEqualTo(Level.ERROR);
                assertThat(e.getFormattedMessage()).contains("FAILED").contains("2026-11-30");
            });

            // La planification suivante reste active : référentiel réparé, le déclenchement réussit.
            referentiel.reparer();
            planification.declencher();

            JobExecution suivante = jobExplorer.getLastJobExecution(
                    jobExplorer.getLastJobInstance(TensionBatchConfiguration.NOM_JOB));
            assertThat(suivante.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(contenu(NOM_D)).contains("\"indice\":11.0");
        }

        @Test
        void ac65LePortEnPanneNEcraseNiNeRemplaceLeFichierDuJour() throws Exception {
            donneesAc56();
            lanceur.lancer(D);
            String avant = contenu(NOM_D);

            referentiel.tomberEnPanne(new IllegalStateException("referentiel indisponible"));
            JobExecution echec = lanceur.lancer(D);

            assertThat(echec.getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(contenu(NOM_D)).isEqualTo(avant);
            assertThat(fichiers()).containsExactly(NOM_D);
        }
    }
}
