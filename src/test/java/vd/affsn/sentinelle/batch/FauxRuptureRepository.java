package vd.affsn.sentinelle.batch;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import vd.affsn.sentinelle.application.port.Page;
import vd.affsn.sentinelle.application.port.Pagination;
import vd.affsn.sentinelle.application.port.out.RuptureRepository;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;

/**
 * Faux dépôt de ruptures pour les tests du batch : seule {@link #findActivesAu(LocalDate)} est
 * implémentée.
 *
 * <p>Par défaut il respecte le contrat du port (ne renvoie que les ruptures actives à la date).
 * En mode {@linkplain #nonConforme() non conforme}, il renvoie TOUTES les ruptures connues, ce
 * qui permet de vérifier que le job filtre lui-même (via le domaine) les ruptures clôturées ou
 * futures. {@link #tomberEnPanne(RuntimeException)} fait lever une exception à chaque appel.
 */
final class FauxRuptureRepository implements RuptureRepository {

    private final List<Rupture> ruptures = new ArrayList<>();
    private boolean conforme = true;
    private RuntimeException panne;
    private final List<LocalDate> datesDemandees = new ArrayList<>();

    synchronized FauxRuptureRepository avec(Rupture... nouvelles) {
        ruptures.addAll(List.of(nouvelles));
        return this;
    }

    synchronized FauxRuptureRepository remplacerPar(Collection<Rupture> nouvelles) {
        ruptures.clear();
        ruptures.addAll(nouvelles);
        return this;
    }

    synchronized FauxRuptureRepository nonConforme() {
        conforme = false;
        return this;
    }

    synchronized FauxRuptureRepository tomberEnPanne(RuntimeException exception) {
        panne = exception;
        return this;
    }

    synchronized FauxRuptureRepository reparer() {
        panne = null;
        return this;
    }

    synchronized void reinitialiser() {
        ruptures.clear();
        conforme = true;
        panne = null;
        datesDemandees.clear();
    }

    synchronized List<LocalDate> datesDemandees() {
        return List.copyOf(datesDemandees);
    }

    @Override
    public synchronized List<Rupture> findActivesAu(LocalDate date) {
        datesDemandees.add(date);
        if (panne != null) {
            throw panne;
        }
        return ruptures.stream().filter(r -> !conforme || r.estActiveLe(date)).toList();
    }

    @Override
    public Rupture save(Rupture rupture) {
        throw new UnsupportedOperationException("non utilisé par le batch");
    }

    @Override
    public Optional<Rupture> findById(RuptureId id) {
        throw new UnsupportedOperationException("non utilisé par le batch");
    }

    @Override
    public Page<Rupture> rechercherEnCours(Optional<Region> region, Optional<CodeProduit> produit,
            Pagination pagination) {
        throw new UnsupportedOperationException("non utilisé par le batch");
    }

    @Override
    public List<Rupture> findEnCoursByProduit(CodeProduit produit) {
        throw new UnsupportedOperationException("non utilisé par le batch");
    }
}
