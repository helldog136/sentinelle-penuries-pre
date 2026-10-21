package vd.affsn.sentinelle.adapter.out.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import vd.affsn.sentinelle.application.port.out.AuditPort;
import vd.affsn.sentinelle.application.port.out.EvenementAudit;
import vd.affsn.sentinelle.application.port.out.TypeEvenementAudit;
import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.IdentifiantDistributeur;
import vd.affsn.sentinelle.domain.model.RuptureId;

/**
 * Infrastructure commune des tests d'intégration du journal d'audit.
 *
 * <ul>
 *   <li>Contexte Spring complet (profil test, H2 mode PostgreSQL, schéma Flyway), sur une base H2
 *       <b>dédiée</b> ({@code sentinelle-audit-test}) pour ne pas partager {@code journal_audit}
 *       avec les autres classes de test.</li>
 *   <li>Transactions réelles (aucun rollback de test automatique) : chaque test part d'un journal
 *       vide. Comme l'adaptateur mémorise la dernière séquence committée (garde de séquence), vider
 *       la table sans réinitialiser cet état ferait refuser toute écriture : l'état du bean du
 *       contexte est donc remis à zéro avant chaque test (réflexion, test uniquement).</li>
 *   <li>Le hash est recalculé ici de façon INDÉPENDANTE, à partir de la seule Javadoc du format
 *       canonique v1 ({@link CanonisationAudit}), sans appeler la classe de production.</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sentinelle-audit-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
abstract class AuditTestSupport {

    static final String GENESE_DEFAUT = "0".repeat(64);

    @Autowired
    protected AuditPort auditPort;
    @Autowired
    protected VerificateurIntegriteJournalAudit verificateur;
    @Autowired
    protected PlatformTransactionManager tm;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected NamedParameterJdbcTemplate namedJdbc;
    @Autowired
    protected AuditProperties proprietes;

    @BeforeEach
    void journalVideEtEtatAdaptateurReinitialise() {
        jdbc.update("DELETE FROM journal_audit");
        JournalAuditJdbcAdapter cible = AopTestUtils.getTargetObject(auditPort);
        ((AtomicLong) ReflectionTestUtils.getField(cible, "derniereSequenceCommittee")).set(-1L);
    }

    // ---------------------------------------------------------------- fabriques

    protected TransactionTemplate tx() {
        return new TransactionTemplate(tm);
    }

    protected TransactionTemplate txRequiresNew() {
        TransactionTemplate t = new TransactionTemplate(tm);
        t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return t;
    }

    /** Nouvel adaptateur (état neuf), enveloppé dans un proxy qui applique son {@code @Transactional}. */
    protected AuditPort nouvelAdaptateur(AuditProperties props) {
        return proxyTransactionnel(new JournalAuditJdbcAdapter(namedJdbc, props));
    }

    protected AuditPort proxyTransactionnel(JournalAuditJdbcAdapter cible) {
        ProxyFactory pf = new ProxyFactory(cible);
        pf.setInterfaces(AuditPort.class);
        pf.addAdvice(new TransactionInterceptor(tm, new AnnotationTransactionAttributeSource()));
        return (AuditPort) pf.getProxy();
    }

    protected VerificateurIntegriteJournalAudit verificateur(AuditProperties props) {
        return new VerificateurIntegriteJournalAudit(namedJdbc, props);
    }

    static AuditProperties props(String genese, Duration delai, Integer page) {
        return new AuditProperties(genese, delai, page);
    }

    static ReentrantLock verrouDe(AuditPort port) {
        Object cible = AopTestUtils.getUltimateTargetObject(port);
        return (ReentrantLock) ReflectionTestUtils.getField(cible, "verrouChaine");
    }

    // ---------------------------------------------------------------- événements

    static final Instant INSTANT = Instant.parse("2026-10-21T08:15:30.123456789Z");

    static EvenementAudit evenement(String resume) {
        return new EvenementAudit(TypeEvenementAudit.DECLARATION, RuptureId.generer(),
                Acteur.distributeur("agent-42", IdentifiantDistributeur.de("PRO-000042")), INSTANT, resume);
    }

    static EvenementAudit evenement(TypeEvenementAudit type, Instant instant, String resume) {
        return new EvenementAudit(type, RuptureId.generer(), Acteur.pharmacien("pharma-7"), instant, resume);
    }

    protected void ecrire(AuditPort port, int n) {
        for (int i = 0; i < n; i++) {
            port.enregistrer(evenement("entrée " + i));
        }
    }

    // ---------------------------------------------------------------- lecture brute

    /** Ligne telle que stockée (valeurs brutes). */
    record Ligne(long sequence, UUID evenementId, String type, UUID ruptureId, String sujet, String role,
                 OffsetDateTime instant, String resume, String hashPrecedent, String hashCourant) {
    }

    protected List<Ligne> lignes() {
        return jdbc.query("SELECT * FROM journal_audit ORDER BY sequence", (rs, i) -> new Ligne(
                rs.getLong("sequence"),
                rs.getObject("evenement_id", UUID.class),
                rs.getString("type_evenement"),
                rs.getObject("rupture_id", UUID.class),
                rs.getString("acteur_sujet"),
                rs.getString("acteur_role"),
                rs.getObject("instant", OffsetDateTime.class),
                rs.getString("resume"),
                rs.getString("hash_precedent"),
                rs.getString("hash_courant")));
    }

    protected long compter() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM journal_audit", Long.class);
    }

    // ---------------------------------------------------------------- recalcul indépendant (Javadoc v1)

    /** Instant au format canonique, construit à la main (sans DateTimeFormatter) : 6 décimales, UTC, tronqué. */
    static String instantCanonique(Instant instant) {
        OffsetDateTime t = instant.atOffset(ZoneOffset.UTC);
        return String.format(java.util.Locale.ROOT, "%04d-%02d-%02dT%02d:%02d:%02d.%06dZ",
                t.getYear(), t.getMonthValue(), t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(),
                t.getNano() / 1000);
    }

    static String hashIndependant(String evenementId, String type, String ruptureId, String sujet, String role,
                                  String instant, String resume, String hashPrecedent) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update("sentinelle-audit-v1".getBytes(StandardCharsets.UTF_8));
            String[][] champs = {
                    {"evenement_id", evenementId}, {"type", type}, {"rupture_id", ruptureId}, {"sujet", sujet},
                    {"role", role}, {"instant", instant}, {"resume", resume}, {"hash_precedent", hashPrecedent}};
            for (String[] c : champs) {
                byte[] valeur = c[1].getBytes(StandardCharsets.UTF_8);
                md.update(("\n" + c[0] + "=" + valeur.length + ":").getBytes(StandardCharsets.UTF_8));
                md.update(valeur);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest()) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String hashIndependant(Ligne l) {
        return hashIndependant(l.evenementId().toString(), l.type(), l.ruptureId().toString(), l.sujet(), l.role(),
                instantCanonique(l.instant().toInstant()), l.resume(), l.hashPrecedent());
    }

    /** Insère directement une ligne forgée (valide au sens du hash) : simule un écrivain hors adaptateur. */
    protected String insererLigneForgee(String hashPrecedent, String resume) {
        UUID ev = UUID.randomUUID();
        UUID rupture = UUID.randomUUID();
        Instant instant = Instant.parse("2026-10-22T00:00:00Z");
        String h = hashIndependant(ev.toString(), "MODIFICATION", rupture.toString(), "intrus", "PHARMACIEN",
                instantCanonique(instant), resume, hashPrecedent);
        jdbc.update("INSERT INTO journal_audit (evenement_id, type_evenement, rupture_id, acteur_sujet, acteur_role,"
                        + " instant, resume, hash_precedent, hash_courant) VALUES (?,?,?,?,?,?,?,?,?)",
                ev, "MODIFICATION", rupture, "intrus", "PHARMACIEN", OffsetDateTime.ofInstant(instant, ZoneOffset.UTC),
                resume, hashPrecedent, h);
        return h;
    }
}
