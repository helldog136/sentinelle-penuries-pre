package vd.affsn.sentinelle.application.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import vd.affsn.sentinelle.application.port.in.ConsulterAlternativesUseCase;
import vd.affsn.sentinelle.application.port.out.RuptureRepository;
import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.Rupture;

public class ConsulterAlternativesService implements ConsulterAlternativesUseCase {

    private final RuptureRepository ruptures;

    public ConsulterAlternativesService(RuptureRepository ruptures) {
        this.ruptures = ruptures;
    }

    @Override
    public Resultat consulter(Acteur acteur, CodeProduit produit) {
        List<AlternativeDeRupture> alternatives = ruptures.findEnCoursByProduit(produit).stream()
                .collect(Collectors.toMap(Rupture::id, Function.identity(), (a, b) -> a, LinkedHashMap::new))
                .values().stream()
                .flatMap(r -> r.alternatives().stream().map(a -> new AlternativeDeRupture(r.id(), a)))
                .sorted(Comparator
                        .comparing((AlternativeDeRupture a) -> a.alternative().produit().valeur())
                        .thenComparing(a -> a.ruptureId().toString()))
                .toList();
        return new Resultat(produit, alternatives);
    }
}
