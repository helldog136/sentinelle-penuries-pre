package vd.affsn.sentinelle.adapter.in.rest.dto;

import java.util.List;

/** Réponse de GET /api/v1/ruptures (schéma PageRuptures). */
public record PageRupturesDto(List<RuptureDto> contenu, int page, int size, long totalElements, int totalPages) {
}
