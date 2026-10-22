package vd.affsn.sentinelle.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import vd.affsn.sentinelle.application.port.Page;
import vd.affsn.sentinelle.application.port.Pagination;
import vd.affsn.sentinelle.application.port.out.RuptureRepository;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.domain.model.StatutRupture;

/**
 * Adaptateur JPA du port {@link RuptureRepository}.
 *
 * <p>Toutes les requêtes passent par l'API Criteria (paramètres liés, aucune concaténation).
 * Verrouillage optimiste : {@code @Version} sur l'entité ; deux mises à jour concurrentes de la
 * même rupture font échouer la seconde ({@code ObjectOptimisticLockingFailureException}, propagée
 * telle quelle à l'appelant). Le domaine n'exposant pas de version, la détection porte sur les
 * transactions qui se chevauchent (lecture puis écriture dans des transactions entrelacées).
 */
@Repository
public class RuptureRepositoryAdapter implements RuptureRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public Rupture save(Rupture rupture) {
        UUID id = rupture.id().valeur();
        RuptureEntity entite = em.find(RuptureEntity.class, id);
        if (entite == null) {
            entite = new RuptureEntity(id);
            RuptureMapper.recopier(rupture, entite);
            em.persist(entite);
        } else {
            RuptureMapper.recopier(rupture, entite);
        }
        // Pas de flush explicite : l'écriture (et donc le contrôle de version et les contraintes du schéma)
        // a lieu au commit de la transaction de l'appelant, ou avant toute requête de lecture qui suit.
        return RuptureMapper.versDomaine(entite);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Rupture> findById(RuptureId id) {
        return Optional.ofNullable(em.find(RuptureEntity.class, id.valeur())).map(RuptureMapper::versDomaine);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Rupture> rechercherEnCours(Optional<Region> region, Optional<CodeProduit> produit,
                                           Pagination pagination) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Long> comptage = cb.createQuery(Long.class);
        Root<RuptureEntity> rc = comptage.from(RuptureEntity.class);
        comptage.select(cb.count(rc)).where(filtres(cb, rc, region, produit));
        long total = em.createQuery(comptage).getSingleResult();

        CriteriaQuery<RuptureEntity> donnees = cb.createQuery(RuptureEntity.class);
        Root<RuptureEntity> r = donnees.from(RuptureEntity.class);
        donnees.select(r)
                .where(filtres(cb, r, region, produit))
                .orderBy(cb.desc(r.get("dateDebut")), cb.asc(r.get("id")));
        List<RuptureEntity> entites = em.createQuery(donnees)
                .setFirstResult(Math.toIntExact(pagination.decalage()))
                .setMaxResults(pagination.taille())
                .getResultList();
        return new Page<>(entites.stream().map(RuptureMapper::versDomaine).toList(), pagination, total);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Rupture> findEnCoursByProduit(CodeProduit produit) {
        return lister((cb, r) -> cb.and(
                cb.equal(r.get("statut"), StatutRupture.EN_COURS),
                cb.equal(r.get("codeProduit"), produit.valeur())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Rupture> findActivesAu(LocalDate date) {
        return lister((cb, r) -> cb.and(
                cb.equal(r.get("statut"), StatutRupture.EN_COURS),
                cb.lessThanOrEqualTo(r.<LocalDate>get("dateDebut"), date)));
    }

    private List<Rupture> lister(java.util.function.BiFunction<CriteriaBuilder, Root<RuptureEntity>, Predicate> filtre) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<RuptureEntity> q = cb.createQuery(RuptureEntity.class);
        Root<RuptureEntity> r = q.from(RuptureEntity.class);
        q.select(r).where(filtre.apply(cb, r)).orderBy(cb.asc(r.get("id")));
        return em.createQuery(q).getResultList().stream().map(RuptureMapper::versDomaine).toList();
    }

    private static Predicate filtres(CriteriaBuilder cb, Root<RuptureEntity> r, Optional<Region> region,
                                     Optional<CodeProduit> produit) {
        Predicate p = cb.equal(r.get("statut"), StatutRupture.EN_COURS);
        if (region.isPresent()) {
            p = cb.and(p, cb.isMember(region.get(), r.<java.util.Set<Region>>get("regions")));
        }
        if (produit.isPresent()) {
            p = cb.and(p, cb.equal(r.get("codeProduit"), produit.get().valeur()));
        }
        return p;
    }
}
