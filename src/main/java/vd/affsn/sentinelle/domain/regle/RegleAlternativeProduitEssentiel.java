package vd.affsn.sentinelle.domain.regle;

import java.util.Collection;

import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.Periode;

/**
 * Rupture de plus de 30 jours (fin estimée > début + 30 jours, ou fin estimée absente) d'un
 * produit essentiel : au moins une alternative est requise.
 */
public final class RegleAlternativeProduitEssentiel {

    public static final long SEUIL_JOURS = 30;

    public static final String MESSAGE =
            "alternatives: au moins une alternative requise (produit essentiel, rupture de plus de 30 jours)";

    public void verifier(boolean produitEssentiel, Periode periode, Collection<Alternative> alternatives) {
        throw new UnsupportedOperationException("à implémenter");
    }
}
