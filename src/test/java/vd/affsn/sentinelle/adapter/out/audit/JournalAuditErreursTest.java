package vd.affsn.sentinelle.adapter.out.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import vd.affsn.sentinelle.application.port.out.AuditPort;
import vd.affsn.sentinelle.application.port.out.EvenementAudit;
import vd.affsn.sentinelle.application.port.out.TypeEvenementAudit;
import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.RuptureId;

/** Erreurs, limites et vérification paginée. */
class JournalAuditErreursTest extends AuditTestSupport {

    private static final String RESUME_SENSIBLE = "VMP-987654 marqueur-unique-xyz";

    // ------------------------------------------------------------------ événement invalide

    @Test
    void evenementNullRefuseSansEcriture() {
        assertThatThrownBy(() -> auditPort.enregistrer(null)).isInstanceOf(NullPointerException.class);
        assertThat(compter()).isZero();
        assertThat(verrouDe(auditPort).isLocked()).isFalse();
    }

    @Test
    void champsObligatoiresDeLEvenement() {
        RuptureId r = RuptureId.generer();
        Acteur a = Acteur.pharmacien("p");
        assertThatThrownBy(() -> new EvenementAudit(null, r, a, INSTANT, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EvenementAudit(TypeEvenementAudit.CLOTURE, null, a, INSTANT, "x"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EvenementAudit(TypeEvenementAudit.CLOTURE, r, null, INSTANT, "x"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EvenementAudit(TypeEvenementAudit.CLOTURE, r, a, null, "x"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EvenementAudit(TypeEvenementAudit.CLOTURE, r, a, INSTANT, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void resumeEnormeOuMultiligneRefuseParLeType() {
        assertThatThrownBy(() -> evenement("a".repeat(501))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evenement("a\nb")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evenement("a\u0085b")).isInstanceOf(IllegalArgumentException.class);
        assertThat(evenement("a".repeat(500)).resume()).hasSize(500);
    }

    /**
     * Ambiguïté documentée : un résumé vide (ou blanc) n'est refusé NI par {@link EvenementAudit}
     * NI par l'adaptateur ; il est stocké comme chaîne vide (après retrait des espaces) et chaîné.
     */
    @Test
    void resumeVideOuBlancAccepteEtStockeVide_ambiguite() {
        auditPort.enregistrer(evenement(""));
        auditPort.enregistrer(evenement("   "));
        auditPort.enregistrer(evenement(" ‮"));
        assertThat(lignes()).extracting(Ligne::resume).containsExactly("", "", "");
        assertThat(verificateur.verifier().integre()).isTrue();
    }

    // ------------------------------------------------------------------ échecs d'écriture

    @Test
    void appelSansProxyNiTransactionRefuse() {
        JournalAuditJdbcAdapter brut = new JournalAuditJdbcAdapter(namedJdbc, proprietes);
        assertThatThrownBy(() -> brut.enregistrer(evenement(RESUME_SENSIBLE)))
                .isInstanceOf(EcritureAuditImpossibleException.class)
                .hasMessageContaining("synchronisation transactionnelle inactive")
                .hasMessageNotContaining("marqueur-unique-xyz");
        assertThat(compter()).isZero();
    }

    /**
     * Comportement constaté (et documenté dans le code : « Toute DataAccessException est propagée
     * telle quelle ») : un échec JDBC n'est PAS enveloppé dans {@link EcritureAuditImpossibleException}
     * mais propagé tel quel. Il n'est jamais avalé, la transaction est annulée et le verrou rendu.
     */
    @Test
    void echecJdbcALInsertionPropageTelQuelEtVerrouRendu() {
        NamedParameterJdbcTemplate jdbcEnPanne = jdbcSimule(List.of());
        DataAccessResourceFailureException panne = new DataAccessResourceFailureException("connexion perdue");
        when(jdbcEnPanne.update(anyString(), any(SqlParameterSource.class))).thenThrow(panne);
        AuditPort port = proxyTransactionnel(new JournalAuditJdbcAdapter(jdbcEnPanne, proprietes));

        assertThatThrownBy(() -> port.enregistrer(evenement(RESUME_SENSIBLE))).isSameAs(panne);
        assertThat(verrouDe(port).isLocked()).isFalse();
    }

    @Test
    void echecJdbcALaLectureDeLaDerniereEntreePropageEtVerrouRendu() {
        NamedParameterJdbcTemplate jdbcEnPanne = mock(NamedParameterJdbcTemplate.class);
        JdbcTemplate classique = mock(JdbcTemplate.class);
        when(jdbcEnPanne.getJdbcTemplate()).thenReturn(classique);
        when(classique.query(anyString(), any(RowMapper.class)))
                .thenThrow(new DataAccessResourceFailureException("base indisponible"));
        AuditPort port = proxyTransactionnel(new JournalAuditJdbcAdapter(jdbcEnPanne, proprietes));

        assertThatThrownBy(() -> port.enregistrer(evenement(RESUME_SENSIBLE)))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(verrouDe(port).isLocked()).isFalse();
    }

    @Test
    void insertionSansLigneAffecteeEnveloppeeSansDonneeDeLEvenement() {
        NamedParameterJdbcTemplate jdbcMuet = jdbcSimule(List.of());
        when(jdbcMuet.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);
        AuditPort port = proxyTransactionnel(new JournalAuditJdbcAdapter(jdbcMuet, proprietes));
        EvenementAudit e = evenement(RESUME_SENSIBLE);

        assertThatThrownBy(() -> port.enregistrer(e))
                .isInstanceOf(EcritureAuditImpossibleException.class)
                .hasMessageNotContaining("marqueur-unique-xyz")
                .hasMessageNotContaining("VMP-987654")
                .hasMessageNotContaining("agent-42")
                .hasMessageNotContaining(e.ruptureId().toString());
        assertThat(verrouDe(port).isLocked()).isFalse();
    }

    @Test
    void messagesDesExceptionsDeRefusSansDonneeDeLEvenement() {
        // Garde de séquence : message générique.
        ecrire(auditPort, 2);
        jdbc.update("DELETE FROM journal_audit WHERE sequence = ?", lignes().get(1).sequence());
        EvenementAudit e = evenement(RESUME_SENSIBLE);
        assertThatThrownBy(() -> auditPort.enregistrer(e))
                .isInstanceOf(EcritureAuditImpossibleException.class)
                .hasMessageNotContaining("marqueur-unique-xyz")
                .hasMessageNotContaining(e.ruptureId().toString())
                .hasMessageNotContaining("agent-42");
    }

    @Test
    void constructeursRefusentLesDependancesNulles() {
        assertThatThrownBy(() -> new JournalAuditJdbcAdapter(null, proprietes)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new JournalAuditJdbcAdapter(namedJdbc, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VerificateurIntegriteJournalAudit(null, proprietes))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VerificateurIntegriteJournalAudit(namedJdbc, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------ vérification

    @Test
    void journalVideIntegre() {
        ResultatVerificationIntegrite r = verificateur.verifier();
        assertThat(r.integre()).isTrue();
        assertThat(r.entreesVerifiees()).isZero();
        assertThat(r.derniereSequenceVerifiee()).isNull();
        assertThat(r.dernierHashVerifie()).isNull();
        assertThat(r.premiereRupture()).isEmpty();
    }

    @Test
    void verificationPagineeJournalPlusGrandQueLaPage() {
        ecrire(auditPort, 10);
        for (int page : new int[] {1, 3, 9, 10, 11}) {
            ResultatVerificationIntegrite r = verificateur(props(null, null, page)).verifier();
            assertThat(r.integre()).as("page %d", page).isTrue();
            assertThat(r.entreesVerifiees()).as("page %d", page).isEqualTo(10);
            assertThat(r.dernierHashVerifie()).isEqualTo(lignes().get(9).hashCourant());
        }
    }

    @Test
    void verificationPagineeDetecteUneRuptureEnDebutDePage() {
        ecrire(auditPort, 10);
        List<Ligne> l = lignes();
        jdbc.update("DELETE FROM journal_audit WHERE sequence = ?", l.get(2).sequence()); // fin de la page 1
        ResultatVerificationIntegrite r = verificateur(props(null, null, 3)).verifier();
        assertThat(r.premiereRupture()).contains(l.get(3).sequence());
        assertThat(r.natureRupture()).contains(ResultatVerificationIntegrite.NatureRupture.CHAINAGE_ROMPU);
        assertThat(r.entreesVerifiees()).isEqualTo(2);
    }

    @Test
    void resultatRefuseLesCombinaisonsIncoherentes() {
        assertThatThrownBy(() -> new ResultatVerificationIntegrite(true, 1, 3L, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResultatVerificationIntegrite(false, 1, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResultatVerificationIntegrite(false, 1, 3L, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResultatVerificationIntegrite(true, -1, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings("unchecked")
    private static NamedParameterJdbcTemplate jdbcSimule(List<Object> dernieres) {
        NamedParameterJdbcTemplate named = mock(NamedParameterJdbcTemplate.class);
        JdbcTemplate classique = mock(JdbcTemplate.class);
        when(named.getJdbcTemplate()).thenReturn(classique);
        when(classique.query(anyString(), any(RowMapper.class))).thenReturn(dernieres);
        return named;
    }
}
