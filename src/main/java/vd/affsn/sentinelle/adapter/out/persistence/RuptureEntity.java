package vd.affsn.sentinelle.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.BatchSize;

import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.StatutRupture;

/**
 * Entité JPA de la table {@code ruptures} (modèle de persistance, distinct de l'agrégat du domaine).
 *
 * <p>Les horodatages {@code cree_le} / {@code modifie_le} sont posés par les callbacks de cycle
 * de vie avec l'heure technique du système ({@link Instant#now()}), et non par {@code HorlogePort} :
 * ce sont des métadonnées techniques de persistance, sans incidence métier.
 *
 * <p>Chargement des collections : {@code @BatchSize} (chargement paresseux par lots) plutôt que
 * {@code JOIN FETCH}, qui multiplierait les lignes (régions x alternatives) et fausserait la
 * pagination SQL. Une page de N ruptures coûte ainsi 1 requête + 1 par collection (et par lot de
 * 100), jamais N+1.
 */
@Entity
@Table(name = "ruptures")
class RuptureEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code_produit", nullable = false, length = 10)
    private String codeProduit;

    @Column(name = "declarant", nullable = false, length = 10)
    private String declarant;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin_estimee")
    private LocalDate dateFinEstimee;

    @Column(name = "date_fin_effective")
    private LocalDate dateFinEffective;

    @Enumerated(EnumType.STRING)
    @Column(name = "motif", nullable = false, length = 20)
    private Motif motif;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutRupture statut;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe;

    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    @ElementCollection
    @CollectionTable(name = "rupture_regions", joinColumns = @JoinColumn(name = "rupture_id"))
    @Column(name = "region", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    @BatchSize(size = 100)
    private Set<Region> regions = EnumSet.noneOf(Region.class);

    // Pas de colonne de position dans le schéma : ordre déterministe par produit alternatif.
    @ElementCollection
    @CollectionTable(name = "rupture_alternatives", joinColumns = @JoinColumn(name = "rupture_id"))
    @OrderBy("produitAlternatif ASC")
    @BatchSize(size = 100)
    private List<AlternativeEmbeddable> alternatives = new ArrayList<>();

    protected RuptureEntity() {
        // requis par JPA
    }

    RuptureEntity(UUID id) {
        this.id = id;
    }

    @PrePersist
    void avantInsertion() {
        Instant maintenant = Instant.now();
        this.creeLe = maintenant;
        this.modifieLe = maintenant;
    }

    @PreUpdate
    void avantMiseAJour() {
        this.modifieLe = Instant.now();
    }

    UUID getId() {
        return id;
    }

    String getCodeProduit() {
        return codeProduit;
    }

    void setCodeProduit(String codeProduit) {
        this.codeProduit = codeProduit;
    }

    String getDeclarant() {
        return declarant;
    }

    void setDeclarant(String declarant) {
        this.declarant = declarant;
    }

    LocalDate getDateDebut() {
        return dateDebut;
    }

    void setDateDebut(LocalDate dateDebut) {
        this.dateDebut = dateDebut;
    }

    LocalDate getDateFinEstimee() {
        return dateFinEstimee;
    }

    void setDateFinEstimee(LocalDate dateFinEstimee) {
        this.dateFinEstimee = dateFinEstimee;
    }

    LocalDate getDateFinEffective() {
        return dateFinEffective;
    }

    void setDateFinEffective(LocalDate dateFinEffective) {
        this.dateFinEffective = dateFinEffective;
    }

    Motif getMotif() {
        return motif;
    }

    void setMotif(Motif motif) {
        this.motif = motif;
    }

    StatutRupture getStatut() {
        return statut;
    }

    void setStatut(StatutRupture statut) {
        this.statut = statut;
    }

    long getVersion() {
        return version;
    }

    Instant getCreeLe() {
        return creeLe;
    }

    Instant getModifieLe() {
        return modifieLe;
    }

    Set<Region> getRegions() {
        return regions;
    }

    List<AlternativeEmbeddable> getAlternatives() {
        return alternatives;
    }
}
