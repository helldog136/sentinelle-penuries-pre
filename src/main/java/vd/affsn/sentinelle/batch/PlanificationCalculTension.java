package vd.affsn.sentinelle.batch;

import java.time.LocalDate;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
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
 *
 * <p>Journalisation : {@code COMPLETED} en INFO, {@code FAILED} en ERROR, tout autre statut en
 * WARN. Toute exception (lancement ou panne de l'horloge) est journalisée en ERROR sans être
 * propagée : la planification suivante reste active.
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
        LocalDate dateReference = null;
        try {
            dateReference = horloge.aujourdhui();
            JobExecution execution = lanceur.lancer(dateReference);
            BatchStatus statut = execution.getStatus();
            if (statut == BatchStatus.COMPLETED) {
                LOG.info("Job calculerTensionNocturne au {} terminé : {}", dateReference, statut);
            } else if (statut == BatchStatus.FAILED) {
                LOG.error("Job calculerTensionNocturne au {} terminé en échec : {}", dateReference, statut);
            } else {
                LOG.warn("Job calculerTensionNocturne au {} terminé sans succès : {}", dateReference, statut);
            }
        } catch (Exception e) {
            // Journalisé sans relancer : la planification suivante reste active. Couvre aussi une
            // panne de l'horloge (date alors inconnue).
            LOG.error("Échec du lancement du job calculerTensionNocturne au {}",
                    dateReference == null ? "(date inconnue)" : dateReference, e);
        }
    }
}
