package vd.affsn.sentinelle.adapter.in.rest;

import java.util.List;

import vd.affsn.sentinelle.adapter.in.rest.dto.ViolationDto;

/** Paramètres de requête ou de chemin invalides ; ne porte jamais les valeurs saisies. */
final class ParametresInvalidesException extends RuntimeException {

    private final transient List<ViolationDto> violations;

    ParametresInvalidesException(List<ViolationDto> violations) {
        super("Paramètres invalides");
        this.violations = List.copyOf(violations);
    }

    List<ViolationDto> violations() {
        return violations;
    }
}
