package vd.affsn.sentinelle.adapter.in.rest.dto;

/** Une violation de validation : nom du champ et message, jamais la valeur saisie. */
public record ViolationDto(String champ, String message) {
}
