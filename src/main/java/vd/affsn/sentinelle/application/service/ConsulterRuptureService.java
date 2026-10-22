package vd.affsn.sentinelle.application.service;

import vd.affsn.sentinelle.application.port.in.ConsulterRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.application.port.out.RuptureRepository;
import vd.affsn.sentinelle.domain.exception.RuptureIntrouvableException;
import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.RuptureId;

public class ConsulterRuptureService implements ConsulterRuptureUseCase {

    private final RuptureRepository ruptures;

    public ConsulterRuptureService(RuptureRepository ruptures) {
        this.ruptures = ruptures;
    }

    @Override
    public RuptureVue consulter(Acteur acteur, RuptureId ruptureId) {
        return ruptures.findById(ruptureId)
                .map(RuptureVue::depuis)
                .orElseThrow(() -> new RuptureIntrouvableException(ruptureId));
    }
}
