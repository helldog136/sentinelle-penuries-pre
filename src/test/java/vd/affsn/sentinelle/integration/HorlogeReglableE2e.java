package vd.affsn.sentinelle.integration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import vd.affsn.sentinelle.application.port.out.HorlogePort;

/**
 * Horloge applicative RÉGLABLE des tests de bout en bout d'ADR-004 : « aujourd'hui » vaut la date
 * réglée par le test (2026-11-01 par défaut, convention des cas d'acceptation), l'instant vaut
 * 09:30 UTC ce jour-là. Comme {@link HorlogeFixeE2e}, seule l'horloge métier est pilotée : la
 * validation des JWT garde l'horloge système.
 */
final class HorlogeReglableE2e implements HorlogePort {

    /** « Aujourd'hui » des cas d'acceptation d'ADR-004. */
    static final LocalDate AUJOURDHUI_ADR_004 = LocalDate.of(2026, 11, 1);

    private final AtomicReference<LocalDate> aujourdhui = new AtomicReference<>(AUJOURDHUI_ADR_004);

    void regler(LocalDate date) {
        aujourdhui.set(date);
    }

    static Instant instantDe(LocalDate date) {
        return date.atTime(9, 30).toInstant(ZoneOffset.UTC);
    }

    @Override
    public Instant maintenant() {
        return instantDe(aujourdhui.get());
    }

    @Override
    public LocalDate aujourdhui() {
        return aujourdhui.get();
    }
}
