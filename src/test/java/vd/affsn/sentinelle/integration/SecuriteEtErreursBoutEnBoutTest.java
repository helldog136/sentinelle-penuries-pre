package vd.affsn.sentinelle.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import vd.affsn.sentinelle.grpc.v1.CloturerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.DeclarerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.MettreAJourRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.MotifRupture;
import vd.affsn.sentinelle.grpc.v1.Region;
import vd.affsn.sentinelle.grpc.v1.Rupture;
import vd.affsn.sentinelle.security.JwtTestFactory;

/**
 * SÉCURITÉ ET ERREURS de bout en bout (vrai intercepteur JWT gRPC, vraie chaîne Spring Security,
 * vrais adaptateurs) : authentification, rôles, validation, absence de persistance après refus,
 * usurpation d'identité et absence de fuite dans les corps d'erreur.
 *
 * <p>Chaque test utilise ses propres codes produit (base dédiée, jamais vidée).
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sentinelle-e2e-securite;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "grpc.server.in-process-name=sentinelle-e2e-securite"})
class SecuriteEtErreursBoutEnBoutTest extends AbstractBoutEnBoutTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @TempDir
    static Path repertoireNotifications;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("sentinelle.batch.notifications.repertoire", () -> repertoireNotifications.toString());
    }

    // ====================================================================== gRPC : authentification et rôle

    @Nested
    class AuthentificationGrpc {

        @Test
        void sansJetonUnauthenticatedEtRienNEstPersiste() {
            long journal = compterJournal();
            StatusRuntimeException e = erreurGrpc(() -> grpcSansJeton()
                    .declarerRupture(declarationSimple("VMP-000501")));

            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
            assertThat(e.getStatus().getDescription()).isEqualTo("Authentification requise");
            assertThat(compterRupturesProduit("VMP-000501")).isZero();
            assertThat(compterJournal()).isEqualTo(journal);
        }

        @Test
        void jetonDePharmacienOuDePrescripteurPermissionDenied() {
            for (String jeton : List.of(jetons.pharmacien(), jetons.prescripteur())) {
                StatusRuntimeException e = erreurGrpc(() -> grpc(jeton).declarerRupture(declarationSimple("VMP-000502")));
                assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
                assertSansFuite(e.getStatus().getDescription(), jeton);
            }
            assertThat(compterRupturesProduit("VMP-000502")).isZero();
        }

        @Test
        void jetonExpireOuSigneParUneAutreCleUnauthenticated() {
            for (String jeton : List.of(jeton201Expire(), jeton201AutreCle(), jetons.expire(),
                    jetons.signeParUneAutreCle(), jetons.mauvaiseAudience(), jetons.algNone())) {
                StatusRuntimeException e = erreurGrpc(() -> grpc(jeton).declarerRupture(declarationSimple("VMP-000503")));
                assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
                // message unique : la raison du refus n'est jamais révélée
                assertThat(e.getStatus().getDescription()).isEqualTo("Authentification requise");
                assertSansFuite(e.getStatus().getDescription(), jeton);
            }
            assertThat(compterRupturesProduit("VMP-000503")).isZero();
        }

        @Test
        void schemaAutreQueBearerUnauthenticated() {
            Metadata md = new Metadata();
            md.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Basic " + jeton201());
            StatusRuntimeException e = erreurGrpc(() -> grpcAvec(md).declarerRupture(declarationSimple("VMP-000504")));
            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
            assertThat(compterRupturesProduit("VMP-000504")).isZero();
        }
    }

    // ====================================================================== REST : authentification et rôle

    @Nested
    class AuthentificationRest {

        @Test
        void sansJeton401ProblemJsonAvecWwwAuthenticateBearerEtCorpsGenerique() {
            Reponse r = get("/api/v1/ruptures", (String) null);

            assertThat(r.statut()).isEqualTo(401);
            assertThat(r.typeContenu()).startsWith(PROBLEM_JSON);
            assertThat(r.entete("WWW-Authenticate")).isEqualTo("Bearer");
            JsonNode corps = r.json();
            assertThat(corps.get("status").asInt()).isEqualTo(401);
            assertThat(corps.get("type").asText()).isEqualTo("https://sentinelle.affsn.example/problemes/non-authentifie");
            assertThat(corps.get("title").asText()).isEqualTo("Authentification requise");
            assertThat(corps.get("detail").asText()).isEqualTo("Authentification requise ou jeton invalide.");
            assertThat(corps.get("instance").asText()).isEqualTo("/api/v1/ruptures");
            assertSansFuite(r.corps());
        }

        @Test
        void jetonExpireOuInvalide401AvecLeMemeCorpsQueSansJeton() {
            Reponse sansJeton = get("/api/v1/ruptures", (String) null);
            for (String jeton : List.of(jetons.expire(), jetons.signeParUneAutreCle(), jeton201Expire(),
                    JwtTestFactory.troisSegmentsIllisibles())) {
                Reponse r = get("/api/v1/ruptures", jeton);
                assertThat(r.statut()).isEqualTo(401);
                assertThat(r.typeContenu()).startsWith(PROBLEM_JSON);
                assertThat(r.entete("WWW-Authenticate")).isEqualTo("Bearer");
                assertThat(r.json()).isEqualTo(sansJeton.json());
                assertSansFuite(r.corps(), jeton);
            }
        }

        @Test
        void jetonDeDistributeur403ProblemJson() {
            String jeton = jeton201();
            for (String chemin : List.of("/api/v1/ruptures", "/api/v1/ruptures/" + UUID.randomUUID(),
                    "/api/v1/produits/VMP-000101/alternatives")) {
                Reponse r = get(chemin, jeton);
                assertThat(r.statut()).as(chemin).isEqualTo(403);
                assertThat(r.typeContenu()).startsWith(PROBLEM_JSON);
                JsonNode corps = r.json();
                assertThat(corps.get("status").asInt()).isEqualTo(403);
                assertThat(corps.get("type").asText()).isEqualTo("https://sentinelle.affsn.example/problemes/acces-refuse");
                assertThat(corps.get("title").asText()).isEqualTo("Accès refusé");
                assertSansFuite(r.corps(), jeton);
                assertThat(r.corps()).doesNotContain(PRO_201, SUJET_201);
            }
        }

        @Test
        void plusieursEntetesAuthorization401() {
            Reponse r = get("/api/v1/ruptures", List.of("Bearer " + jetons.pharmacien(), "Bearer " + jetons.prescripteur()));
            assertThat(r.statut()).isEqualTo(401);
            assertThat(r.typeContenu()).startsWith(PROBLEM_JSON);
        }
    }

    // ====================================================================== REST : paramètres et ressources

    @Nested
    class ErreursRest {

        @Test
        void regionInconnue400AvecViolationSansEchoDeLaValeur() {
            Reponse r = get("/api/v1/ruptures?region=ATLANTIDE_ENGLOUTIE", jetons.pharmacien());

            assertThat(r.statut()).isEqualTo(400);
            assertThat(r.typeContenu()).startsWith(PROBLEM_JSON);
            JsonNode corps = r.json();
            assertThat(corps.get("status").asInt()).isEqualTo(400);
            assertThat(corps.get("type").asText()).isEqualTo("https://sentinelle.affsn.example/problemes/parametre-invalide");
            assertThat(champs(corps)).containsExactly("region");
            assertThat(r.corps()).doesNotContain("ATLANTIDE");
            assertSansFuite(r.corps());
        }

        @Test
        void sizeZero400AvecViolation() {
            Reponse r = get("/api/v1/ruptures?size=0", jetons.prescripteur());

            assertThat(r.statut()).isEqualTo(400);
            assertThat(r.typeContenu()).startsWith(PROBLEM_JSON);
            assertThat(champs(r.json())).containsExactly("size");
            assertSansFuite(r.corps());
        }

        @Test
        void plusieursParametresInvalidesToutesLesViolations() {
            Reponse r = get("/api/v1/ruptures?region=cote_du_timeout&size=1000&page=-1&produit=VMP-12", jetons.pharmacien());

            assertThat(r.statut()).isEqualTo(400);
            assertThat(champs(r.json())).containsExactlyInAnyOrder("region", "size", "page", "produit");
            assertSansFuite(r.corps());
        }

        @Test
        void identifiantMalForme400EtCodeProduitMalForme400() {
            Reponse id = get("/api/v1/ruptures/pas-un-uuid", jetons.pharmacien());
            Reponse code = get("/api/v1/produits/VMP-1/alternatives", jetons.pharmacien());

            assertThat(id.statut()).isEqualTo(400);
            assertThat(champs(id.json())).containsExactly("id");
            assertThat(code.statut()).isEqualTo(400);
            assertThat(champs(code.json())).containsExactly("code");
            assertSansFuite(id.corps());
            assertSansFuite(code.corps());
        }

        @Test
        void detailDUnIdentifiantInconnu404ProblemJson() {
            String inconnu = UUID.randomUUID().toString();
            Reponse r = get("/api/v1/ruptures/" + inconnu, jetons.pharmacien());

            assertThat(r.statut()).isEqualTo(404);
            assertThat(r.typeContenu()).startsWith(PROBLEM_JSON);
            JsonNode corps = r.json();
            assertThat(corps.get("status").asInt()).isEqualTo(404);
            assertThat(corps.get("type").asText()).isEqualTo("https://sentinelle.affsn.example/problemes/rupture-introuvable");
            assertSansFuite(r.corps());
        }

        @Test
        void produitSansRuptureAlternativesVides200() {
            JsonNode corps = getOk("/api/v1/produits/VMP-999999/alternatives", jetons.pharmacien());
            assertThat(corps.get("codeProduit").asText()).isEqualTo("VMP-999999");
            assertThat(corps.get("alternatives")).isEmpty();
        }
    }

    // ====================================================================== gRPC : validation sans persistance

    @Nested
    class ValidationGrpc {

        @Test
        void codeProduitInvalideInvalidArgumentEtRienNEstPersiste() {
            long ruptures = jdbc.queryForObject("SELECT COUNT(*) FROM ruptures", Long.class);
            long journal = compterJournal();
            long totalRest = getOk("/api/v1/ruptures", jetons.pharmacien()).get("totalElements").asLong();

            for (String code : List.of("VMP-12345", "vmp-123456", " VMP-123456", "VMP-1234567", "")) {
                StatusRuntimeException e = erreurGrpc(() -> grpc(jeton201()).declarerRupture(
                        declarationSimple("VMP-000601").toBuilder().setCodeProduit(code).build()));
                assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                assertThat(e.getStatus().getDescription()).contains("code_produit: ");
                if (!code.isBlank()) {
                    assertThat(e.getStatus().getDescription()).doesNotContain(code.strip());
                }
            }

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ruptures", Long.class)).isEqualTo(ruptures);
            assertThat(compterJournal()).isEqualTo(journal);
            assertThat(getOk("/api/v1/ruptures", jetons.pharmacien()).get("totalElements").asLong()).isEqualTo(totalRest);
        }

        @Test
        void datesHorsBornesInvalidArgumentEtRienNEstPersiste() {
            long journal = compterJournal();
            List<DeclarerRuptureRequest> invalides = List.of(
                    // début avant J-365
                    declarationSimple("VMP-000602").toBuilder().setDateDebut(date(J.minusDays(366))).build(),
                    // début après J+90
                    declarationSimple("VMP-000602").toBuilder().setDateDebut(date(J.plusDays(91)))
                            .setDateFinEstimee(date(J.plusDays(100))).build(),
                    // fin estimée avant le début
                    declarationSimple("VMP-000602").toBuilder().setDateFinEstimee(date(J.minusDays(4))).build(),
                    // fin estimée au-delà de début + 730 jours
                    declarationSimple("VMP-000602").toBuilder().setDateFinEstimee(date(J.minusDays(3).plusDays(731))).build(),
                    // date inexistante
                    declarationSimple("VMP-000602").toBuilder().setDateDebut("2026-02-30").build());
            List<String> champsAttendus = List.of("date_debut", "date_debut", "date_fin_estimee", "date_fin_estimee",
                    "date_debut");

            for (int i = 0; i < invalides.size(); i++) {
                DeclarerRuptureRequest requete = invalides.get(i);
                StatusRuntimeException e = erreurGrpc(() -> grpc(jeton201()).declarerRupture(requete));
                assertThat(e.getStatus().getCode()).as("cas %d", i).isEqualTo(Status.Code.INVALID_ARGUMENT);
                assertThat(e.getStatus().getDescription()).as("cas %d", i).contains(champsAttendus.get(i) + ": ");
            }

            assertThat(compterRupturesProduit("VMP-000602")).isZero();
            assertThat(compterJournal()).isEqualTo(journal);
            assertThat(getOk("/api/v1/ruptures?produit=VMP-000602", jetons.pharmacien()).get("totalElements").asLong())
                    .isZero();
        }

        @Test
        void bornesExactesAcceptees() {
            Rupture limiteBasse = grpc(jeton201()).declarerRupture(declarationSimple("VMP-000603").toBuilder()
                    .setDateDebut(date(J.minusDays(365))).setDateFinEstimee(date(J.minusDays(365).plusDays(730)))
                    .build()).getRupture();
            Rupture limiteHaute = grpc(jeton201()).declarerRupture(declarationSimple("VMP-000604").toBuilder()
                    .setDateDebut(date(J.plusDays(90))).clearDateFinEstimee().build()).getRupture();

            assertThat(limiteBasse.getDateDebut()).isEqualTo(date(J.minusDays(365)));
            assertThat(limiteHaute.hasDateFinEstimee()).isFalse();
            assertThat(getOk("/api/v1/ruptures/" + limiteHaute.getId(), jetons.pharmacien()).get("dateFinEstimee")
                    .isNull()).isTrue();
        }

        @Test
        void miseAJourOuClotureInvalidesNeModifientRien() {
            Rupture r = grpc(jeton201()).declarerRupture(declarationSimple("VMP-000605",
                    Region.REGION_HAUTE_PAPERASSE)).getRupture();
            String avant = getOk("/api/v1/ruptures/" + r.getId(), jetons.pharmacien()).toString();
            long journal = compterJournal();

            // fin estimée avant le début enregistré (règle du domaine)
            StatusRuntimeException finAvantDebut = erreurGrpc(() -> grpc(jeton201()).mettreAJourRupture(
                    MettreAJourRuptureRequest.newBuilder().setId(r.getId()).setDateFinEstimee(date(J.minusDays(30)))
                            .setMotif(MotifRupture.MOTIF_RUPTURE_DEMANDE).addRegions(Region.REGION_BASSE_VALIDATION)
                            .build()));
            // clôture dans le futur
            StatusRuntimeException clotureFuture = erreurGrpc(() -> grpc(jeton201()).cloturerRupture(
                    CloturerRuptureRequest.newBuilder().setId(r.getId()).setDateFinEffective(date(J.plusDays(1))).build()));
            // clôture avant le début
            StatusRuntimeException clotureAvantDebut = erreurGrpc(() -> grpc(jeton201()).cloturerRupture(
                    CloturerRuptureRequest.newBuilder().setId(r.getId()).setDateFinEffective(date(J.minusDays(10))).build()));
            // identifiant inconnu
            StatusRuntimeException inconnu = erreurGrpc(() -> grpc(jeton201()).cloturerRupture(
                    CloturerRuptureRequest.newBuilder().setId(UUID.randomUUID().toString())
                            .setDateFinEffective(date(J)).build()));

            assertThat(finAvantDebut.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
            assertThat(clotureFuture.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
            assertThat(clotureAvantDebut.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
            assertThat(inconnu.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
            for (StatusRuntimeException e : List.of(finAvantDebut, clotureFuture, clotureAvantDebut, inconnu)) {
                assertSansFuite(e.getStatus().getDescription());
            }
            assertThat(getOk("/api/v1/ruptures/" + r.getId(), jetons.pharmacien()).toString()).isEqualTo(avant);
            assertThat(compterJournal()).isEqualTo(journal);
        }
    }

    @Nested
    class RemplacementComplet {

        @Test
        void miseAJourRemplaceLesAlternativesYComprisLeCommentaireDUneAlternativeConservee() {
            Rupture r = grpc(jeton201()).declarerRupture(declarationSimple("VMP-000610").toBuilder()
                    .addAlternatives(alternative("VMP-000611", "ancien commentaire"))
                    .addAlternatives(alternative("VMP-000612", "retirée")).build()).getRupture();

            grpc(jeton201()).mettreAJourRupture(MettreAJourRuptureRequest.newBuilder().setId(r.getId())
                    .setMotif(MotifRupture.MOTIF_RUPTURE_DEMANDE).addRegions(Region.REGION_BASSE_VALIDATION)
                    .addAlternatives(alternative("VMP-000613", "ajoutée"))
                    .addAlternatives(alternative("VMP-000611", "  nouveau commentaire  ")).build());

            JsonNode detail = getOk("/api/v1/ruptures/" + r.getId(), jetons.pharmacien());
            // triées par code, commentaire remplacé et nettoyé des espaces de bord, fin estimée effacée (absente)
            assertThat(detail.get("alternatives").toString()).isEqualTo(
                    "[{\"codeProduit\":\"VMP-000611\",\"commentaire\":\"nouveau commentaire\"},"
                            + "{\"codeProduit\":\"VMP-000613\",\"commentaire\":\"ajoutée\"}]");
            assertThat(detail.get("dateFinEstimee").isNull()).isTrue();
            assertThat(detail.get("motif").asText()).isEqualTo("DEMANDE");
            assertThat(getOk("/api/v1/produits/VMP-000610/alternatives", jetons.pharmacien()).get("alternatives"))
                    .hasSize(2);
        }

        @Test
        void commentaireDe280UnitesUtf16HorsBmpAccepteDeBoutEnBout() {
            String commentaire = "\uD83D\uDC8A".repeat(140); // 140 points de code, 280 unités UTF-16
            Rupture r = grpc(jeton201()).declarerRupture(declarationSimple("VMP-000614").toBuilder()
                    .addAlternatives(alternative("VMP-000615", commentaire)).build()).getRupture();

            assertThat(getOk("/api/v1/ruptures/" + r.getId(), jetons.pharmacien()).get("alternatives").get(0)
                    .get("commentaire").asText()).isEqualTo(commentaire);
            StatusRuntimeException tropLong = erreurGrpc(() -> grpc(jeton201()).declarerRupture(
                    declarationSimple("VMP-000616").toBuilder()
                            .addAlternatives(alternative("VMP-000617", commentaire + "x")).build()));
            assertThat(tropLong.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
            assertThat(compterRupturesProduit("VMP-000616")).isZero();
        }
    }

    // ====================================================================== usurpation

    @Nested
    class Usurpation {

        @Test
        void niLaMetadataNiDesChampsInconnusDuMessageNeChangentLIdentite() {
            Metadata md = new Metadata();
            md.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + jeton201());
            for (String cle : List.of("distributeur_id", "x-distributeur-id", "x-forwarded-user", "sub", "role")) {
                md.put(Metadata.Key.of(cle, Metadata.ASCII_STRING_MARSHALLER), cle.equals("role") ? "PHARMACIEN" : PRO_202);
            }
            // champs inconnus (dont les numéros réservés 11 et 12) portant une autre identité
            UnknownFieldSet intrus = UnknownFieldSet.newBuilder()
                    .addField(7, UnknownFieldSet.Field.newBuilder().addLengthDelimited(ByteString.copyFromUtf8(PRO_202)).build())
                    .addField(11, UnknownFieldSet.Field.newBuilder().addLengthDelimited(ByteString.copyFromUtf8(PRO_202)).build())
                    .addField(99, UnknownFieldSet.Field.newBuilder().addLengthDelimited(ByteString.copyFromUtf8(SUJET_202)).build())
                    .build();
            DeclarerRuptureRequest requete = declarationSimple("VMP-000701").toBuilder().setUnknownFields(intrus).build();

            Rupture r = grpcAvec(md).declarerRupture(requete).getRupture();

            assertThat(r.getDistributeurId()).isEqualTo(PRO_201);
            assertThat(getOk("/api/v1/ruptures/" + r.getId(), jetons.pharmacien()).get("distributeurId").asText())
                    .isEqualTo(PRO_201);
            assertThat(jdbc.queryForObject("SELECT declarant FROM ruptures WHERE id = ?", String.class,
                    UUID.fromString(r.getId()))).isEqualTo(PRO_201);
            assertThat(jdbc.queryForObject("SELECT acteur_sujet FROM journal_audit WHERE rupture_id = ?", String.class,
                    UUID.fromString(r.getId()))).isEqualTo(SUJET_201);
        }

        @Test
        void unConcurrentQuiSeDitProprietaireDansLaMetadataResteRefuse() {
            Rupture r = grpc(jeton201()).declarerRupture(declarationSimple("VMP-000702")).getRupture();
            Metadata md = new Metadata();
            md.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + jeton202());
            md.put(Metadata.Key.of("distributeur_id", Metadata.ASCII_STRING_MARSHALLER), PRO_201);

            StatusRuntimeException e = erreurGrpc(() -> grpcAvec(md).cloturerRupture(CloturerRuptureRequest.newBuilder()
                    .setId(r.getId()).setDateFinEffective(date(J)).build()));

            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
            assertThat(getOk("/api/v1/ruptures/" + r.getId(), jetons.pharmacien()).get("statut").asText())
                    .isEqualTo("EN_COURS");
        }

        @Test
        void deuxValeursAuthorizationUnauthenticated() {
            Metadata md = new Metadata();
            Metadata.Key<String> cle = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
            md.put(cle, "Bearer " + jeton202());
            md.put(cle, "Bearer " + jeton201());

            StatusRuntimeException e = erreurGrpc(() -> grpcAvec(md).declarerRupture(declarationSimple("VMP-000703")));

            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
            assertThat(compterRupturesProduit("VMP-000703")).isZero();
        }

        @Test
        void unJetonGreffeAvecLaSignatureDUnAutreEstRefuse() {
            String[] p201 = jeton201().split("\\.");
            String[] p202 = jeton202().split("\\.");
            String greffe = p202[0] + "." + p202[1] + "." + p201[2];

            StatusRuntimeException e = erreurGrpc(() -> grpc(greffe).declarerRupture(declarationSimple("VMP-000704")));
            Reponse rest = get("/api/v1/ruptures", greffe);

            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
            assertThat(rest.statut()).isEqualTo(401);
            assertThat(compterRupturesProduit("VMP-000704")).isZero();
        }
    }

    // ====================================================================== défauts d'intégration

    /**
     * Défauts d'intégration découverts (tests ROUGES tant que le code de production n'est pas corrigé).
     *
     * <p>Cause commune : {@code RestExceptionHandler#inattendue} ({@code @ExceptionHandler(Exception.class)}
     * dans un {@code @RestControllerAdvice} global) intercepte AUSSI les exceptions de Spring MVC qui
     * portent déjà leur statut HTTP ({@code ErrorResponse} : {@code NoResourceFoundException},
     * {@code HttpRequestMethodNotSupportedException}, {@code HttpMediaTypeNotAcceptableException}...) et
     * les transforme en 500 « Erreur interne » journalisé en ERROR avec la pile. Chaque test isole un cas.
     */
    @Nested
    class BugsDecouverts {

        @Test
        void routeInconnueSousApiDoitRepondre404EtNon500() {
            Reponse r = requete("GET", "/api/v1/inconnu", jetons.pharmacien(), "application/json");

            assertThat(r.statut()).as("observé : %s", r.corps()).isEqualTo(404);
            assertThat(r.corps()).doesNotContain("erreur-interne");
        }

        @Test
        void methodeNonSupporteeDoitRepondre405EtNon500() {
            Reponse r = requete("POST", "/api/v1/ruptures", jetons.pharmacien(), "application/json");

            assertThat(r.statut()).as("observé : %s", r.corps()).isEqualTo(405);
            assertThat(r.entete("Allow")).contains("GET");
        }

        @Test
        void representationNonAcceptableDoitRepondre406EtNon500() {
            Reponse r = requete("GET", "/api/v1/ruptures", jetons.pharmacien(), "application/xml");

            assertThat(r.statut()).as("observé : %s", r.corps()).isEqualTo(406);
        }
    }

    // ====================================================================== outillage

    private static List<String> champs(JsonNode probleme) {
        assertThat(probleme.get("violations")).as("violations").isNotNull();
        List<String> champs = new ArrayList<>();
        probleme.get("violations").forEach(v -> {
            assertThat(v.get("message").asText()).isNotBlank();
            champs.add(v.get("champ").asText());
        });
        return champs;
    }
}
