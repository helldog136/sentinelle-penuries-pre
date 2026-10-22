package vd.affsn.sentinelle.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import vd.affsn.sentinelle.adapter.out.audit.ResultatVerificationIntegrite;
import vd.affsn.sentinelle.adapter.out.audit.VerificateurIntegriteJournalAudit;
import vd.affsn.sentinelle.batch.LanceurCalculTension;
import vd.affsn.sentinelle.grpc.v1.CloturerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.CloturerRuptureResponse;
import vd.affsn.sentinelle.grpc.v1.DeclarerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.MettreAJourRuptureResponse;
import vd.affsn.sentinelle.grpc.v1.MotifRupture;
import vd.affsn.sentinelle.grpc.v1.Region;
import vd.affsn.sentinelle.grpc.v1.Rupture;
import vd.affsn.sentinelle.grpc.v1.StatutRupture;

/**
 * PARCOURS ADR-004 de bout en bout, ordonné (TDD, phase rouge : échoue tant que la règle, le
 * référentiel, l'exposition et la pondération ne sont pas câblés) :
 * <ol>
 *   <li>(a) un distributeur déclare en gRPC une rupture longue du produit essentiel VMP-100001 sans
 *       alternative : refus exact, rien écrit, journal intact (propriété d'entrée en vigueur absente :
 *       règle immédiate) ;</li>
 *   <li>(b) avec une alternative : acceptée, {@code essentiel = true}, auditée comme avant ;</li>
 *   <li>(c) un pharmacien la voit en REST avec {@code essentiel: true} et filtre {@code ?essentiel=} ;</li>
 *   <li>(d) le batch recalcule l'indice (× 2) et écrit le fichier de notification ;</li>
 *   <li>(e) une mise à jour qui retire l'alternative est refusée : base et journal inchangés ;</li>
 *   <li>(f) mises à jour conformes, clôture, chaîne d'audit intègre.</li>
 * </ol>
 *
 * <p>Jeu de données (aujourd'hui = 2026-11-01, PRO-000201, région HAUTE_PAPERASSE) :
 * A = VMP-100001 (essentiel) début 2026-11-16 sans fin, alternative VMP-400004 ;
 * B = VMP-200002 (non essentiel) début 2026-11-16 sans fin ni alternative ;
 * C = VMP-300003 (essentiel) début 2026-11-30, fin 2026-12-30 (début + 30 j) sans alternative.
 * C'est exactement le jeu d'AC-56 : à D = 2026-11-30, HAUTE_PAPERASSE = 6 + 3 + 2 = 11.0 &gt; 10.0.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sentinelle-e2e-essentiel-parcours;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "grpc.server.in-process-name=sentinelle-e2e-essentiel-parcours"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ParcoursProduitEssentielBoutEnBoutTest extends AbstractEssentielBoutEnBoutTest {

    private static final LocalDate DEBUT_AB = LocalDate.of(2026, 11, 16);
    private static final LocalDate DEBUT_C = LocalDate.of(2026, 11, 30);
    private static final LocalDate D = LocalDate.of(2026, 11, 30);
    private static final String FICHIER_D = "notifications-tension-2026-11-30.json";

    @TempDir
    static Path repertoireNotifications;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("sentinelle.batch.notifications.repertoire", () -> repertoireNotifications.toString());
        // Convention des cas d'acceptation batch d'ADR-004 (le socle fixe 0.5).
        registry.add("sentinelle.batch.seuil-tension", () -> "10.0");
    }

    @Autowired
    private LanceurCalculTension lanceur;
    @Autowired
    private VerificateurIntegriteJournalAudit verificateur;

    private static String idA;
    private static String idB;
    private static String idC;

    // ====================================================================== (a)

    @Test
    @Order(1)
    void a_declarationLongueDUnProduitEssentielSansAlternativeEstRefuseeSansRienEcrire() {
        long journalAvant = compterJournal();
        String jeton = jeton201();

        // AC-20 / AC-04 : propriété d'entrée en vigueur absente => règle immédiate ; sans fin estimée = longue.
        assertRefuseParLaRegle(() -> grpc(jeton).declarerRupture(declaration(ESSENTIEL_1, DEBUT_AB, null)));
        // AC-02 : 31 jours.
        assertRefuseParLaRegle(() -> grpc(jeton).declarerRupture(declaration(ESSENTIEL_1,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 2))));
        // AC-12 : six régions, une seule fois le message (description ÉGALE au message, non concaténée).
        assertRefuseParLaRegle(() -> grpc(jeton).declarerRupture(declaration(ESSENTIEL_1, DEBUT_AB, null,
                List.of(Region.REGION_HAUTE_PAPERASSE, Region.REGION_BASSE_VALIDATION, Region.REGION_PLATEAU_DU_LEGACY,
                        Region.REGION_COTE_DU_TIMEOUT, Region.REGION_MARAIS_DE_LA_RECETTE,
                        Region.REGION_PROVINCE_DU_TICKET_OUVERT))));
        // AC-10 : début passé, sans fin : la durée ne dépend pas de la date du jour.
        assertRefuseParLaRegle(() -> grpc(jeton).declarerRupture(declaration(ESSENTIEL_1,
                LocalDate.of(2026, 9, 1), null)));

        // « Refusé » : aucune écriture, aucune entrée d'audit (AC-46, volet déclaration).
        assertThat(compterRupturesProduit(ESSENTIEL_1)).isZero();
        assertThat(compterJournal()).isEqualTo(journalAvant);
        assertThat(verificateur.verifier().integre()).isTrue();
    }

    @Test
    @Order(2)
    void a2_lesViolationsDeFormatPassentAvantLaRegle() {
        // AC-13 : seules les violations de format sont renvoyées, la règle n'est pas évaluée.
        DeclarerRuptureRequest sansMotif = declaration(ESSENTIEL_1, DEBUT_AB, null).toBuilder()
                .setMotif(MotifRupture.MOTIF_RUPTURE_UNSPECIFIED).build();
        long avant = compterRupturesProduit(ESSENTIEL_1);
        StatusRuntimeException e = erreurGrpc(() -> grpc(jeton201()).declarerRupture(sansMotif));
        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(e.getStatus().getDescription()).isEqualTo("motif: valeur obligatoire");
        assertThat(compterRupturesProduit(ESSENTIEL_1)).isEqualTo(avant);
    }

    // ====================================================================== (b)

    @Test
    @Order(3)
    void b_avecUneAlternativeLaDeclarationEstAccepteeMarqueeEssentielleEtAuditeeCommeAvant() {
        long n = compterJournal();

        // AC-03 / AC-05 : longue (sans fin) + 1 alternative non essentielle.
        Rupture a = grpc(jeton201()).declarerRupture(
                declaration(ESSENTIEL_1, DEBUT_AB, null, alt(NON_ESSENTIEL_4))).getRupture();
        idA = a.getId();
        assertThat(a.getCodeProduit()).isEqualTo(ESSENTIEL_1);
        assertThat(a.getStatut()).isEqualTo(StatutRupture.STATUT_RUPTURE_EN_COURS);
        assertThat(a.getAlternativesList()).extracting(x -> x.getCodeProduit()).containsExactly(NON_ESSENTIEL_4);
        assertThat(a.getEssentiel()).as("essentiel dans la réponse de DeclarerRupture").isTrue();

        // AC-47 : N + 1, DECLARATION, détail de même forme qu'avant ADR-004 (sans mention du caractère essentiel).
        assertThat(compterJournal()).isEqualTo(n + 1);
        Map<String, Object> derniere = jdbc.queryForMap(
                "SELECT type_evenement, rupture_id, resume FROM journal_audit ORDER BY sequence DESC LIMIT 1");
        assertThat(derniere.get("type_evenement")).isEqualTo("DECLARATION");
        assertThat(derniere.get("rupture_id")).isEqualTo(UUID.fromString(idA));
        assertThat(derniere.get("resume")).isEqualTo("produit VMP-100001, 1 régions");

        // AC-07 : non essentiel, sans fin ni alternative : accepté, essentiel = false.
        Rupture b = grpc(jeton201()).declarerRupture(declaration(NON_ESSENTIEL_2, DEBUT_AB, null)).getRupture();
        idB = b.getId();
        assertThat(b.getEssentiel()).isFalse();

        // AC-01 : essentiel, fin = début + 30 j exactement, sans alternative : accepté (borne), essentiel = true.
        Rupture c = grpc(jeton201()).declarerRupture(
                declaration(ESSENTIEL_3, DEBUT_C, DEBUT_C.plusDays(30))).getRupture();
        idC = c.getId();
        assertThat(c.getDateFinEstimee()).isEqualTo("2026-12-30");
        assertThat(c.getAlternativesList()).isEmpty();
        assertThat(c.getEssentiel()).isTrue();

        assertThat(List.of(idA, idB, idC)).doesNotHaveDuplicates();
        assertThat(verificateur.verifier().integre()).isTrue();
    }

    // ====================================================================== (c)

    @Test
    @Order(4)
    void c_unPharmacienVoitLeCaractereEssentielEnRestEtFiltreDessus() {
        String pharmacien = jetons.pharmacien();

        // Détail : propriété additive toujours présente (AC-39 côté détail).
        JsonNode detailA = getOk("/api/v1/ruptures/" + idA, pharmacien);
        assertSchemaRuptureV11(detailA);
        assertThat(essentielDe(detailA)).isTrue();
        assertThat(detailA.get("alternatives").get(0).get("codeProduit").asText()).isEqualTo(NON_ESSENTIEL_4);
        assertThat(essentielDe(getOk("/api/v1/ruptures/" + idB, pharmacien))).isFalse();
        assertThat(essentielDe(getOk("/api/v1/ruptures/" + idC, pharmacien))).isTrue();
        // Le prescripteur voit la même chose.
        assertThat(getOk("/api/v1/ruptures/" + idA, jetons.prescripteur())).isEqualTo(detailA);

        // AC-39 : sans filtre, comportement antérieur (les trois), chacune avec essentiel renseigné.
        JsonNode tout = getOk("/api/v1/ruptures?size=100", pharmacien);
        // tri existant : date de début décroissante (C d'abord ; A et B ont le même début)
        assertThat(ids(tout)).containsExactlyInAnyOrder(idA, idB, idC).first().isEqualTo(idC);
        assertThat(tout.get("totalElements").asLong()).isEqualTo(3);
        tout.get("contenu").forEach(r -> {
            assertSchemaRuptureV11(r);
            boolean attendu = !r.get("id").asText().equals(idB);
            assertThat(essentielDe(r)).as("essentiel de %s", r.get("id")).isEqualTo(attendu);
        });

        // AC-37 : essentiel=true => A et C, tri existant (date de début décroissante) conservé.
        JsonNode essentiels = getOk("/api/v1/ruptures?essentiel=true", pharmacien);
        assertThat(ids(essentiels)).containsExactly(idC, idA);
        assertThat(essentiels.get("totalElements").asLong()).isEqualTo(2);
        // AC-38 : essentiel=false => B seule.
        JsonNode autres = getOk("/api/v1/ruptures?essentiel=false", pharmacien);
        assertThat(ids(autres)).containsExactly(idB);
        assertThat(autres.get("totalElements").asLong()).isEqualTo(1);
        // AC-40 : combinaison par ET avec region et produit.
        assertThat(ids(getOk("/api/v1/ruptures?essentiel=true&produit=VMP-300003", pharmacien))).containsExactly(idC);
        assertThat(ids(getOk("/api/v1/ruptures?essentiel=false&region=HAUTE_PAPERASSE", pharmacien)))
                .containsExactly(idB);
        JsonNode vide = getOk("/api/v1/ruptures?essentiel=true&region=BASSE_VALIDATION", pharmacien);
        assertThat(ids(vide)).isEmpty();
        assertThat(vide.get("totalElements").asLong()).isZero();
        // Pagination portant sur le résultat filtré.
        JsonNode page = getOk("/api/v1/ruptures?essentiel=true&size=1", pharmacien);
        assertThat(ids(page)).containsExactly(idC);
        assertThat(page.get("totalElements").asLong()).isEqualTo(2);
        assertThat(page.get("totalPages").asLong()).isEqualTo(2);

        // AC-41 : seules true/false (minuscules) ; sinon 400 au format existant, violation sur « essentiel ».
        for (String invalide : List.of("oui", "TRUE", "1")) {
            Reponse r = get("/api/v1/ruptures?essentiel=" + invalide, pharmacien);
            assertThat(r.statut()).as("essentiel=%s : %s", invalide, r.corps()).isEqualTo(400);
            JsonNode probleme = r.json();
            assertThat(probleme.get("type").asText())
                    .isEqualTo("https://sentinelle.affsn.example/problemes/parametre-invalide");
            List<String> champs = new ArrayList<>();
            probleme.get("violations").forEach(v -> champs.add(v.get("champ").asText()));
            assertThat(champs).containsExactly("essentiel");
            assertThat(r.corps()).doesNotContain(invalide.equals("1") ? "\"1\"" : invalide);
        }

        // Le schéma AlternativesProduit est inchangé (pas de caractère essentiel des alternatives).
        JsonNode alternatives = getOk("/api/v1/produits/VMP-100001/alternatives", pharmacien);
        assertThat(alternatives.get("alternatives")).hasSize(1);
        List<String> champsAlt = new ArrayList<>();
        alternatives.get("alternatives").get(0).fieldNames().forEachRemaining(champsAlt::add);
        assertThat(champsAlt).doesNotContain("essentiel");
    }

    // ====================================================================== (d)

    @Test
    @Order(5)
    void d_leBatchPondereLesProduitsEssentielsEtNotifieLaRegionDevenueEnTension() throws Exception {
        JobExecution execution = lanceur.lancer(D);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        // AC-56 : fichier EXACT (format inchangé, valeur pondérée 3 + 6 + 2 = 11.0 > 10.0).
        assertThat(Files.readString(repertoireNotifications.resolve(FICHIER_D))).isEqualTo(
                "{\"dateReference\":\"2026-11-30\",\"seuil\":10.0,"
                        + "\"regions\":[{\"region\":\"HAUTE_PAPERASSE\",\"indice\":11.0}]}");

        // AC-64 : contexte d'exécution.
        assertThat(execution.getStepExecutions()).hasSize(1);
        StepExecution etape = execution.getStepExecutions().iterator().next();
        var contexte = etape.getExecutionContext();
        assertThat(contexte.getDouble("indice.HAUTE_PAPERASSE")).isCloseTo(11.0, within(1e-9));
        assertThat(contexte.getInt("nombreRupturesActives")).isEqualTo(3);
        assertThat(contexte.getInt("nombreRegionsSignalees")).isEqualTo(1);
        for (String autre : List.of("BASSE_VALIDATION", "PLATEAU_DU_LEGACY", "COTE_DU_TIMEOUT",
                "MARAIS_DE_LA_RECETTE", "PROVINCE_DU_TICKET_OUVERT")) {
            assertThat(contexte.getDouble("indice." + autre)).as(autre).isZero();
        }
    }

    @Test
    @Order(6)
    void d2_leReferentielCourantFaitFoiAuRejeuQuiRemplaceLeFichierDuJour() throws Exception {
        JobExecution premiere = lanceur.lancer(D);
        assertThat(premiere.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // AC-57 / AC-62 : référentiel vidé, relance pour la même D => 3 + 3 + 1 = 7.0, plus de région signalée.
        referentiel();
        JobExecution relance = lanceur.lancer(D);

        assertThat(relance.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(relance.getJobInstance().getInstanceId()).isNotEqualTo(premiere.getJobInstance().getInstanceId());
        var contexte = relance.getStepExecutions().iterator().next().getExecutionContext();
        assertThat(contexte.getDouble("indice.HAUTE_PAPERASSE")).isCloseTo(7.0, within(1e-9));
        assertThat(contexte.getInt("nombreRegionsSignalees")).isZero();
        try (var fichiers = Files.list(repertoireNotifications)) {
            assertThat(fichiers.map(f -> f.getFileName().toString()).toList()).containsExactly(FICHIER_D);
        }
        assertThat(Files.readString(repertoireNotifications.resolve(FICHIER_D)))
                .isEqualTo("{\"dateReference\":\"2026-11-30\",\"seuil\":10.0,\"regions\":[]}");
        // La pondération × 1 retrouvée prouve que la rupture de C (VMP-300003) comptait bien double avant.
    }

    // ====================================================================== (e)

    @Test
    @Order(7)
    void e_uneMiseAJourQuiRetireLAlternativeEstRefuseeEtNeTouchePasALaBaseNiAuJournal() {
        Map<String, Object> avant = etatEnBase(idA);
        long n = compterJournal();
        String jeton = jeton201();

        // AC-21 : sans fin, 0 alternative (l'enregistrée en avait une).
        assertRefuseParLaRegle(() -> grpc(jeton).mettreAJourRupture(miseAJour(idA, null)));
        // AC-33 : une région de plus, toujours aucune alternative : une seule alternative exigée, et elle manque.
        assertRefuseParLaRegle(() -> grpc(jeton).mettreAJourRupture(miseAJour(idA, null,
                List.of(Region.REGION_HAUTE_PAPERASSE, Region.REGION_COTE_DU_TIMEOUT))));
        // AC-22 (forme) : fin à début + 31 j, sans alternative.
        assertRefuseParLaRegle(() -> grpc(jeton).mettreAJourRupture(miseAJour(idA, DEBUT_AB.plusDays(31))));

        // AC-30 : un tiers non propriétaire n'apprend rien, ni de l'état ni de la règle.
        StatusRuntimeException tiers = erreurGrpc(() -> grpc(jeton202()).mettreAJourRupture(miseAJour(idA, null)));
        assertThat(tiers.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(tiers.getStatus().getDescription()).isEqualTo("Accès refusé");

        // AC-21 / AC-46 : la rupture relue en base garde son alternative ; journal inchangé et intègre.
        assertThat(etatEnBase(idA)).isEqualTo(avant);
        assertThat((List<?>) avant.get("alternatives")).hasSize(1);
        assertThat(compterJournal()).isEqualTo(n);
        ResultatVerificationIntegrite verification = verificateur.verifier();
        assertThat(verification.integre()).isTrue();
        assertThat(verification.entreesVerifiees()).isEqualTo(n);
        // Et le REST ne voit aucun changement.
        assertThat(getOk("/api/v1/ruptures/" + idA, jetons.pharmacien()).get("alternatives")).hasSize(1);
    }

    // ====================================================================== (f)

    @Test
    @Order(8)
    void f_misesAJourConformesPuisClotureEtChaineDAuditIntegre() {
        String jeton = jeton201();

        // AC-36 : l'alternative remplacée par un produit lui-même essentiel satisfait la règle.
        MettreAJourRuptureResponse m1 = grpc(jeton).mettreAJourRupture(miseAJour(idA, null, alt(ESSENTIEL_3)));
        assertThat(m1.getRupture().getAlternativesList()).extracting(x -> x.getCodeProduit())
                .containsExactly(ESSENTIEL_3);
        assertThat(m1.getRupture().getEssentiel()).isTrue();

        // AC-24 (forme) : raccourcie à début + 30 j, les alternatives peuvent être retirées.
        MettreAJourRuptureResponse m2 = grpc(jeton).mettreAJourRupture(miseAJour(idA, DEBUT_AB.plusDays(30)));
        assertThat(m2.getRupture().getAlternativesList()).isEmpty();
        assertThat(m2.getRupture().getDateFinEstimee()).isEqualTo("2026-12-16");
        assertThat(m2.getRupture().getEssentiel()).isTrue();

        // Clôture (jamais soumise à la règle) : on avance l'horloge pour une fin effective postérieure au début.
        horloge.regler(LocalDate.of(2026, 12, 1));
        CloturerRuptureResponse cloture = grpc(jeton).cloturerRupture(CloturerRuptureRequest.newBuilder()
                .setId(idA).setDateFinEffective("2026-11-20").build());
        assertThat(cloture.getRupture().getStatut()).isEqualTo(StatutRupture.STATUT_RUPTURE_CLOTUREE);
        assertThat(cloture.getRupture().getEssentiel()).isTrue();
        // AC-42 : rupture clôturée d'un produit essentiel, détail REST essentiel: true.
        JsonNode detail = getOk("/api/v1/ruptures/" + idA, jetons.pharmacien());
        assertThat(detail.get("statut").asText()).isEqualTo("CLOTUREE");
        assertThat(essentielDe(detail)).isTrue();

        // Journal : seules les écritures RÉUSSIES, format sentinelle-audit-v1 inchangé, chaîne intègre.
        assertThat(typesAudit(idA)).containsExactly("DECLARATION", "MODIFICATION", "MODIFICATION", "CLOTURE");
        assertThat(jdbc.queryForList("SELECT resume FROM journal_audit WHERE rupture_id = ? ORDER BY sequence",
                String.class, UUID.fromString(idA))).containsExactly("produit VMP-100001, 1 régions",
                "produit VMP-100001, 1 régions", "produit VMP-100001, 1 régions", "produit VMP-100001, clôture");
        List<String> resumes = jdbc.queryForList("SELECT resume FROM journal_audit", String.class);
        assertThat(resumes).allSatisfy(r -> assertThat(r.toLowerCase()).doesNotContain("essentiel"));
        // 3 déclarations + 2 modifications + 1 clôture : aucun des refus n'a laissé de trace.
        ResultatVerificationIntegrite verification = verificateur.verifier();
        assertThat(verification.integre()).isTrue();
        assertThat(verification.entreesVerifiees()).isEqualTo(compterJournal()).isEqualTo(6);
    }

    // ====================================================================== outillage

    /** Schéma OpenAPI {@code Rupture} v1.1.0 : champs v1 + {@code essentiel} booléen toujours présent. */
    static void assertSchemaRuptureV11(JsonNode rupture) {
        List<String> champs = new ArrayList<>();
        rupture.fieldNames().forEachRemaining(champs::add);
        assertThat(champs).containsExactlyInAnyOrder("id", "codeProduit", "dateDebut", "dateFinEstimee",
                "dateFinEffective", "motif", "regions", "alternatives", "statut", "distributeurId", "essentiel");
        assertThat(rupture.get("essentiel").isBoolean()).as("essentiel est un booléen JSON").isTrue();
    }
}
