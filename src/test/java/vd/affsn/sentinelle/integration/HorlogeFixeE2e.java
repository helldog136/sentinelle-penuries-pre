package vd.affsn.sentinelle.integration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import vd.affsn.sentinelle.application.port.out.HorlogePort;

/**
 * Horloge applicative FIXE des tests de bout en bout : « aujourd'hui » vaut toujours {@link #J}, quel
 * que soit le jour d'exécution. Seule l'horloge métier est figée : la validation des JWT garde
 * l'horloge système (les jetons de test sont émis à l'heure réelle).
 */
final class HorlogeFixeE2e implements HorlogePort {

    /** Date du jour figée de tous les scénarios. */
    static final LocalDate J = LocalDate.of(2026, 3, 16);

    /** Instant figé (09:30 UTC le jour J), recopié dans le journal d'audit. */
    static final Instant MAINTENANT = J.atTime(9, 30).toInstant(ZoneOffset.UTC);

    @Override
    public Instant maintenant() {
        return MAINTENANT;
    }

    @Override
    public LocalDate aujourdhui() {
        return J;
    }
}
