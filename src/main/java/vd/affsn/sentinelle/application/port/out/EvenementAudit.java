package vd.affsn.sentinelle.application.port.out;

import java.time.Instant;
import java.util.Objects;

import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.RuptureId;

/**
 * Entrée du journal d'audit. Le résumé est un texte court, sur une ligne, SANS donnée
 * sensible (ni donnée patient, ni jeton, ni secret) : typiquement « produit VMP-123456,
 * 2 régions ».
 */
public record EvenementAudit(TypeEvenementAudit type,
                             RuptureId ruptureId,
                             Acteur acteur,
                             Instant instant,
                             String resume) {

    public static final int LONGUEUR_MAX_RESUME = 500;

    public EvenementAudit {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(ruptureId, "ruptureId");
        Objects.requireNonNull(acteur, "acteur");
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(resume, "resume");
        if (resume.length() > LONGUEUR_MAX_RESUME || resume.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Résumé d'audit trop long ou multi-ligne");
        }
    }
}
