package vd.affsn.sentinelle.application.port.in;

import java.util.Objects;
import java.util.Optional;

import vd.affsn.sentinelle.application.port.Page;
import vd.affsn.sentinelle.application.port.Pagination;
import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.Region;

/** Liste paginée des ruptures EN COURS, filtrable par région et/ou produit. Tous rôles. */
public interface ConsulterRupturesUseCase {

    Page<RuptureVue> consulter(Acteur acteur, Critere critere);

    record Critere(Optional<Region> region, Optional<CodeProduit> produit, Pagination pagination) {

        public Critere {
            region = region == null ? Optional.empty() : region;
            produit = produit == null ? Optional.empty() : produit;
            Objects.requireNonNull(pagination, "pagination");
        }

        public static Critere sansFiltre(Pagination pagination) {
            return new Critere(Optional.empty(), Optional.empty(), pagination);
        }
    }
}
