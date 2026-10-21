package vd.affsn.sentinelle.adapter.in.rest;

import vd.affsn.sentinelle.adapter.in.rest.dto.AlternativesProduitDto;
import vd.affsn.sentinelle.adapter.in.rest.dto.PageRupturesDto;
import vd.affsn.sentinelle.adapter.in.rest.dto.RuptureDto;
import vd.affsn.sentinelle.application.port.Page;
import vd.affsn.sentinelle.application.port.in.ConsulterAlternativesUseCase;
import vd.affsn.sentinelle.application.port.in.RuptureVue;

/** Conversion application vers DTO REST. STUB phase A. */
final class RuptureRestMapper {

    private RuptureRestMapper() {
    }

    static RuptureDto versDto(RuptureVue vue) {
        throw new UnsupportedOperationException("phase B");
    }

    static PageRupturesDto versDto(Page<RuptureVue> page) {
        throw new UnsupportedOperationException("phase B");
    }

    static AlternativesProduitDto versDto(ConsulterAlternativesUseCase.Resultat resultat) {
        throw new UnsupportedOperationException("phase B");
    }
}
