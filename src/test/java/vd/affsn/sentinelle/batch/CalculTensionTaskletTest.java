package vd.affsn.sentinelle.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static vd.affsn.sentinelle.domain.Fixtures.ruptureCloturee;
import static vd.affsn.sentinelle.domain.Fixtures.ruptureEnCours;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;

import vd.affsn.sentinelle.application.port.out.NotificationRegionPort;
import vd.affsn.sentinelle.application.port.out.NotificationRegionPort.Notification;
import vd.affsn.sentinelle.application.port.out.NotificationRegionPort.RegionEnTension;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.tension.CalculateurTension;
import vd.affsn.sentinelle.domain.tension.SeuilTension;

/**
 * Tasklet unique du job, testée sans Spring : faux dépôt, calculateur RÉEL du domaine, port de
 * notification capturant ou adaptateur fichier réel sur {@link TempDir}.
 *
 * <p>Formule (ADR-003 b) : poids = 1 + joursEcoules / 7 ; indice = somme des poids.
 */
class CalculTensionTaskletTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 21);

    @TempDir
    Path tmp;

    private final FauxRuptureRepository depot = new FauxRuptureRepository();
    private final List<Notification> publiees = new ArrayList<>();
    private final NotificationRegionPort capture = publiees::add;

    private static StepExecution stepPour(String dateReference) {
        return MetaDataInstanceFactory.createStepExecution(
                new JobParametersBuilder().addString("dateReference", dateReference).toJobParameters());
    }

    private static RepeatStatus executer(CalculTensionTasklet tasklet, StepExecution step) throws Exception {
        return tasklet.execute(new StepContribution(step), new ChunkContext(new StepContext(step)));
    }

    private CalculTensionTasklet tasklet(double seuil, NotificationRegionPort port) {
        return new CalculTensionTasklet(depot, new CalculateurTension(), port, new SeuilTension(seuil));
    }

    private Notification executerEtCapturer(double seuil, LocalDate date) throws Exception {
        StepExecution step = stepPour(date.toString());
        assertThat(executer(tasklet(seuil, capture), step)).isEqualTo(RepeatStatus.FINISHED);
        assertThat(publiees).hasSize(1);
        return publiees.get(0);
    }

    private List<String> fichiers() throws IOException {
        try (Stream<Path> s = Files.list(tmp)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    @Nested
    class Nominal {

        @Test
        void seulesLesRegionsStrictementAuDessusDuSeuilSontNotifieesDansLOrdreDeLEnum() throws Exception {
            // HAUTE_PAPERASSE : 3 ruptures débutées il y a 0, 7 et 14 jours -> 1 + 2 + 3 = 6.0
            // COTE_DU_TIMEOUT : 1 rupture de 28 jours -> 1 + 4 = 5.0
            // BASSE_VALIDATION : 1 rupture de 21 jours -> 4.0 (= seuil 4.0 : non notifiée)
            // PROVINCE_DU_TICKET_OUVERT : 2 ruptures de 7 jours + 1 du jour -> 2 + 2 + 1 = 5.0
            // Autres régions : 0.0
            depot.avec(
                    ruptureEnCours(D, Region.HAUTE_PAPERASSE),
                    ruptureEnCours(D.minusDays(7), Region.HAUTE_PAPERASSE),
                    ruptureEnCours(D.minusDays(14), Region.HAUTE_PAPERASSE),
                    ruptureEnCours(D.minusDays(28), Region.COTE_DU_TIMEOUT),
                    ruptureEnCours(D.minusDays(21), Region.BASSE_VALIDATION),
                    ruptureEnCours(D.minusDays(7), Region.PROVINCE_DU_TICKET_OUVERT),
                    ruptureEnCours(D.minusDays(7), Region.PROVINCE_DU_TICKET_OUVERT),
                    ruptureEnCours(D, Region.PROVINCE_DU_TICKET_OUVERT));

            Notification n = executerEtCapturer(4.0, D);

            assertThat(n.dateReference()).isEqualTo(D);
            assertThat(n.seuil()).isEqualTo(new SeuilTension(4.0));
            assertThat(n.regions()).containsExactly(
                    new RegionEnTension(Region.HAUTE_PAPERASSE, 6.0),
                    new RegionEnTension(Region.COTE_DU_TIMEOUT, 5.0),
                    new RegionEnTension(Region.PROVINCE_DU_TICKET_OUVERT, 5.0));
            assertThat(depot.datesDemandees()).containsExactly(D);
        }

        @Test
        void fichierProduitDeBoutEnBoutAuFormatExact() throws Exception {
            depot.avec(
                    ruptureEnCours(D.minusDays(14), Region.MARAIS_DE_LA_RECETTE, Region.HAUTE_PAPERASSE),
                    ruptureEnCours(D.minusDays(7), Region.MARAIS_DE_LA_RECETTE));
            // HAUTE_PAPERASSE = 3.0 ; MARAIS_DE_LA_RECETTE = 3.0 + 2.0 = 5.0 ; seuil 2.5
            executer(tasklet(2.5, new FichierNotificationRegionAdapter(tmp)), stepPour("2026-10-21"));

            assertThat(Files.readString(tmp.resolve("notifications-tension-2026-10-21.json"))).isEqualTo(
                    "{\"dateReference\":\"2026-10-21\",\"seuil\":2.5,\"regions\":["
                            + "{\"region\":\"HAUTE_PAPERASSE\",\"indice\":3.0},"
                            + "{\"region\":\"MARAIS_DE_LA_RECETTE\",\"indice\":5.0}]}");
            assertThat(fichiers()).containsExactly("notifications-tension-2026-10-21.json");
        }

        @Test
        void joursNonMultiplesDeSeptCalculesALaMain() throws Exception {
            // 10 jours : 1 + 10/7 = 17/7 ; 3 jours : 1 + 3/7 = 10/7 ; total 27/7 ≈ 3.857142857
            depot.avec(ruptureEnCours(D.minusDays(10), Region.PLATEAU_DU_LEGACY),
                    ruptureEnCours(D.minusDays(3), Region.PLATEAU_DU_LEGACY));

            Notification n = executerEtCapturer(3.8, D);

            assertThat(n.regions()).singleElement().satisfies(r -> {
                assertThat(r.region()).isEqualTo(Region.PLATEAU_DU_LEGACY);
                assertThat(r.indice()).isCloseTo(27.0 / 7.0, within(1e-12));
            });
        }

        @Test
        void contexteDExecutionContientIndicesDesSixRegionsEtCompteurs() throws Exception {
            depot.avec(ruptureEnCours(D.minusDays(7), Region.HAUTE_PAPERASSE, Region.COTE_DU_TIMEOUT),
                    ruptureEnCours(D, Region.HAUTE_PAPERASSE));
            StepExecution step = stepPour("2026-10-21");

            executer(tasklet(2.5, capture), step);

            ExecutionContext ctx = step.getExecutionContext();
            assertThat(ctx.getInt(CalculTensionTasklet.CLE_NB_RUPTURES)).isEqualTo(2);
            assertThat(ctx.getInt(CalculTensionTasklet.CLE_NB_SIGNALEES)).isEqualTo(1);
            assertThat(ctx.getDouble("indice.HAUTE_PAPERASSE")).isEqualTo(3.0);
            assertThat(ctx.getDouble("indice.COTE_DU_TIMEOUT")).isEqualTo(2.0);
            for (Region r : List.of(Region.BASSE_VALIDATION, Region.PLATEAU_DU_LEGACY,
                    Region.MARAIS_DE_LA_RECETTE, Region.PROVINCE_DU_TICKET_OUVERT)) {
                assertThat(ctx.getDouble(CalculTensionTasklet.PREFIXE_INDICE + r.name())).isZero();
            }
            assertThat(ctx.entrySet().stream().filter(e -> e.getKey().startsWith("indice.")).count()).isEqualTo(6);
        }
    }

    @Nested
    class Limites {

        @Test
        void indiceExactementEgalAuSeuilNonNotifie() throws Exception {
            depot.avec(ruptureEnCours(D.minusDays(14), Region.HAUTE_PAPERASSE)); // 3.0
            assertThat(executerEtCapturer(3.0, D).regions()).isEmpty();
        }

        @Test
        void indiceJusteAuDessusDuSeuilNotifie() throws Exception {
            depot.avec(ruptureEnCours(D.minusDays(14), Region.HAUTE_PAPERASSE)); // 3.0
            assertThat(executerEtCapturer(Math.nextDown(3.0), D).regions())
                    .containsExactly(new RegionEnTension(Region.HAUTE_PAPERASSE, 3.0));
        }

        @Test
        void seuilZeroNotifieToutesLesRegionsTouchéesEtSeulementElles() throws Exception {
            depot.avec(ruptureEnCours(D, Region.BASSE_VALIDATION));
            assertThat(executerEtCapturer(0.0, D).regions())
                    .containsExactly(new RegionEnTension(Region.BASSE_VALIDATION, 1.0));
        }

        @Test
        void aucuneRupturePublieUneListeVideEtEcritLeFichier() throws Exception {
            StepExecution step = stepPour("2026-10-21");
            executer(tasklet(0.0, new FichierNotificationRegionAdapter(tmp)), step);

            assertThat(Files.readString(tmp.resolve("notifications-tension-2026-10-21.json")))
                    .isEqualTo("{\"dateReference\":\"2026-10-21\",\"seuil\":0.0,\"regions\":[]}");
            assertThat(step.getExecutionContext().getInt(CalculTensionTasklet.CLE_NB_RUPTURES)).isZero();
            assertThat(step.getExecutionContext().getInt(CalculTensionTasklet.CLE_NB_SIGNALEES)).isZero();
        }

        @Test
        void ruptureMultiRegionsCompteeAvecSonPoidsEntierDansChacune() throws Exception {
            depot.avec(ruptureEnCours(D.minusDays(7), Region.HAUTE_PAPERASSE, Region.BASSE_VALIDATION,
                    Region.PROVINCE_DU_TICKET_OUVERT)); // 2.0 dans chacune, non réparti

            assertThat(executerEtCapturer(1.5, D).regions()).containsExactly(
                    new RegionEnTension(Region.HAUTE_PAPERASSE, 2.0),
                    new RegionEnTension(Region.BASSE_VALIDATION, 2.0),
                    new RegionEnTension(Region.PROVINCE_DU_TICKET_OUVERT, 2.0));
        }

        @Test
        void sixRegionsToutesAuDessusDuSeuil() throws Exception {
            depot.avec(ruptureEnCours(D.minusDays(7), Region.HAUTE_PAPERASSE, Region.values()));
            Notification n = executerEtCapturer(1.0, D);

            assertThat(n.regions()).extracting(RegionEnTension::region).containsExactly(Region.values());
            assertThat(n.regions()).extracting(RegionEnTension::indice).containsOnly(2.0);
        }

        @Test
        void rupturesClotureesEtFuturesIgnoreesMemeSiLeDepotLesRenvoie() throws Exception {
            depot.nonConforme().avec(
                    ruptureEnCours(D.minusDays(7), Region.HAUTE_PAPERASSE), // 2.0
                    ruptureCloturee(D.minusDays(70), D.minusDays(1), Region.HAUTE_PAPERASSE), // clôturée
                    ruptureCloturee(D.minusDays(70), D.plusDays(5), Region.COTE_DU_TIMEOUT), // clôturée (fin future)
                    ruptureEnCours(D.plusDays(1), Region.COTE_DU_TIMEOUT)); // future
            StepExecution step = stepPour("2026-10-21");
            executer(tasklet(0.0, capture), step);

            assertThat(publiees.get(0).regions()).containsExactly(new RegionEnTension(Region.HAUTE_PAPERASSE, 2.0));
            assertThat(step.getExecutionContext().getDouble("indice.COTE_DU_TIMEOUT")).isZero();
        }

        @Test
        void ruptureDebutantLeJourDeReferenceCompteAvecPoidsUn() throws Exception {
            depot.avec(ruptureEnCours(D, Region.HAUTE_PAPERASSE), ruptureEnCours(D.plusDays(1), Region.BASSE_VALIDATION));
            assertThat(executerEtCapturer(0.0, D).regions())
                    .containsExactly(new RegionEnTension(Region.HAUTE_PAPERASSE, 1.0));
        }

        @Test
        void dateDeReferenceAuxBornesDuFormat() throws Exception {
            LocalDate fin = LocalDate.of(9999, 12, 31);
            depot.avec(ruptureEnCours(fin.minusDays(70), Region.HAUTE_PAPERASSE)); // 1 + 10 = 11.0
            executer(tasklet(10.0, new FichierNotificationRegionAdapter(tmp)), stepPour("9999-12-31"));

            assertThat(Files.readString(tmp.resolve("notifications-tension-9999-12-31.json"))).isEqualTo(
                    "{\"dateReference\":\"9999-12-31\",\"seuil\":10.0,"
                            + "\"regions\":[{\"region\":\"HAUTE_PAPERASSE\",\"indice\":11.0}]}");

            depot.reinitialiser();
            executer(tasklet(10.0, new FichierNotificationRegionAdapter(tmp)), stepPour("0000-01-01"));
            assertThat(Files.readString(tmp.resolve("notifications-tension-0000-01-01.json")))
                    .isEqualTo("{\"dateReference\":\"0000-01-01\",\"seuil\":10.0,\"regions\":[]}");
        }

        @Test
        void finDeMoisEtAnneeBissextile() throws Exception {
            LocalDate bissextile = LocalDate.of(2028, 2, 29);
            depot.avec(ruptureEnCours(LocalDate.of(2028, 2, 1), Region.HAUTE_PAPERASSE)); // 28 j -> 5.0
            assertThat(executerEtCapturer(4.9, bissextile).regions())
                    .containsExactly(new RegionEnTension(Region.HAUTE_PAPERASSE, 5.0));
        }
    }

    @Nested
    class Erreurs {

        @Test
        void echecDuDepotPropageSansPublicationNiFichier() throws Exception {
            depot.tomberEnPanne(new IllegalStateException("base indisponible"));
            StepExecution step = stepPour("2026-10-21");
            CalculTensionTasklet t = tasklet(0.0, new FichierNotificationRegionAdapter(tmp));

            assertThatThrownBy(() -> executer(t, step))
                    .isInstanceOf(IllegalStateException.class).hasMessage("base indisponible");
            assertThat(fichiers()).isEmpty();
            assertThat(step.getExecutionContext().containsKey(CalculTensionTasklet.CLE_NB_RUPTURES)).isFalse();
        }

        @Test
        void echecDuDepotNeTouchePasAuFichierExistantDuJour() throws Exception {
            Path existant = Files.writeString(tmp.resolve("notifications-tension-2026-10-21.json"), "ancien");
            depot.tomberEnPanne(new IllegalStateException("base indisponible"));

            assertThatThrownBy(() -> executer(tasklet(0.0, new FichierNotificationRegionAdapter(tmp)),
                    stepPour("2026-10-21"))).isInstanceOf(IllegalStateException.class);
            assertThat(Files.readString(existant)).isEqualTo("ancien");
            assertThat(fichiers()).containsExactly("notifications-tension-2026-10-21.json");
        }

        @Test
        void echecDEcriturePropage() throws Exception {
            Path fichier = Files.writeString(tmp.resolve("pas-un-repertoire"), "x");
            depot.avec(ruptureEnCours(D, Region.HAUTE_PAPERASSE));

            assertThatThrownBy(() -> executer(tasklet(0.0, new FichierNotificationRegionAdapter(fichier)),
                    stepPour("2026-10-21"))).isInstanceOf(UncheckedIOException.class);
        }

        @Test
        void dateReferenceInvalideRefuseeAvantToutAccesAuDepot() {
            CalculTensionTasklet t = tasklet(0.0, capture);
            assertThatThrownBy(() -> executer(t, stepPour("2026-02-30")))
                    .isInstanceOf(JobParametersInvalidException.class);
            assertThat(depot.datesDemandees()).isEmpty();
            assertThat(publiees).isEmpty();
        }

        @Test
        void dateReferenceAbsenteRefusee() {
            StepExecution step = MetaDataInstanceFactory.createStepExecution();
            assertThatThrownBy(() -> executer(tasklet(0.0, capture), step))
                    .isInstanceOf(JobParametersInvalidException.class);
        }

        @Test
        void dependancesObligatoires() {
            var calc = new CalculateurTension();
            var seuil = new SeuilTension(1.0);
            assertThatThrownBy(() -> new CalculTensionTasklet(null, calc, capture, seuil))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new CalculTensionTasklet(depot, null, capture, seuil))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new CalculTensionTasklet(depot, calc, null, seuil))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new CalculTensionTasklet(depot, calc, capture, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class AmbiguitesDocumentees {

        /**
         * L'ADR-003 dit « prise en compte si son statut est EN_COURS À CETTE DATE » et « reproductible
         * pour une date donnée ». Le domaine (Rupture#estActiveLe, validé) utilise le statut
         * ACTUEL : rejouer une date passée ignore une rupture clôturée depuis, alors qu'elle était
         * en cours à cette date. Le rejeu d'une date passée ne reproduit donc pas le calcul initial.
         */
        @Test
        void rejeuDUneDatePasseeIgnoreUneRuptureClotureeDepuis() throws Exception {
            LocalDate passe = D.minusDays(30);
            // Débutée 14 jours avant la date rejouée, clôturée 10 jours APRÈS : EN_COURS à cette date.
            depot.nonConforme().avec(ruptureCloturee(passe.minusDays(14), passe.plusDays(10), Region.HAUTE_PAPERASSE));

            assertThat(executerEtCapturer(0.0, passe).regions()).isEmpty();
        }

        /**
         * Le compteur {@code nombreRupturesActives} reprend la taille de la liste renvoyée par le
         * dépôt, sans recompter après le filtrage du domaine. Avec un dépôt conforme au contrat
         * ({@code findActivesAu} ne renvoie que des actives), les deux coïncident ; avec un dépôt
         * non conforme, le compteur inclut des ruptures ignorées par le calcul.
         */
        @Test
        void compteurRupturesActivesEstLaTailleRenvoyeeParLeDepot() throws Exception {
            depot.nonConforme().avec(
                    ruptureEnCours(D, Region.HAUTE_PAPERASSE),
                    ruptureEnCours(D.plusDays(3), Region.HAUTE_PAPERASSE),
                    ruptureCloturee(D.minusDays(9), D.minusDays(2), Region.HAUTE_PAPERASSE));
            StepExecution step = stepPour("2026-10-21");
            executer(tasklet(0.0, capture), step);

            assertThat(step.getExecutionContext().getInt(CalculTensionTasklet.CLE_NB_RUPTURES)).isEqualTo(3);
            assertThat(step.getExecutionContext().getDouble("indice.HAUTE_PAPERASSE")).isEqualTo(1.0);
        }
    }
}
