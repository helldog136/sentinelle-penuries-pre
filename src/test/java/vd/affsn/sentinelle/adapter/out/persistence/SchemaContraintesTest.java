package vd.affsn.sentinelle.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Exerce les CHECK et FK de V1 par de vrais INSERT SQL (chaque test est annulé en fin de transaction). */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SchemaContraintesTest {

    private static final String INSERT = "INSERT INTO ruptures (id, code_produit, declarant, date_debut, "
            + "date_fin_estimee, date_fin_effective, motif, statut, cree_le, modifie_le) "
            + "VALUES (?, ?, ?, CAST(? AS DATE), CAST(? AS DATE), CAST(? AS DATE), ?, ?, "
            + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

    @Autowired
    private JdbcTemplate jdbc;

    private void inserer(String code, String declarant, String debut, String finEstimee, String finEffective,
                         String motif, String statut) {
        jdbc.update(INSERT, UUID.randomUUID(), code, declarant, debut, finEstimee, finEffective, motif, statut);
    }

    private UUID insererValide() {
        UUID id = UUID.randomUUID();
        jdbc.update(INSERT, id, "VMP-000001", "PRO-000001", "2026-05-01", null, null, "PRODUCTION", "EN_COURS");
        return id;
    }

    @Test
    void insertValideAccepte() {
        UUID id = insererValide();
        assertThat(jdbc.queryForObject("SELECT version FROM ruptures WHERE id = ?", Long.class, id)).isZero();
        inserer("VMP-000001", "PRO-000001", "2026-05-01", "2026-05-01", "2026-05-01", "DEMANDE", "CLOTUREE");
    }

    @Test
    void enCoursAvecFinEffectiveRefuse() {
        assertThatThrownBy(() -> inserer("VMP-000001", "PRO-000001", "2026-05-01", null, "2026-05-02",
                "PRODUCTION", "EN_COURS")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void clotureeSansFinEffectiveRefusee() {
        assertThatThrownBy(() -> inserer("VMP-000001", "PRO-000001", "2026-05-01", null, null,
                "PRODUCTION", "CLOTUREE")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void finEffectiveAvantDebutRefusee() {
        assertThatThrownBy(() -> inserer("VMP-000001", "PRO-000001", "2026-05-10", null, "2026-05-09",
                "PRODUCTION", "CLOTUREE")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void finEstimeeAvantDebutRefusee() {
        assertThatThrownBy(() -> inserer("VMP-000001", "PRO-000001", "2026-05-10", "2026-05-09", null,
                "PRODUCTION", "EN_COURS")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void statutMotifEtPrefixesInvalidesRefuses() {
        assertThatThrownBy(() -> inserer("VMP-000001", "PRO-000001", "2026-05-01", null, null,
                "PRODUCTION", "ARCHIVEE")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserer("VMP-000001", "PRO-000001", "2026-05-01", null, null,
                "INCONNU", "EN_COURS")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserer("XYZ-000001", "PRO-000001", "2026-05-01", null, null,
                "PRODUCTION", "EN_COURS")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserer("VMP-000001", "XYZ-000001", "2026-05-01", null, null,
                "PRODUCTION", "EN_COURS")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void champsObligatoiresRefuses() {
        assertThatThrownBy(() -> inserer(null, "PRO-000001", "2026-05-01", null, null, "PRODUCTION", "EN_COURS"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void regionInconnueOuRuptureOrphelineRefusees() {
        UUID id = insererValide();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO rupture_regions (rupture_id, region) VALUES (?, ?)",
                id, "ATLANTIDE")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO rupture_regions (rupture_id, region) VALUES (?, ?)",
                UUID.randomUUID(), "HAUTE_PAPERASSE")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO rupture_alternatives (rupture_id, produit_alternatif) VALUES (?, ?)",
                UUID.randomUUID(), "VMP-000002")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void clePrimaireEmpecheLesDoublons() {
        UUID id = insererValide();
        jdbc.update("INSERT INTO rupture_regions (rupture_id, region) VALUES (?, ?)", id, "HAUTE_PAPERASSE");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO rupture_regions (rupture_id, region) VALUES (?, ?)",
                id, "HAUTE_PAPERASSE")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void suppressionEnCascade() {
        UUID id = insererValide();
        jdbc.update("INSERT INTO rupture_regions (rupture_id, region) VALUES (?, ?)", id, "HAUTE_PAPERASSE");
        jdbc.update("INSERT INTO rupture_regions (rupture_id, region) VALUES (?, ?)", id, "COTE_DU_TIMEOUT");
        jdbc.update("INSERT INTO rupture_alternatives (rupture_id, produit_alternatif) VALUES (?, ?)",
                id, "VMP-000002");

        assertThat(jdbc.update("DELETE FROM ruptures WHERE id = ?", id)).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rupture_regions WHERE rupture_id = ?", Long.class, id))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rupture_alternatives WHERE rupture_id = ?",
                Long.class, id)).isZero();
    }
}
