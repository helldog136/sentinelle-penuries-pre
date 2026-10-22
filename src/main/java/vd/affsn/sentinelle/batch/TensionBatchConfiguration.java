package vd.affsn.sentinelle.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

import vd.affsn.sentinelle.application.port.out.HorlogePort;
import vd.affsn.sentinelle.application.port.out.NotificationRegionPort;
import vd.affsn.sentinelle.application.port.out.ReferentielProduitsPort;
import vd.affsn.sentinelle.application.port.out.RuptureRepository;
import vd.affsn.sentinelle.domain.tension.CalculateurTension;
import vd.affsn.sentinelle.domain.tension.SeuilTension;

/**
 * Câblage du job nocturne {@value #NOM_JOB} (ADR-003 b).
 *
 * <p>Activé uniquement par {@code sentinelle.batch.enabled=true} : le job dépend de ports
 * ({@link RuptureRepository}, {@link HorlogePort}) dont l'implémentation n'est pas encore livrée.
 * Le JobRepository est celui auto-configuré par Spring Boot sur la DataSource (tables de la
 * migration Flyway V3, {@code spring.batch.jdbc.initialize-schema=never}) ; on n'utilise donc
 * PAS {@code @EnableBatchProcessing}, qui désactiverait cette auto-configuration.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "sentinelle.batch", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TensionBatchProperties.class)
@EnableScheduling
public class TensionBatchConfiguration {

    public static final String NOM_JOB = "calculerTensionNocturne";
    public static final String NOM_STEP = "calculerTensionStep";

    @Bean
    SeuilTension seuilTensionNocturne(TensionBatchProperties proprietes) {
        return new SeuilTension(proprietes.seuilTension());
    }

    @Bean
    NotificationRegionPort fichierNotificationRegionAdapter(TensionBatchProperties proprietes) {
        return new FichierNotificationRegionAdapter(proprietes.notifications().repertoire());
    }

    @Bean
    CalculTensionTasklet calculTensionTasklet(RuptureRepository ruptureRepository,
            NotificationRegionPort fichierNotificationRegionAdapter, SeuilTension seuilTensionNocturne,
            ObjectProvider<ReferentielProduitsPort> referentiel) {
        // Sans port de référentiel dans le contexte : aucun produit essentiel (comportement antérieur).
        return new CalculTensionTasklet(ruptureRepository, new CalculateurTension(),
                fichierNotificationRegionAdapter, seuilTensionNocturne, referentiel.getIfAvailable());
    }

    @Bean
    Step calculerTensionStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            CalculTensionTasklet calculTensionTasklet) {
        return new StepBuilder(NOM_STEP, jobRepository)
                .tasklet(calculTensionTasklet, transactionManager)
                .build();
    }

    @Bean
    Job calculerTensionNocturne(JobRepository jobRepository, Step calculerTensionStep) {
        return new JobBuilder(NOM_JOB, jobRepository)
                .validator(new DateReferenceJobParametersValidator())
                .incrementer(new RunIdIncrementer())
                .start(calculerTensionStep)
                .build();
    }

    @Bean
    LanceurCalculTension lanceurCalculTension(JobLauncher jobLauncher, JobExplorer jobExplorer,
            Job calculerTensionNocturne) {
        return new LanceurCalculTension(jobLauncher, jobExplorer, calculerTensionNocturne);
    }

    @Bean
    PlanificationCalculTension planificationCalculTension(LanceurCalculTension lanceurCalculTension,
            HorlogePort horloge) {
        return new PlanificationCalculTension(lanceurCalculTension, horloge);
    }
}
