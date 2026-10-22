package vd.affsn.sentinelle.domain.model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import vd.affsn.sentinelle.domain.exception.AccesRefuseException;
import vd.affsn.sentinelle.domain.exception.RuptureDejaClotureeException;
import vd.affsn.sentinelle.domain.exception.ValidationMetierException;

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
        this.id = obligatoire(id, "identifiant");
        this.produit = obligatoire(produit, "produit");
        this.declarant = obligatoire(declarant, "déclarant");
        this.periode = obligatoire(periode, "période");
        this.motif = obligatoire(motif, "motif");
        this.regions = copieRegions(regions);
        this.alternatives = copieAlternatives(alternatives, produit);
        this.statut = obligatoire(statut, "statut");
        if ((statut == StatutRupture.CLOTUREE) != (finEffective != null)) {
            throw new ValidationMetierException(
                    "Rupture : fin effective présente si et seulement si la rupture est clôturée");
        }
        if (finEffective != null && finEffective.isBefore(periode.debut())) {
            throw new ValidationMetierException("Rupture : la fin effective ne peut pas précéder le début");
        }
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
        if (acteur == null || acteur.role() != Role.DISTRIBUTEUR) {
            throw new AccesRefuseException("Seul un distributeur peut déclarer une rupture");
        }
        return new Rupture(id, produit, acteur.distributeurRattache(), periode, motif, regions, alternatives,
                StatutRupture.EN_COURS, null);
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
        return new Rupture(id, produit, declarant, periode, motif, regions, alternatives, statut, finEffective);
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
        verifierModifiablePar(acteur);
        return new Rupture(id, produit, declarant, nouvellePeriode, nouveauMotif, nouvellesRegions,
                nouvellesAlternatives, StatutRupture.EN_COURS, null);
    }

    /** Clôture la rupture en fixant sa fin effective (>= début). */
    public Rupture cloturer(Acteur acteur, LocalDate dateFinEffective) {
        verifierModifiablePar(acteur);
        obligatoire(dateFinEffective, "fin effective");
        return new Rupture(id, produit, declarant, periode, motif, regions, alternatives,
                StatutRupture.CLOTUREE, dateFinEffective);
    }

    public boolean estActiveLe(LocalDate date) {
        return statut == StatutRupture.EN_COURS && periode.aCommenceLe(date);
    }

    /** Autorisation d'abord (déclarant uniquement), puis état (pas déjà clôturée). */
    private void verifierModifiablePar(Acteur acteur) {
        if (acteur == null || !acteur.agitPour(declarant)) {
            throw new AccesRefuseException("Seul le distributeur déclarant peut modifier cette rupture");
        }
        if (statut == StatutRupture.CLOTUREE) {
            throw new RuptureDejaClotureeException(id);
        }
    }

    private static <T> T obligatoire(T valeur, String champ) {
        if (valeur == null) {
            throw new ValidationMetierException("Rupture : " + champ + " obligatoire");
        }
        return valeur;
    }

    private static Set<Region> copieRegions(Set<Region> regions) {
        obligatoire(regions, "régions");
        if (regions.isEmpty()) {
            throw new ValidationMetierException("Rupture : au moins une région est requise");
        }
        Set<Region> copie = EnumSet.noneOf(Region.class);
        for (Region region : regions) {
            copie.add(obligatoire(region, "région"));
        }
        return Collections.unmodifiableSet(copie);
    }

    private static List<Alternative> copieAlternatives(List<Alternative> alternatives, CodeProduit produit) {
        obligatoire(alternatives, "alternatives");
        Set<CodeProduit> produitsVus = new HashSet<>();
        for (Alternative alternative : alternatives) {
            obligatoire(alternative, "alternative");
            if (alternative.produit().equals(produit)) {
                throw new ValidationMetierException("Rupture : une alternative ne peut pas être le produit en rupture");
            }
            if (!produitsVus.add(alternative.produit())) {
                throw new ValidationMetierException("Rupture : deux alternatives pour le même produit");
            }
        }
        return List.copyOf(alternatives);
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
