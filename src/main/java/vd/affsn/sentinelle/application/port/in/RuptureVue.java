package vd.affsn.sentinelle.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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

/** Vue en lecture d'une rupture, renvoyée par les cas d'usage. */
public record RuptureVue(RuptureId id,
                         CodeProduit produit,
                         IdentifiantDistributeur declarant,
                         Periode periode,
                         Optional<LocalDate> finEffective,
                         Motif motif,
                         Set<Region> regions,
                         List<Alternative> alternatives,
                         StatutRupture statut) {

    public static RuptureVue depuis(Rupture rupture) {
        return new RuptureVue(
                rupture.id(),
                rupture.produit(),
                rupture.declarant(),
                rupture.periode(),
                rupture.finEffective(),
                rupture.motif(),
                rupture.regions(),
                rupture.alternatives(),
                rupture.statut());
    }
}
