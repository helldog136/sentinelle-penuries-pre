package vd.affsn.sentinelle.application.port.out;

import vd.affsn.sentinelle.domain.model.CodeProduit;

/** Référentiel des produits : statut courant « essentiel » d'un produit. */
public interface ReferentielProduitsPort {

    boolean estEssentiel(CodeProduit code);
}
