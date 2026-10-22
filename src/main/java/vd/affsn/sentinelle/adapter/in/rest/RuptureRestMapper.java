package vd.affsn.sentinelle.adapter.in.rest;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import vd.affsn.sentinelle.adapter.in.rest.dto.AlternativeDto;
import vd.affsn.sentinelle.adapter.in.rest.dto.AlternativeProposeeDto;
import vd.affsn.sentinelle.adapter.in.rest.dto.AlternativesProduitDto;
import vd.affsn.sentinelle.adapter.in.rest.dto.PageRupturesDto;
import vd.affsn.sentinelle.adapter.in.rest.dto.RuptureDto;
import vd.affsn.sentinelle.application.port.Page;
import vd.affsn.sentinelle.application.port.in.ConsulterAlternativesUseCase;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.Region;

/** Conversion application vers DTO REST (enums sans préfixe, dates AAAA-MM-JJ). */
final class RuptureRestMapper {

    private RuptureRestMapper() {
    }

    static RuptureDto versDto(RuptureVue vue) {
        List<String> regions = vue.regions().stream()
                .sorted(Comparator.comparing(Region::ordinal))
                .map(Region::name)
                .toList();
        return new RuptureDto(
                vue.id().toString(),
                vue.produit().valeur(),
                vue.periode().debut().toString(),
                texte(vue.periode().finEstimee()),
                vue.finEffective().map(LocalDate::toString).orElse(null),
                vue.motif().name(),
                regions,
                vue.alternatives().stream().map(RuptureRestMapper::versDto).toList(),
                vue.statut().name(),
                vue.declarant().valeur());
    }

    static PageRupturesDto versDto(Page<RuptureVue> page) {
        return new PageRupturesDto(
                page.elements().stream().map(RuptureRestMapper::versDto).toList(),
                page.pagination().numero(),
                page.pagination().taille(),
                page.totalElements(),
                Math.toIntExact(page.totalPages()));
    }

    static AlternativesProduitDto versDto(ConsulterAlternativesUseCase.Resultat resultat) {
        return new AlternativesProduitDto(
                resultat.produit().valeur(),
                resultat.alternatives().stream()
                        .map(a -> new AlternativeProposeeDto(
                                a.alternative().produit().valeur(),
                                a.alternative().commentaire(),
                                a.ruptureId().toString()))
                        .toList());
    }

    private static AlternativeDto versDto(Alternative alternative) {
        return new AlternativeDto(alternative.produit().valeur(), alternative.commentaire());
    }

    private static String texte(LocalDate date) {
        return date == null ? null : date.toString();
    }
}
