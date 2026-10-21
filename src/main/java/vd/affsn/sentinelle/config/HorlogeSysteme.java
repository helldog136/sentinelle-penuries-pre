package vd.affsn.sentinelle.config;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import vd.affsn.sentinelle.application.port.out.HorlogePort;

/** Horloge applicative basée sur {@link Clock} ; « aujourd'hui » est calculé dans le fuseau configuré. */
public class HorlogeSysteme implements HorlogePort {

    private final Clock clock;

    public HorlogeSysteme(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Construit l'horloge système pour un fuseau (défaut UTC si vide) ; fuseau inconnu : échec sans écho de la valeur. */
    public static HorlogeSysteme pourFuseau(String fuseau) {
        String id = (fuseau == null || fuseau.isBlank()) ? "UTC" : fuseau.trim();
        try {
            return new HorlogeSysteme(Clock.system(ZoneId.of(id)));
        } catch (DateTimeException e) {
            throw new IllegalStateException("Fuseau horaire invalide pour la propriété sentinelle.horloge.fuseau");
        }
    }

    @Override
    public Instant maintenant() {
        return clock.instant();
    }

    @Override
    public LocalDate aujourdhui() {
        return LocalDate.now(clock);
    }
}
