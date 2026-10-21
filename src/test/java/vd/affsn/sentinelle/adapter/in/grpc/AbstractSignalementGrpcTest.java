package vd.affsn.sentinelle.adapter.in.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import vd.affsn.sentinelle.application.port.in.CloturerRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.DeclarerRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.MettreAJourRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.application.port.out.HorlogePort;
import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.IdentifiantDistributeur;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.domain.model.StatutRupture;
import vd.affsn.sentinelle.grpc.v1.SignalementServiceGrpc;
import vd.affsn.sentinelle.security.ActeurGrpcContext;

/**
 * Socle des tests de l'adaptateur gRPC : serveur gRPC in-process (aucun port réseau), cas
 * d'usage et horloge simulés avec Mockito, intercepteur de test qui place l'acteur dans le
 * contexte gRPC comme le ferait l'intercepteur JWT (aucun acteur si {@code acteurAuthentifie}
 * est vide).
 */
abstract class AbstractSignalementGrpcTest {

    /** « Aujourd'hui » fixe, indépendant de l'horloge réelle. */
    static final LocalDate AUJOURDHUI = LocalDate.of(2026, 6, 15);
    static final IdentifiantDistributeur DISTRIBUTEUR = IdentifiantDistributeur.de("PRO-000042");
    static final String UUID_VALIDE = "123e4567-e89b-42d3-a456-426614174000";

    static final vd.affsn.sentinelle.grpc.v1.Region PROTO_HAUTE_PAPERASSE =
            vd.affsn.sentinelle.grpc.v1.Region.REGION_HAUTE_PAPERASSE;
    static final vd.affsn.sentinelle.grpc.v1.Region PROTO_PROVINCE =
            vd.affsn.sentinelle.grpc.v1.Region.REGION_PROVINCE_DU_TICKET_OUVERT;
    static final List<vd.affsn.sentinelle.grpc.v1.Region> PROTO_SIX_REGIONS = List.of(
            vd.affsn.sentinelle.grpc.v1.Region.REGION_HAUTE_PAPERASSE,
            vd.affsn.sentinelle.grpc.v1.Region.REGION_BASSE_VALIDATION,
            vd.affsn.sentinelle.grpc.v1.Region.REGION_PLATEAU_DU_LEGACY,
            vd.affsn.sentinelle.grpc.v1.Region.REGION_COTE_DU_TIMEOUT,
            vd.affsn.sentinelle.grpc.v1.Region.REGION_MARAIS_DE_LA_RECETTE,
            vd.affsn.sentinelle.grpc.v1.Region.REGION_PROVINCE_DU_TICKET_OUVERT);

    protected final DeclarerRuptureUseCase declarer = mock(DeclarerRuptureUseCase.class);
    protected final MettreAJourRuptureUseCase mettreAJour = mock(MettreAJourRuptureUseCase.class);
    protected final CloturerRuptureUseCase cloturer = mock(CloturerRuptureUseCase.class);
    protected final HorlogePort horloge = mock(HorlogePort.class);

    /** Acteur que l'« intercepteur JWT » de test place dans le contexte ; vide = non authentifié. */
    protected final AtomicReference<Acteur> acteurAuthentifie = new AtomicReference<>();

    protected SignalementServiceGrpc.SignalementServiceBlockingStub client;

    private Server serveur;
    private ManagedChannel canal;

    @BeforeEach
    void demarrerServeur() throws IOException {
        when(horloge.aujourdhui()).thenReturn(AUJOURDHUI);
        when(horloge.maintenant()).thenReturn(AUJOURDHUI.atStartOfDay(ZoneOffset.UTC).toInstant());
        acteurAuthentifie.set(distributeurCourant());

        String nom = InProcessServerBuilder.generateName();
        serveur = InProcessServerBuilder.forName(nom)
                .directExecutor()
                .addService(ServerInterceptors.intercept(
                        new SignalementGrpcService(declarer, mettreAJour, cloturer, horloge),
                        intercepteurActeur()))
                .build()
                .start();
        canal = InProcessChannelBuilder.forName(nom).directExecutor().build();
        client = SignalementServiceGrpc.newBlockingStub(canal);
    }

    @AfterEach
    void arreterServeur() throws InterruptedException {
        canal.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        serveur.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    private ServerInterceptor intercepteurActeur() {
        return new ServerInterceptor() {
            @Override
            public <A, B> ServerCall.Listener<A> interceptCall(
                    ServerCall<A, B> appel, Metadata entetes, ServerCallHandler<A, B> suite) {
                Acteur acteur = acteurAuthentifie.get();
                Context contexte = acteur == null
                        ? Context.current()
                        : Context.current().withValue(ActeurGrpcContext.ACTEUR, acteur);
                return Contexts.interceptCall(contexte, appel, entetes, suite);
            }
        };
    }

    // ---------------------------------------------------------------- acteurs

    static Acteur distributeurCourant() {
        return Acteur.distributeur("distributeur-fictif-01", DISTRIBUTEUR);
    }

    // ------------------------------------------------------------- assertions

    /** Exécute l'appel et renvoie l'erreur gRPC reçue (échoue si l'appel réussit). */
    protected static StatusRuntimeException erreurGrpc(ThrowingCallable appel) {
        Throwable erreur = catchThrowable(appel);
        assertThat(erreur).isInstanceOf(StatusRuntimeException.class);
        return (StatusRuntimeException) erreur;
    }

    protected void assertAucunCasDUsageAppele() {
        verifyNoInteractions(declarer, mettreAJour, cloturer);
    }

    /** INVALID_ARGUMENT dont la description cite le champ ; aucun cas d'usage n'est appelé. */
    protected void assertInvalide(ThrowingCallable appel, String champ) {
        StatusRuntimeException erreur = erreurGrpc(appel);
        assertThat(erreur.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(erreur.getStatus().getDescription()).contains(champ + ": ");
        assertAucunCasDUsageAppele();
    }

    protected void assertCode(ThrowingCallable appel, Status.Code attendu) {
        assertThat(erreurGrpc(appel).getStatus().getCode()).isEqualTo(attendu);
    }

    // ----------------------------------------------------------- jeux de données

    static vd.affsn.sentinelle.grpc.v1.Alternative alternativeProto(String code, String commentaire) {
        return vd.affsn.sentinelle.grpc.v1.Alternative.newBuilder()
                .setCodeProduit(code)
                .setCommentaire(commentaire)
                .build();
    }

    static vd.affsn.sentinelle.grpc.v1.Alternative alternativeProto(String code) {
        return vd.affsn.sentinelle.grpc.v1.Alternative.newBuilder().setCodeProduit(code).build();
    }

    /** Vue renvoyée par les cas d'usage simulés. */
    static RuptureVue vue(StatutRupture statut, LocalDate finEffective) {
        return new RuptureVue(
                RuptureId.depuis(UUID_VALIDE),
                CodeProduit.de("VMP-123456"),
                DISTRIBUTEUR,
                Periode.entre(AUJOURDHUI.minusDays(3), AUJOURDHUI.plusDays(30)),
                Optional.ofNullable(finEffective),
                Motif.LOGISTIQUE,
                Set.of(Region.PROVINCE_DU_TICKET_OUVERT, Region.HAUTE_PAPERASSE),
                List.of(new Alternative(CodeProduit.de("VMP-654321"), "Boîte de 30"),
                        Alternative.sansCommentaire(CodeProduit.de("VMP-111111"))),
                statut);
    }
}
