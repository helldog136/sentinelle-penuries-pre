package vd.affsn.sentinelle.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

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
import vd.affsn.sentinelle.grpc.v1.DeclarerRuptureResponse;
import vd.affsn.sentinelle.grpc.v1.MettreAJourRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.MettreAJourRuptureResponse;
import vd.affsn.sentinelle.grpc.v1.MotifRupture;
import vd.affsn.sentinelle.grpc.v1.Region;
import vd.affsn.sentinelle.grpc.v1.StatutRupture;

/**
 * SCÉNARIO PRINCIPAL de bout en bout, ordonné : un distributeur déclare une rupture via gRPC, les
 * professionnels la consultent en REST, le distributeur la met à jour, un concurrent échoue à la
 * modifier, le batch recalcule l'indice de tension des régions, le distributeur la clôture, puis le
 * journal d'audit est contrôlé.
 *
 * <p>Jeu de données (J = {@value #J_TEXTE}, horloge figée) :
 * <ul>
 *   <li>R1 (sujet du scénario, PRO-000201) : VMP-000101, début J-10, fin estimée J+20, PRODUCTION,
 *       COTE_DU_TIMEOUT + MARAIS_DE_LA_RECETTE, alternative VMP-000102 ; mise à jour ensuite en
 *       COTE_DU_TIMEOUT + HAUTE_PAPERASSE, fin estimée J+45 ;</li>
 *   <li>R2 (témoin, PRO-000202) : VMP-000301, début J-21, HAUTE_PAPERASSE + BASSE_VALIDATION ;</li>
 *   <li>R3 (témoin non commencé, PRO-000202) : VMP-000302, début J+5, PLATEAU_DU_LEGACY.</li>
 * </ul>
 *
 * <p>Les étapes partagent leur état par des champs statiques (instance par méthode : le contexte
 * Spring doit être chargé après l'injection du répertoire temporaire statique).
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sentinelle-e2e-parcours;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "grpc.server.in-process-name=sentinelle-e2e-parcours"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ParcoursRuptureBoutEnBoutTest extends AbstractBoutEnBoutTest {

    static final String J_TEXTE = "2026-03-16";
    private static final String COMMENTAIRE = "Boîte de 30, même dosage, stock limité";
    private static final String FICHIER_J = "notifications-tension-" + J_TEXTE + ".json";

    /** Poids calculés À LA MAIN : 1 + jours écoulés / 7. */
    private static final double POIDS_R1 = 1 + 10 / 7.0;
    private static final double POIDS_R2 = 1 + 21 / 7.0; // 4.0

    @TempDir
    static Path repertoireNotifications;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("sentinelle.batch.notifications.repertoire", () -> repertoireNotifications.toString());
    }

    @Autowired
    private LanceurCalculTension lanceur;
    @Autowired
    private VerificateurIntegriteJournalAudit verificateur;

    private static String idR1;
    private static String idR2;
    private static String idR3;
    /** Détail REST de R1 après la mise à jour (c), référence pour (d). */
    private static String detailApresMiseAJour;
    private static Long instanceBatchE;

    // ====================================================================== (a)

    @Test
    @Order(1)
    void a_unDistributeurDeclareUneRuptureViaGrpc() {
        assertThat(J_TEXTE).isEqualTo(J.toString());
        DeclarerRuptureRequest requete = DeclarerRuptureRequest.newBuilder()
                .setCodeProduit("VMP-000101")
                .setDateDebut(date(J.minusDays(10)))
                .setDateFinEstimee(date(J.plusDays(20)))
                .setMotif(MotifRupture.MOTIF_RUPTURE_PRODUCTION)
                .addRegions(Region.REGION_COTE_DU_TIMEOUT)
                .addRegions(Region.REGION_MARAIS_DE_LA_RECETTE)
                .addAlternatives(alternative("VMP-000102", COMMENTAIRE))
                .build();

        DeclarerRuptureResponse reponse = grpc(jeton201()).declarerRupture(requete);

        var r = reponse.getRupture();
        idR1 = r.getId();
        assertThat(UUID.fromString(idR1).toString()).isEqualTo(idR1);
        assertThat(r.getCodeProduit()).isEqualTo("VMP-000101");
        assertThat(r.getDateDebut()).isEqualTo("2026-03-06");
        assertThat(r.getDateFinEstimee()).isEqualTo("2026-04-05");
        assertThat(r.hasDateFinEffective()).isFalse();
        assertThat(r.getMotif()).isEqualTo(MotifRupture.MOTIF_RUPTURE_PRODUCTION);
        assertThat(r.getRegionsList()).containsExactly(Region.REGION_COTE_DU_TIMEOUT, Region.REGION_MARAIS_DE_LA_RECETTE);
        assertThat(r.getAlternativesList()).singleElement().satisfies(a -> {
            assertThat(a.getCodeProduit()).isEqualTo("VMP-000102");
            assertThat(a.getCommentaire()).isEqualTo(COMMENTAIRE);
        });
        assertThat(r.getStatut()).isEqualTo(StatutRupture.STATUT_RUPTURE_EN_COURS);
        assertThat(r.getDistributeurId()).isEqualTo(PRO_201);

        // Témoins pour le batch (autre distributeur) : R2 active depuis 21 jours, R3 pas encore commencée.
        idR2 = grpc(jeton202()).declarerRupture(DeclarerRuptureRequest.newBuilder()
                .setCodeProduit("VMP-000301").setDateDebut(date(J.minusDays(21)))
                .setMotif(MotifRupture.MOTIF_RUPTURE_DEMANDE)
                .addRegions(Region.REGION_HAUTE_PAPERASSE).addRegions(Region.REGION_BASSE_VALIDATION)
                .build()).getRupture().getId();
        idR3 = grpc(jeton202()).declarerRupture(DeclarerRuptureRequest.newBuilder()
                .setCodeProduit("VMP-000302").setDateDebut(date(J.plusDays(5)))
                .setMotif(MotifRupture.MOTIF_RUPTURE_LOGISTIQUE)
                .addRegions(Region.REGION_PLATEAU_DU_LEGACY)
                .build()).getRupture().getId();
        assertThat(List.of(idR1, idR2, idR3)).doesNotHaveDuplicates();
    }

    // ====================================================================== (b)

    @Test
    @Order(2)
    void b_unPharmacienVoitLaRuptureEnRestConformementAuContrat() {
        String pharmacien = jetons.pharmacien();

        assertThat(ids(getOk("/api/v1/ruptures?region=COTE_DU_TIMEOUT", pharmacien))).contains(idR1);
        assertThat(ids(getOk("/api/v1/ruptures?region=MARAIS_DE_LA_RECETTE", pharmacien))).contains(idR1);
        // autre région : non vide (R2) mais sans R1 ; région sans rupture : vide
        assertThat(ids(getOk("/api/v1/ruptures?region=HAUTE_PAPERASSE", pharmacien))).contains(idR2).doesNotContain(idR1);
        JsonNode vide = getOk("/api/v1/ruptures?region=PROVINCE_DU_TICKET_OUVERT", pharmacien);
        assertThat(ids(vide)).isEmpty();
        assertThat(vide.get("totalElements").asLong()).isZero();
        assertThat(ids(getOk("/api/v1/ruptures?produit=VMP-000101", pharmacien))).containsExactly(idR1);

        JsonNode detail = getOk("/api/v1/ruptures/" + idR1, pharmacien);
        assertDetailConformeOpenApi(detail);
        assertThat(detail.get("id").asText()).isEqualTo(idR1);
        assertThat(detail.get("codeProduit").asText()).isEqualTo("VMP-000101");
        assertThat(detail.get("dateDebut").asText()).isEqualTo("2026-03-06");
        assertThat(detail.get("dateFinEstimee").asText()).isEqualTo("2026-04-05");
        assertThat(detail.get("dateFinEffective").isNull()).isTrue();
        assertThat(detail.get("motif").asText()).isEqualTo("PRODUCTION");
        assertThat(textes(detail.get("regions"))).containsExactly("COTE_DU_TIMEOUT", "MARAIS_DE_LA_RECETTE");
        assertThat(detail.get("alternatives")).hasSize(1);
        assertThat(detail.get("alternatives").get(0).get("codeProduit").asText()).isEqualTo("VMP-000102");
        assertThat(detail.get("alternatives").get(0).get("commentaire").asText()).isEqualTo(COMMENTAIRE);
        assertThat(detail.get("statut").asText()).isEqualTo("EN_COURS");
        assertThat(detail.get("distributeurId").asText()).isEqualTo(PRO_201);

        JsonNode alternatives = getOk("/api/v1/produits/VMP-000101/alternatives", pharmacien);
        assertThat(alternatives.get("codeProduit").asText()).isEqualTo("VMP-000101");
        assertThat(alternatives.get("alternatives")).hasSize(1);
        JsonNode alt = alternatives.get("alternatives").get(0);
        assertThat(alt.get("codeProduit").asText()).isEqualTo("VMP-000102");
        assertThat(alt.get("commentaire").asText()).isEqualTo(COMMENTAIRE);
        assertThat(alt.get("ruptureId").asText()).isEqualTo(idR1);

        // Le PRESCRIPTEUR voit exactement la même chose.
        String prescripteur = jetons.prescripteur();
        assertThat(getOk("/api/v1/ruptures/" + idR1, prescripteur)).isEqualTo(detail);
        assertThat(ids(getOk("/api/v1/ruptures?region=COTE_DU_TIMEOUT", prescripteur))).contains(idR1);
        assertThat(getOk("/api/v1/produits/VMP-000101/alternatives", prescripteur)).isEqualTo(alternatives);
    }

    // ====================================================================== (c)

    @Test
    @Order(3)
    void c_leDistributeurMetAJourViaGrpcEtRestLeReflete() {
        MettreAJourRuptureResponse reponse = grpc(jeton201()).mettreAJourRupture(miseAJourR1(J.plusDays(45),
                Region.REGION_HAUTE_PAPERASSE, Region.REGION_COTE_DU_TIMEOUT));

        assertThat(reponse.getRupture().getId()).isEqualTo(idR1);
        assertThat(reponse.getRupture().getDateFinEstimee()).isEqualTo("2026-04-30");
        assertThat(reponse.getRupture().getRegionsList())
                .containsExactly(Region.REGION_HAUTE_PAPERASSE, Region.REGION_COTE_DU_TIMEOUT);

        String pharmacien = jetons.pharmacien();
        JsonNode detail = getOk("/api/v1/ruptures/" + idR1, pharmacien);
        assertDetailConformeOpenApi(detail);
        assertThat(detail.get("dateDebut").asText()).isEqualTo("2026-03-06"); // immuable
        assertThat(detail.get("dateFinEstimee").asText()).isEqualTo("2026-04-30");
        assertThat(textes(detail.get("regions"))).containsExactly("HAUTE_PAPERASSE", "COTE_DU_TIMEOUT");
        assertThat(detail.get("statut").asText()).isEqualTo("EN_COURS");
        assertThat(detail.get("distributeurId").asText()).isEqualTo(PRO_201);
        assertThat(ids(getOk("/api/v1/ruptures?region=MARAIS_DE_LA_RECETTE", pharmacien))).doesNotContain(idR1);
        assertThat(ids(getOk("/api/v1/ruptures?region=HAUTE_PAPERASSE", pharmacien))).contains(idR1, idR2);
        detailApresMiseAJour = detail.toString();
    }

    // ====================================================================== (d)

    @Test
    @Order(4)
    void d_unAutreDistributeurNePeutNiModifierNiCloturer() {
        String jeton202 = jeton202();
        StatusRuntimeException modification = erreurGrpc(() -> grpc(jeton202).mettreAJourRupture(
                miseAJourR1(J.plusDays(60), Region.REGION_PROVINCE_DU_TICKET_OUVERT)));
        StatusRuntimeException cloture = erreurGrpc(() -> grpc(jeton202).cloturerRupture(
                CloturerRuptureRequest.newBuilder().setId(idR1).setDateFinEffective(date(J)).build()));

        for (StatusRuntimeException e : List.of(modification, cloture)) {
            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
            assertThat(e.getStatus().getDescription()).doesNotContain(PRO_201, SUJET_201, idR1);
            assertSansFuite(e.getStatus().getDescription(), jeton202);
        }
        assertThat(getOk("/api/v1/ruptures/" + idR1, jetons.pharmacien()).toString()).isEqualTo(detailApresMiseAJour);
    }

    // ====================================================================== (e)

    @Test
    @Order(5)
    void e_leBatchRecalculeLIndiceDesRegionsEtNotifieCellesAuDessusDuSeuil() throws Exception {
        JobExecution execution = lanceur.lancer(J);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        instanceBatchE = execution.getJobInstance().getInstanceId();
        assertThat(fichiers()).containsExactly(FICHIER_J);

        JsonNode notification = JSON.readTree(Files.readString(repertoireNotifications.resolve(FICHIER_J)));
        assertThat(notification.get("dateReference").asText()).isEqualTo(J_TEXTE);
        assertThat(notification.get("seuil").asDouble()).isEqualTo(0.5);
        // HAUTE_PAPERASSE = R1 + R2 ; BASSE_VALIDATION = R2 ; COTE_DU_TIMEOUT = R1 ;
        // MARAIS_DE_LA_RECETTE retirée par la mise à jour, PLATEAU_DU_LEGACY pas commencée, PROVINCE sans rupture.
        Map<String, Double> attendu = new java.util.LinkedHashMap<>();
        attendu.put("HAUTE_PAPERASSE", POIDS_R1 + POIDS_R2);
        attendu.put("BASSE_VALIDATION", POIDS_R2);
        attendu.put("COTE_DU_TIMEOUT", POIDS_R1);
        assertRegionsNotifiees(notification, attendu);
    }

    // ====================================================================== (f)

    @Test
    @Order(6)
    void f_leDistributeurClotureEtLaRuptureSortDesListesEtDuBatch() throws Exception {
        CloturerRuptureResponse reponse = grpc(jeton201()).cloturerRupture(CloturerRuptureRequest.newBuilder()
                .setId(idR1).setDateFinEffective(date(J.minusDays(1))).build());
        assertThat(reponse.getRupture().getStatut()).isEqualTo(StatutRupture.STATUT_RUPTURE_CLOTUREE);
        assertThat(reponse.getRupture().getDateFinEffective()).isEqualTo("2026-03-15");

        String pharmacien = jetons.pharmacien();
        assertThat(ids(getOk("/api/v1/ruptures?size=100", pharmacien))).contains(idR2, idR3).doesNotContain(idR1);
        assertThat(ids(getOk("/api/v1/ruptures?region=COTE_DU_TIMEOUT", pharmacien))).doesNotContain(idR1);
        JsonNode detail = getOk("/api/v1/ruptures/" + idR1, pharmacien);
        assertDetailConformeOpenApi(detail);
        assertThat(detail.get("statut").asText()).isEqualTo("CLOTUREE");
        assertThat(detail.get("dateFinEffective").asText()).isEqualTo("2026-03-15");
        assertThat(detail.get("dateFinEstimee").asText()).isEqualTo("2026-04-30");
        // les alternatives ne sont agrégées que depuis les ruptures EN COURS
        assertThat(getOk("/api/v1/produits/VMP-000101/alternatives", pharmacien).get("alternatives")).isEmpty();

        // Une seconde clôture n'est pas idempotente.
        assertThat(erreurGrpc(() -> grpc(jeton201()).cloturerRupture(CloturerRuptureRequest.newBuilder()
                .setId(idR1).setDateFinEffective(date(J)).build())).getStatus().getCode())
                .isEqualTo(Status.Code.FAILED_PRECONDITION);

        // Batch relancé le même jour : nouvelle instance, COTE_DU_TIMEOUT n'est plus signalée.
        JobExecution relance = lanceur.lancer(J);
        assertThat(relance.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(relance.getJobInstance().getInstanceId()).isNotEqualTo(instanceBatchE);
        assertThat(fichiers()).containsExactly(FICHIER_J);
        JsonNode notification = JSON.readTree(Files.readString(repertoireNotifications.resolve(FICHIER_J)));
        Map<String, Double> attendu = new java.util.LinkedHashMap<>();
        attendu.put("HAUTE_PAPERASSE", POIDS_R2);
        attendu.put("BASSE_VALIDATION", POIDS_R2);
        assertRegionsNotifiees(notification, attendu);
    }

    // ====================================================================== (g)

    @Test
    @Order(7)
    void g_leJournalDAuditTraceExactementLeCycleDeVieEtResteIntegre() {
        List<Map<String, Object>> lignes = jdbc.queryForList(
                "SELECT type_evenement, acteur_sujet, acteur_role, instant, resume FROM journal_audit"
                        + " WHERE rupture_id = ? ORDER BY sequence", UUID.fromString(idR1));

        assertThat(lignes).extracting(l -> l.get("type_evenement"))
                .containsExactly("DECLARATION", "MODIFICATION", "CLOTURE");
        assertThat(lignes).extracting(l -> l.get("acteur_sujet")).containsOnly(SUJET_201);
        assertThat(lignes).extracting(l -> l.get("acteur_role")).containsOnly("DISTRIBUTEUR");
        // instant issu de l'horloge FIXE injectée (et non de l'horloge système)
        assertThat(lignes).extracting(l -> ((java.time.OffsetDateTime) l.get("instant")).toInstant())
                .containsOnly(HorlogeFixeE2e.MAINTENANT);
        assertThat(lignes).extracting(l -> String.valueOf(l.get("resume"))).containsExactly(
                "produit VMP-000101, 2 régions", "produit VMP-000101, 2 régions", "produit VMP-000101, clôture");

        // Témoins : une seule DECLARATION chacun, par PRO-000202 ; les refus de (d) n'ont rien écrit.
        for (String id : List.of(idR2, idR3)) {
            assertThat(jdbc.queryForList("SELECT type_evenement FROM journal_audit WHERE rupture_id = ?",
                    String.class, UUID.fromString(id))).containsExactly("DECLARATION");
        }
        assertThat(jdbc.queryForList("SELECT DISTINCT acteur_sujet FROM journal_audit WHERE rupture_id = ?",
                String.class, UUID.fromString(idR1))).containsExactly(SUJET_201);

        ResultatVerificationIntegrite verification = verificateur.verifier();
        assertThat(verification.integre()).isTrue();
        assertThat(verification.entreesVerifiees()).isEqualTo(compterJournal()).isEqualTo(5);

        // Aucun résumé ne contient de jeton ni de valeur libre (commentaire d'alternative, sujet...).
        List<String> resumes = jdbc.queryForList("SELECT resume FROM journal_audit", String.class);
        assertThat(resumes).allSatisfy(resume -> {
            assertThat(resume).matches("produit VMP-\\d{6}, (\\d régions?|clôture)");
            assertThat(resume).doesNotContain("eyJ", "Bearer", "Boîte", "dosage", SUJET_201, SUJET_202);
        });
    }

    // ====================================================================== outillage

    private MettreAJourRuptureRequest miseAJourR1(java.time.LocalDate finEstimee, Region... regions) {
        return MettreAJourRuptureRequest.newBuilder()
                .setId(idR1)
                .setDateFinEstimee(date(finEstimee))
                .setMotif(MotifRupture.MOTIF_RUPTURE_PRODUCTION)
                .addAllRegions(List.of(regions))
                .addAlternatives(alternative("VMP-000102", COMMENTAIRE))
                .build();
    }

    /** Champs et formats du schéma OpenAPI {@code Rupture} (ni creeLe ni modifieLe, enums sans préfixe). */
    static void assertDetailConformeOpenApi(JsonNode detail) {
        List<String> champs = new ArrayList<>();
        detail.fieldNames().forEachRemaining(champs::add);
        assertThat(champs).containsExactlyInAnyOrder("id", "codeProduit", "dateDebut", "dateFinEstimee",
                "dateFinEffective", "motif", "regions", "alternatives", "statut", "distributeurId");
        assertThat(champs).doesNotContain("creeLe", "modifieLe", "version", "declarant");
        assertThat(detail.get("id").asText())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(detail.get("codeProduit").asText()).matches("VMP-\\d{6}");
        assertThat(detail.get("dateDebut").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
        for (String champ : List.of("dateFinEstimee", "dateFinEffective")) {
            if (!detail.get(champ).isNull()) {
                assertThat(detail.get(champ).asText()).matches("\\d{4}-\\d{2}-\\d{2}");
            }
        }
        assertThat(detail.get("motif").asText()).isIn("PRODUCTION", "LOGISTIQUE", "REGLEMENTAIRE", "DEMANDE");
        assertThat(detail.get("statut").asText()).isIn("EN_COURS", "CLOTUREE");
        assertThat(detail.get("dateFinEffective").isNull())
                .isEqualTo("EN_COURS".equals(detail.get("statut").asText()));
        assertThat(detail.get("regions").size()).isBetween(1, 6);
        assertThat(textes(detail.get("regions"))).doesNotHaveDuplicates().allSatisfy(r -> assertThat(r)
                .doesNotStartWith("REGION_")
                .isIn("HAUTE_PAPERASSE", "BASSE_VALIDATION", "PLATEAU_DU_LEGACY", "COTE_DU_TIMEOUT",
                        "MARAIS_DE_LA_RECETTE", "PROVINCE_DU_TICKET_OUVERT"));
        assertThat(detail.get("alternatives").isArray()).isTrue();
        detail.get("alternatives").forEach(a -> assertThat(a.get("codeProduit").asText()).matches("VMP-\\d{6}"));
        assertThat(detail.get("distributeurId").asText()).matches("PRO-\\d{6}");
    }

    static List<String> textes(JsonNode tableau) {
        List<String> valeurs = new ArrayList<>();
        tableau.forEach(n -> valeurs.add(n.asText()));
        return valeurs;
    }

    private static void assertRegionsNotifiees(JsonNode notification, Map<String, Double> attendu) {
        JsonNode regions = notification.get("regions");
        List<String> noms = new ArrayList<>();
        regions.forEach(r -> noms.add(r.get("region").asText()));
        assertThat(noms).containsExactlyElementsOf(attendu.keySet());
        regions.forEach(r -> assertThat(r.get("indice").asDouble())
                .isCloseTo(attendu.get(r.get("region").asText()), within(1e-9))
                .isGreaterThan(0.5));
    }

    private List<String> fichiers() throws IOException {
        try (Stream<Path> s = Files.list(repertoireNotifications)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }
}
