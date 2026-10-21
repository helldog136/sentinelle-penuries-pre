package vd.affsn.sentinelle.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Agrégat « rupture d'approvisionnement » déclarée par un distributeur agréé.
 *
 * <p><b>Immuable</b> : les opérations de mise à jour et de clôture renvoient une nouvelle
 * instance ; l'instance d'origine n'est jamais modifiée.
 *
 * <p>Invariants (vérifiés à la déclaration, à la reconstitution et à chaque mise à jour) :
 * <ul>
 *   <li>tous les champs obligatoires sont non nuls ;</li>
 *   <li>au moins une région ; les régions forment un ensemble non modifiable ;</li>
 *   <li>aucune alternative ne porte le produit en rupture ; pas deux alternatives pour le même
 *       produit ; la liste exposée est non modifiable et conserve l'ordre fourni ;</li>
 *   <li>{@code EN_COURS} ⇔ pas de fin effective ; {@code CLOTUREE} ⇔ fin effective présente,
 *       postérieure ou égale au début ;</li>
 *   <li>une rupture clôturée ne peut plus être modifiée ni reclôturée
 *       ({@link vd.affsn.sentinelle.domain.exception.RuptureDejaClotureeException}).</li>
 * </ul>
 *
 * <p>Autorisation : déclarer, mettre à jour et clôturer exigent un acteur de rôle
 * {@link Role#DISTRIBUTEUR} ; mettre à jour et clôturer exigent en plus que ce soit le
 * déclarant. Sinon {@link vd.affsn.sentinelle.domain.exception.AccesRefuseException}.
 * L'autorisation est vérifiée <em>avant</em> l'état (un tiers reçoit un refus d'accès, pas
 * l'information « déjà clôturée »).
 *
 * <p>Activité : une rupture est active à une date si elle est {@code EN_COURS} et que son
 * début est antérieur ou égal à cette date. La fin estimée n'entre pas en compte.
 *
 * <p><b>STUB TDD (T0.2)</b> : fabriques et comportement à implémenter.
 */
public final class Rupture {

    private final RuptureId id;
    private final CodeProduit produit;
    private final IdentifiantDistributeur declarant;
    private final Periode periode;
    private final Motif motif;
    private final Set<Region> regions;
    private final List<Alternative> alternatives;
    private final StatutRupture statut;
    private final LocalDate finEffective;

    private Rupture(RuptureId id,
                    CodeProduit produit,
                    IdentifiantDistributeur declarant,
                    Periode periode,
                    Motif motif,
                    Set<Region> regions,
                    List<Alternative> alternatives,
                    StatutRupture statut,
                    LocalDate finEffective) {
        this.id = id;
        this.produit = produit;
        this.declarant = declarant;
        this.periode = periode;
        this.motif = motif;
        this.regions = regions;
        this.alternatives = alternatives;
        this.statut = statut;
        this.finEffective = finEffective;
    }

    /**
     * Déclare une nouvelle rupture {@code EN_COURS}. Le déclarant est le distributeur auquel
     * l'acteur est rattaché.
     */
    public static Rupture declarer(RuptureId id,
                                   CodeProduit produit,
                                   Acteur acteur,
                                   Periode periode,
                                   Motif motif,
                                   Set<Region> regions,
                                   List<Alternative> alternatives) {
        throw new UnsupportedOperationException("T0.2 : à implémenter");
    }

    /**
     * Reconstitue une rupture depuis la persistance. Aucune vérification d'acteur, mais tous
     * les invariants s'appliquent. {@code finEffective} est {@code null} pour une rupture en cours.
     */
    public static Rupture reconstituer(RuptureId id,
                                       CodeProduit produit,
                                       IdentifiantDistributeur declarant,
                                       Periode periode,
                                       Motif motif,
                                       Set<Region> regions,
                                       List<Alternative> alternatives,
                                       StatutRupture statut,
                                       LocalDate finEffective) {
        throw new UnsupportedOperationException("T0.2 : à implémenter");
    }

    /**
     * Remplace période, motif, régions et alternatives (sémantique de remplacement complet).
     * Le produit, le déclarant et l'identifiant sont immuables.
     */
    public Rupture mettreAJour(Acteur acteur,
                               Periode nouvellePeriode,
                               Motif nouveauMotif,
                               Set<Region> nouvellesRegions,
                               List<Alternative> nouvellesAlternatives) {
        throw new UnsupportedOperationException("T0.2 : à implémenter");
    }

    /** Clôture la rupture en fixant sa fin effective (>= début). */
    public Rupture cloturer(Acteur acteur, LocalDate dateFinEffective) {
        throw new UnsupportedOperationException("T0.2 : à implémenter");
    }

    public boolean estActiveLe(LocalDate date) {
        throw new UnsupportedOperationException("T0.2 : à implémenter");
    }

    public RuptureId id() {
        return id;
    }

    public CodeProduit produit() {
        return produit;
    }

    public IdentifiantDistributeur declarant() {
        return declarant;
    }

    public Periode periode() {
        return periode;
    }

    public Motif motif() {
        return motif;
    }

    public Set<Region> regions() {
        return regions;
    }

    public List<Alternative> alternatives() {
        return alternatives;
    }

    public StatutRupture statut() {
        return statut;
    }

    public Optional<LocalDate> finEffective() {
        return Optional.ofNullable(finEffective);
    }

    /** Égalité d'entité : par identifiant. */
    @Override
    public boolean equals(Object o) {
        return o instanceof Rupture autre && Objects.equals(id, autre.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Rupture[" + id + ", " + produit + ", " + statut + "]";
    }
}
