package vd.affsn.sentinelle.domain.model;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Période d'une rupture : date de début obligatoire, date de fin <em>estimée</em> optionnelle
 * ({@code null} = inconnue). La fin estimée ne peut pas précéder le début (elle peut l'égaler).
 * La fin <em>effective</em> n'est pas portée ici : elle est fixée par la clôture de la
 * {@link Rupture}.
 *
 * <p><b>STUB TDD (T0.2)</b> : validation et comportement à implémenter.
 */
public record Periode(LocalDate debut, LocalDate finEstimee) {

    public Periode {
        // TODO T0.2 : debut null ou finEstimee < debut -> ValidationMetierException
    }

    public static Periode aPartirDu(LocalDate debut) {
        return new Periode(debut, null);
    }

    public static Periode entre(LocalDate debut, LocalDate finEstimee) {
        return new Periode(debut, finEstimee);
    }

    public Optional<LocalDate> finEstimeeOptionnelle() {
        throw new UnsupportedOperationException("T0.2 : à implémenter");
    }

    /** Vrai si la date donnée est postérieure ou égale au début. */
    public boolean aCommenceLe(LocalDate date) {
        throw new UnsupportedOperationException("T0.2 : à implémenter");
    }
}
