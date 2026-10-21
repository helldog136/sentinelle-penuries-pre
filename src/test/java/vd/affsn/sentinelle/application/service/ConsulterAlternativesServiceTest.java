package vd.affsn.sentinelle.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static vd.affsn.sentinelle.domain.Fixtures.ACTEUR_A;
import static vd.affsn.sentinelle.domain.Fixtures.PHARMACIEN;
import static vd.affsn.sentinelle.domain.Fixtures.PRODUIT;
import static vd.affsn.sentinelle.domain.Fixtures.PRODUIT_ALT_1;
import static vd.affsn.sentinelle.domain.Fixtures.PRODUIT_ALT_2;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import vd.affsn.sentinelle.application.port.in.ConsulterAlternativesUseCase.AlternativeDeRupture;
import vd.affsn.sentinelle.application.port.in.ConsulterAlternativesUseCase.Resultat;
import vd.affsn.sentinelle.application.service.Fakes.RepositoryMemoire;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;

class ConsulterAlternativesServiceTest {

    private static Rupture rupture(RuptureId id, CodeProduit produit, Alternative... alternatives) {
        return Rupture.declarer(id, produit, ACTEUR_A, Periode.aPartirDu(LocalDate.of(2026, 10, 1)),
                Motif.PRODUCTION, EnumSet.of(Region.HAUTE_PAPERASSE), List.of(alternatives));
    }

    private static RuptureId id(String uuid) {
        return new RuptureId(UUID.fromString(uuid));
    }

    @Test
    void agrege_les_alternatives_des_ruptures_en_cours_du_produit() {
        RuptureId id = RuptureId.generer();
        Rupture r = rupture(id, PRODUIT, new Alternative(PRODUIT_ALT_1, "dosage"), Alternative.sansCommentaire(PRODUIT_ALT_2));
        ConsulterAlternativesService service = new ConsulterAlternativesService(new RepositoryMemoire().avec(r));

        Resultat res = service.consulter(PHARMACIEN, PRODUIT);

        assertThat(res.produit()).isEqualTo(PRODUIT);
        assertThat(res.alternatives()).containsExactly(
                new AlternativeDeRupture(id, Alternative.sansCommentaire(PRODUIT_ALT_2)),
                new AlternativeDeRupture(id, new Alternative(PRODUIT_ALT_1, "dosage")));
    }

    @Test
    void trie_par_code_alternatif_puis_par_forme_textuelle_de_l_uuid() {
        // Ces deux UUID s'ordonnent différemment avec UUID.compareTo (octet de poids fort signé)
        // et avec la forme textuelle : 8... est négatif en long signé, donc avant 1... en compareTo.
        RuptureId idTexteApres = id("80000000-0000-0000-0000-000000000001");
        RuptureId idTexteAvant = id("10000000-0000-0000-0000-000000000001");
        assertThat(idTexteApres.valeur().compareTo(idTexteAvant.valeur())).isNegative();
        assertThat(idTexteApres.toString().compareTo(idTexteAvant.toString())).isPositive();

        Rupture r1 = rupture(idTexteApres, PRODUIT, new Alternative(PRODUIT_ALT_1, "a"),
                new Alternative(PRODUIT_ALT_2, "b"));
        Rupture r2 = rupture(idTexteAvant, PRODUIT, new Alternative(PRODUIT_ALT_1, "c"));
        ConsulterAlternativesService service = new ConsulterAlternativesService(new RepositoryMemoire().avec(r1, r2));

        Resultat res = service.consulter(PHARMACIEN, PRODUIT);

        assertThat(res.alternatives()).containsExactly(
                new AlternativeDeRupture(idTexteApres, new Alternative(PRODUIT_ALT_2, "b")),   // VMP-000777
                new AlternativeDeRupture(idTexteAvant, new Alternative(PRODUIT_ALT_1, "c")),   // VMP-654321
                new AlternativeDeRupture(idTexteApres, new Alternative(PRODUIT_ALT_1, "a")));
    }

    @Test
    void meme_alternative_citee_par_deux_ruptures_apparait_une_fois_par_rupture() {
        RuptureId a = id("00000000-0000-0000-0000-00000000000a");
        RuptureId b = id("00000000-0000-0000-0000-00000000000b");
        ConsulterAlternativesService service = new ConsulterAlternativesService(new RepositoryMemoire().avec(
                rupture(b, PRODUIT, new Alternative(PRODUIT_ALT_1, "second")),
                rupture(a, PRODUIT, new Alternative(PRODUIT_ALT_1, "premier"))));

        Resultat res = service.consulter(PHARMACIEN, PRODUIT);

        assertThat(res.alternatives()).containsExactly(
                new AlternativeDeRupture(a, new Alternative(PRODUIT_ALT_1, "premier")),
                new AlternativeDeRupture(b, new Alternative(PRODUIT_ALT_1, "second")));
    }

    @Test
    void sans_doublon_meme_si_le_repository_renvoie_deux_fois_la_meme_rupture() {
        RuptureId id = RuptureId.generer();
        Rupture r = rupture(id, PRODUIT, new Alternative(PRODUIT_ALT_1, "x"));
        RepositoryMemoire repo = new RepositoryMemoire() {
            @Override
            public List<Rupture> findEnCoursByProduit(CodeProduit produit) {
                return List.of(r, r);
            }
        };
        ConsulterAlternativesService service = new ConsulterAlternativesService(repo);

        assertThat(service.consulter(PHARMACIEN, PRODUIT).alternatives())
                .containsExactly(new AlternativeDeRupture(id, new Alternative(PRODUIT_ALT_1, "x")));
    }

    @Test
    void ignore_les_ruptures_cloturees_et_les_autres_produits() {
        Rupture cloturee = rupture(RuptureId.generer(), PRODUIT, new Alternative(PRODUIT_ALT_1, "x"))
                .cloturer(ACTEUR_A, LocalDate.of(2026, 10, 5));
        Rupture autre = rupture(RuptureId.generer(), PRODUIT_ALT_1, new Alternative(PRODUIT_ALT_2, "y"));
        ConsulterAlternativesService service = new ConsulterAlternativesService(
                new RepositoryMemoire().avec(cloturee, autre));

        assertThat(service.consulter(PHARMACIEN, PRODUIT).alternatives()).isEmpty();
    }

    @Test
    void liste_vide_sans_rupture_ni_erreur() {
        ConsulterAlternativesService service = new ConsulterAlternativesService(new RepositoryMemoire());

        Resultat res = service.consulter(PHARMACIEN, PRODUIT);

        assertThat(res.produit()).isEqualTo(PRODUIT);
        assertThat(res.alternatives()).isEmpty();
    }

    @Test
    void rupture_sans_alternative_ne_produit_aucune_entree() {
        ConsulterAlternativesService service = new ConsulterAlternativesService(
                new RepositoryMemoire().avec(rupture(RuptureId.generer(), PRODUIT)));

        assertThat(service.consulter(PHARMACIEN, PRODUIT).alternatives()).isEmpty();
    }
}
