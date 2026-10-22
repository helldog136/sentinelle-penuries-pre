package vd.affsn.sentinelle.domain.exception;

import vd.affsn.sentinelle.domain.model.RuptureId;

/** La rupture est clôturée : elle ne peut plus être modifiée ni reclôturée. */
public final class RuptureDejaClotureeException extends ExceptionMetier {

    private final RuptureId ruptureId;

    public RuptureDejaClotureeException(RuptureId ruptureId) {
        super("Rupture déjà clôturée : " + ruptureId);
        this.ruptureId = ruptureId;
    }

    public RuptureId ruptureId() {
        return ruptureId;
    }
}
