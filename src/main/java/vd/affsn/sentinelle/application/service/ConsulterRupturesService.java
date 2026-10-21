package vd.affsn.sentinelle.application.service;

import vd.affsn.sentinelle.application.port.Page;
import vd.affsn.sentinelle.application.port.in.ConsulterRupturesUseCase;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.application.port.out.RuptureRepository;
import vd.affsn.sentinelle.domain.model.Acteur;

public class ConsulterRupturesService implements ConsulterRupturesUseCase {

    private final RuptureRepository ruptures;

    public ConsulterRupturesService(RuptureRepository ruptures) {
        this.ruptures = ruptures;
    }

    @Override
    public Page<RuptureVue> consulter(Acteur acteur, Critere critere) {
        return ruptures.rechercherEnCours(critere.region(), critere.produit(), critere.pagination())
                .map(RuptureVue::depuis);
    }
}
