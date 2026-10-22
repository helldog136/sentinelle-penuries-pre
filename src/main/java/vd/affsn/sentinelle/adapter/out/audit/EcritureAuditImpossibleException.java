package vd.affsn.sentinelle.adapter.out.audit;

/**
 * L'entrée d'audit n'a pas pu être écrite sans risquer de rompre la chaîne (verrou non obtenu à
 * temps, transaction imbriquée, instantané de lecture périmé). Propagée à l'appelant : la
 * transaction métier est annulée, aucune opération n'est effectuée sans trace. Le message ne
 * contient aucune donnée de l'événement.
 */
public class EcritureAuditImpossibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EcritureAuditImpossibleException(String message) {
        super(message);
    }

    public EcritureAuditImpossibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
