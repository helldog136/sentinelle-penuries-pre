package vd.affsn.sentinelle.adapter.in.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.LocalDate;
import java.util.function.Function;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import vd.affsn.sentinelle.application.port.in.CloturerRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.DeclarerRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.MettreAJourRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.application.port.out.HorlogePort;
import vd.affsn.sentinelle.domain.exception.AccesRefuseException;
import vd.affsn.sentinelle.domain.exception.ExceptionMetier;
import vd.affsn.sentinelle.domain.exception.RuptureDejaClotureeException;
import vd.affsn.sentinelle.domain.exception.RuptureIntrouvableException;
import vd.affsn.sentinelle.domain.exception.ValidationMetierException;
import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Role;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.grpc.v1.CloturerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.CloturerRuptureResponse;
import vd.affsn.sentinelle.grpc.v1.DeclarerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.DeclarerRuptureResponse;
import vd.affsn.sentinelle.grpc.v1.MettreAJourRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.MettreAJourRuptureResponse;
import vd.affsn.sentinelle.grpc.v1.SignalementServiceGrpc;
import vd.affsn.sentinelle.security.ActeurGrpcContext;

/**
 * Adaptateur gRPC d'ingestion des signalements (écriture, rôle DISTRIBUTEUR).
 *
 * <p>Ordre d'évaluation (proto) : authentification, rôle, validation, puis le cas d'usage
 * (existence, propriété, état). L'acteur vient uniquement du contexte gRPC.
 */
