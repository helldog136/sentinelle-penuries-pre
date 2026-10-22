package vd.affsn.sentinelle.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import vd.affsn.sentinelle.application.port.out.HorlogePort;
import vd.affsn.sentinelle.application.port.out.ReferentielProduitsPort;
import vd.affsn.sentinelle.domain.model.CodeProduit;

/**
 * Doublures propres aux tests ADR-004 (produits essentiels). Complètent {@link Fakes} sans le
 * modifier. Pas de Mockito.
 */
final class FakesEssentiel {

    /** Conventions des cas d'acceptation d'ADR-004. */
    static final CodeProduit VMP_100001 = new CodeProduit("VMP-100001"); // essentiel
    static final CodeProduit VMP_200002 = new CodeProduit("VMP-200002"); // non essentiel
    static final CodeProduit VMP_300003 = new CodeProduit("VMP-300003"); // essentiel
    static final CodeProduit VMP_400004 = new CodeProduit("VMP-400004"); // non essentiel
    static final CodeProduit VMP_999999 = new CodeProduit("VMP-999999"); // inconnu partout

    /** Message fixe de la règle (ADR-004 § 3), recopié tel quel : le test ne dépend pas de la constante. */
    static final String MSG =
            "alternatives: au moins une alternative requise (produit essentiel, rupture de plus de 30 jours)";

    private FakesEssentiel() {
    }

    /**
     * Référentiel en mémoire, modifiable en cours de test (reclassement, retrait) pour simuler le
     * « statut courant ». Enregistre chaque interrogation et peut simuler une panne technique.
     */
    static final class ReferentielMemoire implements ReferentielProduitsPort {
        final Set<CodeProduit> essentiels = new HashSet<>();
        final List<CodeProduit> interrogations = new ArrayList<>();
        RuntimeException panne;

        /** Référentiel par défaut d'ADR-004 : VMP-100001 et VMP-300003 essentiels. */
        static ReferentielMemoire parDefaut() {
            return new ReferentielMemoire().ajouter(VMP_100001).ajouter(VMP_300003);
        }

        ReferentielMemoire ajouter(CodeProduit code) {
            essentiels.add(code);
            return this;
        }

        ReferentielMemoire retirer(CodeProduit code) {
            essentiels.remove(code);
            return this;
        }

        @Override
        public boolean estEssentiel(CodeProduit code) {
            interrogations.add(code);
            if (panne != null) {
                throw panne;
            }
            return essentiels.contains(code);
        }
    }

    /** Horloge dont le jour peut être avancé en cours de test. */
    static final class HorlogeReglable implements HorlogePort {
        LocalDate jour;

        HorlogeReglable(LocalDate jour) {
            this.jour = jour;
        }

        @Override
        public Instant maintenant() {
            return jour.atTime(9, 0).toInstant(ZoneOffset.UTC);
        }

        @Override
        public LocalDate aujourdhui() {
            return jour;
        }
    }
}
