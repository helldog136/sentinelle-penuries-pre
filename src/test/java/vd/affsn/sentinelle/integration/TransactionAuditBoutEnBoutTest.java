package vd.affsn.sentinelle.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import vd.affsn.sentinelle.adapter.out.audit.JournalAuditJdbcAdapter;
import vd.affsn.sentinelle.adapter.out.audit.VerificateurIntegriteJournalAudit;
import vd.affsn.sentinelle.application.port.out.AuditPort;
import vd.affsn.sentinelle.application.port.out.EvenementAudit;
import vd.affsn.sentinelle.grpc.v1.CloturerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.MettreAJourRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.MotifRupture;
import vd.affsn.sentinelle.grpc.v1.Region;
import vd.affsn.sentinelle.grpc.v1.Rupture;

/**
 * TRANSACTION DE BOUT EN BOUT : un {@link AuditPort} de test ({@code @Primary}) DÉLÈGUE à l'adaptateur
 * JDBC réel puis peut être forcé à échouer. On vérifie, depuis gRPC jusqu'à la base, que l'échec de
 * l'audit annule aussi la sauvegarde métier (même transaction), que la réponse est INTERNAL sans
 * fuite, et que le verrou d'audit est relâché (une opération normale réussit ensuite sans attente).
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sentinelle-e2e-transaction;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "grpc.server.in-process-name=sentinelle-e2e-transaction",
        // Un verrou non relâché ferait échouer vite l'opération suivante au lieu de bloquer 10 s.
        "sentinelle.audit.delai-attente-verrou=2s"})
@Import(TransactionAuditBoutEnBoutTest.ConfigurationAuditPilotable.class)
class TransactionAuditBoutEnBoutTest extends AbstractBoutEnBoutTest {

    @TempDir
    static Path repertoireNotifications;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("sentinelle.batch.notifications.repertoire", () -> repertoireNotifications.toString());
    }

    /** Doublure écrite à la main (pas de Mockito) : délègue au vrai adaptateur, puis échoue sur demande. */
    static final class AuditPilotable implements AuditPort {

        enum Mode { NORMAL, ECHEC_APRES_ECRITURE, ECHEC_AVANT_ECRITURE }

        private final AuditPort reel;
        private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.NORMAL);
        /** Si présent, le PROCHAIN appel signale son arrivée puis attend la libération (avant d'écrire). */
        private final AtomicReference<java.util.concurrent.CountDownLatch[]> pause = new AtomicReference<>();

        /** Le prochain appel s'arrêtera avant d'écrire : {@code [0]} = arrivé, {@code [1]} = libérer. */
        java.util.concurrent.CountDownLatch[] suspendreLeProchainAppel() {
            var verrous = new java.util.concurrent.CountDownLatch[] {
                    new java.util.concurrent.CountDownLatch(1), new java.util.concurrent.CountDownLatch(1)};
            pause.set(verrous);
            return verrous;
        }

        AuditPilotable(AuditPort reel) {
            this.reel = reel;
        }

        void mode(Mode nouveau) {
            mode.set(nouveau);
        }

        @Override
        public void enregistrer(EvenementAudit evenement) {
            var verrous = pause.getAndSet(null);
            if (verrous != null) {
                verrous[0].countDown();
                try {
                    if (!verrous[1].await(20, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new IllegalStateException("libération attendue");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            Mode courant = mode.get();
            if (courant == Mode.ECHEC_AVANT_ECRITURE) {
                throw new IllegalStateException("panne d'audit simulée (avant écriture) : détail interne");
            }
            reel.enregistrer(evenement);
            if (courant == Mode.ECHEC_APRES_ECRITURE) {
                throw new IllegalStateException("panne d'audit simulée (après écriture) : détail interne");
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfigurationAuditPilotable {
        @Bean
        @Primary
        AuditPilotable auditPilotable(JournalAuditJdbcAdapter reel) {
            return new AuditPilotable(reel);
        }
    }

    @Autowired
    private AuditPilotable audit;
    @Autowired
    private JournalAuditJdbcAdapter adaptateurReel;
    @Autowired
    private AuditPort auditInjecte;
    @Autowired
    private VerificateurIntegriteJournalAudit verificateur;

    @BeforeEach
    @AfterEach
    void auditNormal() {
        audit.mode(AuditPilotable.Mode.NORMAL);
    }

    @Test
    void laDoublureEstBienLAuditPortInjecte() {
        assertThat(auditInjecte).isSameAs(audit);
    }

    @Test
    void declarationAvecAuditEnEchecInternalSansFuiteEtRienNEstPersiste() {
        for (AuditPilotable.Mode panne : List.of(AuditPilotable.Mode.ECHEC_APRES_ECRITURE,
                AuditPilotable.Mode.ECHEC_AVANT_ECRITURE)) {
            long journal = compterJournal();
            long ruptures = jdbc.queryForObject("SELECT COUNT(*) FROM ruptures", Long.class);
            audit.mode(panne);
            String jeton = jeton201();

            StatusRuntimeException e = erreurGrpc(() -> grpc(jeton).declarerRupture(
                    declarationSimple("VMP-000801", Region.REGION_COTE_DU_TIMEOUT)));

            assertThat(e.getStatus().getCode()).as(panne.name()).isEqualTo(Status.Code.INTERNAL);
            assertThat(e.getStatus().getDescription()).isEqualTo("Erreur interne");
            assertSansFuite(e.getStatus().getDescription(), jeton);
            assertThat(e.getStatus().getDescription()).doesNotContain("panne", "simulée", "détail");
            // le rollback englobe la sauvegarde : ni rupture, ni ligne d'audit, ni alternative/région orpheline
            assertThat(compterRupturesProduit("VMP-000801")).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ruptures", Long.class)).isEqualTo(ruptures);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rupture_regions r LEFT JOIN ruptures p"
                    + " ON p.id = r.rupture_id WHERE p.id IS NULL", Long.class)).isZero();
            assertThat(compterJournal()).isEqualTo(journal);
            assertThat(getOk("/api/v1/ruptures?produit=VMP-000801", jetons.pharmacien())
                    .get("totalElements").asLong()).isZero();
            assertVerrouLibre();
        }

        audit.mode(AuditPilotable.Mode.NORMAL);
        assertOperationNormaleReussit("VMP-000802");
    }

    @Test
    void miseAJourAvecAuditEnEchecLaRuptureResteInchangee() {
        Rupture r = grpc(jeton201()).declarerRupture(declarationSimple("VMP-000803", Region.REGION_HAUTE_PAPERASSE))
                .getRupture();
        String detailAvant = getOk("/api/v1/ruptures/" + r.getId(), jetons.pharmacien()).toString();
        Map<String, Object> ligneAvant = ligneRupture(r.getId());
        long journal = compterJournal();

        for (AuditPilotable.Mode panne : List.of(AuditPilotable.Mode.ECHEC_APRES_ECRITURE,
                AuditPilotable.Mode.ECHEC_AVANT_ECRITURE)) {
            audit.mode(panne);
            StatusRuntimeException e = erreurGrpc(() -> grpc(jeton201()).mettreAJourRupture(
                    MettreAJourRuptureRequest.newBuilder().setId(r.getId()).setDateFinEstimee(date(J.plusDays(80)))
                            .setMotif(MotifRupture.MOTIF_RUPTURE_REGLEMENTAIRE)
                            .addRegions(Region.REGION_MARAIS_DE_LA_RECETTE)
                            .addAlternatives(alternative("VMP-000804", "remplacement"))
                            .build()));

            assertThat(e.getStatus().getCode()).as(panne.name()).isEqualTo(Status.Code.INTERNAL);
            assertThat(e.getStatus().getDescription()).isEqualTo("Erreur interne");
            assertThat(getOk("/api/v1/ruptures/" + r.getId(), jetons.pharmacien()).toString()).isEqualTo(detailAvant);
            assertThat(ligneRupture(r.getId())).isEqualTo(ligneAvant); // version et modifie_le compris
            assertThat(jdbc.queryForList("SELECT region FROM rupture_regions WHERE rupture_id = ?", String.class,
                    UUID.fromString(r.getId()))).containsExactly("HAUTE_PAPERASSE");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rupture_alternatives WHERE rupture_id = ?",
                    Long.class, UUID.fromString(r.getId()))).isZero();
            assertThat(compterJournal()).isEqualTo(journal);
            assertVerrouLibre();
        }

        audit.mode(AuditPilotable.Mode.NORMAL);
        Rupture modifiee = grpc(jeton201()).mettreAJourRupture(MettreAJourRuptureRequest.newBuilder()
                .setId(r.getId()).setMotif(MotifRupture.MOTIF_RUPTURE_DEMANDE)
                .addRegions(Region.REGION_BASSE_VALIDATION).build()).getRupture();
        assertThat(modifiee.getRegionsList()).containsExactly(Region.REGION_BASSE_VALIDATION);
        assertThat(compterJournal()).isEqualTo(journal + 1);
    }

    @Test
    void clotureAvecAuditEnEchecLaRuptureResteEnCours() {
        Rupture r = grpc(jeton201()).declarerRupture(declarationSimple("VMP-000805")).getRupture();
        long journal = compterJournal();

        for (AuditPilotable.Mode panne : List.of(AuditPilotable.Mode.ECHEC_APRES_ECRITURE,
                AuditPilotable.Mode.ECHEC_AVANT_ECRITURE)) {
            audit.mode(panne);
            StatusRuntimeException e = erreurGrpc(() -> grpc(jeton201()).cloturerRupture(CloturerRuptureRequest
                    .newBuilder().setId(r.getId()).setDateFinEffective(date(J)).build()));

            assertThat(e.getStatus().getCode()).as(panne.name()).isEqualTo(Status.Code.INTERNAL);
            assertThat(e.getStatus().getDescription()).isEqualTo("Erreur interne");
            var detail = getOk("/api/v1/ruptures/" + r.getId(), jetons.pharmacien());
            assertThat(detail.get("statut").asText()).isEqualTo("EN_COURS");
            assertThat(detail.get("dateFinEffective").isNull()).isTrue();
            assertThat(ids(getOk("/api/v1/ruptures?produit=VMP-000805",
                    jetons.pharmacien()))).containsExactly(r.getId());
            assertThat(jdbc.queryForObject("SELECT statut FROM ruptures WHERE id = ?", String.class,
                    UUID.fromString(r.getId()))).isEqualTo("EN_COURS");
            assertThat(compterJournal()).isEqualTo(journal);
            assertVerrouLibre();
        }

        // Puis la clôture normale réussit : elle n'avait donc pas été appliquée (pas de FAILED_PRECONDITION).
        audit.mode(AuditPilotable.Mode.NORMAL);
        Rupture cloturee = grpc(jeton201()).cloturerRupture(CloturerRuptureRequest.newBuilder()
                .setId(r.getId()).setDateFinEffective(date(J)).build()).getRupture();
        assertThat(cloturee.getStatut()).isEqualTo(vd.affsn.sentinelle.grpc.v1.StatutRupture.STATUT_RUPTURE_CLOTUREE);
        assertThat(jdbc.queryForList("SELECT type_evenement FROM journal_audit WHERE rupture_id = ? ORDER BY sequence",
                String.class, UUID.fromString(r.getId()))).containsExactly("DECLARATION", "CLOTURE");
    }

    @Test
    void apresDesEchecsLeJournalResteIntegreEtLesOperationsNeSontPasBloquees() {
        audit.mode(AuditPilotable.Mode.ECHEC_APRES_ECRITURE);
        for (int i = 0; i < 3; i++) {
            erreurGrpc(() -> grpc(jeton201()).declarerRupture(declarationSimple("VMP-000806")));
        }
        audit.mode(AuditPilotable.Mode.NORMAL);

        long debut = System.nanoTime();
        assertOperationNormaleReussit("VMP-000807");
        assertOperationNormaleReussit("VMP-000808");
        // bien en deçà du délai d'attente du verrou (2 s) : aucune attente sur un verrou oublié
        assertThat((System.nanoTime() - debut) / 1_000_000).isLessThan(2_000);

        assertThat(compterRupturesProduit("VMP-000806")).isZero();
        assertThat(verificateur.verifier().integre()).isTrue();
    }

    /**
     * Deux mises à jour concurrentes de la même rupture : la seconde à committer est refusée par le
     * verrou optimiste (aucune mise à jour perdue, pas d'audit, verrou relâché) et le client reçoit
     * ABORTED (conflit normal, réessayable) avec un message fixe ; aucune ligne ERROR n'est journalisée.
     */
    @Test
    void conflitDeMisesAJourConcurrentesAucuneMiseAJourPerdueEtCodeAborted() throws Exception {
        Rupture r = grpc(jeton201()).declarerRupture(declarationSimple("VMP-000901", Region.REGION_HAUTE_PAPERASSE))
                .getRupture();
        var journaux = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        journaux.start();
        var loggerRacine = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                org.slf4j.Logger.ROOT_LOGGER_NAME);
        loggerRacine.addAppender(journaux);
        var verrous = audit.suspendreLeProchainAppel();
        var executeur = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            String jeton = jeton201();
            // Premier écrivain : a relu la version initiale, s'arrête juste avant d'auditer.
            var premier = executeur.submit(() -> grpc(jeton).mettreAJourRupture(MettreAJourRuptureRequest.newBuilder()
                    .setId(r.getId()).setMotif(MotifRupture.MOTIF_RUPTURE_DEMANDE)
                    .addRegions(Region.REGION_BASSE_VALIDATION).build()));
            assertThat(verrous[0].await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

            // Second écrivain : passe et committe pendant ce temps.
            Rupture second = grpc(jeton201()).mettreAJourRupture(MettreAJourRuptureRequest.newBuilder()
                    .setId(r.getId()).setMotif(MotifRupture.MOTIF_RUPTURE_LOGISTIQUE)
                    .addRegions(Region.REGION_MARAIS_DE_LA_RECETTE).build()).getRupture();
            verrous[1].countDown();

            java.util.concurrent.ExecutionException echec = org.assertj.core.api.Assertions.catchThrowableOfType(
                    () -> premier.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    java.util.concurrent.ExecutionException.class);
            assertThat(echec).isNotNull();
            assertThat(echec.getCause()).isInstanceOf(StatusRuntimeException.class);
            Status statut = ((StatusRuntimeException) echec.getCause()).getStatus();
            assertThat(statut.getCode()).isEqualTo(Status.Code.ABORTED);
            assertThat(statut.getDescription()).isEqualTo("Conflit de modification, veuillez réessayer");
            assertThat(statut.getCause()).isNull();
            assertThat(journaux.list.stream().filter(e -> e.getLevel() == ch.qos.logback.classic.Level.ERROR))
                    .isEmpty();

            assertThat(second.getRegionsList()).containsExactly(Region.REGION_MARAIS_DE_LA_RECETTE);
            var detail = getOk("/api/v1/ruptures/" + r.getId(), jetons.pharmacien());
            assertThat(detail.get("motif").asText()).isEqualTo("LOGISTIQUE");
            assertThat(textes(detail.get("regions"))).containsExactly("MARAIS_DE_LA_RECETTE");
            assertThat(jdbc.queryForList("SELECT type_evenement FROM journal_audit WHERE rupture_id = ? ORDER BY sequence",
                    String.class, UUID.fromString(r.getId()))).containsExactly("DECLARATION", "MODIFICATION");
            assertVerrouLibre();
            assertThat(verificateur.verifier().integre()).isTrue();
        } finally {
            loggerRacine.detachAppender(journaux);
            verrous[1].countDown();
            executeur.shutdownNow();
        }
    }

    // ====================================================================== outillage

    private void assertOperationNormaleReussit(String produit) {
        long journal = compterJournal();
        Rupture r = grpc(jeton201()).declarerRupture(declarationSimple(produit)).getRupture();
        assertThat(compterRupturesProduit(produit)).isEqualTo(1);
        assertThat(compterJournal()).isEqualTo(journal + 1);
        assertThat(getOk("/api/v1/ruptures/" + r.getId(), jetons.pharmacien()).get("statut").asText())
                .isEqualTo("EN_COURS");
        assertVerrouLibre();
    }

    private static List<String> textes(com.fasterxml.jackson.databind.JsonNode tableau) {
        List<String> valeurs = new java.util.ArrayList<>();
        tableau.forEach(n -> valeurs.add(n.asText()));
        return valeurs;
    }

    private Map<String, Object> ligneRupture(String id) {
        return jdbc.queryForMap("SELECT * FROM ruptures WHERE id = ?", UUID.fromString(id));
    }

    /** Le verrou de chaîne de l'adaptateur réel n'est tenu par personne (lecture par réflexion, test uniquement). */
    private void assertVerrouLibre() {
        Object cible = AopTestUtils.getUltimateTargetObject(adaptateurReel);
        ReentrantLock verrou = (ReentrantLock) ReflectionTestUtils.getField(cible, "verrouChaine");
        assertThat(verrou).isNotNull();
        assertThat(verrou.isLocked()).isFalse();
    }
}
