package vd.affsn.sentinelle.application.port.in;

import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.RuptureId;

/**
 * Détail d'une rupture (en cours ou clôturée). Tous rôles.
 *
 * @throws vd.affsn.sentinelle.domain.exception.RuptureIntrouvableException
 */
public interface ConsulterRuptureUseCase {

    RuptureVue consulter(Acteur acteur, RuptureId ruptureId);
}
