package vd.affsn.sentinelle.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import vd.affsn.sentinelle.application.port.Page;
import vd.affsn.sentinelle.application.port.Pagination;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;

/**
 * Persistance des ruptures. L'adaptateur reconstitue les agrégats via
 * {@link Rupture#reconstituer} ; il gère lui-même le verrouillage optimiste.
 */
public interface RuptureRepository {

    /** Crée ou remplace la rupture (même identifiant) et renvoie l'état persisté. */
    Rupture save(Rupture rupture);

    Optional<Rupture> findById(RuptureId id);

    /**
     * Ruptures de statut EN_COURS, filtrées si les filtres sont présents, triées par date de
     * début décroissante puis identifiant (ordre stable pour la pagination).
     */
    Page<Rupture> rechercherEnCours(Optional<Region> region, Optional<CodeProduit> produit, Pagination pagination);

    /**
     * Toutes les ruptures de statut EN_COURS du produit donné (aucune pagination). Liste vide si
     * le produit n'a aucune rupture en cours ; l'ordre n'est pas garanti, l'appelant trie.
     */
    List<Rupture> findEnCoursByProduit(CodeProduit produit);

    /** Toutes les ruptures actives à la date donnée (cf. {@link Rupture#estActiveLe}). */
    List<Rupture> findActivesAu(LocalDate date);
}
