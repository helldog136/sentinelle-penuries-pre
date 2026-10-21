package vd.affsn.sentinelle.domain.exception;

/** L'acteur n'a pas le droit d'effectuer l'opération (mauvais rôle ou pas le déclarant). */
public final class AccesRefuseException extends ExceptionMetier {

    public AccesRefuseException(String message) {
        super(message);
    }
}
