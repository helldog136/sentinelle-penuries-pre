package vd.affsn.sentinelle.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.MetadataUtils;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import vd.affsn.sentinelle.application.port.out.HorlogePort;
import vd.affsn.sentinelle.grpc.v1.Alternative;
import vd.affsn.sentinelle.grpc.v1.DeclarerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.MotifRupture;
import vd.affsn.sentinelle.grpc.v1.Region;
import vd.affsn.sentinelle.grpc.v1.SignalementServiceGrpc;
import vd.affsn.sentinelle.security.JwtTestFactory;

/**
 * Socle des tests de bout en bout : application COMPLÈTE (profil test) sur un port HTTP aléatoire,
 * serveur gRPC IN-PROCESS (aucun port réseau gRPC : {@code grpc.server.port=-1} du profil test) avec
 * le vrai intercepteur JWT global et le vrai service, REST interrogé par un vrai client HTTP
 * ({@link HttpClient}), job batch activé, horloge métier figée ({@link HorlogeFixeE2e}).
 *
 * <p>Chaque classe concrète fixe par {@code @TestPropertySource} une base H2 DÉDIÉE et un nom de
 * serveur in-process unique (les contextes restent en cache). Le journal d'audit n'est jamais vidé :
 * les tests utilisent des codes produit propres et raisonnent par rupture ou par différence.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sentinelle.grpc.enabled=true",
        "sentinelle.rest.enabled=true",
        "sentinelle.batch.enabled=true",
        "sentinelle.batch.seuil-tension=0.5",
        // Planification inerte (29 février) : le job n'est lancé qu'explicitement.
        "sentinelle.batch.cron=0 0 3 29 2 *",
        "grpc.server.port=-1"})
@ActiveProfiles("test")
@Import(AbstractBoutEnBoutTest.ConfigurationHorlogeFixe.class)
abstract class AbstractBoutEnBoutTest {

    static final String SUJET_201 = "distributeur-e2e-201";
    static final String SUJET_202 = "distributeur-e2e-202";
    static final String PRO_201 = "PRO-000201";
    static final String PRO_202 = "PRO-000202";

    static final LocalDate J = HorlogeFixeE2e.J;

    /** Clé RSA « attaquante », générée à l'exécution (aucune clé de test réelle). */
    static final KeyPair PAIRE_ATTAQUANTE = genererPaire();

    static final ObjectMapper JSON = new ObjectMapper();

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfigurationHorlogeFixe {
        @Bean
        HorlogePort horlogeFixeE2e() {
            return new HorlogeFixeE2e();
        }
    }

    @LocalServerPort
    protected int port;

    @Value("${grpc.server.in-process-name}")
    protected String nomServeurGrpc;

    @Autowired
    protected JdbcTemplate jdbc;

    protected final JwtTestFactory jetons = JwtTestFactory.systeme();
    protected final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private ManagedChannel canal;

    @BeforeEach
    void ouvrirCanalGrpc() {
        canal = InProcessChannelBuilder.forName(nomServeurGrpc).build();
    }

    @AfterEach
    void fermerCanalGrpc() throws InterruptedException {
        canal.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------ jetons

    String jeton201() {
        return jetons.signer(jetons.claimsValides(SUJET_201, "DISTRIBUTEUR", PRO_201).build());
    }

    String jeton202() {
        return jetons.signer(jetons.claimsValides(SUJET_202, "DISTRIBUTEUR", PRO_202).build());
    }

    /** Jeton DISTRIBUTEUR PRO-000201 expiré depuis 10 minutes (au-delà de la tolérance de 30 s). */
    String jeton201Expire() {
        Instant t = Instant.now();
        return jetons.signer(jetons.claimsValides(SUJET_201, "DISTRIBUTEUR", PRO_201)
                .issueTime(Date.from(t.minusSeconds(900)))
                .notBeforeTime(Date.from(t.minusSeconds(900)))
                .expirationTime(Date.from(t.minusSeconds(600)))
                .build());
    }

    /** Jeton DISTRIBUTEUR PRO-000201 aux claims valides mais signé par une autre clé RSA. */
    String jeton201AutreCle() {
        return jetons.signer(jetons.claimsValides(SUJET_201, "DISTRIBUTEUR", PRO_201).build(),
                JWSAlgorithm.RS256, (RSAPrivateKey) PAIRE_ATTAQUANTE.getPrivate());
    }

    // ------------------------------------------------------------------ gRPC

    SignalementServiceGrpc.SignalementServiceBlockingStub grpc(String jeton) {
        Metadata md = new Metadata();
        md.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + jeton);
        return grpcAvec(md);
    }

    SignalementServiceGrpc.SignalementServiceBlockingStub grpcAvec(Metadata md) {
        return SignalementServiceGrpc.newBlockingStub(canal)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(md))
                .withDeadlineAfter(20, TimeUnit.SECONDS);
    }

    SignalementServiceGrpc.SignalementServiceBlockingStub grpcSansJeton() {
        return SignalementServiceGrpc.newBlockingStub(canal).withDeadlineAfter(20, TimeUnit.SECONDS);
    }

    static StatusRuntimeException erreurGrpc(ThrowingCallable appel) {
        Throwable erreur = catchThrowable(appel);
        assertThat(erreur).as("l'appel gRPC aurait dû échouer").isInstanceOf(StatusRuntimeException.class);
        return (StatusRuntimeException) erreur;
    }

    static String date(LocalDate d) {
        return d.toString();
    }

    static Alternative alternative(String code, String commentaire) {
        return Alternative.newBuilder().setCodeProduit(code).setCommentaire(commentaire).build();
    }

    /** Déclaration valide minimale (début J-3, une région) pour le produit donné. */
    static DeclarerRuptureRequest declarationSimple(String produit, Region... regions) {
        return DeclarerRuptureRequest.newBuilder()
                .setCodeProduit(produit)
                .setDateDebut(date(J.minusDays(3)))
                .setDateFinEstimee(date(J.plusDays(30)))
                .setMotif(MotifRupture.MOTIF_RUPTURE_LOGISTIQUE)
                .addAllRegions(regions.length == 0 ? List.of(Region.REGION_PROVINCE_DU_TICKET_OUVERT) : List.of(regions))
                .build();
    }

    // ------------------------------------------------------------------ REST

    record Reponse(int statut, String typeContenu, Map<String, List<String>> entetes, String corps) {

        JsonNode json() {
            try {
                return JSON.readTree(corps);
            } catch (IOException e) {
                throw new AssertionError("Corps non JSON : " + corps, e);
            }
        }

        String entete(String nom) {
            return entetes.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(nom))
                    .flatMap(e -> e.getValue().stream())
                    .findFirst().orElse(null);
        }
    }

    Reponse get(String chemin, String jeton) {
        return get(chemin, jeton == null ? List.of() : List.of("Bearer " + jeton));
    }

    Reponse get(String chemin, List<String> autorisations) {
        HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .GET();
        for (String valeur : autorisations) {
            requete.header("Authorization", valeur);
        }
        return envoyer(requete, chemin);
    }

    /** Requête arbitraire (méthode, en-tête Accept) avec un Bearer. */
    Reponse requete(String methode, String chemin, String jeton, String accept) {
        HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", accept)
                .header("Authorization", "Bearer " + jeton)
                .method(methode, HttpRequest.BodyPublishers.noBody());
        return envoyer(requete, chemin);
    }

    private Reponse envoyer(HttpRequest.Builder requete, String chemin) {
        try {
            HttpResponse<String> r = http.send(requete.build(), HttpResponse.BodyHandlers.ofString());
            return new Reponse(r.statusCode(), r.headers().firstValue("Content-Type").orElse(null),
                    r.headers().map(), r.body());
        } catch (IOException e) {
            throw new AssertionError("Appel REST impossible : " + chemin, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    /** GET 200 attendu, corps JSON renvoyé. */
    JsonNode getOk(String chemin, String jeton) {
        Reponse r = get(chemin, jeton);
        assertThat(r.statut()).as("GET %s : %s", chemin, r.corps()).isEqualTo(200);
        assertThat(r.typeContenu()).startsWith("application/json");
        return r.json();
    }

    static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("contenu").forEach(n -> ids.add(n.get("id").asText()));
        return ids;
    }

    /** Aucun jeton, aucune trace technique dans un corps ou un message d'erreur. */
    static void assertSansFuite(String texte, String... jetonsUtilises) {
        assertThat(texte).isNotNull();
        for (String j : jetonsUtilises) {
            assertThat(texte).doesNotContain(j);
            // aucun segment du jeton (en-tête, charge utile, signature) n'est recopié
            for (String segment : j.split("\\.")) {
                if (segment.length() > 8) {
                    assertThat(texte).doesNotContain(segment);
                }
            }
        }
        assertThat(texte).doesNotContain("eyJ", "Exception", "exception", "java.", "org.springframework",
                "at vd.", "vd.affsn", "SQL", "Hibernate", "hibernate", "trace", "Bearer ");
    }

    long compterRupturesProduit(String produit) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ruptures WHERE code_produit = ?", Long.class, produit);
    }

    long compterJournal() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM journal_audit", Long.class);
    }

    private static KeyPair genererPaire() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
