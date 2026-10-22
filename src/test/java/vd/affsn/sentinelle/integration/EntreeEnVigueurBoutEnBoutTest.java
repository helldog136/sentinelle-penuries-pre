package vd.affsn.sentinelle.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import vd.affsn.sentinelle.batch.LanceurCalculTension;
import vd.affsn.sentinelle.grpc.v1.Region;
import vd.affsn.sentinelle.grpc.v1.Rupture;

/**
 * ENTRÉE EN VIGUEUR (ADR-004 § 5) de bout en bout, horloge métier pilotée (TDD, phase rouge).
 *
 * <p>Propriété {@code sentinelle.regles.alternative-obligatoire.entree-en-vigueur = 2026-12-01} :
 * avant cette date (date de l'ACTE, selon {@code HorlogePort}), la règle ne s'applique ni à la
 * déclaration ni à la mise à jour ; le jour même, elle s'applique (borne incluse). La pondération × 2
 * du batch, elle, ignore cette date. Le cas « propriété absente => immédiate » (AC-20) est couvert par
 * les classes qui ne fixent pas la propriété ({@link ParcoursProduitEssentielBoutEnBoutTest}, étape a).
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sentinelle-e2e-essentiel-vigueur;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "grpc.server.in-process-name=sentinelle-e2e-essentiel-vigueur",
        EntreeEnVigueurBoutEnBoutTest.PROPRIETE + "=2026-12-01"})
class EntreeEnVigueurBoutEnBoutTest extends AbstractEssentielBoutEnBoutTest {

    static final String PROPRIETE = "sentinelle.regles.alternative-obligatoire.entree-en-vigueur";

    private static final LocalDate VEILLE = LocalDate.of(2026, 11, 30);
    private static final LocalDate ENTREE_EN_VIGUEUR = LocalDate.of(2026, 12, 1);

    @TempDir
    static Path repertoireNotifications;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("sentinelle.batch.notifications.repertoire", () -> repertoireNotifications.toString());
    }

    @Autowired
    private LanceurCalculTension lanceur;

    @Test
    void ac16_laVeilleDeLEntreeEnVigueurLaDeclarationSansAlternativeEstAcceptee() {
        horloge.regler(VEILLE);

        Rupture r = grpc(jeton201()).declarerRupture(declaration(ESSENTIEL_1, VEILLE, null)).getRupture();

        assertThat(r.getAlternativesList()).isEmpty();
        assertThat(r.getEssentiel()).as("essentiel exposé même quand la règle est inactive").isTrue();
    }

    @Test
    void ac17_leJourMemeDeLEntreeEnVigueurLaRegleSAppliqueBorneIncluse() {
        horloge.regler(ENTREE_EN_VIGUEUR);
        long avant = compterJournal();

        assertRefuseParLaRegle(() -> grpc(jeton201()).declarerRupture(
                declaration(ESSENTIEL_1, ENTREE_EN_VIGUEUR, null)));
        assertThat(compterJournal()).isEqualTo(avant);
    }

    @Test
    void ac18_cEstLaDateDeLActeQuiCompteEtNonLaDateDeDebutDeLaRupture() {
        horloge.regler(VEILLE);
        String id = grpc(jeton201()).declarerRupture(declaration(ESSENTIEL_1, VEILLE, null)).getRupture().getId();
        // Mise à jour identique la veille : acceptée.
        Rupture veille = grpc(jeton201()).mettreAJourRupture(miseAJour(id, null)).getRupture();
        assertThat(veille.getEssentiel()).isTrue();
        Map<String, Object> avant = etatEnBase(id);
        long journalAvant = compterJournal();

        // Même mise à jour le lendemain (horloge avancée, sans redémarrage) : refusée.
        horloge.regler(ENTREE_EN_VIGUEUR);
        assertRefuseParLaRegle(() -> grpc(jeton201()).mettreAJourRupture(miseAJour(id, null)));
        assertThat(etatEnBase(id)).isEqualTo(avant);
        assertThat(compterJournal()).isEqualTo(journalAvant);
        assertThat(typesAudit(id)).containsExactly("DECLARATION", "MODIFICATION");
    }

    @Test
    void ac61_laPonderationIgnoreLaDateDEntreeEnVigueur() throws Exception {
        // Région dédiée pour ne pas cumuler les ruptures des autres tests de la classe.
        horloge.regler(VEILLE);
        grpc(jeton201()).declarerRupture(declaration(ESSENTIEL_1, LocalDate.of(2026, 11, 16), null,
                List.of(Region.REGION_BASSE_VALIDATION), alt(NON_ESSENTIEL_4)));

        JobExecution execution = lanceur.lancer(VEILLE); // D = 2026-11-30, antérieure à l'entrée en vigueur

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        var contexte = execution.getStepExecutions().iterator().next().getExecutionContext();
        assertThat(contexte.getDouble("indice.BASSE_VALIDATION")).isCloseTo(6.0, within(1e-9));
    }
}
