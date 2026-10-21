package vd.affsn.sentinelle.adapter.in.rest;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import vd.affsn.sentinelle.adapter.in.rest.dto.ViolationDto;
import vd.affsn.sentinelle.domain.exception.AccesRefuseException;
import vd.affsn.sentinelle.domain.exception.RuptureIntrouvableException;
import vd.affsn.sentinelle.domain.exception.ValidationMetierException;

/** Traduction des erreurs en ProblemDetail (RFC 7807, application/problem+json). */
@RestControllerAdvice
@ConditionalOnProperty(prefix = "sentinelle.rest", name = "enabled", havingValue = "true")
public class RestExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RestExceptionHandler.class);
    private static final String BASE = "https://sentinelle.affsn.example/problemes/";

    @ExceptionHandler(ParametresInvalidesException.class)
    ResponseEntity<ProblemDetail> parametresInvalides(ParametresInvalidesException e) {
        return validation("Un ou plusieurs paramètres sont invalides.", e.violations());
    }

    @ExceptionHandler(ValidationMetierException.class)
    ResponseEntity<ProblemDetail> validationMetier(ValidationMetierException e) {
        return validation("La requête ne respecte pas une règle métier.",
                List.of(new ViolationDto("requete", e.getMessage())));
    }

    @ExceptionHandler(RuptureIntrouvableException.class)
    ResponseEntity<ProblemDetail> introuvable(RuptureIntrouvableException e) {
        return reponse(HttpStatus.NOT_FOUND, "rupture-introuvable", "Rupture introuvable",
                "Aucune rupture ne correspond à l'identifiant demandé.");
    }

    @ExceptionHandler(AccesRefuseException.class)
    ResponseEntity<ProblemDetail> accesRefuse(AccesRefuseException e) {
        return reponse(HttpStatus.FORBIDDEN, "acces-refuse", "Accès refusé",
                "Vous n'avez pas accès à cette ressource.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> inattendue(Exception e) {
        LOG.error("Erreur inattendue lors du traitement d'une requête REST", e);
        return reponse(HttpStatus.INTERNAL_SERVER_ERROR, "erreur-interne", "Erreur interne",
                "Une erreur interne est survenue.");
    }

    private static ResponseEntity<ProblemDetail> validation(String detail, List<ViolationDto> violations) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setType(URI.create(BASE + "parametre-invalide"));
        pd.setTitle("Paramètre invalide");
        pd.setProperty("violations", violations);
        return enveloppe(pd);
    }

    private static ResponseEntity<ProblemDetail> reponse(HttpStatus statut, String type, String titre, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(statut, detail);
        pd.setType(URI.create(BASE + type));
        pd.setTitle(titre);
        return enveloppe(pd);
    }

    private static ResponseEntity<ProblemDetail> enveloppe(ProblemDetail pd) {
        return ResponseEntity.status(pd.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
