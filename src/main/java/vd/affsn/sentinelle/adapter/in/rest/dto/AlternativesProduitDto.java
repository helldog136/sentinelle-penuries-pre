package vd.affsn.sentinelle.adapter.in.rest.dto;

import java.util.List;

/** Réponse de GET /api/v1/produits/{code}/alternatives (schéma AlternativesProduit). */
public record AlternativesProduitDto(String codeProduit, List<AlternativeProposeeDto> alternatives) {
}
