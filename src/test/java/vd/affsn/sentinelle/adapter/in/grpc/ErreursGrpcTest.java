package vd.affsn.sentinelle.adapter.in.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.stream.Stream;
import net.devh.boot.grpc.server.service.GrpcService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import vd.affsn.sentinelle.domain.exception.AccesRefuseException;
import vd.affsn.sentinelle.domain.exception.RuptureDejaClotureeException;
import vd.affsn.sentinelle.domain.exception.RuptureIntrouvableException;
import vd.affsn.sentinelle.domain.exception.ValidationMetierException;
import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.grpc.v1.CloturerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.DeclarerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.MettreAJourRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.MotifRupture;

/**
 * Authentification, rôle, ordre d'évaluation et table de correspondance exceptions du domaine
 * vers codes gRPC, vérifiés à travers les RPC DeclarerRupture et CloturerRupture.
 */
class ErreursGrpcTest extends AbstractSignalementGrpcTest {

    private static DeclarerRuptureRequest declarationValide() {
        return DeclarerRuptureRequest.newBuilder()
                .setCodeProduit("VMP-123456")
                .setDateDebut(AUJOURDHUI.minusDays(3).toString())
                .setMotif(MotifRupture.MOTIF_RUPTURE_PRODUCTION)
                .addRegions(PROTO_HAUTE_PAPERASSE)
                .build();
    }

    private static CloturerRuptureRequest clotureValide() {
        return CloturerRuptureRequest.newBuilder()
                .setId(UUID_VALIDE)
                .setDateFinEffective(AUJOURDHUI.minusDays(1).toString())
                .build();
    }

    private static MettreAJourRuptureRequest miseAJourValide() {
        return MettreAJourRuptureRequest.newBuilder()
                .setId(UUID_VALIDE)
                .setMotif(MotifRupture.MOTIF_RUPTURE_DEMANDE)
                .addRegions(PROTO_HAUTE_PAPERASSE)
                .build();
    }

    // ------------------------------------------------------------ configuration

    @Test
    void leServiceEstUnServiceGrpcActiveSeulementParLaPropriete() {
        assertThat(SignalementGrpcService.class.isAnnotationPresent(GrpcService.class)).isTrue();
        ConditionalOnProperty condition = SignalementGrpcService.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("sentinelle.grpc");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
    }

    // ---------------------------------------------------------- UNAUTHENTICATED

    @Test
    void declarerSansActeurDansLeContexteEstNonAuthentifie() {
        acteurAuthentifie.set(null);
        assertCode(() -> client.declarerRupture(declarationValide()), Status.Code.UNAUTHENTICATED);
        assertAucunCasDUsageAppele();
    }

    @Test
    void mettreAJourSansActeurDansLeContexteEstNonAuthentifie() {
        acteurAuthentifie.set(null);
        assertCode(() -> client.mettreAJourRupture(miseAJourValide()), Status.Code.UNAUTHENTICATED);
        assertAucunCasDUsageAppele();
    }

    @Test
    void cloturerSansActeurDansLeContexteEstNonAuthentifie() {
        acteurAuthentifie.set(null);
        assertCode(() -> client.cloturerRupture(clotureValide()), Status.Code.UNAUTHENTICATED);
        assertAucunCasDUsageAppele();
    }

    @Test
    void lAuthentificationPrecedeLaValidation() {
        acteurAuthentifie.set(null);
        assertCode(() -> client.declarerRupture(DeclarerRuptureRequest.getDefaultInstance()),
                Status.Code.UNAUTHENTICATED);
        assertAucunCasDUsageAppele();
    }

    // -------------------------------------------------------- PERMISSION_DENIED (rôle)

    static Stream<Acteur> acteursNonDistributeurs() {
        return Stream.of(Acteur.pharmacien("pharmacien-fictif-01"), Acteur.prescripteur("prescripteur-fictif-01"));
    }

    @ParameterizedTest
    @MethodSource("acteursNonDistributeurs")
    void declarerParUnActeurNonDistributeurEstRefuse(Acteur acteur) {
        acteurAuthentifie.set(acteur);
        when(declarer.declarer(any(), any())).thenThrow(new AccesRefuseException("Seul un distributeur peut déclarer"));

        assertCode(() -> client.declarerRupture(declarationValide()), Status.Code.PERMISSION_DENIED);
    }

    @ParameterizedTest
    @MethodSource("acteursNonDistributeurs")
    void cloturerParUnActeurNonDistributeurEstRefuse(Acteur acteur) {
        acteurAuthentifie.set(acteur);
        when(cloturer.cloturer(any(), any())).thenThrow(new AccesRefuseException("Seul le déclarant peut modifier"));

        assertCode(() -> client.cloturerRupture(clotureValide()), Status.Code.PERMISSION_DENIED);
    }

