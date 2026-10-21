package vd.affsn.sentinelle.batch;

import java.time.LocalDate;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.scheduling.annotation.Scheduled;

import vd.affsn.sentinelle.application.port.out.HorlogePort;

/**
 * Déclenchement planifié du job nocturne. La date de référence est la date du jour fournie par
 * {@link HorlogePort#aujourdhui()} (fuseau de référence de la Valdarie, fixé par l'adaptateur
 * d'horloge), au moment du déclenchement.
 *
 * <p>Attention : le cron est interprété dans {@code sentinelle.batch.fuseau}, la date du jour dans
 * le fuseau de l'horloge ; si ces fuseaux diffèrent, la date retenue peut ne pas être celle
 * attendue autour de minuit.
 */
public class PlanificationCalculTension {

    private static final Logger LOG = LoggerFactory.getLogger(PlanificationCalculTension.class);

    private final LanceurCalculTension lanceur;
    private final HorlogePort horloge;

    public PlanificationCalculTension(LanceurCalculTension lanceur, HorlogePort horloge) {
        this.lanceur = Objects.requireNonNull(lanceur);
        this.horloge = Objects.requireNonNull(horloge);
    }

    @Scheduled(cron = "${sentinelle.batch.cron:0 0 2 * * *}", zone = "${sentinelle.batch.fuseau:UTC}")
    public void declencher() {
        LocalDate dateReference = horloge.aujourdhui();
        try {
            JobExecution execution = lanceur.lancer(dateReference);
            LOG.info("Job calculerTensionNocturne au {} terminé : {}", dateReference, execution.getStatus());
        } catch (Exception e) {
            // Journalisé sans relancer : la planification suivante reste active.
            LOG.error("Échec du lancement du job calculerTensionNocturne au {}", dateReference, e);
        }
    }
}
