package vd.affsn.sentinelle.domain.model;

/** Les six régions (fictives) de la Valdarie, avec leur libellé d'affichage. */
public enum Region {
    HAUTE_PAPERASSE("Haute-Paperasse"),
    BASSE_VALIDATION("Basse-Validation"),
    PLATEAU_DU_LEGACY("Plateau du Legacy"),
    COTE_DU_TIMEOUT("Côte du Timeout"),
    MARAIS_DE_LA_RECETTE("Marais de la Recette"),
    PROVINCE_DU_TICKET_OUVERT("Province du Ticket Ouvert");

    private final String libelle;

    Region(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
