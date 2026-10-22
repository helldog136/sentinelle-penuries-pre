package vd.affsn.sentinelle.adapter.in.grpc;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.grpc.v1.MotifRupture;
import vd.affsn.sentinelle.grpc.v1.Region;
import vd.affsn.sentinelle.grpc.v1.Rupture;
import vd.affsn.sentinelle.grpc.v1.StatutRupture;

/** Conversions entre messages gRPC (déjà validés) et types du domaine. */
final class MappeurRuptures {

    private MappeurRuptures() {
    }

    // ---------------------------------------------------------- proto -> domaine

    static Motif motif(MotifRupture motif) {
        return Motif.valueOf(motif.name().substring("MOTIF_RUPTURE_".length()));
    }

    static Set<vd.affsn.sentinelle.domain.model.Region> regions(List<Region> regions) {
        Set<vd.affsn.sentinelle.domain.model.Region> resultat =
                EnumSet.noneOf(vd.affsn.sentinelle.domain.model.Region.class);
        for (Region region : regions) {
            resultat.add(vd.affsn.sentinelle.domain.model.Region.valueOf(
                    region.name().substring("REGION_".length())));
        }
        return resultat;
    }

    static List<Alternative> alternatives(List<vd.affsn.sentinelle.grpc.v1.Alternative> alternatives) {
        return alternatives.stream()
                .map(a -> new Alternative(CodeProduit.de(a.getCodeProduit()), a.getCommentaire()))
                .toList();
    }

    // ---------------------------------------------------------- domaine -> proto

    static Rupture rupture(RuptureVue vue) {
        Rupture.Builder rupture = Rupture.newBuilder()
                .setId(vue.id().toString())
                .setCodeProduit(vue.produit().valeur())
                .setDateDebut(vue.periode().debut().toString())
                .setMotif(MotifRupture.valueOf("MOTIF_RUPTURE_" + vue.motif().name()))
                .setStatut(StatutRupture.valueOf("STATUT_RUPTURE_" + vue.statut().name()))
                .setDistributeurId(vue.declarant().valeur());
        vue.periode().finEstimeeOptionnelle().ifPresent(d -> rupture.setDateFinEstimee(d.toString()));
        vue.finEffective().ifPresent(d -> rupture.setDateFinEffective(d.toString()));
        vue.regions().stream()
                .map(r -> Region.valueOf("REGION_" + r.name()))
                .sorted(Comparator.comparingInt(Region::getNumber))
                .forEach(rupture::addRegions);
        vue.alternatives().forEach(a -> rupture.addAlternatives(
                vd.affsn.sentinelle.grpc.v1.Alternative.newBuilder()
                        .setCodeProduit(a.produit().valeur())
                        .setCommentaire(a.commentaire())));
        return rupture.build();
    }
}
