package vd.affsn.sentinelle.adapter.in.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import vd.affsn.sentinelle.adapter.in.rest.dto.ViolationDto;
import vd.affsn.sentinelle.application.port.Pagination;
import vd.affsn.sentinelle.application.port.in.ConsulterAlternativesUseCase;
import vd.affsn.sentinelle.application.port.in.ConsulterRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.ConsulterRupturesUseCase;
import vd.affsn.sentinelle.domain.exception.ValidationMetierException;
import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.RuptureId;

/**
 * Routes REST de consultation (lecture seule). Les paramètres sont reçus en texte et validés ici
 * pour que les erreurs ne répètent jamais la valeur saisie.
 */
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
        List<ViolationDto> violations = new ArrayList<>();
        Optional<Region> regionFiltre = Optional.empty();
        Optional<CodeProduit> produitFiltre = Optional.empty();
        int numero = 0;
        int taille = Pagination.TAILLE_PAR_DEFAUT;

        if (region != null) {
            try {
                regionFiltre = Optional.of(Region.valueOf(region));
            } catch (IllegalArgumentException e) {
                violations.add(new ViolationDto("region", "valeur non reconnue"));
            }
        }
        if (produit != null) {
            if (produit.matches(CodeProduit.FORMAT)) {
                produitFiltre = Optional.of(new CodeProduit(produit));
            } else {
                violations.add(new ViolationDto("produit", "format attendu VMP-NNNNNN"));
            }
        }
        if (page != null) {
            Integer valeur = entier(page);
            if (valeur == null || valeur < 0) {
                violations.add(new ViolationDto("page", "doit être un entier supérieur ou égal à 0"));
            } else {
                numero = valeur;
            }
        }
        if (size != null) {
            Integer valeur = entier(size);
            if (valeur == null || valeur < 1 || valeur > Pagination.TAILLE_MAX) {
                violations.add(new ViolationDto("size",
                        "doit être un entier compris entre 1 et " + Pagination.TAILLE_MAX));
            } else {
                taille = valeur;
            }
        }
        if (!violations.isEmpty()) {
            throw new ParametresInvalidesException(violations);
        }

        var critere = new ConsulterRupturesUseCase.Critere(
                regionFiltre, produitFiltre, new Pagination(numero, taille));
        return RuptureRestMapper.versDto(consulterRuptures.consulter(acteur, critere));
    }

    @GetMapping(value = "/ruptures/{id}", produces = "application/json")
    public RuptureDto obtenir(@AuthenticationPrincipal Acteur acteur, @PathVariable String id) {
        RuptureId ruptureId;
        try {
            ruptureId = RuptureId.depuis(id);
        } catch (ValidationMetierException e) {
            throw new ParametresInvalidesException(
                    List.of(new ViolationDto("id", "doit être un UUID au format canonique")));
        }
        return RuptureRestMapper.versDto(consulterRupture.consulter(acteur, ruptureId));
    }

    @GetMapping(value = "/produits/{code}/alternatives", produces = "application/json")
    public AlternativesProduitDto alternatives(@AuthenticationPrincipal Acteur acteur, @PathVariable String code) {
        if (!code.matches(CodeProduit.FORMAT)) {
            throw new ParametresInvalidesException(
                    List.of(new ViolationDto("code", "format attendu VMP-NNNNNN")));
        }
        return RuptureRestMapper.versDto(consulterAlternatives.consulter(acteur, new CodeProduit(code)));
    }

    private static Integer entier(String texte) {
        try {
            return Integer.valueOf(texte);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
