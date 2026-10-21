package vd.affsn.sentinelle.domain.exception;

/** Une donnée ou un invariant métier n'est pas respecté. */
public final class ValidationMetierException extends ExceptionMetier {

    public ValidationMetierException(String message) {
        super(message);
    }
}
