package vd.affsn.sentinelle.batch;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;

import vd.affsn.sentinelle.application.port.out.NotificationRegionPort;
import vd.affsn.sentinelle.application.port.out.NotificationRegionPort.Notification;
import vd.affsn.sentinelle.application.port.out.NotificationRegionPort.RegionEnTension;
import vd.affsn.sentinelle.application.port.out.ReferentielProduitsPort;
import vd.affsn.sentinelle.application.port.out.RuptureRepository;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.tension.CalculateurTension;
import vd.affsn.sentinelle.domain.tension.IndiceTension;
import vd.affsn.sentinelle.domain.tension.SeuilTension;

/**
 * Unique étape du job : charge les ruptures actives, calcule l'indice de chaque région via le
 * domaine et publie la notification.
 *
 * <p>Choix d'un Tasklet plutôt que d'un step chunk (reader/processor/writer) : l'indice est un
 * agrégat sur l'ensemble des ruptures actives qui n'existe qu'une fois toutes lues, et la
 * sortie est un fichier unique écrit en une fois, donc le découpage en chunks n'apporterait ni
 * reprise partielle ni gain mémoire significatif pour le volume attendu.
 *
 * <p>Les indices ne sont pas persistés en base (aucune table prévue) : ils sont journalisés en
 * résumé et placés dans le contexte d'exécution du step (clés {@code indice.<REGION>}).
 */
public final class CalculTensionTasklet implements Tasklet {

    static final String CLE_NB_RUPTURES = "nombreRupturesActives";
    static final String CLE_NB_SIGNALEES = "nombreRegionsSignalees";
    static final String PREFIXE_INDICE = "indice.";

    private static final Logger LOG = LoggerFactory.getLogger(CalculTensionTasklet.class);

    private final RuptureRepository ruptureRepository;
    private final CalculateurTension calculateur;
    private final NotificationRegionPort notificationPort;
    private final SeuilTension seuil;
    /** {@code null} : aucun produit essentiel (comportement antérieur à ADR-004). */
    private final ReferentielProduitsPort referentiel;

    public CalculTensionTasklet(RuptureRepository ruptureRepository, CalculateurTension calculateur,
            NotificationRegionPort notificationPort, SeuilTension seuil) {
        this(ruptureRepository, calculateur, notificationPort, seuil, null);
    }

    /** ADR-004 : le référentiel fournit le caractère essentiel (poids x 2). */
    public CalculTensionTasklet(RuptureRepository ruptureRepository, CalculateurTension calculateur,
            NotificationRegionPort notificationPort, SeuilTension seuil, ReferentielProduitsPort referentiel) {
        this.referentiel = referentiel;
        this.ruptureRepository = Objects.requireNonNull(ruptureRepository);
        this.calculateur = Objects.requireNonNull(calculateur);
        this.notificationPort = Objects.requireNonNull(notificationPort);
        this.seuil = Objects.requireNonNull(seuil);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        var stepExecution = chunkContext.getStepContext().getStepExecution();
        // Paramètre déjà validé par DateReferenceJobParametersValidator ; ré-analysé strictement.
        LocalDate dateReference = DateReferenceJobParametersValidator.analyser(
                stepExecution.getJobParameters().getString(DateReferenceJobParametersValidator.DATE_REFERENCE));

        if (referentiel != null) {
            throw new UnsupportedOperationException("à implémenter (ADR-004 : pondération des produits essentiels)");
        }
        List<Rupture> ruptures = ruptureRepository.findActivesAu(dateReference);
        IndiceTension indice = calculateur.calculer(ruptures, dateReference);

        List<RegionEnTension> enTension = indice.regionsAuDessusDe(seuil).stream()
                .map(region -> new RegionEnTension(region, indice.de(region)))
                .toList();
        notificationPort.publier(new Notification(dateReference, seuil, enTension));

        ExecutionContext contexte = stepExecution.getExecutionContext();
        contexte.putInt(CLE_NB_RUPTURES, ruptures.size());
        contexte.putInt(CLE_NB_SIGNALEES, enTension.size());
        for (Region region : Region.values()) {
            contexte.putDouble(PREFIXE_INDICE + region.name(), indice.de(region));
        }

        // Résumé agrégé uniquement (aucune donnée individuelle de rupture).
        LOG.info("Indice de tension au {} : {} rupture(s) active(s), {} région(s) au-dessus du seuil {} : {}",
                dateReference, ruptures.size(), enTension.size(), seuil.valeur(),
                enTension.stream().map(r -> r.region().name()).toList());
        return RepeatStatus.FINISHED;
    }
}
