package vd.affsn.sentinelle.batch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import vd.affsn.sentinelle.application.port.out.ReferentielProduitsPort;
import vd.affsn.sentinelle.domain.model.CodeProduit;

/**
 * Faux référentiel de produits essentiels pour les tests du batch : ensemble modifiable,
 * journal des interrogations et panne simulable (même esprit que {@link FauxRuptureRepository}).
 */
final class FauxReferentielProduits implements ReferentielProduitsPort {

    private final Set<String> essentiels = new HashSet<>();
    private final List<CodeProduit> interrogations = new ArrayList<>();
    private RuntimeException panne;

    synchronized FauxReferentielProduits avec(String... codes) {
        essentiels.addAll(List.of(codes));
        return this;
    }

    synchronized FauxReferentielProduits retirer(String code) {
        essentiels.remove(code);
        return this;
    }

    synchronized FauxReferentielProduits vider() {
        essentiels.clear();
        return this;
    }

    synchronized FauxReferentielProduits tomberEnPanne(RuntimeException exception) {
        panne = exception;
        return this;
    }

    synchronized FauxReferentielProduits reparer() {
        panne = null;
        return this;
    }

    synchronized void reinitialiser() {
        essentiels.clear();
        interrogations.clear();
        panne = null;
    }

    synchronized List<CodeProduit> interrogations() {
        return List.copyOf(interrogations);
    }

    @Override
    public synchronized boolean estEssentiel(CodeProduit code) {
        interrogations.add(code);
        if (panne != null) {
            throw panne;
        }
        return essentiels.contains(code.valeur());
    }
}
