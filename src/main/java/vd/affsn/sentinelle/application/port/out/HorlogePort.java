package vd.affsn.sentinelle.application.port.out;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Source de temps de l'application (testable). L'adaptateur fixe le fuseau de référence de
 * la Valdarie pour {@link #aujourdhui()}.
 */
public interface HorlogePort {

    Instant maintenant();

    LocalDate aujourdhui();
}
