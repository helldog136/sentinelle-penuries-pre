package vd.affsn.sentinelle.adapter.out.persistence;

import java.util.List;

import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.IdentifiantDistributeur;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;

/** Conversions entité de persistance <-> agrégat du domaine. */
final class RuptureMapper {

    private RuptureMapper() {
    }

    /** Reconstitue l'agrégat via {@link Rupture#reconstituer} : tous les invariants s'appliquent. */
    static Rupture versDomaine(RuptureEntity e) {
        List<Alternative> alternatives = e.getAlternatives().stream()
                .map(a -> new Alternative(CodeProduit.de(a.getProduitAlternatif()), a.getCommentaire()))
                .toList();
        return Rupture.reconstituer(
                new RuptureId(e.getId()),
                CodeProduit.de(e.getCodeProduit()),
                IdentifiantDistributeur.de(e.getDeclarant()),
                Periode.entre(e.getDateDebut(), e.getDateFinEstimee()),
                e.getMotif(),
                e.getRegions(),
                alternatives,
                e.getStatut(),
                e.getDateFinEffective());
    }

    /** Recopie l'état de l'agrégat dans l'entité (existante ou nouvelle), collections comprises. */
    static void recopier(Rupture r, RuptureEntity e) {
        e.setCodeProduit(r.produit().valeur());
        e.setDeclarant(r.declarant().valeur());
        e.setDateDebut(r.periode().debut());
        e.setDateFinEstimee(r.periode().finEstimee());
        e.setDateFinEffective(r.finEffective().orElse(null));
        e.setMotif(r.motif());
        e.setStatut(r.statut());
        // Mise à jour différentielle des collections (évite de tout supprimer / réinsérer pour les régions).
        e.getRegions().retainAll(r.regions());
        e.getRegions().addAll(r.regions());
        List<AlternativeEmbeddable> voulues = r.alternatives().stream()
                .map(a -> new AlternativeEmbeddable(a.produit().valeur(), a.commentaire()))
                .toList();
        // L'ordre rechargé est trié par produit (pas de colonne de position) : comparaison sans ordre.
        if (e.getAlternatives().size() != voulues.size() || !e.getAlternatives().containsAll(voulues)) {
            e.getAlternatives().clear();
            e.getAlternatives().addAll(voulues);
        }
    }
}
