package vd.affsn.sentinelle.application.port.in;

import java.util.List;
import java.util.Set;

import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;

/**
 * Déclaration d'une rupture par un distributeur. Le déclarant est déduit de l'acteur.
 * Produit un événement d'audit DECLARATION.
 *
 * @throws vd.affsn.sentinelle.domain.exception.AccesRefuseException si l'acteur n'est pas DISTRIBUTEUR
 * @throws vd.affsn.sentinelle.domain.exception.ValidationMetierException si un invariant est violé
 */
public interface DeclarerRuptureUseCase {

    RuptureVue declarer(Acteur acteur, Commande commande);

    record Commande(CodeProduit produit,
                    Periode periode,
                    Motif motif,
                    Set<Region> regions,
                    List<Alternative> alternatives) {
    }
}
