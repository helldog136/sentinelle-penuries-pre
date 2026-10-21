package vd.affsn.sentinelle.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static vd.affsn.sentinelle.adapter.out.persistence.RuptureFixtures.cloturee;
import static vd.affsn.sentinelle.adapter.out.persistence.RuptureFixtures.enCours;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import vd.affsn.sentinelle.application.port.Page;
import vd.affsn.sentinelle.application.port.Pagination;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.domain.model.StatutRupture;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RuptureRepositoryAdapter.class)
class RuptureRepositoryAdapterTest {

    private static final LocalDate D = LocalDate.of(2026, 5, 10);

    @Autowired
    private RuptureRepositoryAdapter repository;
    @Autowired
    private EntityManager em;
    @Autowired
    private JdbcTemplate jdbc;

    private void viderCache() {
        em.flush();
        em.clear();
    }

    @Test
    void allerRetourCompletAvecSixRegionsEtAlternatives() {
        Rupture r = RuptureFixtures.complete(RuptureId.generer());
        repository.save(r);
        viderCache();

        Rupture relue = repository.findById(r.id()).orElseThrow();

        assertThat(relue.id()).isEqualTo(r.id());
        assertThat(relue.produit()).isEqualTo(r.produit());
        assertThat(relue.declarant()).isEqualTo(r.declarant());
        assertThat(relue.periode()).isEqualTo(r.periode());
        assertThat(relue.motif()).isEqualTo(Motif.REGLEMENTAIRE);
        assertThat(relue.regions()).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Region.class)).hasSize(6);
        assertThat(relue.statut()).isEqualTo(StatutRupture.EN_COURS);
        assertThat(relue.finEffective()).isEmpty();
        // Ordre non persisté (pas de colonne de position) : retour trié par produit alternatif.
        assertThat(relue.alternatives()).containsExactly(
                new Alternative(CodeProduit.de("VMP-000150"), "Dosage supérieur, à fractionner"),
                new Alternative(CodeProduit.de("VMP-000200"), "Équivalent générique"),
                Alternative.sansCommentaire(CodeProduit.de("VMP-000300")));
    }

    @Test
    void horodatagesPosesParAdaptateur() {
        Rupture r = enCours("VMP-000001", D, Region.HAUTE_PAPERASSE);
        repository.save(r);
        viderCache();
        Long nonNuls = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ruptures WHERE id = ? AND cree_le IS NOT NULL AND modifie_le IS NOT NULL",
                Long.class, r.id().valeur());
        assertThat(nonNuls).isEqualTo(1L);
    }

    @Test
    void findByIdInconnuEstVide() {
        assertThat(repository.findById(RuptureId.generer())).isEmpty();
    }

    @Test
    void miseAJourRemplaceTout() {
        RuptureId id = RuptureId.generer();
        repository.save(RuptureFixtures.complete(id));
        viderCache();

        Rupture modifiee = Rupture.reconstituer(id, CodeProduit.de("VMP-000100"), RuptureFixtures.DECLARANT,
                Periode.aPartirDu(LocalDate.of(2026, 4, 1)), Motif.DEMANDE,
                EnumSet.of(Region.COTE_DU_TIMEOUT),
                List.of(Alternative.sansCommentaire(CodeProduit.de("VMP-000999"))),
                StatutRupture.EN_COURS, null);
        Rupture retour = repository.save(modifiee);
        viderCache();

        assertThat(retour.regions()).containsExactly(Region.COTE_DU_TIMEOUT);
        Rupture relue = repository.findById(id).orElseThrow();
        assertThat(relue.motif()).isEqualTo(Motif.DEMANDE);
        assertThat(relue.periode()).isEqualTo(Periode.aPartirDu(LocalDate.of(2026, 4, 1)));
        assertThat(relue.regions()).containsExactly(Region.COTE_DU_TIMEOUT);
        assertThat(relue.alternatives()).containsExactly(Alternative.sansCommentaire(CodeProduit.de("VMP-000999")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rupture_regions WHERE rupture_id = ?", Long.class,
                id.valeur())).isEqualTo(1L);
    }

    @Test
    void clotureEstPersistee() {
        Rupture r = enCours("VMP-000002", D, Region.BASSE_VALIDATION);
        repository.save(r);
        viderCache();
        Rupture close = Rupture.reconstituer(r.id(), r.produit(), r.declarant(), r.periode(), r.motif(),
                r.regions(), r.alternatives(), StatutRupture.CLOTUREE, D.plusDays(5));
        repository.save(close);
        viderCache();

        Rupture relue = repository.findById(r.id()).orElseThrow();
        assertThat(relue.statut()).isEqualTo(StatutRupture.CLOTUREE);
        assertThat(relue.finEffective()).contains(D.plusDays(5));
    }

    @Test
    void rechercheExclutLesClotureesEtAppliqueLesFiltres() {
        Rupture a = enCours("VMP-000010", D, EnumSet.of(Region.HAUTE_PAPERASSE, Region.BASSE_VALIDATION));
        Rupture b = enCours("VMP-000010", D.plusDays(1), EnumSet.of(Region.BASSE_VALIDATION));
        Rupture c = enCours("VMP-000020", D.plusDays(2), EnumSet.of(Region.HAUTE_PAPERASSE));
        Rupture x = cloturee("VMP-000010", D, D.plusDays(3), Region.HAUTE_PAPERASSE);
        List.of(a, b, c, x).forEach(repository::save);
        viderCache();
        Pagination p = Pagination.premiere();

        assertThat(ids(repository.rechercherEnCours(Optional.empty(), Optional.empty(), p)))
                .containsExactly(c.id(), b.id(), a.id());
        assertThat(repository.rechercherEnCours(Optional.empty(), Optional.empty(), p).totalElements()).isEqualTo(3);

        assertThat(ids(repository.rechercherEnCours(Optional.of(Region.HAUTE_PAPERASSE), Optional.empty(), p)))
                .containsExactly(c.id(), a.id());
        assertThat(ids(repository.rechercherEnCours(Optional.empty(), Optional.of(CodeProduit.de("VMP-000010")), p)))
                .containsExactly(b.id(), a.id());
        assertThat(ids(repository.rechercherEnCours(Optional.of(Region.HAUTE_PAPERASSE),
                Optional.of(CodeProduit.de("VMP-000010")), p))).containsExactly(a.id());
        assertThat(repository.rechercherEnCours(Optional.of(Region.COTE_DU_TIMEOUT), Optional.empty(), p)
                .elements()).isEmpty();
        // Une rupture multi-régions n'est comptée qu'une fois.
        assertThat(repository.rechercherEnCours(Optional.of(Region.BASSE_VALIDATION), Optional.empty(), p)
                .totalElements()).isEqualTo(2);
    }

    @Test
    void paginationEtTriStables() {
        // Même date de début pour 5 ruptures : le tri secondaire par id garantit un ordre stable.
        List<Rupture> memeDate = java.util.stream.IntStream.range(0, 5)
                .mapToObj(i -> enCours("VMP-000030", D, Region.PLATEAU_DU_LEGACY)).toList();
        Rupture plusRecente = enCours("VMP-000030", D.plusDays(10), Region.PLATEAU_DU_LEGACY);
        Rupture plusAncienne = enCours("VMP-000030", D.minusDays(10), Region.PLATEAU_DU_LEGACY);
        memeDate.forEach(repository::save);
        repository.save(plusRecente);
        repository.save(plusAncienne);
        viderCache();

        List<RuptureId> attendu = new java.util.ArrayList<>();
        attendu.add(plusRecente.id());
        memeDate.stream().map(Rupture::id).sorted(java.util.Comparator.comparing(RuptureId::toString))
                .forEach(attendu::add);
        attendu.add(plusAncienne.id());

        List<RuptureId> collecte = new java.util.ArrayList<>();
        for (int n = 0; n < 3; n++) {
            Page<Rupture> page = repository.rechercherEnCours(Optional.empty(), Optional.empty(), new Pagination(n, 3));
            assertThat(page.totalElements()).isEqualTo(7);
            assertThat(page.totalPages()).isEqualTo(3);
            collecte.addAll(ids(page));
        }
        assertThat(collecte).containsExactlyElementsOf(attendu);
        assertThat(repository.rechercherEnCours(Optional.empty(), Optional.empty(), new Pagination(3, 3)).elements())
                .isEmpty();
    }

    @Test
    void findEnCoursByProduit() {
        Rupture a = enCours("VMP-000040", D, Region.HAUTE_PAPERASSE);
        Rupture b = enCours("VMP-000040", D.plusDays(1), Region.COTE_DU_TIMEOUT);
        repository.save(a);
        repository.save(b);
        repository.save(enCours("VMP-000041", D, Region.HAUTE_PAPERASSE));
        repository.save(cloturee("VMP-000040", D, D.plusDays(2), Region.HAUTE_PAPERASSE));
        viderCache();

        assertThat(repository.findEnCoursByProduit(CodeProduit.de("VMP-000040")))
                .extracting(Rupture::id).containsExactlyInAnyOrder(a.id(), b.id());
        assertThat(repository.findEnCoursByProduit(CodeProduit.de("VMP-000099"))).isEmpty();
    }

    @Test
    void findActivesAuRespecteLesBornes() {
        Rupture veille = enCours("VMP-000050", D.minusDays(1), Region.HAUTE_PAPERASSE);
        Rupture memeJour = enCours("VMP-000051", D, Region.HAUTE_PAPERASSE);
        Rupture lendemain = enCours("VMP-000052", D.plusDays(1), Region.HAUTE_PAPERASSE);
        Rupture close = cloturee("VMP-000053", D.minusDays(5), D.plusDays(5), Region.HAUTE_PAPERASSE);
        List.of(veille, memeJour, lendemain, close).forEach(repository::save);
        viderCache();

        assertThat(repository.findActivesAu(D)).extracting(Rupture::id)
                .containsExactlyInAnyOrder(veille.id(), memeJour.id());
        assertThat(repository.findActivesAu(D.minusDays(2))).isEmpty();
        assertThat(repository.findActivesAu(D.plusDays(1))).extracting(Rupture::id)
                .containsExactlyInAnyOrder(veille.id(), memeJour.id(), lendemain.id());
    }

    private static List<RuptureId> ids(Page<Rupture> page) {
        return page.elements().stream().map(Rupture::id).toList();
    }
}
