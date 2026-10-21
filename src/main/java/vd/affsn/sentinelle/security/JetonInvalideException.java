package vd.affsn.sentinelle.security;

/**
 * Jeton absent, mal formé ou refusé (signature, algorithme, dates, émetteur, audience, claims).
 *
 * <p>Le message est <strong>toujours</strong> le même ({@link #MESSAGE_GENERIQUE}) : il ne révèle
 * jamais la raison du refus et ne contient jamais le jeton. Aucune cause n'est chaînée, pour
 * qu'une trace ne puisse pas fuiter de détail de vérification.
 */
public final class JetonInvalideException extends RuntimeException {

    /** Message unique, exposé tel quel aux appelants (REST et gRPC). */
    public static final String MESSAGE_GENERIQUE = "Authentification requise";

    public JetonInvalideException() {
        super(MESSAGE_GENERIQUE, null, false, false);
    }
}
