package vd.affsn.sentinelle.adapter.out.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import vd.affsn.sentinelle.adapter.out.audit.ResultatVerificationIntegrite.NatureRupture;

/**
 * Vérification d'intégrité du journal d'audit (ADR-003), hors port : relit {@code journal_audit}
 * par séquence croissante (pagination par clé, mémoire constante), recalcule chaque
 * {@code hash_courant} et contrôle le chaînage. S'arrête à la <b>première</b> rupture constatée.
 *
 * <p>Pour chaque entrée, dans l'ordre :
 * <ol>
 *   <li>recalcul du hash à partir des valeurs stockées (format de {@link CanonisationAudit}) ;
 *       différent de {@code hash_courant} → {@link NatureRupture#HASH_RECALCULE_DIFFERENT} ;</li>
 *   <li>{@code hash_precedent} doit valoir le {@code hash_courant} de l'entrée relue juste avant
 *       (la genèse configurée pour la première) ; sinon : s'il vaut la genèse ou le hash d'une
 *       entrée de séquence inférieure → {@link NatureRupture#FOURCHE}, sinon
 *       {@link NatureRupture#CHAINAGE_ROMPU}.</li>
 * </ol>
 *
 * <p>Les trous de séquence ne sont pas une rupture : une transaction annulée consomme une valeur
 * d'identité. Une suppression est détectée par le chaînage, pas par la numérotation.
 *
 * <p><b>Limite</b> : la suppression des dernières entrées (troncature de la queue) ou la réécriture
 * complète et cohérente de la chaîne par quelqu'un qui a un accès en écriture à la base ne sont
 * pas détectables par ce seul recalcul ; il faut ancrer hors base le dernier hash vérifié
 * ({@link ResultatVerificationIntegrite#dernierHashVerifie()}).
 *
 * <p>Ce composant ne fait que des lectures.
 */
@Component
public class VerificateurIntegriteJournalAudit {

    private static final String SQL_PAGE =
            "SELECT sequence, evenement_id, type_evenement, rupture_id, acteur_sujet, acteur_role,"
                    + " instant, resume, hash_precedent, hash_courant"
                    + " FROM journal_audit WHERE sequence > :apres ORDER BY sequence LIMIT :taille";

    private static final String SQL_PREDECESSEUR_ANTERIEUR =
            "SELECT COUNT(*) FROM journal_audit WHERE hash_courant = :hash AND sequence < :sequence";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditProperties proprietes;

    public VerificateurIntegriteJournalAudit(NamedParameterJdbcTemplate jdbc, AuditProperties proprietes) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.proprietes = Objects.requireNonNull(proprietes, "proprietes");
    }

    /** Relit tout le journal et recalcule la chaîne ; voir la documentation de la classe. */
    @Transactional(readOnly = true)
    public ResultatVerificationIntegrite verifier() {
        String genese = proprietes.hashGenese();
        String hashAttendu = genese;
        Long derniereSequence = null;
        String dernierHash = null;
        long verifiees = 0;
        long apres = Long.MIN_VALUE;

        while (true) {
            List<Ligne> page = jdbc.query(SQL_PAGE,
                    new MapSqlParameterSource()
                            .addValue("apres", apres)
                            .addValue("taille", proprietes.taillePageVerification()),
                    (rs, i) -> Ligne.lire(rs));
            if (page.isEmpty()) {
                return ResultatVerificationIntegrite.integre(verifiees, derniereSequence, dernierHash);
            }
            for (Ligne ligne : page) {
                String recalcule = CanonisationAudit.hacher(
                        ligne.evenementId(), ligne.type(), ligne.ruptureId(), ligne.sujet(), ligne.role(),
                        ligne.instantCanonique(), ligne.resume(), ligne.hashPrecedent());
                if (!recalcule.equals(ligne.hashCourant())) {
                    return ResultatVerificationIntegrite.rompu(verifiees, ligne.sequence(),
                            NatureRupture.HASH_RECALCULE_DIFFERENT, derniereSequence, dernierHash);
                }
                if (!hashAttendu.equals(ligne.hashPrecedent())) {
                    return ResultatVerificationIntegrite.rompu(verifiees, ligne.sequence(),
                            natureDuChainageInattendu(ligne, genese), derniereSequence, dernierHash);
                }
                verifiees++;
                hashAttendu = ligne.hashCourant();
                derniereSequence = ligne.sequence();
                dernierHash = ligne.hashCourant();
                apres = ligne.sequence();
            }
        }
    }

    private NatureRupture natureDuChainageInattendu(Ligne ligne, String genese) {
        if (genese.equals(ligne.hashPrecedent())) {
            return NatureRupture.FOURCHE; // une entrée antérieure partait déjà de la genèse
        }
        Long anterieurs = jdbc.queryForObject(SQL_PREDECESSEUR_ANTERIEUR,
                new MapSqlParameterSource()
                        .addValue("hash", ligne.hashPrecedent())
                        .addValue("sequence", ligne.sequence()),
                Long.class);
        return anterieurs != null && anterieurs > 0 ? NatureRupture.FOURCHE : NatureRupture.CHAINAGE_ROMPU;
    }

    /** Valeurs brutes d'une ligne, sans conversion vers les types du domaine (une ligne altérée ne doit pas faire échouer la lecture). */
    private record Ligne(long sequence,
                         String evenementId,
                         String type,
                         String ruptureId,
                         String sujet,
                         String role,
                         String instantCanonique,
                         String resume,
                         String hashPrecedent,
                         String hashCourant) {

        static Ligne lire(ResultSet rs) throws SQLException {
            OffsetDateTime instant = rs.getObject("instant", OffsetDateTime.class);
            return new Ligne(
                    rs.getLong("sequence"),
                    rs.getObject("evenement_id", UUID.class).toString(),
                    rs.getString("type_evenement"),
                    rs.getObject("rupture_id", UUID.class).toString(),
                    rs.getString("acteur_sujet"),
                    rs.getString("acteur_role"),
                    CanonisationAudit.formaterInstant(CanonisationAudit.tronquer(instant.toInstant())),
                    rs.getString("resume"),
                    rs.getString("hash_precedent"),
                    rs.getString("hash_courant"));
        }
    }
}
