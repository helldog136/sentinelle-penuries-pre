package vd.affsn.sentinelle.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;

import vd.affsn.sentinelle.application.port.out.NotificationRegionPort;
import vd.affsn.sentinelle.application.port.out.NotificationRegionPort.Notification;
import vd.affsn.sentinelle.domain.Fixtures;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.domain.model.StatutRupture;
import vd.affsn.sentinelle.domain.tension.CalculateurTension;
import vd.affsn.sentinelle.domain.tension.SeuilTension;

/**
 * Pondération des produits essentiels dans le tasklet (ADR-004 § 4, AC-48 à AC-65), testée sans
 * Spring : faux dépôt, faux référentiel, calculateur RÉEL du domaine.
 *
 * <p>Référentiel de l'ADR : {@code VMP-100001} et {@code VMP-300003} essentiels ; {@code VMP-200002}
 * et {@code VMP-400004} absents. D = 2026-11-30, seuil 10.0 (sauf mention).
 */
class CalculTensionTaskletEssentielsTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 30);
    private static final LocalDate IL_Y_A_14_JOURS = LocalDate.of(2026, 11, 16);
    private static final LocalDate IL_Y_A_7_JOURS = LocalDate.of(2026, 11, 23);
    private static final String NOM_FICHIER = "notifications-tension-2026-11-30.json";
    private static final String ESS_1 = "VMP-100001";
    private static final String NON_ESS = "VMP-200002";
    private static final String ESS_3 = "VMP-300003";
    private static final String INCONNU = "VMP-999999";
    private static final double EPS = 1e-9;

    @TempDir
    Path tmp;

    private final FauxRuptureRepository depot = new FauxRuptureRepository();
    private final FauxReferentielProduits referentiel = new FauxReferentielProduits().avec(ESS_1, ESS_3);
    private final List<Notification> publiees = new ArrayList<>();
    private final NotificationRegionPort capture = publiees::add;

    private static Rupture rupture(String produit, LocalDate debut, Region premiere, Region... autres) {
        return rupture(produit, debut, List.of(), premiere, autres);
    }

    private static Rupture rupture(String produit, LocalDate debut, List<Alternative> alternatives,
            Region premiere, Region... autres) {
        return Rupture.declarer(RuptureId.generer(), new CodeProduit(produit), Fixtures.ACTEUR_A,
                Periode.aPartirDu(debut), Motif.PRODUCTION, EnumSet.of(premiere, autres), alternatives);
    }

    private static Rupture cloturee(String produit, LocalDate debut, LocalDate fin, Region region) {
        return Rupture.reconstituer(RuptureId.generer(), new CodeProduit(produit), Fixtures.DISTRIBUTEUR_A,
                Periode.aPartirDu(debut), Motif.LOGISTIQUE, EnumSet.of(region), List.of(),
                StatutRupture.CLOTUREE, fin);
    }

    private static StepExecution stepPour(LocalDate date) {
        return MetaDataInstanceFactory.createStepExecution(
                new JobParametersBuilder().addString("dateReference", date.toString()).toJobParameters());
    }

    private CalculTensionTasklet tasklet(NotificationRegionPort port, double seuil) {
        return new CalculTensionTasklet(depot, new CalculateurTension(), port, new SeuilTension(seuil), referentiel);
    }

    private static RepeatStatus executer(CalculTensionTasklet tasklet, StepExecution step) throws Exception {
        return tasklet.execute(new StepContribution(step), new ChunkContext(new StepContext(step)));
    }

    /** Exécute avec le seuil 10.0 et le port de capture ; renvoie le step (contexte d'exécution). */
    private StepExecution calculer() throws Exception {
        StepExecution step = stepPour(D);
        assertThat(executer(tasklet(capture, 10.0), step)).isEqualTo(RepeatStatus.FINISHED);
        return step;
    }

    private static double indice(StepExecution step, Region region) {
        return step.getExecutionContext().getDouble("indice." + region.name());
    }

    @Nested
    class PonderationParRupture {

        @Test
        void ac48ProduitNonEssentielInchange() throws Exception {
            depot.avec(rupture(NON_ESS, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE));
            assertThat(indice(calculer(), Region.HAUTE_PAPERASSE)).isCloseTo(3.0, within(EPS));
        }

        @Test
        void ac49ProduitEssentielQuatorzeJoursPese6() throws Exception {
            depot.avec(rupture(ESS_1, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE));
            StepExecution step = calculer();
            assertThat(indice(step, Region.HAUTE_PAPERASSE)).isCloseTo(6.0, within(EPS));
            assertThat(indice(step, Region.COTE_DU_TIMEOUT)).isZero();
        }

        @Test
        void ac50ProduitEssentielDuJourPese2EtNonUn() throws Exception {
            depot.avec(rupture(ESS_1, D, Region.HAUTE_PAPERASSE));
            assertThat(indice(calculer(), Region.HAUTE_PAPERASSE)).isCloseTo(2.0, within(EPS));
        }

        @Test
        void ac51ProduitEssentielSeptJoursPese4() throws Exception {
            depot.avec(rupture(ESS_1, IL_Y_A_7_JOURS, Region.HAUTE_PAPERASSE));
            assertThat(indice(calculer(), Region.HAUTE_PAPERASSE)).isCloseTo(4.0, within(EPS));
        }

        @Test
        void ac52ProduitEssentielDixJoursPeseTrenteQuatreSeptiemes() throws Exception {
            depot.avec(rupture(ESS_1, LocalDate.of(2026, 11, 20), Region.HAUTE_PAPERASSE));
            assertThat(indice(calculer(), Region.HAUTE_PAPERASSE)).isCloseTo(4.857142857142857, within(EPS));
        }

        @Test
        void ac53RuptureEssentielleCommenceeApresDNonPriseEnCompte() throws Exception {
            depot.nonConforme().avec(rupture(ESS_1, LocalDate.of(2026, 12, 1), Region.HAUTE_PAPERASSE));
            assertThat(indice(calculer(), Region.HAUTE_PAPERASSE)).isZero();
        }

        @Test
        void ac54RuptureEssentielleClotureeNePesePas() throws Exception {
            depot.nonConforme().avec(cloturee(ESS_1, IL_Y_A_14_JOURS, LocalDate.of(2026, 11, 20),
                    Region.HAUTE_PAPERASSE));
            assertThat(indice(calculer(), Region.HAUTE_PAPERASSE)).isZero();
        }

        @Test
        void ac55RuptureMultiRegionsPese6DansChaqueRegionTouchee() throws Exception {
            depot.avec(rupture(ESS_1, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE, Region.COTE_DU_TIMEOUT));
            StepExecution step = calculer();
            for (Region region : Region.values()) {
                double attendu = region == Region.HAUTE_PAPERASSE || region == Region.COTE_DU_TIMEOUT ? 6.0 : 0.0;
                assertThat(indice(step, region)).as(region.name()).isCloseTo(attendu, within(EPS));
            }
        }

        @Test
        void ac59ProduitAbsentDuReferentielEstNonEssentiel() throws Exception {
            depot.avec(rupture(INCONNU, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE));
            assertThat(indice(calculer(), Region.HAUTE_PAPERASSE)).isCloseTo(3.0, within(EPS));
        }

        @Test
        void ac60AlternativesEtConformiteNInfluentPasSurLePoids() throws Exception {
            depot.avec(
                    rupture(ESS_1, IL_Y_A_14_JOURS, List.of(new Alternative(new CodeProduit("VMP-400004"), "alt")),
                            Region.HAUTE_PAPERASSE),
                    rupture(ESS_1, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE));
            assertThat(indice(calculer(), Region.HAUTE_PAPERASSE)).isCloseTo(12.0, within(EPS));
        }

        @Test
        void ac61LaPonderationIgnoreLaDateDEntreeEnVigueur() throws Exception {
            // Le tasklet ne reçoit aucune date d'entrée en vigueur : même résultat qu'AC-49
            // (le test d'intégration AC-61 positionne en plus la propriété du contexte Spring).
            depot.avec(rupture(ESS_1, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE));
            assertThat(indice(calculer(), Region.HAUTE_PAPERASSE)).isCloseTo(6.0, within(EPS));
        }
    }

    @Nested
    class SeuilEtFichier {

        private void donneesAc56() {
            depot.avec(
                    rupture(NON_ESS, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE), // 3.0
                    rupture(ESS_1, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE), // 6.0
                    rupture(ESS_3, D, Region.HAUTE_PAPERASSE)); // 2.0
        }

        @Test
        void ac56OnzeAuDessusDuSeuilStrictFichierExact() throws Exception {
            donneesAc56();

            executer(tasklet(new FichierNotificationRegionAdapter(tmp), 10.0), stepPour(D));

            assertThat(Files.readString(tmp.resolve(NOM_FICHIER))).isEqualTo(
                    "{\"dateReference\":\"2026-11-30\",\"seuil\":10.0,\"regions\":["
                            + "{\"region\":\"HAUTE_PAPERASSE\",\"indice\":11.0}]}");
        }

        @Test
        void ac56NotificationCaptureeUneSeuleRegionSignalee() throws Exception {
            donneesAc56();
            calculer();
            assertThat(publiees).hasSize(1);
            assertThat(publiees.get(0).regions()).singleElement().satisfies(r -> {
                assertThat(r.region()).isEqualTo(Region.HAUTE_PAPERASSE);
                assertThat(r.indice()).isCloseTo(11.0, within(EPS));
            });
        }

        @Test
        void ac57ReferentielVideSeptNonSignaleRegionsVides() throws Exception {
            donneesAc56();
            referentiel.vider();

            StepExecution step = calculer();

            assertThat(indice(step, Region.HAUTE_PAPERASSE)).isCloseTo(7.0, within(EPS));
            assertThat(publiees).hasSize(1);
            assertThat(publiees.get(0).regions()).isEmpty();
        }

        @Test
        void ac58DixExactementNEstPasSignaleSeuilStrict() throws Exception {
            depot.avec(
                    rupture(ESS_1, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE), // 6.0
                    rupture(ESS_3, IL_Y_A_7_JOURS, Region.HAUTE_PAPERASSE)); // 4.0

            StepExecution step = calculer();

            assertThat(indice(step, Region.HAUTE_PAPERASSE)).isEqualTo(10.0);
            assertThat(publiees).hasSize(1);
            assertThat(publiees.get(0).regions()).isEmpty();
        }
    }

    @Nested
    class RejeuEtInterrogationDuPort {

        @Test
        void ac62RejeuApresRetraitDuReferentielRemplaceLeFichier() throws Exception {
            depot.avec(rupture(ESS_1, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE));
            // seuil 4.0 : 6.0 (essentiel) signalé, 3.0 (retiré du référentiel) non signalé
            var t = tasklet(new FichierNotificationRegionAdapter(tmp), 4.0);

            executer(t, stepPour(D));
            assertThat(Files.readString(tmp.resolve(NOM_FICHIER))).contains("\"indice\":6.0");

            referentiel.retirer(ESS_1);
            StepExecution rejeu = stepPour(D);
            executer(t, rejeu);

            assertThat(indice(rejeu, Region.HAUTE_PAPERASSE)).isCloseTo(3.0, within(EPS));
            assertThat(Files.readString(tmp.resolve(NOM_FICHIER)))
                    .isEqualTo("{\"dateReference\":\"2026-11-30\",\"seuil\":4.0,\"regions\":[]}");
            try (Stream<Path> s = Files.list(tmp)) {
                assertThat(s.map(p -> p.getFileName().toString()).toList()).containsExactly(NOM_FICHIER);
            }
        }

        @Test
        void ac63PortInterrogeUneSeuleFoisPourUnCodeProduitRepete() throws Exception {
            depot.avec(
                    rupture(ESS_1, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE, Region.BASSE_VALIDATION),
                    rupture(ESS_1, IL_Y_A_7_JOURS, Region.HAUTE_PAPERASSE));

            StepExecution step = calculer();

            assertThat(referentiel.interrogations()).containsExactly(new CodeProduit(ESS_1));
            assertThat(indice(step, Region.HAUTE_PAPERASSE)).isCloseTo(10.0, within(EPS));
            assertThat(indice(step, Region.BASSE_VALIDATION)).isCloseTo(6.0, within(EPS));
        }

        @Test
        void ac63PortInterrogeUneFoisParCodeDistinct() throws Exception {
            depot.avec(
                    rupture(ESS_1, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE),
                    rupture(NON_ESS, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE),
                    rupture(ESS_1, IL_Y_A_14_JOURS, Region.COTE_DU_TIMEOUT),
                    rupture(NON_ESS, IL_Y_A_14_JOURS, Region.BASSE_VALIDATION),
                    rupture(ESS_3, IL_Y_A_14_JOURS, Region.PLATEAU_DU_LEGACY));

            calculer();

            assertThat(referentiel.interrogations()).hasSize(3).containsExactlyInAnyOrder(
                    new CodeProduit(ESS_1), new CodeProduit(NON_ESS), new CodeProduit(ESS_3));
        }
    }

    @Nested
    class ContexteDExecution {

        @Test
        void ac64ContexteContientIndicesEtCompteurs() throws Exception {
            depot.avec(
                    rupture(NON_ESS, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE),
                    rupture(ESS_1, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE),
                    rupture(ESS_3, D, Region.HAUTE_PAPERASSE));

            ExecutionContext ctx = calculer().getExecutionContext();

            assertThat(ctx.getDouble("indice.HAUTE_PAPERASSE")).isCloseTo(11.0, within(EPS));
            assertThat(ctx.getInt("nombreRupturesActives")).isEqualTo(3);
            assertThat(ctx.getInt("nombreRegionsSignalees")).isEqualTo(1);
        }
    }

    @Nested
    class Erreurs {

        @Test
        void ac65PanneDuPortPropageeAucunFichierEcrit() throws Exception {
            IllegalStateException panne = new IllegalStateException("referentiel indisponible");
            referentiel.tomberEnPanne(panne);
            depot.avec(rupture(ESS_1, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE));

            assertThatThrownBy(() -> executer(tasklet(new FichierNotificationRegionAdapter(tmp), 10.0), stepPour(D)))
                    .isSameAs(panne);

            assertThat(referentiel.interrogations()).isNotEmpty();
            try (Stream<Path> s = Files.list(tmp)) {
                assertThat(s.toList()).isEmpty();
            }
        }

        @Test
        void ac65PanneDuPortConserveLeFichierPrecedentDuJour() throws Exception {
            depot.avec(rupture(ESS_1, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE));
            var t = tasklet(new FichierNotificationRegionAdapter(tmp), 4.0);
            executer(t, stepPour(D));
            String avant = Files.readString(tmp.resolve(NOM_FICHIER));
            assertThat(avant).contains("\"indice\":6.0");

            referentiel.tomberEnPanne(new IllegalStateException("referentiel indisponible"));
            assertThatThrownBy(() -> executer(t, stepPour(D))).isInstanceOf(IllegalStateException.class)
                    .hasMessage("referentiel indisponible");

            assertThat(Files.readString(tmp.resolve(NOM_FICHIER))).isEqualTo(avant);
        }
    }

    @Nested
    class CompatibiliteAncienConstructeur {

        @Test
        void sansReferentielAucunProduitNEstEssentielPoidsInchange() throws Exception {
            depot.avec(rupture(ESS_1, IL_Y_A_14_JOURS, Region.HAUTE_PAPERASSE));
            var ancien = new CalculTensionTasklet(depot, new CalculateurTension(), capture, new SeuilTension(10.0));
            StepExecution step = stepPour(D);

            executer(ancien, step);

            assertThat(indice(step, Region.HAUTE_PAPERASSE)).isCloseTo(3.0, within(EPS));
        }
    }
}
