package vd.affsn.sentinelle.domain.exception;

/**
 * Racine (scellée) des exceptions métier de Sentinelle.
 *
 * <p>Les adaptateurs entrants traduisent chaque sous-type en code d'erreur
 * (validation, accès refusé, introuvable, conflit). Les messages ne doivent
 * jamais contenir de donnée sensible.
 */
public abstract sealed class ExceptionMetier extends RuntimeException
        permits ValidationMetierException,
                AccesRefuseException,
                RuptureIntrouvableException,
                RuptureDejaClotureeException {

    protected ExceptionMetier(String message) {
        super(message);
    }
}
