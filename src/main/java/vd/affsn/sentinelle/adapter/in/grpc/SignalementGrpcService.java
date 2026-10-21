package vd.affsn.sentinelle.adapter.in.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import vd.affsn.sentinelle.application.port.in.CloturerRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.DeclarerRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.MettreAJourRuptureUseCase;
import vd.affsn.sentinelle.application.port.out.HorlogePort;
import vd.affsn.sentinelle.grpc.v1.CloturerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.CloturerRuptureResponse;
import vd.affsn.sentinelle.grpc.v1.DeclarerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.DeclarerRuptureResponse;
import vd.affsn.sentinelle.grpc.v1.MettreAJourRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.MettreAJourRuptureResponse;
import vd.affsn.sentinelle.grpc.v1.SignalementServiceGrpc;

/**
 * Adaptateur gRPC d'ingestion des signalements (écriture, rôle DISTRIBUTEUR).
 *
 * <p>STUB de la phase A du TDD : aucune logique, les trois RPC répondent
 * {@code UNIMPLEMENTED}.
 */
@GrpcService
@ConditionalOnProperty(prefix = "sentinelle.grpc", name = "enabled", havingValue = "true")
public class SignalementGrpcService extends SignalementServiceGrpc.SignalementServiceImplBase {

    private static final Status NON_IMPLEMENTE = Status.UNIMPLEMENTED.withDescription("Stub phase A");

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
        reponse.onError(NON_IMPLEMENTE.asRuntimeException());
    }

    @Override
    public void mettreAJourRupture(MettreAJourRuptureRequest requete,
                                   StreamObserver<MettreAJourRuptureResponse> reponse) {
        reponse.onError(NON_IMPLEMENTE.asRuntimeException());
    }

    @Override
    public void cloturerRupture(CloturerRuptureRequest requete,
                                StreamObserver<CloturerRuptureResponse> reponse) {
        reponse.onError(NON_IMPLEMENTE.asRuntimeException());
    }
}
