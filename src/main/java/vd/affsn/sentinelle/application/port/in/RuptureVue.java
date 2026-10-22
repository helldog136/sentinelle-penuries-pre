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
                         StatutRupture statut,
                         boolean essentiel) {

    /** Constructeur de compatibilité : produit non essentiel. */
    public RuptureVue(RuptureId id, CodeProduit produit, IdentifiantDistributeur declarant, Periode periode,
                      Optional<LocalDate> finEffective, Motif motif, Set<Region> regions,
                      List<Alternative> alternatives, StatutRupture statut) {
        this(id, produit, declarant, periode, finEffective, motif, regions, alternatives, statut, false);
    }

    public static RuptureVue depuis(Rupture rupture, boolean essentiel) {
        return new RuptureVue(rupture.id(), rupture.produit(), rupture.declarant(), rupture.periode(),
                rupture.finEffective(), rupture.motif(), rupture.regions(), rupture.alternatives(),
                rupture.statut(), essentiel);
    }

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
                rupture.statut(),
                false);
    }
}
