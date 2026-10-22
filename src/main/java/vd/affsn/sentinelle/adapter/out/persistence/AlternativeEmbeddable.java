package vd.affsn.sentinelle.adapter.out.persistence;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Ligne de la table {@code rupture_alternatives} (hors clé étrangère, portée par la collection). */
@Embeddable
class AlternativeEmbeddable {

    @Column(name = "produit_alternatif", nullable = false, length = 10)
    private String produitAlternatif;

    @Column(name = "commentaire", nullable = false, length = 280)
    private String commentaire;

    protected AlternativeEmbeddable() {
        // requis par JPA
    }

    AlternativeEmbeddable(String produitAlternatif, String commentaire) {
        this.produitAlternatif = produitAlternatif;
        this.commentaire = commentaire;
    }

    String getProduitAlternatif() {
        return produitAlternatif;
    }

    String getCommentaire() {
        return commentaire;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AlternativeEmbeddable autre
                && Objects.equals(produitAlternatif, autre.produitAlternatif)
                && Objects.equals(commentaire, autre.commentaire);
    }

    @Override
    public int hashCode() {
        return Objects.hash(produitAlternatif, commentaire);
    }
}
