package vd.affsn.sentinelle.batch;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import vd.affsn.sentinelle.application.port.out.HorlogePort;

/** Horloge de test : date fixée par le test, aucune lecture de l'horloge système. */
final class HorlogeFixe implements HorlogePort {

    private volatile LocalDate aujourdhui;
    private volatile RuntimeException panne;
    private final AtomicInteger appels = new AtomicInteger();

    HorlogeFixe(LocalDate aujourdhui) {
        this.aujourdhui = aujourdhui;
    }

    void fixer(LocalDate date) {
        this.aujourdhui = date;
        this.panne = null;
    }

    void tomberEnPanne(RuntimeException exception) {
        this.panne = exception;
    }

    int appels() {
        return appels.get();
    }

    @Override
    public Instant maintenant() {
        return aujourdhui.atTime(2, 0).toInstant(ZoneOffset.UTC);
    }

    @Override
    public LocalDate aujourdhui() {
        appels.incrementAndGet();
        if (panne != null) {
            throw panne;
        }
        return aujourdhui;
    }
}
