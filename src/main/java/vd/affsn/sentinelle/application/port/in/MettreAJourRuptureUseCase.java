package vd.affsn.sentinelle.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.RuptureId;

/**
 * Mise à jour (remplacement complet de la fin estimée, du motif, des régions et des alternatives)
 * d'une rupture en cours par son déclarant. Produit un événement d'audit MODIFICATION.
 * <p>
 * Le début enregistré est conservé : la date de début ne change jamais (pour la corriger, il faut
 * clôturer la rupture puis la redéclarer). La {@link Commande} ne porte donc que la nouvelle fin
 * estimée ; c'est le use case qui recharge la rupture et reconstruit la
 * {@link vd.affsn.sentinelle.domain.model.Periode} (début enregistré, fin estimée) avant
 * d'appeler le domaine.
 * <p>
 * La validation « fin estimée &gt;= début » relève du domaine ({@code Periode}) et lève
 * {@code ValidationMetierException}.
 *
 * @throws vd.affsn.sentinelle.domain.exception.RuptureIntrouvableException
 * @throws vd.affsn.sentinelle.domain.exception.AccesRefuseException
 * @throws vd.affsn.sentinelle.domain.exception.RuptureDejaClotureeException
 * @throws vd.affsn.sentinelle.domain.exception.ValidationMetierException
 */
public interface MettreAJourRuptureUseCase {

    RuptureVue mettreAJour(Acteur acteur, Commande commande);

    /**
     * @param finEstimee nouvelle fin estimée ; {@code null} signifie « fin estimée effacée »
     */
    record Commande(RuptureId ruptureId,
                    LocalDate finEstimee,
                    Motif motif,
                    Set<Region> regions,
                    List<Alternative> alternatives) {
    }
}
