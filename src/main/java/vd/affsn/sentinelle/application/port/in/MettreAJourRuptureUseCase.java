package vd.affsn.sentinelle.application.port.in;

import java.util.List;
import java.util.Set;

import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.RuptureId;

/**
 * Mise à jour (remplacement complet de période, motif, régions, alternatives) d'une rupture
 * en cours par son déclarant. Produit un événement d'audit MODIFICATION.
 *
 * @throws vd.affsn.sentinelle.domain.exception.RuptureIntrouvableException
 * @throws vd.affsn.sentinelle.domain.exception.AccesRefuseException
 * @throws vd.affsn.sentinelle.domain.exception.RuptureDejaClotureeException
 * @throws vd.affsn.sentinelle.domain.exception.ValidationMetierException
 */
public interface MettreAJourRuptureUseCase {

    RuptureVue mettreAJour(Acteur acteur, Commande commande);

    record Commande(RuptureId ruptureId,
                    Periode periode,
                    Motif motif,
                    Set<Region> regions,
                    List<Alternative> alternatives) {
    }
}
