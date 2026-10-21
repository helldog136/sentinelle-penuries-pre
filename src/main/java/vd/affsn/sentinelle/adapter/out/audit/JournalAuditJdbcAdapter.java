package vd.affsn.sentinelle.adapter.out.audit;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import vd.affsn.sentinelle.application.port.out.AuditPort;
import vd.affsn.sentinelle.application.port.out.EvenementAudit;

/**
 * Implémentation JDBC du journal d'audit append-only chaîné (ADR-003, table {@code journal_audit}
 * de la migration V2).
 *
 * <h2>Append-only</h2>
 * L'unique écriture est un {@code INSERT}. Ce code ne contient aucun {@code UPDATE},
 * {@code DELETE} ni {@code TRUNCATE} sur {@code journal_audit} et n'utilise pas d'entité JPA.
 *
 * <h2>Transaction (décision à confirmer par le tech lead)</h2>
 * Propagation {@code REQUIRED} : l'écriture rejoint la transaction de l'opération métier
 * appelante. Une déclaration annulée n'a donc pas d'entrée d'audit, et une entrée d'audit qui
 * échoue annule l'opération (aucune opération sans trace). Appelé hors transaction, l'adaptateur
 * ouvre la sienne.
 *
 * <h2>Concurrence : choix retenu</h2>
 * Un verrou applicatif équitable ({@link ReentrantLock}) sérialise les écrivains de la chaîne.
 * Il est pris avant la lecture de la dernière entrée et <b>relâché seulement à la fin de la
 * transaction</b> (après commit ou rollback, via {@link TransactionSynchronization}) : sans cela,
 * un second écrivain pourrait lire la dernière entrée committée alors que l'entrée du premier,
 * insérée mais pas encore committée, lui est invisible, et forker la chaîne.
 *
 * <ul>
 *   <li><b>Garantit</b> : pas de fourche tant qu'<b>une seule instance</b> de l'application écrit
 *       dans la base, en isolation {@code READ COMMITTED} (défaut H2 et PostgreSQL). Un
 *       instantané périmé (isolation plus forte côté appelant) est détecté par comparaison à la
 *       dernière séquence committée connue de l'instance et refusé.</li>
 *   <li><b>Ne garantit pas</b> : l'absence de fourche avec <b>plusieurs instances</b> (le verrou
 *       est local à la JVM) tant qu'aucune contrainte {@code UNIQUE} sur {@code hash_precedent}
 *       n'existe en base. Une fourche reste alors <i>détectée</i> par la vérification d'intégrité,
 *       mais pas empêchée.</li>
 *   <li>Verrou de ligne {@code SELECT ... FOR UPDATE} sur la dernière entrée écarté : sous
 *       PostgreSQL en {@code READ COMMITTED}, l'écrivain mis en attente récupère, après le commit
 *       du premier, la même « dernière » ligne (la requête n'est pas réévaluée) et forke ; et la
 *       table vide n'offre aucune ligne à verrouiller.</li>
 *   <li>Coût : le verrou est tenu jusqu'à la fin de la transaction métier, qui est donc
 *       sérialisée avec les autres transactions auditées. Recommandation aux appelants : auditer
 *       en <b>dernière</b> opération de la transaction. Un délai d'attente borné
 *       ({@code sentinelle.audit.delai-attente-verrou}) évite un interblocage indétectable entre
 *       ce verrou et un verrou de ligne en base : il se termine en échec et en rollback.</li>
 * </ul>
 */
@Component
public class JournalAuditJdbcAdapter implements AuditPort {

    private static final String SQL_DERNIERE_ENTREE =
            "SELECT sequence, hash_courant FROM journal_audit ORDER BY sequence DESC LIMIT 1";

    private static final String SQL_INSERTION =
            "INSERT INTO journal_audit (evenement_id, type_evenement, rupture_id, acteur_sujet,"
                    + " acteur_role, instant, resume, hash_precedent, hash_courant)"
                    + " VALUES (:evenementId, :type, :ruptureId, :sujet, :role, :instant, :resume,"
                    + " :hashPrecedent, :hashCourant)";

    private static final String SQL_SEQUENCE_INSEREE =
            "SELECT sequence FROM journal_audit WHERE evenement_id = :evenementId";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditProperties proprietes;

    private final ReentrantLock verrouChaine = new ReentrantLock(true);

    /** Plus grande séquence dont le commit a été observé par cette instance (-1 : aucune). */
    private final AtomicLong derniereSequenceCommittee = new AtomicLong(-1L);

    /** Clé de ressource transactionnelle : marque « verrou déjà tenu pour CETTE transaction ». */
    private final Object cleRessource = new Object();

