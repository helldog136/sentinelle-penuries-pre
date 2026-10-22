package vd.affsn.sentinelle.domain.model;

import java.time.LocalDate;
import java.util.Optional;

import vd.affsn.sentinelle.domain.exception.ValidationMetierException;

/**
 * Période d'une rupture : date de début obligatoire, date de fin <em>estimée</em> optionnelle
 * ({@code null} = inconnue). La fin estimée ne peut pas précéder le début (elle peut l'égaler).
 * La fin <em>effective</em> n'est pas portée ici : elle est fixée par la clôture de la
 * {@link Rupture}.
 */
public record Periode(LocalDate debut, LocalDate finEstimee) {

    public Periode {
        if (debut == null) {
            throw new ValidationMetierException("Période : date de début obligatoire");
        }
        if (finEstimee != null && finEstimee.isBefore(debut)) {
            throw new ValidationMetierException("Période : la fin estimée ne peut pas précéder le début");
        }
    }

    public static Periode aPartirDu(LocalDate debut) {
        return new Periode(debut, null);
    }

    public static Periode entre(LocalDate debut, LocalDate finEstimee) {
        return new Periode(debut, finEstimee);
    }

    public Optional<LocalDate> finEstimeeOptionnelle() {
        return Optional.ofNullable(finEstimee);
    }

    /** Vrai si la date donnée est postérieure ou égale au début. */
    public boolean aCommenceLe(LocalDate date) {
        return !date.isBefore(debut);
    }
}