    @Test
    void leRolePrecedeLaValidationPourLesTroisRpc() {
        acteurAuthentifie.set(Acteur.pharmacien("pharmacien-fictif-01"));

        assertCode(() -> client.declarerRupture(DeclarerRuptureRequest.getDefaultInstance()),
                Status.Code.PERMISSION_DENIED);
        assertCode(() -> client.mettreAJourRupture(MettreAJourRuptureRequest.getDefaultInstance()),
                Status.Code.PERMISSION_DENIED);
        assertCode(() -> client.cloturerRupture(CloturerRuptureRequest.getDefaultInstance()),
                Status.Code.PERMISSION_DENIED);
        assertAucunCasDUsageAppele();
    }

    // ------------------------------------------- table exceptions du domaine -> code

    static Stream<Arguments> exceptionsDuDomaine() {
        return Stream.of(
                Arguments.of(new ValidationMetierException("Période : la fin estimée ne peut pas précéder le début"),
                        Status.Code.INVALID_ARGUMENT),
                Arguments.of(new AccesRefuseException("Seul le distributeur déclarant peut modifier cette rupture"),
                        Status.Code.PERMISSION_DENIED),
                Arguments.of(new RuptureIntrouvableException(RuptureId.depuis(UUID_VALIDE)),
                        Status.Code.NOT_FOUND),
                Arguments.of(new RuptureDejaClotureeException(RuptureId.depuis(UUID_VALIDE)),
                        Status.Code.FAILED_PRECONDITION));
    }

    @ParameterizedTest
    @MethodSource("exceptionsDuDomaine")
    void cloturerTraduitLesExceptionsDuDomaine(RuntimeException exception, Status.Code attendu) {
        when(cloturer.cloturer(any(), any())).thenThrow(exception);

        StatusRuntimeException erreur = erreurGrpc(() -> client.cloturerRupture(clotureValide()));

        assertThat(erreur.getStatus().getCode()).isEqualTo(attendu);
        assertThat(erreur.getStatus().getDescription()).isNotBlank();
    }

    @ParameterizedTest
    @MethodSource("exceptionsDuDomaine")
    void declarerTraduitLesExceptionsDuDomaine(RuntimeException exception, Status.Code attendu) {
        when(declarer.declarer(any(), any())).thenThrow(exception);

        StatusRuntimeException erreur = erreurGrpc(() -> client.declarerRupture(declarationValide()));

        assertThat(erreur.getStatus().getCode()).isEqualTo(attendu);
        assertThat(erreur.getStatus().getDescription()).isNotBlank();
    }

    @Test
    void uneViolationMetierDuDomaineEstRestitueeAuClient() {
        when(declarer.declarer(any(), any()))
                .thenThrow(new ValidationMetierException("Rupture : une alternative ne peut pas être le produit en rupture"));

        StatusRuntimeException erreur = erreurGrpc(() -> client.declarerRupture(declarationValide()));

        assertThat(erreur.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(erreur.getStatus().getDescription()).contains("une alternative ne peut pas être le produit en rupture");
    }

    @Test
    void lAccesRefuseNeDivulguePasLeProprietaireDeLaRupture() {
        when(cloturer.cloturer(any(), any()))
                .thenThrow(new AccesRefuseException("Rupture appartenant au distributeur PRO-000099"));

        StatusRuntimeException erreur = erreurGrpc(() -> client.cloturerRupture(clotureValide()));

        assertThat(erreur.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(String.valueOf(erreur.getStatus().getDescription())).doesNotContain("PRO-000099");
    }

    // ----------------------------------------------------------------- INTERNAL

    static Stream<RuntimeException> erreursInattendues() {
        return Stream.of(
                new IllegalStateException("jdbc:postgresql://db-interne:5432/sentinelle password=hunter2"),
                new NullPointerException("Cannot invoke \"Object.toString()\" because secret is null"),
                new UnsupportedOperationException());
    }

    @ParameterizedTest
    @MethodSource("erreursInattendues")
    void uneErreurInattendueDonneInternalSansAucuneFuite(RuntimeException exception) {
        when(declarer.declarer(any(), any())).thenThrow(exception);

        StatusRuntimeException erreur = erreurGrpc(() -> client.declarerRupture(declarationValide()));

        assertThat(erreur.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
        String description = String.valueOf(erreur.getStatus().getDescription());
        assertThat(description)
                .doesNotContain("hunter2", "jdbc", "postgresql", "secret", "Exception", "java.", "\tat ");
        assertThat(erreur.getStatus().getCause()).isNull();
        assertThat(erreur.getTrailers().keys()).doesNotContain("grpc-status-details-bin");
    }

    @Test
    void uneErreurInattendueALaClotureDonneAussiInternal() {
        when(cloturer.cloturer(any(), any())).thenThrow(new IllegalStateException("détail interne confidentiel"));

        StatusRuntimeException erreur = erreurGrpc(() -> client.cloturerRupture(clotureValide()));

        assertThat(erreur.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(String.valueOf(erreur.getStatus().getDescription())).doesNotContain("confidentiel");
    }
}
