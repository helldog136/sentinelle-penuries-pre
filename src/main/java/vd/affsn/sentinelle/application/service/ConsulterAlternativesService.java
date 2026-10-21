package vd.affsn.sentinelle.application.service;

import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.application.port.in.ConsulterAlternativesUseCase;
import vd.affsn.sentinelle.application.port.out.RuptureRepository;
import vd.affsn.sentinelle.domain.model.Acteur;

/** STUB (phase A du TDD) : aucune logique, l'implémentation viendra en phase B. */
public class ConsulterAlternativesService implements ConsulterAlternativesUseCase {

    private final RuptureRepository ruptures;

    public ConsulterAlternativesService(RuptureRepository ruptures) {
        this.ruptures = ruptures;
    }

    @Override
    public Resultat consulter(Acteur acteur, CodeProduit produit) {
        throw new UnsupportedOperationException("à implémenter (phase B)");
    }
}
