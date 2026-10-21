package vd.affsn.sentinelle.batch;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;

/**
 * Lance le job {@code calculerTensionNocturne} pour une date de référence donnée.
 *
 * <p>Le paramètre identifiant {@code dateReference} est complété par {@code run.id} (incrémenteur
 * du job, via {@link JobParametersBuilder#getNextJobParameters(Job)}) : chaque lancement crée une
 * nouvelle instance, ce qui permet de relancer le même jour (rejeu ou correction) sans erreur
 * « instance déjà complète ». Le calcul n'ayant pas d'état intermédiaire, une relance recalcule
 * tout et remplace le fichier du jour.
 */
public class LanceurCalculTension {

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final Job job;

    public LanceurCalculTension(JobLauncher jobLauncher, JobExplorer jobExplorer, Job job) {
        this.jobLauncher = Objects.requireNonNull(jobLauncher);
        this.jobExplorer = Objects.requireNonNull(jobExplorer);
        this.job = Objects.requireNonNull(job);
    }

    public JobExecution lancer(LocalDate dateReference) throws Exception {
        Objects.requireNonNull(dateReference, "dateReference");
        JobParameters parametres = new JobParametersBuilder(jobExplorer)
                .addString(DateReferenceJobParametersValidator.DATE_REFERENCE,
                        DateTimeFormatter.ISO_LOCAL_DATE.format(dateReference))
                .getNextJobParameters(job)
                .toJobParameters();
        return jobLauncher.run(job, parametres);
    }
}
