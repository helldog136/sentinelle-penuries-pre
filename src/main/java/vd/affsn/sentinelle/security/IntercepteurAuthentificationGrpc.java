package vd.affsn.sentinelle.security;

import java.util.Iterator;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import net.devh.boot.grpc.common.util.InterceptorOrder;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.core.annotation.Order;

import vd.affsn.sentinelle.domain.model.Acteur;

/**
 * Intercepteur gRPC global (ADR-002) : lit la metadata {@code authorization: Bearer <jwt>} (schéma
 * insensible à la casse, une seule valeur), la fait vérifier par {@link VerificateurJwt} et place
 * l'acteur dans {@link ActeurGrpcContext#ACTEUR} via {@code Contexts.interceptCall}.
 *
 * <p>Échec : {@code Status.UNAUTHENTICATED} avec le message unique
 * {@link JetonInvalideException#MESSAGE_GENERIQUE} ; la raison n'est jamais révélée et le jeton
 * n'est jamais journalisé. Le contrôle de rôle (PERMISSION_DENIED) relève de l'adaptateur gRPC et
 * du domaine, pas de cet intercepteur.
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
        Acteur acteur;
        try {
            acteur = verificateur.verifier(jetonUnique(headers));
        } catch (JetonInvalideException refus) {
            call.close(Status.UNAUTHENTICATED.withDescription(JetonInvalideException.MESSAGE_GENERIQUE), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }
        Context contexte = Context.current().withValue(ActeurGrpcContext.ACTEUR, acteur);
        return Contexts.interceptCall(contexte, call, headers, next);
    }

    private static String jetonUnique(Metadata headers) {
        Iterable<String> valeurs = headers.getAll(AUTHORIZATION);
        if (valeurs == null) {
            throw new JetonInvalideException();
        }
        Iterator<String> it = valeurs.iterator();
        String valeur = it.next();
        if (it.hasNext()) {
            throw new JetonInvalideException();
        }
        return ExtracteurBearer.extraire(valeur).orElseThrow(JetonInvalideException::new);
    }
}
