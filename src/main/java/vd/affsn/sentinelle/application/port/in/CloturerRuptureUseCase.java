package vd.affsn.sentinelle.application.port.in;

import java.time.LocalDate;

import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.RuptureId;

/**
 * Clôture d'une rupture par son déclarant, en fixant la fin effective.
 * Produit un événement d'audit CLOTURE.
 *
 * @throws vd.affsn.sentinelle.domain.exception.RuptureIntrouvableException
 * @throws vd.affsn.sentinelle.domain.exception.AccesRefuseException
 * @throws vd.affsn.sentinelle.domain.exception.RuptureDejaClotureeException
 * @throws vd.affsn.sentinelle.domain.exception.ValidationMetierException fin effective avant le début
 */
public interface CloturerRuptureUseCase {

    RuptureVue cloturer(Acteur acteur, Commande commande);

    record Commande(RuptureId ruptureId, LocalDate dateFinEffective) {
    }
}
