package vd.affsn.sentinelle.domain.tension;

import java.time.LocalDate;
import java.util.Collection;

import vd.affsn.sentinelle.domain.model.Rupture;

/**
 * Service de domaine PUR (sans état, sans horloge, sans I/O) calculant l'indice de tension.
 *
 * <p>Formule validée :
 * <ul>
 *   <li>seules comptent les ruptures actives à la date de référence
 *       ({@link Rupture#estActiveLe(LocalDate)}) : les ruptures clôturées et celles non encore
 *       commencées sont ignorées ;</li>
 *   <li>poids d'une rupture = {@code 1 + joursEcoules / 7.0}, où {@code joursEcoules} est le
 *       nombre de jours entiers entre le début et la date de référence, ramené à 0 s'il est
 *       négatif ;</li>
 *   <li>indice d'une région = somme des poids des ruptures actives qui la touchent ; une
 *       rupture touchant plusieurs régions compte (avec son poids entier) dans chacune ;</li>
 *   <li>les six régions figurent dans le résultat (0.0 si aucune rupture).</li>
 * </ul>
 *
 * <p><b>STUB TDD (T0.2)</b> : à implémenter.
 */
public final class CalculateurTension {

    public IndiceTension calculer(Collection<Rupture> ruptures, LocalDate dateReference) {
        throw new UnsupportedOperationException("T0.2 : à implémenter");
    }

    /** Poids d'une rupture à la date de référence, sans filtrage d'activité. */
    public double poids(Rupture rupture, LocalDate dateReference) {
        throw new UnsupportedOperationException("T0.2 : à implémenter");
    }
}