@GrpcService
@ConditionalOnProperty(prefix = "sentinelle.grpc", name = "enabled", havingValue = "true")
public class SignalementGrpcService extends SignalementServiceGrpc.SignalementServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(SignalementGrpcService.class);

    static final String MESSAGE_CONFLIT = "Conflit de modification, veuillez réessayer";

    private static final long JOURS_AVANT_DEBUT_MAX = 365;
    private static final long JOURS_APRES_DEBUT_MAX = 90;
    private static final long JOURS_FIN_ESTIMEE_MAX = 730;

    private final DeclarerRuptureUseCase declarerRupture;
    private final MettreAJourRuptureUseCase mettreAJourRupture;
    private final CloturerRuptureUseCase cloturerRupture;
    private final HorlogePort horloge;

    public SignalementGrpcService(DeclarerRuptureUseCase declarerRupture,
                                  MettreAJourRuptureUseCase mettreAJourRupture,
                                  CloturerRuptureUseCase cloturerRupture,
                                  HorlogePort horloge) {
        this.declarerRupture = declarerRupture;
        this.mettreAJourRupture = mettreAJourRupture;
        this.cloturerRupture = cloturerRupture;
        this.horloge = horloge;
    }

    @Override
    public void declarerRupture(DeclarerRuptureRequest requete,
                                StreamObserver<DeclarerRuptureResponse> reponse) {
        traiter("DeclarerRupture", reponse, acteur -> {
            ValidateurRequetes v = new ValidateurRequetes();
            LocalDate aujourdhui = horloge.aujourdhui();
            v.codeProduit("code_produit", requete.getCodeProduit());
            LocalDate debut = v.date("date_debut", requete.getDateDebut()).orElse(null);
            if (debut != null
                    && (debut.isBefore(aujourdhui.minusDays(JOURS_AVANT_DEBUT_MAX))
                    || debut.isAfter(aujourdhui.plusDays(JOURS_APRES_DEBUT_MAX)))) {
                v.violation("date_debut", "doit être entre J-365 et J+90");
            }
            LocalDate finEstimee = null;
            if (requete.hasDateFinEstimee()) {
                finEstimee = v.date("date_fin_estimee", requete.getDateFinEstimee()).orElse(null);
                if (finEstimee != null && debut != null
                        && (finEstimee.isBefore(debut) || finEstimee.isAfter(debut.plusDays(JOURS_FIN_ESTIMEE_MAX)))) {
                    v.violation("date_fin_estimee", "doit être entre le début et début+730 jours");
                }
            }
            v.motif(requete.getMotif());
            v.regions(requete.getRegionsList());
            v.alternatives(requete.getAlternativesList(), requete.getCodeProduit());
            refuserSiInvalide(v);

            RuptureVue vue = declarerRupture.declarer(acteur, new DeclarerRuptureUseCase.Commande(
                    vd.affsn.sentinelle.domain.model.CodeProduit.de(requete.getCodeProduit()),
                    new Periode(debut, finEstimee),
                    MappeurRuptures.motif(requete.getMotif()),
                    MappeurRuptures.regions(requete.getRegionsList()),
                    MappeurRuptures.alternatives(requete.getAlternativesList())));
            return DeclarerRuptureResponse.newBuilder().setRupture(MappeurRuptures.rupture(vue)).build();
        });
    }

    @Override
    public void mettreAJourRupture(MettreAJourRuptureRequest requete,
                                   StreamObserver<MettreAJourRuptureResponse> reponse) {
        traiter("MettreAJourRupture", reponse, acteur -> {
            ValidateurRequetes v = new ValidateurRequetes();
            v.uuid("id", requete.getId());
            LocalDate finEstimee = null;
            if (requete.hasDateFinEstimee()) {
                finEstimee = v.date("date_fin_estimee", requete.getDateFinEstimee()).orElse(null);
            }
            v.motif(requete.getMotif());
            v.regions(requete.getRegionsList());
            v.alternatives(requete.getAlternativesList(), null);
            refuserSiInvalide(v);

            RuptureVue vue = mettreAJourRupture.mettreAJour(acteur, new MettreAJourRuptureUseCase.Commande(
                    RuptureId.depuis(requete.getId()),
                    finEstimee,
                    MappeurRuptures.motif(requete.getMotif()),
                    MappeurRuptures.regions(requete.getRegionsList()),
                    MappeurRuptures.alternatives(requete.getAlternativesList())));
            return MettreAJourRuptureResponse.newBuilder().setRupture(MappeurRuptures.rupture(vue)).build();
        });
    }

    @Override
    public void cloturerRupture(CloturerRuptureRequest requete,
                                StreamObserver<CloturerRuptureResponse> reponse) {
        traiter("CloturerRupture", reponse, acteur -> {
            ValidateurRequetes v = new ValidateurRequetes();
            v.uuid("id", requete.getId());
            LocalDate fin = v.date("date_fin_effective", requete.getDateFinEffective()).orElse(null);
            if (fin != null && fin.isAfter(horloge.aujourdhui())) {
                v.violation("date_fin_effective", "ne peut pas être dans le futur");
            }
            refuserSiInvalide(v);

            RuptureVue vue = cloturerRupture.cloturer(acteur, new CloturerRuptureUseCase.Commande(
                    RuptureId.depuis(requete.getId()), fin));
            return CloturerRuptureResponse.newBuilder().setRupture(MappeurRuptures.rupture(vue)).build();
        });
    }

    // ------------------------------------------------------------------ socle

    private static void refuserSiInvalide(ValidateurRequetes validateur) {
        if (!validateur.valide()) {
            throw new ValidationMetierException(validateur.message());
        }
    }

    /**
     * Applique authentification, puis contrôle de rôle, puis le traitement ; traduit toute
     * exception en statut gRPC sans divulguer de détail interne.
     */
    private <R> void traiter(String rpc, StreamObserver<R> reponse, Function<Acteur, R> traitement) {
        try {
            Acteur acteur = ActeurGrpcContext.acteurCourant().orElse(null);
            if (acteur == null) {
                reponse.onError(Status.UNAUTHENTICATED.withDescription("Authentification requise").asRuntimeException());
                return;
            }
            if (acteur.role() != Role.DISTRIBUTEUR) {
                reponse.onError(Status.PERMISSION_DENIED.withDescription("Accès refusé").asRuntimeException());
                return;
            }
            R resultat = traitement.apply(acteur);
            reponse.onNext(resultat);
            reponse.onCompleted();
        } catch (ExceptionMetier e) {
            reponse.onError(statut(e).asRuntimeException());
        } catch (OptimisticLockingFailureException e) {
            // Conflit normal de mise à jour concurrente (verrou optimiste, levé au commit) : le client
            // peut relire et réessayer. Message fixe, sans cause, sans pile, sans donnée de la requête.
            journaliserConflit(rpc);
            reponse.onError(Status.ABORTED.withDescription(MESSAGE_CONFLIT).asRuntimeException());
        } catch (RuntimeException e) {
            // Aucun message ni pile : le type seul est journalisé (le message peut contenir des données).
            journaliser(rpc, e);
            reponse.onError(Status.INTERNAL.withDescription("Erreur interne").asRuntimeException());
        }
    }

    private static void journaliserConflit(String rpc) {
        try {
            LOG.warn("Conflit de modification concurrente dans {}", rpc);
        } catch (RuntimeException | LinkageError ignore) {
            // rien d'autre à faire côté serveur
        }
    }

    /** Une défaillance de la journalisation ne doit jamais masquer la réponse INTERNAL. */
    private static void journaliser(String rpc, RuntimeException e) {
        try {
            LOG.error("Erreur inattendue dans {} : {}", rpc, e.getClass().getName());
        } catch (RuntimeException | LinkageError ignore) {
            // rien d'autre à faire côté serveur
        }
    }

    private static Status statut(ExceptionMetier e) {
        if (e instanceof ValidationMetierException) {
            return Status.INVALID_ARGUMENT.withDescription(e.getMessage());
        }
        if (e instanceof AccesRefuseException) {
            return Status.PERMISSION_DENIED.withDescription("Accès refusé");
        }
        if (e instanceof RuptureIntrouvableException) {
            return Status.NOT_FOUND.withDescription("Rupture introuvable");
        }
        if (e instanceof RuptureDejaClotureeException) {
            return Status.FAILED_PRECONDITION.withDescription("Rupture déjà clôturée");
        }
        return Status.INTERNAL.withDescription("Erreur interne");
    }
}