    public JournalAuditJdbcAdapter(NamedParameterJdbcTemplate jdbc, AuditProperties proprietes) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.proprietes = Objects.requireNonNull(proprietes, "proprietes");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void enregistrer(EvenementAudit evenement) {
        Objects.requireNonNull(evenement, "evenement");
        // Préparation (sans accès base) avant de prendre le verrou, pour le tenir le moins possible.
        String evenementId = UUID.randomUUID().toString();
        String type = evenement.type().name();
        String ruptureId = evenement.ruptureId().valeur().toString();
        String sujet = evenement.acteur().sujet();
        String role = evenement.acteur().role().name();
        Instant instant = CanonisationAudit.tronquer(evenement.instant());
        String instantCanonique = CanonisationAudit.formaterInstant(instant);
        String resume = CanonisationAudit.assainirResume(evenement.resume());

        EtatTransaction etat = verrouillerJusquaFinDeTransaction();

        String hashPrecedent = lireHashPrecedent();
        String hashCourant = CanonisationAudit.hacher(
                evenementId, type, ruptureId, sujet, role, instantCanonique, resume, hashPrecedent);

        MapSqlParameterSource parametres = new MapSqlParameterSource()
                .addValue("evenementId", UUID.fromString(evenementId))
                .addValue("type", type)
                .addValue("ruptureId", UUID.fromString(ruptureId))
                .addValue("sujet", sujet)
                .addValue("role", role)
                .addValue("instant", OffsetDateTime.ofInstant(instant, ZoneOffset.UTC))
                .addValue("resume", resume)
                .addValue("hashPrecedent", hashPrecedent)
                .addValue("hashCourant", hashCourant);

        // Toute DataAccessException est propagée telle quelle : jamais avalée.
        int lignes = jdbc.update(SQL_INSERTION, parametres);
        if (lignes != 1) {
            throw new EcritureAuditImpossibleException(
                    "Insertion d'audit : une ligne attendue, " + lignes + " obtenue(s)");
        }
        Long sequence = jdbc.queryForObject(SQL_SEQUENCE_INSEREE,
                new MapSqlParameterSource("evenementId", UUID.fromString(evenementId)), Long.class);
        etat.derniereSequenceEcrite = Objects.requireNonNull(sequence, "sequence insérée");
    }

    /** Lit le hash de la dernière entrée, ou la genèse si le journal est vide. Verrou tenu. */
    private String lireHashPrecedent() {
        List<DerniereEntree> dernieres = jdbc.getJdbcTemplate().query(SQL_DERNIERE_ENTREE,
                (rs, i) -> new DerniereEntree(rs.getLong("sequence"), rs.getString("hash_courant")));
        long connue = derniereSequenceCommittee.get();
        long lue = dernieres.isEmpty() ? -1L : dernieres.get(0).sequence();
        if (lue < connue) {
            // Instantané de lecture antérieur au dernier commit connu (isolation REPEATABLE READ ou
            // SERIALIZABLE chez l'appelant), ou queue du journal supprimée : écrire forkerait.
            throw new EcritureAuditImpossibleException(
                    "Journal d'audit : dernière entrée lue antérieure à la dernière entrée committée"
                            + " connue (instantané périmé ou journal tronqué) ; écriture refusée");
        }
        return dernieres.isEmpty() ? proprietes.hashGenese() : dernieres.get(0).hashCourant();
    }

    /**
     * Prend le verrou de la chaîne pour la transaction courante, une seule fois par transaction,
     * et programme sa libération en fin de transaction.
     */
    private EtatTransaction verrouillerJusquaFinDeTransaction() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Ne devrait pas arriver (méthode @Transactional) : refuser plutôt que risquer une fourche.
            throw new EcritureAuditImpossibleException(
                    "Journal d'audit : synchronisation transactionnelle inactive ; écriture refusée");
        }
        Object deja = TransactionSynchronizationManager.getResource(cleRessource);
        if (deja instanceof EtatTransaction etat) {
            return etat; // second audit dans la même transaction : verrou déjà tenu
        }
        if (verrouChaine.isHeldByCurrentThread()) {
            // Transaction indépendante (REQUIRES_NEW) ouverte dans un thread qui tient déjà le verrou
            // pour une transaction englobante non committée : son entrée serait invisible ici.
            throw new EcritureAuditImpossibleException(
                    "Journal d'audit : écriture depuis une transaction imbriquée alors que la"
                            + " transaction englobante a déjà audité ; écriture refusée");
        }
        boolean obtenu;
        try {
            obtenu = verrouChaine.tryLock(proprietes.delaiAttenteVerrou().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EcritureAuditImpossibleException("Journal d'audit : attente du verrou interrompue", e);
        }
        if (!obtenu) {
            throw new EcritureAuditImpossibleException(
                    "Journal d'audit : verrou de chaîne non obtenu dans le délai imparti");
        }
        EtatTransaction etat = new EtatTransaction();
        try {
            TransactionSynchronizationManager.bindResource(cleRessource, etat);
            TransactionSynchronizationManager.registerSynchronization(new LiberationVerrou(etat));
        } catch (RuntimeException e) {
            TransactionSynchronizationManager.unbindResourceIfPossible(cleRessource);
            verrouChaine.unlock();
            throw e;
        }
        return etat;
    }

    private record DerniereEntree(long sequence, String hashCourant) {
    }

    /** État propre à une transaction auditée. */
    private static final class EtatTransaction {
        private long derniereSequenceEcrite = -1L;
    }

    /** Libère le verrou après commit ou rollback, dans le thread de la transaction. */
    private final class LiberationVerrou implements TransactionSynchronization {

        private final EtatTransaction etat;

        private LiberationVerrou(EtatTransaction etat) {
            this.etat = etat;
        }

        @Override
        public void afterCompletion(int status) {
            try {
                if (status == STATUS_COMMITTED && etat.derniereSequenceEcrite >= 0) {
                    derniereSequenceCommittee.accumulateAndGet(etat.derniereSequenceEcrite, Math::max);
                }
            } finally {
                TransactionSynchronizationManager.unbindResourceIfPossible(cleRessource);
                if (verrouChaine.isHeldByCurrentThread()) {
                    verrouChaine.unlock();
                }
            }
        }
    }
}
