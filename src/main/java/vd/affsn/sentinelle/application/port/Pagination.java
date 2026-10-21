package vd.affsn.sentinelle.application.port;

import vd.affsn.sentinelle.domain.exception.ValidationMetierException;

/**
 * Demande de page : numéro à partir de 0, taille entre 1 et {@value #TAILLE_MAX}.
 */
public record Pagination(int numero, int taille) {

    public static final int TAILLE_PAR_DEFAUT = 20;
    public static final int TAILLE_MAX = 100;

    public Pagination {
        if (numero < 0) {
            throw new ValidationMetierException("Le numéro de page doit être >= 0");
        }
        if (taille < 1 || taille > TAILLE_MAX) {
            throw new ValidationMetierException("La taille de page doit être comprise entre 1 et " + TAILLE_MAX);
        }
    }

    public static Pagination premiere() {
        return new Pagination(0, TAILLE_PAR_DEFAUT);
    }

    public long decalage() {
        return (long) numero * taille;
    }
}
