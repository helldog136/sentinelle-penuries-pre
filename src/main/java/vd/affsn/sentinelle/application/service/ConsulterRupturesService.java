package vd.affsn.sentinelle.application.service;

import vd.affsn.sentinelle.application.port.Page;
import vd.affsn.sentinelle.application.port.in.ConsulterRupturesUseCase;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.application.port.out.RuptureRepository;
import vd.affsn.sentinelle.domain.model.Acteur;

/** STUB (phase A du TDD) : aucune logique, l'implémentation viendra en phase B. */
public class ConsulterRupturesService implements ConsulterRupturesUseCase {

    private final RuptureRepository ruptures;

    public ConsulterRupturesService(RuptureRepository ruptures) {
        this.ruptures = ruptures;
    }

    @Override
    public Page<RuptureVue> consulter(Acteur acteur, Critere critere) {
        throw new UnsupportedOperationException("à implémenter (phase B)");
    }
}
