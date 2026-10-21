package vd.affsn.sentinelle.adapter.in.rest.dto;

import java.util.List;

/**
 * Rupture au format REST (schéma OpenAPI Rupture). Dates AAAA-MM-JJ en texte.
 * ECART SIGNALE : creeLe / modifieLe (requis par l'OpenAPI) absents car RuptureVue ne les porte pas.
 */
public record RuptureDto(String id,
                         String codeProduit,
                         String dateDebut,
                         String dateFinEstimee,
                         String dateFinEffective,
                         String motif,
                         List<String> regions,
                         List<AlternativeDto> alternatives,
                         String statut,
                         String distributeurId) {
}
