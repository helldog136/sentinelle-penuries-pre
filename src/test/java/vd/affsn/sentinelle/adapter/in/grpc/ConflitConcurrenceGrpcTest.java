package vd.affsn.sentinelle.adapter.in.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vd.affsn.sentinelle.grpc.v1.CloturerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.MettreAJourRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.MotifRupture;

/** Un conflit de verrou optimiste est ABORTED (réessayable), jamais INTERNAL, sans fuite ni ERROR. */
class ConflitConcurrenceGrpcTest extends AbstractSignalementGrpcTest {

    private static final String MESSAGE_FIXE = "Conflit de modification, veuillez réessayer";
    private static final String SECRET = "ruptures#00000000-0000-0000-0000-00000000dead";

    private final ListAppender<ILoggingEvent> journaux = new ListAppender<>();
    private ch.qos.logback.classic.Logger logger;

    @BeforeEach
    void capturerLesJournaux() {
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(SignalementGrpcService.class);
        journaux.start();
        logger.addAppender(journaux);
    }

    @AfterEach
    void libererLesJournaux() {
        logger.detachAppender(journaux);
    }

    private static MettreAJourRuptureRequest miseAJour() {
        return MettreAJourRuptureRequest.newBuilder().setId(UUID_VALIDE)
                .setMotif(MotifRupture.MOTIF_RUPTURE_DEMANDE).addRegions(PROTO_HAUTE_PAPERASSE).build();
    }

    private static CloturerRuptureRequest cloture() {
        return CloturerRuptureRequest.newBuilder().setId(UUID_VALIDE)
                .setDateFinEffective(AUJOURDHUI.minusDays(1).toString()).build();
    }

    private static RuntimeException conflit(boolean parente) {
        return parente
                ? new OptimisticLockingFailureException("version obsolète " + SECRET, new IllegalStateException(SECRET))
                : new ObjectOptimisticLockingFailureException("RuptureEntity", SECRET);
    }

    private void verifier(StatusRuntimeException erreur) {
        assertThat(erreur.getStatus().getCode()).isEqualTo(Status.Code.ABORTED);
        assertThat(erreur.getStatus().getDescription()).isEqualTo(MESSAGE_FIXE);
        assertThat(erreur.getStatus().getCause()).isNull();
        assertThat(erreur.getCause()).isNull();
        assertThat(erreur.getTrailers().keys()).doesNotContain("grpc-status-details-bin");
        assertThat(journaux.list).isNotEmpty().allSatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getThrowableProxy()).isNull();
            assertThat(e.getFormattedMessage()).doesNotContain(SECRET, UUID_VALIDE);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void mettreAJourConflitDonneAbortedSansFuite(boolean parente) {
        Supplier<RuntimeException> e = () -> conflit(parente);
        when(mettreAJour.mettreAJour(any(), any())).thenThrow(e.get());

        verifier(erreurGrpc(() -> client.mettreAJourRupture(miseAJour())));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cloturerConflitDonneAbortedSansFuite(boolean parente) {
        when(cloturer.cloturer(any(), any())).thenThrow(conflit(parente));

        verifier(erreurGrpc(() -> client.cloturerRupture(cloture())));
    }

    @Test
    void uneErreurInattendueResteInternal() {
        when(mettreAJour.mettreAJour(any(), any())).thenThrow(new IllegalStateException("x"));

        StatusRuntimeException erreur = erreurGrpc(() -> client.mettreAJourRupture(miseAJour()));

        assertThat(erreur.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
    }
}
