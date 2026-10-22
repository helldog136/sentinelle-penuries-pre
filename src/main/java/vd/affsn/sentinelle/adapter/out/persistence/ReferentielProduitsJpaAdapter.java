package vd.affsn.sentinelle.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.transaction.annotation.Transactional;

import vd.affsn.sentinelle.application.port.out.ReferentielProduitsPort;
import vd.affsn.sentinelle.domain.model.CodeProduit;

/** Adaptateur JPA en lecture seule du référentiel des produits essentiels (STUB, phase rouge). */
// STUB (phase rouge) : volontairement pas un bean tant que l'implémentation n'est pas écrite ; réactiver @Repository à l'implémentation.
public class ReferentielProduitsJpaAdapter implements ReferentielProduitsPort {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public boolean estEssentiel(CodeProduit code) {
        throw new UnsupportedOperationException("à implémenter");
    }
}
