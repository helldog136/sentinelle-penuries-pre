package vd.affsn.sentinelle.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.Stream;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import vd.affsn.sentinelle.application.port.out.ReferentielProduitsPort;
import vd.affsn.sentinelle.batch.LanceurCalculTension;
import vd.affsn.sentinelle.domain.model.CodeProduit;

/**
 * RÉFÉRENTIEL INDISPONIBLE (ADR-004 § 1, AC-15, AC-65) de bout en bout : un
 * {@link ReferentielProduitsPort} de test ({@code @Primary}) lève une exception technique. Aucun repli :
 * gRPC répond INTERNAL « Erreur interne », rien n'est écrit ni audité ; le job batch finit FAILED sans
 * écrire de fichier. TDD, phase rouge : tant que le port n'est pas consulté, tout réussit.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sentinelle-e2e-essentiel-indisponible;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "grpc.server.in-process-name=sentinelle-e2e-essentiel-indisponible"})
@Import(ReferentielIndisponibleBoutEnBoutTest.ConfigurationReferentielEnPanne.class)
class ReferentielIndisponibleBoutEnBoutTest extends AbstractEssentielBoutEnBoutTest {

    @TempDir
    static Path repertoireNotifications;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("sentinelle.batch.notifications.repertoire", () -> repertoireNotifications.toString());
    }

    /** Doublure écrite à la main : le référentiel est toujours en panne. */
    static final class ReferentielEnPanne implements ReferentielProduitsPort {
        @Override
        public boolean estEssentiel(CodeProduit code) {
            throw new IllegalStateException("référentiel indisponible (simulé) : détail interne");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfigurationReferentielEnPanne {
        @Bean
        @Primary
        ReferentielEnPanne referentielEnPanne() {
            return new ReferentielEnPanne();
        }
    }

    @Autowired
    private LanceurCalculTension lanceur;

    @Test
    void ac15_declarationInterneSansEcritureNiAuditQuelleQueSoitLaDuree() {
        long journalAvant = compterJournal();

        // 30 jours, avec alternative : la règle n'aurait rien exigé, mais le statut ne peut pas être lu.
        StatusRuntimeException e = erreurGrpc(() -> grpc(jeton201()).declarerRupture(
                declaration(ESSENTIEL_1, AUJOURDHUI, AUJOURDHUI.plusDays(30), alt(NON_ESSENTIEL_4))));

        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(e.getStatus().getDescription()).isEqualTo("Erreur interne");
        assertSansFuite(e.getStatus().getDescription());
        assertThat(compterRupturesProduit(ESSENTIEL_1)).isZero();
        assertThat(compterJournal()).isEqualTo(journalAvant);
    }

    @Test
    void ac65_leJobFinitEnEchecSansEcrireDeFichier() throws Exception {
        insererRuptureAnterieure(ESSENTIEL_1, PRO_201, LocalDate.of(2026, 11, 16), null, NON_ESSENTIEL_4);

        JobExecution execution = lanceur.lancer(LocalDate.of(2026, 11, 30));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        try (Stream<Path> fichiers = Files.list(repertoireNotifications)) {
            assertThat(fichiers.map(p -> p.getFileName().toString()).toList())
                    .noneMatch(nom -> nom.startsWith("notifications-tension-"));
        } catch (IOException ex) {
            // répertoire jamais créé : aucun fichier, c'est conforme
        }
    }
}
