package vd.affsn.sentinelle.domain.exception;

import vd.affsn.sentinelle.domain.model.RuptureId;

/** Aucune rupture ne correspond à l'identifiant demandé. */
public final class RuptureIntrouvableException extends ExceptionMetier {

    private final RuptureId ruptureId;

    public RuptureIntrouvableException(RuptureId ruptureId) {
        super("Rupture introuvable : " + ruptureId);
        this.ruptureId = ruptureId;
    }

    public RuptureId ruptureId() {
        return ruptureId;
    }
}
