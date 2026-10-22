package vd.affsn.sentinelle.application.port.in;

import java.util.List;
import java.util.Objects;

import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.RuptureId;

/**
 * Alternatives connues pour un produit, agrégées depuis ses ruptures EN COURS. Tous rôles.
 *
 * <p>Sentinelle ne tient pas de référentiel produits : un produit sans rupture en cours (ou
 * inconnu) n'est pas une erreur, le résultat contient simplement une liste vide.
 */
public interface ConsulterAlternativesUseCase {

    Resultat consulter(Acteur acteur, CodeProduit produit);

    /**
     * Alternatives d'un produit. {@code alternatives} contient une entrée par couple (rupture,
     * produit alternatif) : une même alternative citée par plusieurs ruptures apparaît une fois
     * par rupture (le commentaire peut différer), jamais deux fois pour la même rupture. Ordre
     * déterministe : code du produit alternatif croissant, puis identifiant de rupture croissant
     * (forme textuelle canonique de l'UUID). Liste immuable, éventuellement vide.
     */
    record Resultat(CodeProduit produit, List<AlternativeDeRupture> alternatives) {

        public Resultat {
            Objects.requireNonNull(produit, "produit");
            alternatives = List.copyOf(alternatives);
        }
    }

    /** Alternative déclarée sur la rupture {@code ruptureId}. */
    record AlternativeDeRupture(RuptureId ruptureId, Alternative alternative) {

        public AlternativeDeRupture {
            Objects.requireNonNull(ruptureId, "ruptureId");
            Objects.requireNonNull(alternative, "alternative");
        }
    }
}
