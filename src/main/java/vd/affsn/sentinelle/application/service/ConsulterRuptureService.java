package vd.affsn.sentinelle.application.service;

import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.application.port.in.ConsulterRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.application.port.out.RuptureRepository;
import vd.affsn.sentinelle.domain.model.Acteur;

/** STUB (phase A du TDD) : aucune logique, l'implémentation viendra en phase B. */
public class ConsulterRuptureService implements ConsulterRuptureUseCase {

    private final RuptureRepository ruptures;

    public ConsulterRuptureService(RuptureRepository ruptures) {
        this.ruptures = ruptures;
    }

    @Override
    public RuptureVue consulter(Acteur acteur, RuptureId ruptureId) {
        throw new UnsupportedOperationException("à implémenter (phase B)");
    }
}
