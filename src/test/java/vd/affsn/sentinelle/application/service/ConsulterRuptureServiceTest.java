package vd.affsn.sentinelle.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vd.affsn.sentinelle.domain.Fixtures.ACTEUR_A;
import static vd.affsn.sentinelle.domain.Fixtures.PHARMACIEN;
import static vd.affsn.sentinelle.domain.Fixtures.PRESCRIPTEUR;
import static vd.affsn.sentinelle.domain.Fixtures.ruptureCloturee;
import static vd.affsn.sentinelle.domain.Fixtures.ruptureEnCours;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.application.service.Fakes.RepositoryMemoire;
import vd.affsn.sentinelle.domain.exception.RuptureIntrouvableException;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.domain.model.StatutRupture;

class ConsulterRuptureServiceTest {

    @Test
    void retourne_le_detail_d_une_rupture_en_cours_pour_tous_les_roles() {
        Rupture r = ruptureEnCours(LocalDate.of(2026, 10, 1), Region.HAUTE_PAPERASSE);
        ConsulterRuptureService service = new ConsulterRuptureService(new RepositoryMemoire().avec(r));

        for (var acteur : new vd.affsn.sentinelle.domain.model.Acteur[] {ACTEUR_A, PHARMACIEN, PRESCRIPTEUR}) {
            RuptureVue vue = service.consulter(acteur, r.id());
            assertThat(vue).isEqualTo(RuptureVue.depuis(r));
        }
    }

    @Test
    void retourne_le_detail_d_une_rupture_cloturee() {
        Rupture r = ruptureCloturee(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10), Region.COTE_DU_TIMEOUT);
        ConsulterRuptureService service = new ConsulterRuptureService(new RepositoryMemoire().avec(r));

        RuptureVue vue = service.consulter(PHARMACIEN, r.id());

        assertThat(vue.statut()).isEqualTo(StatutRupture.CLOTUREE);
        assertThat(vue.finEffective()).contains(LocalDate.of(2026, 9, 10));
    }

    @Test
    void introuvable() {
        ConsulterRuptureService service = new ConsulterRuptureService(new RepositoryMemoire());
        RuptureId inconnue = RuptureId.generer();

        assertThatThrownBy(() -> service.consulter(PHARMACIEN, inconnue))
                .isInstanceOf(RuptureIntrouvableException.class)
                .extracting(e -> ((RuptureIntrouvableException) e).ruptureId()).isEqualTo(inconnue);
    }
}
