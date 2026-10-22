package vd.affsn.sentinelle.application.port.out;

/**
 * Journal d'audit <b>append-only par conception</b> : ce port n'expose qu'un ajout. Il n'existe
 * volontairement aucune méthode de lecture pour modification, de mise à jour ni de suppression.
 * L'adaptateur garantit l'immuabilité côté stockage (chaînage de hachage, cf. ADR-003).
 */
public interface AuditPort {

    void enregistrer(EvenementAudit evenement);
}
