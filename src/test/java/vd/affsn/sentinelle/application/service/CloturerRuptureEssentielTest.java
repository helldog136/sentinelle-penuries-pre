package vd.affsn.sentinelle.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static vd.affsn.sentinelle.application.service.FakesEssentiel.VMP_100001;
import static vd.affsn.sentinelle.application.service.FakesEssentiel.VMP_400004;
import static vd.affsn.sentinelle.domain.Fixtures.ACTEUR_A;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vd.affsn.sentinelle.application.port.in.CloturerRuptureUseCase.Commande;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.application.port.out.TypeEvenementAudit;
import vd.affsn.sentinelle.application.service.Fakes.AuditEnregistreur;
import vd.affsn.sentinelle.application.service.Fakes.Journal;
import vd.affsn.sentinelle.application.service.Fakes.RepositoryMemoire;
import vd.affsn.sentinelle.application.service.FakesEssentiel.HorlogeReglable;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.domain.model.StatutRupture;

/**
 * ADR-004 § 3 : la clôture n'est jamais bloquée par la règle d'alternative obligatoire, même sur
 * une rupture longue non conforme d'un produit essentiel. Rupture {@code R} : VMP-100001, début
 * 2026-10-01, sans fin estimée ; horloge 2026-11-01.
 */
class CloturerRuptureEssentielTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 1);
    private static final LocalDate DEBUT_R = LocalDate.of(2026, 10, 1);

    private Journal journal;
    private RepositoryMemoire repo;
    private AuditEnregistreur audit;
    private CloturerRuptureService service;

    @BeforeEach
    void init() {
        journal = new Journal();
        repo = new RepositoryMemoire(journal);
        audit = new AuditEnregistreur(journal);
        service = new CloturerRuptureService(repo, audit, new HorlogeReglable(AUJOURDHUI));
    }

    private Rupture existante(List<Alternative> alternatives) {
        Rupture r = Rupture.declarer(RuptureId.generer(), VMP_100001, ACTEUR_A, Periode.aPartirDu(DEBUT_R),
                Motif.PRODUCTION, EnumSet.of(Region.HAUTE_PAPERASSE), alternatives);
        repo.avec(r);
        return r;
    }

    @Test
    void ac28_cloture_d_une_rupture_longue_non_conforme_acceptee() {
        Rupture r = existante(List.of());

        RuptureVue vue = service.cloturer(ACTEUR_A, new Commande(r.id(), AUJOURDHUI));

        assertThat(vue.statut()).isEqualTo(StatutRupture.CLOTUREE);
        assertThat(vue.finEffective()).isEqualTo(Optional.of(AUJOURDHUI));
        assertThat(vue.alternatives()).isEmpty();
        assertThat(repo.findById(r.id()).orElseThrow().statut()).isEqualTo(StatutRupture.CLOTUREE);
        assertThat(journal.appels).containsExactly("save", "audit");
        assertThat(audit.evenements.get(0).type()).isEqualTo(TypeEvenementAudit.CLOTURE);
    }

    @Test
    void ac29_cloture_d_une_rupture_conforme_acceptee() {
        Rupture r = existante(List.of(new Alternative(VMP_400004, "Même principe actif")));

        RuptureVue vue = service.cloturer(ACTEUR_A, new Commande(r.id(), AUJOURDHUI));

        assertThat(vue.statut()).isEqualTo(StatutRupture.CLOTUREE);
        assertThat(journal.appels).containsExactly("save", "audit");
    }
}
