package vd.affsn.sentinelle.adapter.out.persistence;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.IdentifiantDistributeur;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.domain.model.StatutRupture;

/** Fabriques de ruptures fictives pour les tests de persistance. */
final class RuptureFixtures {

    static final IdentifiantDistributeur DECLARANT = IdentifiantDistributeur.de("PRO-000001");

    private RuptureFixtures() {
    }

    static Rupture enCours(String produit, LocalDate debut, Set<Region> regions) {
        return Rupture.reconstituer(RuptureId.generer(), CodeProduit.de(produit), DECLARANT,
                Periode.aPartirDu(debut), Motif.PRODUCTION, regions, List.of(), StatutRupture.EN_COURS, null);
    }

    static Rupture enCours(String produit, LocalDate debut, Region region) {
        return enCours(produit, debut, EnumSet.of(region));
    }

    static Rupture cloturee(String produit, LocalDate debut, LocalDate fin, Region region) {
        return Rupture.reconstituer(RuptureId.generer(), CodeProduit.de(produit), DECLARANT,
                Periode.aPartirDu(debut), Motif.LOGISTIQUE, EnumSet.of(region), List.of(),
                StatutRupture.CLOTUREE, fin);
    }

    static Rupture complete(RuptureId id) {
        return Rupture.reconstituer(id, CodeProduit.de("VMP-000100"), DECLARANT,
                Periode.entre(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 30)), Motif.REGLEMENTAIRE,
                EnumSet.allOf(Region.class),
                List.of(new Alternative(CodeProduit.de("VMP-000200"), "Équivalent générique"),
                        Alternative.sansCommentaire(CodeProduit.de("VMP-000300")),
                        new Alternative(CodeProduit.de("VMP-000150"), "Dosage supérieur, à fractionner")),
                StatutRupture.EN_COURS, null);
    }
}
