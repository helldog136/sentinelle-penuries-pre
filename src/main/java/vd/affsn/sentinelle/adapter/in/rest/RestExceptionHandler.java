package vd.affsn.sentinelle.adapter.in.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduction des erreurs en ProblemDetail (RFC 7807). STUB phase A : aucun handler. */
@RestControllerAdvice
@ConditionalOnProperty(prefix = "sentinelle.rest", name = "enabled", havingValue = "true")
public class RestExceptionHandler {
}
