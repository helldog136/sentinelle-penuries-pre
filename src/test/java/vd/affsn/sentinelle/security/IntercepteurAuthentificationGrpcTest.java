package vd.affsn.sentinelle.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.ServerCalls;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import vd.affsn.sentinelle.domain.Fixtures;
import vd.affsn.sentinelle.domain.exception.AccesRefuseException;
import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.IdentifiantDistributeur;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;

/**
 * Intercepteur gRPC en in-process, avec un service de SONDE défini côté tests (méthode unaire
 * texte → texte) qui renvoie l'Acteur lu dans {@link ActeurGrpcContext}.
 */
@ExtendWith(OutputCaptureExtension.class)
class IntercepteurAuthentificationGrpcTest {

    private static final Instant T0 = Instant.parse("2026-10-21T08:00:00Z");
    private static final JwtTestFactory JETONS = JwtTestFactory.a(T0);
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private static final MethodDescriptor.Marshaller<String> TEXTE = new MethodDescriptor.Marshaller<>() {
        @Override
        public InputStream stream(String valeur) {
            return new ByteArrayInputStream(valeur.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream flux) {
            try {
                return new String(flux.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    };

    private static final MethodDescriptor<String, String> QUI = methode("Qui");
    private static final MethodDescriptor<String, String> MODIFIER = methode("Modifier");

    /** Rupture déclarée par PRO-111111, cible des tentatives de modification. */
    private static final Rupture RUPTURE_DE_111 = Rupture.declarer(
            RuptureId.generer(),
            Fixtures.PRODUIT,
            Acteur.distributeur("sujet-proprietaire", IdentifiantDistributeur.de("PRO-111111")),
            Periode.aPartirDu(LocalDate.of(2026, 10, 1)),
            Motif.PRODUCTION,
            Fixtures.regions(Region.HAUTE_PAPERASSE),
            List.of());

    private final AtomicInteger appelsAuService = new AtomicInteger();
    private Server serveur;
    private ManagedChannel canal;

    private static MethodDescriptor<String, String> methode(String nom) {
        return MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName("sentinelle.test.Sonde", nom))
                .setRequestMarshaller(TEXTE)
                .setResponseMarshaller(TEXTE)
                .build();
    }

    @BeforeEach
    void demarrer() throws IOException {
        VerificateurJwt verificateur = new VerificateurJwt(FabriqueDecodeurJwt.creer(
                JwtTestFactory.clePubliqueTest(), JwtTestFactory.EMETTEUR, JwtTestFactory.AUDIENCE,
                Duration.ofSeconds(30), Clock.fixed(T0, ZoneOffset.UTC)));

        ServerServiceDefinition sonde = ServerServiceDefinition.builder("sentinelle.test.Sonde")
                .addMethod(QUI, ServerCalls.asyncUnaryCall((requete, reponse) -> {
                    appelsAuService.incrementAndGet();
                    reponse.onNext(ActeurGrpcContext.acteurCourant().map(IntercepteurAuthentificationGrpcTest::decrire)
                            .orElse("AUCUN"));
                    reponse.onCompleted();
                }))
                .addMethod(MODIFIER, ServerCalls.asyncUnaryCall((requete, reponse) -> {
                    // La requête prétend agir pour PRO-111111 : elle est ignorée pour l'identité.
                    appelsAuService.incrementAndGet();
                    Acteur acteur = ActeurGrpcContext.acteurCourant().orElseThrow();
                    String resultat;
                    try {
                        RUPTURE_DE_111.mettreAJour(acteur, RUPTURE_DE_111.periode(), Motif.LOGISTIQUE,
                                RUPTURE_DE_111.regions(), List.of());
                        resultat = "MODIFIEE";
                    } catch (AccesRefuseException refus) {
                        resultat = "ACCES_REFUSE";
                    }
                    reponse.onNext(resultat);
                    reponse.onCompleted();
                }))
                .build();

        String nom = InProcessServerBuilder.generateName();
        serveur = InProcessServerBuilder.forName(nom).directExecutor()
                .addService(ServerInterceptors.intercept(sonde, new IntercepteurAuthentificationGrpc(verificateur)))
                .build().start();
        canal = InProcessChannelBuilder.forName(nom).directExecutor().build();
    }

    @AfterEach
    void arreter() {
        canal.shutdownNow();
        serveur.shutdownNow();
    }

    private static String decrire(Acteur acteur) {
        return acteur.sujet() + "|" + acteur.role() + "|"
                + acteur.identifiantDistributeur().map(Object::toString).orElse("-");
    }

    private String appeler(MethodDescriptor<String, String> methode, Metadata metadata, String requete) {
        Channel avecMetadata = ClientInterceptors.intercept(canal, MetadataUtils.newAttachHeadersInterceptor(metadata));
        return ClientCalls.blockingUnaryCall(avecMetadata, methode, CallOptions.DEFAULT, requete);
    }

    private static Metadata authorization(String... valeurs) {
        Metadata metadata = new Metadata();
        for (String valeur : valeurs) {
            metadata.put(AUTHORIZATION, valeur);
        }
        return metadata;
    }

    private void assertNonAuthentifie(Metadata metadata, String jetonAEviter, CapturedOutput sortie) {
        StatusRuntimeException refus = catchThrowableOfType(
                () -> appeler(QUI, metadata, "requete"), StatusRuntimeException.class);

        assertThat(refus).as("appel refusé par un statut gRPC").isNotNull();
        assertThat(refus.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(refus.getStatus().getDescription()).isEqualTo(JetonInvalideException.MESSAGE_GENERIQUE);
        assertThat(refus.getStatus().getCause()).isNull();
        assertThat(appelsAuService).as("le service ne doit pas être atteint").hasValue(0);
        if (jetonAEviter != null && !jetonAEviter.isBlank()) {
            assertThat(sortie.getAll()).as("le jeton ne doit jamais être journalisé").doesNotContain(jetonAEviter);
        }
    }

    // ------------------------------------------------------------------ accepté

    @Test
    void distributeurValidePlaceLActeurDuJetonDansLeContexte() {
        String reponse = appeler(QUI, authorization("Bearer " + JETONS.distributeur111()), "requete");

        assertThat(reponse).isEqualTo(JwtTestFactory.SUJET_DISTRIBUTEUR_111 + "|DISTRIBUTEUR|PRO-111111");
    }

    @Test
    void schemaInsensibleALaCasse() {
        assertThat(appeler(QUI, authorization("bearer " + JETONS.pharmacien()), "r")).contains("|PHARMACIEN|");
        assertThat(appeler(QUI, authorization("BEARER " + JETONS.prescripteur()), "r")).contains("|PRESCRIPTEUR|");
    }

    @Test
    void lIntercepteurNeFiltrePasLesRoles() {
        // Le contrôle de rôle (PERMISSION_DENIED) relève de l'adaptateur et du domaine.
        assertThat(appeler(QUI, authorization("Bearer " + JETONS.pharmacien()), "r"))
                .isEqualTo(JwtTestFactory.SUJET_PHARMACIEN + "|PHARMACIEN|-");
    }

    @Test
    void lActeurNeFuitPasHorsDeLAppel() {
        appeler(QUI, authorization("Bearer " + JETONS.distributeur111()), "r");
        assertThat(ActeurGrpcContext.acteurCourant()).isEmpty();
    }

    // ------------------------------------------------------------------ refusé

    @Test
    void sansMetadataAuthorization(CapturedOutput sortie) {
        assertNonAuthentifie(new Metadata(), null, sortie);
    }

    static Stream<Arguments> entetesRefuses() {
        return Stream.of(
                cas("jeton expiré", j -> "Bearer " + j.expire()),
                cas("pas encore valide", j -> "Bearer " + j.pasEncoreValide()),
                cas("signature d'une autre clé", j -> "Bearer " + j.signeParUneAutreCle()),
                cas("charge utile altérée", j -> "Bearer " + j.signatureAlteree()),
                cas("alg none", j -> "Bearer " + j.algNone()),
                cas("confusion HS256", j -> "Bearer " + j.confusionHs256()),
                cas("RS512 non épinglé", j -> "Bearer " + j.algorithmeNonEpingle(com.nimbusds.jose.JWSAlgorithm.RS512)),
                cas("mauvais émetteur", j -> "Bearer " + j.mauvaisEmetteur()),
                cas("mauvaise audience", j -> "Bearer " + j.mauvaiseAudience()),
                cas("rôle inconnu", j -> "Bearer " + j.roleInconnu()),
                cas("DISTRIBUTEUR sans distributeur_id", j -> "Bearer " + j.distributeurSansIdentifiant()),
                cas("PHARMACIEN avec distributeur_id", j -> "Bearer " + j.pharmacienAvecIdentifiantDistributeur()),
                cas("jeton surdimensionné", j -> "Bearer " + j.surdimensionne()),
                cas("jeton mal formé", j -> "Bearer " + JwtTestFactory.malForme()),
                cas("schéma Basic", j -> "Basic dXRpbGlzYXRldXI6bW90LWRlLXBhc3Nl"),
                cas("Bearer sans jeton", j -> "Bearer "),
                cas("jeton sans schéma", j -> j.distributeur111()));
    }

    private static Arguments cas(String nom, Function<JwtTestFactory, String> entete) {
        return Arguments.of(nom, entete);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entetesRefuses")
    void enTeteRefuseUnauthenticatedAvecMessageGenerique(String nom, Function<JwtTestFactory, String> fabrique,
                                                         CapturedOutput sortie) {
        String entete = fabrique.apply(JETONS);
        String jeton = entete.regionMatches(true, 0, "Bearer ", 0, 7) ? entete.substring(7) : entete;

        assertNonAuthentifie(authorization(entete), jeton, sortie);
    }

    @Test
    void plusieursValeursAuthorizationRefusees(CapturedOutput sortie) {
        assertNonAuthentifie(authorization("Bearer " + JETONS.pharmacien(), "Bearer " + JETONS.distributeur111()),
                null, sortie);
    }

    // ------------------------------------------------------------------ usurpation

    @Test
    void unChampDeRequeteOuUneMetadataNePeuventPasUsurperLIdentite() {
        Metadata metadata = authorization("Bearer " + JETONS.distributeur222());
        metadata.put(Metadata.Key.of("x-distributeur-id", Metadata.ASCII_STRING_MARSHALLER), "PRO-111111");

        String reponse = appeler(QUI, metadata, "distributeur_id=PRO-111111");

        assertThat(reponse).isEqualTo(JwtTestFactory.SUJET_DISTRIBUTEUR_222 + "|DISTRIBUTEUR|PRO-222222");
    }

    @Test
    void leDistributeur222NePeutPasModifierLaDeclarationDe111() {
        Metadata metadata = authorization("Bearer " + JETONS.distributeur222());
        metadata.put(Metadata.Key.of("x-distributeur-id", Metadata.ASCII_STRING_MARSHALLER), "PRO-111111");

        assertThat(appeler(MODIFIER, metadata, "distributeur_id=PRO-111111")).isEqualTo("ACCES_REFUSE");
    }

    @Test
    void leDistributeur111PeutModifierSaDeclaration() {
        assertThat(appeler(MODIFIER, authorization("Bearer " + JETONS.distributeur111()), "r")).isEqualTo("MODIFIEE");
    }

    // ------------------------------------------------------------------ câblage

    @Test
    void estUnIntercepteurGlobalDuStarter() {
        assertThat(IntercepteurAuthentificationGrpc.class.isAnnotationPresent(GrpcGlobalServerInterceptor.class)).isTrue();
    }
}
