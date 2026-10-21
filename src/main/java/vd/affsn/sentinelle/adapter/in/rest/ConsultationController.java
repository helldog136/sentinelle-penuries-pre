package vd.affsn.sentinelle.adapter.in.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vd.affsn.sentinelle.adapter.in.rest.dto.AlternativesProduitDto;
import vd.affsn.sentinelle.adapter.in.rest.dto.PageRupturesDto;
import vd.affsn.sentinelle.adapter.in.rest.dto.RuptureDto;
import vd.affsn.sentinelle.application.port.in.ConsulterAlternativesUseCase;
import vd.affsn.sentinelle.application.port.in.ConsulterRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.ConsulterRupturesUseCase;
import vd.affsn.sentinelle.domain.model.Acteur;

/** Routes REST de consultation (lecture seule). STUB phase A. */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "sentinelle.rest", name = "enabled", havingValue = "true")
public class ConsultationController {

    private final ConsulterRupturesUseCase consulterRuptures;
    private final ConsulterRuptureUseCase consulterRupture;
    private final ConsulterAlternativesUseCase consulterAlternatives;

    public ConsultationController(ConsulterRupturesUseCase consulterRuptures,
                                  ConsulterRuptureUseCase consulterRupture,
                                  ConsulterAlternativesUseCase consulterAlternatives) {
        this.consulterRuptures = consulterRuptures;
        this.consulterRupture = consulterRupture;
        this.consulterAlternatives = consulterAlternatives;
    }

    @GetMapping(value = "/ruptures", produces = "application/json")
    public PageRupturesDto lister(@AuthenticationPrincipal Acteur acteur,
                                  @RequestParam(required = false) String region,
                                  @RequestParam(required = false) String produit,
                                  @RequestParam(required = false) String page,
                                  @RequestParam(required = false) String size) {
        throw new UnsupportedOperationException("phase B");
    }

    @GetMapping(value = "/ruptures/{id}", produces = "application/json")
    public RuptureDto obtenir(@AuthenticationPrincipal Acteur acteur, @PathVariable String id) {
        throw new UnsupportedOperationException("phase B");
    }

    @GetMapping(value = "/produits/{code}/alternatives", produces = "application/json")
    public AlternativesProduitDto alternatives(@AuthenticationPrincipal Acteur acteur, @PathVariable String code) {
        throw new UnsupportedOperationException("phase B");
    }
}
