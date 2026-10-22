package vd.affsn.sentinelle.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import vd.affsn.sentinelle.application.port.out.ReferentielProduitsPort;
import vd.affsn.sentinelle.domain.model.CodeProduit;

/** ADR-004 AC-43, AC-66 et AC-67 : adaptateur de lecture seule du référentiel (H2, schéma Flyway V4). */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ReferentielProduitsJpaAdapter.class)
class ReferentielProduitsJpaAdapterTest {

    private static final CodeProduit ESSENTIEL = CodeProduit.de("VMP-100001");
    private static final CodeProduit ABSENT = CodeProduit.de("VMP-200002");

    @Autowired
    private ReferentielProduitsJpaAdapter adaptateur;
    @Autowired
    private JdbcTemplate jdbc;

    private void inscrire(String code) {
        jdbc.update("INSERT INTO produits_essentiels (code_produit, depuis) VALUES (?, ?)",
                code, Date.valueOf(LocalDate.of(2026, 1, 1)));
    }

    private int compter() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM produits_essentiels", Integer.class);
    }

    @Test
    void produitPresentAuReferentielEstEssentiel() {
        inscrire("VMP-100001");

        assertThat(adaptateur.estEssentiel(ESSENTIEL)).isTrue();
    }

    @Test
    void produitAbsentDuReferentielNEstPasEssentiel() {
        inscrire("VMP-100001");

        assertThat(adaptateur.estEssentiel(ABSENT)).isFalse();
    }

    @Test
    void referentielVideAucunProduitEssentiel() {
        assertThat(adaptateur.estEssentiel(ESSENTIEL)).isFalse();
    }

    @Test
    void laRechercheEstExacteEtNeTraitePasLeCodeCommeUnMotif() {
        inscrire("VMP-100001");

        assertThat(adaptateur.estEssentiel(CodeProduit.de("VMP-100002"))).isFalse();
        assertThat(adaptateur.estEssentiel(CodeProduit.de("VMP-000001"))).isFalse();
    }

    @Test
    void retraitDuReferentielEstVisibleImmediatement_sansCache() {
        inscrire("VMP-100001");
        assertThat(adaptateur.estEssentiel(ESSENTIEL)).isTrue();

        jdbc.update("DELETE FROM produits_essentiels WHERE code_produit = ?", "VMP-100001");

        assertThat(adaptateur.estEssentiel(ESSENTIEL)).isFalse();
    }

    @Test
    void ajoutAuReferentielEstVisibleImmediatement_sansCache() {
        assertThat(adaptateur.estEssentiel(ESSENTIEL)).isFalse();

        inscrire("VMP-100001");

        assertThat(adaptateur.estEssentiel(ESSENTIEL)).isTrue();
    }

    @Test
    void lectureSeule_aucuneLigneCreeeNiModifieeNiSupprimee() {
        inscrire("VMP-100001");
        int avant = compter();

        adaptateur.estEssentiel(ESSENTIEL);
        adaptateur.estEssentiel(ABSENT);

        assertThat(compter()).isEqualTo(avant);
        assertThat(jdbc.queryForList("SELECT code_produit FROM produits_essentiels", String.class))
                .containsExactly("VMP-100001");
    }

    @Test
    void lectureSeule_uneSeuleMethodePublique_etTransactionLectureSeule() throws Exception {
        List<Method> publiques = Arrays.stream(ReferentielProduitsJpaAdapter.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic())
                .toList();

        assertThat(publiques).extracting(Method::getName).containsExactly("estEssentiel");
        Transactional transactional = ReferentielProduitsJpaAdapter.class
                .getMethod("estEssentiel", CodeProduit.class).getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    void implementeLePortDuReferentiel() {
        assertThat(ReferentielProduitsPort.class).isAssignableFrom(ReferentielProduitsJpaAdapter.class);
    }
}
