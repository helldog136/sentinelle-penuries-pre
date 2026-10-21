package vd.affsn.sentinelle.security;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.common.util.InterceptorOrder;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.core.annotation.Order;

/**
 * Intercepteur gRPC global (ADR-002) : lit la metadata {@code authorization: Bearer <jwt>} (schéma
 * insensible à la casse), la fait vérifier par {@link VerificateurJwt} et place l'acteur dans
 * {@link ActeurGrpcContext#ACTEUR} via {@code Contexts.interceptCall}.
 *
 * <p>Échec : {@code Status.UNAUTHENTICATED} avec le message unique
 * {@link JetonInvalideException#MESSAGE_GENERIQUE} ; la raison n'est jamais révélée et le jeton
 * n'est jamais journalisé. Le contrôle de rôle (PERMISSION_DENIED) relève de l'adaptateur gRPC et
 * du domaine, pas de cet intercepteur.
 *
 * <p>STUB (phase A).
 */
@GrpcGlobalServerInterceptor
@Order(InterceptorOrder.ORDER_SECURITY_AUTHENTICATION)
public class IntercepteurAuthentificationGrpc implements ServerInterceptor {

    /** Clé de metadata lue (les clés gRPC sont en minuscules). */
    public static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final VerificateurJwt verificateur;

    public IntercepteurAuthentificationGrpc(VerificateurJwt verificateur) {
        this.verificateur = verificateur;
    }

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call,
                                                       Metadata headers,
                                                       ServerCallHandler<Q, R> next) {
        throw new UnsupportedOperationException("IntercepteurAuthentificationGrpc : phase B");
    }
}
