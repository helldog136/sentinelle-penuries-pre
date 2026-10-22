package vd.affsn.sentinelle.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static vd.affsn.sentinelle.domain.Fixtures.PHARMACIEN;
import static vd.affsn.sentinelle.domain.Fixtures.PRODUIT_ALT_1;
import static vd.affsn.sentinelle.domain.Fixtures.ruptureCloturee;
import static vd.affsn.sentinelle.domain.Fixtures.ruptureEnCours;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vd.affsn.sentinelle.application.port.Page;
import vd.affsn.sentinelle.application.port.Pagination;
import vd.affsn.sentinelle.application.port.in.ConsulterRupturesUseCase.Critere;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.application.service.Fakes.RepositoryMemoire;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.domain.model.CodeProduit;

import static vd.affsn.sentinelle.domain.Fixtures.ACTEUR_A;

class ConsulterRupturesServiceTest {

    private Rupture ancienneHaute;
    private Rupture recenteCote;
    private Rupture moyenneDeuxRegions;
    private Rupture autreProduit;
    private Rupture cloturee;
    private ConsulterRupturesService service;

    @BeforeEach
    void init() {
        ancienneHaute = ruptureEnCours(LocalDate.of(2026, 9, 1), Region.HAUTE_PAPERASSE);
        recenteCote = ruptureEnCours(LocalDate.of(2026, 10, 15), Region.COTE_DU_TIMEOUT);
        moyenneDeuxRegions = ruptureEnCours(LocalDate.of(2026, 10, 1), Region.HAUTE_PAPERASSE,
                Region.COTE_DU_TIMEOUT);
        autreProduit = Rupture.declarer(RuptureId.generer(), PRODUIT_ALT_1, ACTEUR_A,
                Periode.aPartirDu(LocalDate.of(2026, 10, 5)), Motif.DEMANDE,
                EnumSet.of(Region.HAUTE_PAPERASSE), List.of(new Alternative(new CodeProduit("VMP-000001"), "")));
        cloturee = ruptureCloturee(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5), Region.HAUTE_PAPERASSE);
        service = new ConsulterRupturesService(new RepositoryMemoire()
                .avec(ancienneHaute, recenteCote, moyenneDeuxRegions, autreProduit, cloturee));
    }

    private static List<RuptureId> ids(Page<RuptureVue> page) {
        return page.elements().stream().map(RuptureVue::id).toList();
    }

    @Test
    void sans_filtre_liste_les_en_cours_du_plus_recent_au_plus_ancien_sans_les_cloturees() {
        Page<RuptureVue> page = service.consulter(PHARMACIEN, Critere.sansFiltre(Pagination.premiere()));

        assertThat(ids(page)).containsExactly(recenteCote.id(), autreProduit.id(),
                moyenneDeuxRegions.id(), ancienneHaute.id());
        assertThat(page.totalElements()).isEqualTo(4);
        assertThat(ids(page)).doesNotContain(cloturee.id());
    }

    @Test
    void filtre_par_region() {
        Page<RuptureVue> page = service.consulter(PHARMACIEN, new Critere(
                Optional.of(Region.COTE_DU_TIMEOUT), Optional.empty(), Pagination.premiere()));

        assertThat(ids(page)).containsExactly(recenteCote.id(), moyenneDeuxRegions.id());
        assertThat(page.totalElements()).isEqualTo(2);
    }

    @Test
    void filtre_par_produit() {
        Page<RuptureVue> page = service.consulter(PHARMACIEN, new Critere(
                Optional.empty(), Optional.of(PRODUIT_ALT_1), Pagination.premiere()));

        assertThat(ids(page)).containsExactly(autreProduit.id());
    }

    @Test
    void filtres_region_et_produit_combines() {
        Page<RuptureVue> page = service.consulter(PHARMACIEN, new Critere(
                Optional.of(Region.HAUTE_PAPERASSE), Optional.of(PRODUIT_ALT_1), Pagination.premiere()));
        Page<RuptureVue> vide = service.consulter(PHARMACIEN, new Critere(
                Optional.of(Region.PLATEAU_DU_LEGACY), Optional.of(PRODUIT_ALT_1), Pagination.premiere()));

        assertThat(ids(page)).containsExactly(autreProduit.id());
        assertThat(vide.elements()).isEmpty();
        assertThat(vide.totalElements()).isZero();
    }

    @Test
    void pagination_decoupe_en_pages_avec_total() {
        Page<RuptureVue> p0 = service.consulter(PHARMACIEN, Critere.sansFiltre(new Pagination(0, 3)));
        Page<RuptureVue> p1 = service.consulter(PHARMACIEN, Critere.sansFiltre(new Pagination(1, 3)));
        Page<RuptureVue> p2 = service.consulter(PHARMACIEN, Critere.sansFiltre(new Pagination(2, 3)));

        assertThat(p0.elements()).hasSize(3);
        assertThat(p1.elements()).hasSize(1);
        assertThat(p2.elements()).isEmpty();
        assertThat(p0.totalElements()).isEqualTo(4);
        assertThat(p0.totalPages()).isEqualTo(2);
        assertThat(p1.pagination()).isEqualTo(new Pagination(1, 3));
        assertThat(ids(p0)).doesNotContainAnyElementsOf(ids(p1));
    }

    @Test
    void les_elements_sont_des_vues_des_ruptures() {
        Page<RuptureVue> page = service.consulter(PHARMACIEN, new Critere(
                Optional.empty(), Optional.of(PRODUIT_ALT_1), Pagination.premiere()));

        assertThat(page.elements()).containsExactly(RuptureVue.depuis(autreProduit));
    }

    @Test
    void liste_vide_sans_rupture() {
        ConsulterRupturesService vide = new ConsulterRupturesService(new RepositoryMemoire());

        Page<RuptureVue> page = vide.consulter(PHARMACIEN, Critere.sansFiltre(Pagination.premiere()));

        assertThat(page.elements()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }
}
