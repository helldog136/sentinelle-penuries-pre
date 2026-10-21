package vd.affsn.sentinelle.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vd.affsn.sentinelle.domain.Fixtures.ACTEUR_A;
import static vd.affsn.sentinelle.domain.Fixtures.ACTEUR_B;
import static vd.affsn.sentinelle.domain.Fixtures.PHARMACIEN;
import static vd.affsn.sentinelle.domain.Fixtures.PRODUIT;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vd.affsn.sentinelle.application.port.in.CloturerRuptureUseCase.Commande;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.application.port.out.EvenementAudit;
import vd.affsn.sentinelle.application.port.out.TypeEvenementAudit;
import vd.affsn.sentinelle.application.service.Fakes.AuditEnregistreur;
import vd.affsn.sentinelle.application.service.Fakes.HorlogeFixe;
import vd.affsn.sentinelle.application.service.Fakes.Journal;
import vd.affsn.sentinelle.application.service.Fakes.RepositoryMemoire;
import vd.affsn.sentinelle.domain.exception.AccesRefuseException;
import vd.affsn.sentinelle.domain.exception.RuptureDejaClotureeException;
import vd.affsn.sentinelle.domain.exception.RuptureIntrouvableException;
import vd.affsn.sentinelle.domain.exception.ValidationMetierException;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.domain.model.StatutRupture;

class CloturerRuptureServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-21T10:00:00Z");
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 21);
    private static final LocalDate DEBUT = LocalDate.of(2026, 10, 10);

    private Journal journal;
    private RepositoryMemoire repo;
    private AuditEnregistreur audit;
    private CloturerRuptureService service;
    private Rupture existante;

    @BeforeEach
    void init() {
        journal = new Journal();
        repo = new RepositoryMemoire(journal);
        audit = new AuditEnregistreur(journal);
        service = new CloturerRuptureService(repo, audit, new HorlogeFixe(MAINTENANT, AUJOURDHUI));
        existante = Rupture.declarer(RuptureId.generer(), PRODUIT, ACTEUR_A, Periode.aPartirDu(DEBUT),
                Motif.PRODUCTION, EnumSet.of(Region.HAUTE_PAPERASSE), List.of());
        repo.avec(existante);
    }

    @Test
    void cloture_a_une_date_passee() {
        LocalDate fin = LocalDate.of(2026, 10, 15);

        RuptureVue vue = service.cloturer(ACTEUR_A, new Commande(existante.id(), fin));

        assertThat(vue.statut()).isEqualTo(StatutRupture.CLOTUREE);
        assertThat(vue.finEffective()).contains(fin);
        assertThat(repo.findById(existante.id()).orElseThrow().statut()).isEqualTo(StatutRupture.CLOTUREE);
    }

    @Test
    void cloture_a_la_date_du_jour_acceptee() {
        RuptureVue vue = service.cloturer(ACTEUR_A, new Commande(existante.id(), AUJOURDHUI));

        assertThat(vue.finEffective()).contains(AUJOURDHUI);
    }

    @Test
    void date_du_lendemain_refusee_sans_sauvegarde_ni_audit() {
        assertThatThrownBy(() -> service.cloturer(ACTEUR_A,
                new Commande(existante.id(), AUJOURDHUI.plusDays(1))))
                .isInstanceOf(ValidationMetierException.class);

        assertThat(journal.appels).isEmpty();
        assertThat(repo.findById(existante.id()).orElseThrow().statut()).isEqualTo(StatutRupture.EN_COURS);
    }

    @Test
    void fin_avant_le_debut_refusee_par_le_domaine_sans_audit() {
        assertThatThrownBy(() -> service.cloturer(ACTEUR_A, new Commande(existante.id(), DEBUT.minusDays(1))))
                .isInstanceOf(ValidationMetierException.class);

        assertThat(journal.appels).isEmpty();
    }

    @Test
    void audite_une_cloture_apres_la_sauvegarde_avec_l_instant_de_l_horloge() {
        service.cloturer(ACTEUR_A, new Commande(existante.id(), AUJOURDHUI));

        assertThat(journal.appels).containsExactly("save", "audit");
        assertThat(audit.evenements).hasSize(1);
        EvenementAudit e = audit.evenements.get(0);
        assertThat(e.type()).isEqualTo(TypeEvenementAudit.CLOTURE);
        assertThat(e.ruptureId()).isEqualTo(existante.id());
        assertThat(e.acteur()).isEqualTo(ACTEUR_A);
        assertThat(e.instant()).isEqualTo(MAINTENANT);
    }

    @Test
    void introuvable() {
        assertThatThrownBy(() -> service.cloturer(ACTEUR_A, new Commande(RuptureId.generer(), AUJOURDHUI)))
                .isInstanceOf(RuptureIntrouvableException.class);

        assertThat(journal.appels).isEmpty();
    }

    @Test
    void autre_declarant_refuse_sans_audit() {
        assertThatThrownBy(() -> service.cloturer(ACTEUR_B, new Commande(existante.id(), AUJOURDHUI)))
                .isInstanceOf(AccesRefuseException.class);

        assertThat(journal.appels).isEmpty();
    }

    @Test
    void pharmacien_refuse_sans_audit() {
        assertThatThrownBy(() -> service.cloturer(PHARMACIEN, new Commande(existante.id(), AUJOURDHUI)))
                .isInstanceOf(AccesRefuseException.class);

        assertThat(journal.appels).isEmpty();
    }

    @Test
    void double_cloture_refusee_sans_second_audit() {
        service.cloturer(ACTEUR_A, new Commande(existante.id(), AUJOURDHUI));
        journal.appels.clear();

        assertThatThrownBy(() -> service.cloturer(ACTEUR_A, new Commande(existante.id(), AUJOURDHUI)))
                .isInstanceOf(RuptureDejaClotureeException.class);

        assertThat(journal.appels).isEmpty();
        assertThat(audit.evenements).hasSize(1);
    }

    @Test
    void echec_du_save_n_audite_pas() {
        repo.echecSave = new IllegalStateException("base indisponible");

        assertThatThrownBy(() -> service.cloturer(ACTEUR_A, new Commande(existante.id(), AUJOURDHUI)))
                .isSameAs(repo.echecSave);

        assertThat(journal.appels).containsExactly("save");
    }

    @Test
    void echec_de_l_audit_se_propage() {
        audit.echec = new IllegalStateException("audit indisponible");

        assertThatThrownBy(() -> service.cloturer(ACTEUR_A, new Commande(existante.id(), AUJOURDHUI)))
                .isSameAs(audit.echec);

        assertThat(journal.appels).containsExactly("save", "audit");
    }
}
