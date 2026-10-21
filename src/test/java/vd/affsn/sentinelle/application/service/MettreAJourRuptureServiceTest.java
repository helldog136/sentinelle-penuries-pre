package vd.affsn.sentinelle.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vd.affsn.sentinelle.domain.Fixtures.ACTEUR_A;
import static vd.affsn.sentinelle.domain.Fixtures.ACTEUR_B;
import static vd.affsn.sentinelle.domain.Fixtures.PHARMACIEN;
import static vd.affsn.sentinelle.domain.Fixtures.PRODUIT;
import static vd.affsn.sentinelle.domain.Fixtures.PRODUIT_ALT_1;
import static vd.affsn.sentinelle.domain.Fixtures.PRODUIT_ALT_2;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vd.affsn.sentinelle.application.port.in.MettreAJourRuptureUseCase.Commande;
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
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.domain.model.StatutRupture;

class MettreAJourRuptureServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-21T09:00:00Z");
    private static final LocalDate DEBUT = LocalDate.of(2026, 10, 10);

    private Journal journal;
    private RepositoryMemoire repo;
    private AuditEnregistreur audit;
    private MettreAJourRuptureService service;
    private Rupture existante;

    @BeforeEach
    void init() {
        journal = new Journal();
        repo = new RepositoryMemoire(journal);
        audit = new AuditEnregistreur(journal);
        service = new MettreAJourRuptureService(repo, audit,
                new HorlogeFixe(MAINTENANT, LocalDate.of(2026, 10, 21)));
        existante = Rupture.declarer(RuptureId.generer(), PRODUIT, ACTEUR_A,
                Periode.entre(DEBUT, LocalDate.of(2026, 11, 1)), Motif.PRODUCTION,
                EnumSet.of(Region.HAUTE_PAPERASSE), List.of(new Alternative(PRODUIT_ALT_1, "avant")));
        repo.avec(existante);
    }

    private Commande commande(RuptureId id, LocalDate finEstimee) {
        return new Commande(id, finEstimee, Motif.LOGISTIQUE,
                EnumSet.of(Region.COTE_DU_TIMEOUT, Region.BASSE_VALIDATION),
                List.of(new Alternative(PRODUIT_ALT_2, "après")));
    }

    @Test
    void remplace_motif_regions_alternatives_et_conserve_le_debut() {
        RuptureVue vue = service.mettreAJour(ACTEUR_A, commande(existante.id(), LocalDate.of(2026, 12, 1)));

        assertThat(vue.id()).isEqualTo(existante.id());
        assertThat(vue.periode().debut()).isEqualTo(DEBUT);
        assertThat(vue.periode().finEstimee()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(vue.motif()).isEqualTo(Motif.LOGISTIQUE);
        assertThat(vue.regions()).containsExactlyInAnyOrder(Region.COTE_DU_TIMEOUT, Region.BASSE_VALIDATION);
        assertThat(vue.alternatives()).containsExactly(new Alternative(PRODUIT_ALT_2, "après"));
        assertThat(vue.statut()).isEqualTo(StatutRupture.EN_COURS);
        assertThat(repo.findById(existante.id()).orElseThrow().motif()).isEqualTo(Motif.LOGISTIQUE);
    }

    @Test
    void fin_estimee_nulle_efface_la_fin() {
        RuptureVue vue = service.mettreAJour(ACTEUR_A, commande(existante.id(), null));

        assertThat(vue.periode().finEstimee()).isNull();
        assertThat(vue.periode().debut()).isEqualTo(DEBUT);
    }

    @Test
    void fin_estimee_posee_quand_elle_etait_absente() {
        Rupture sansFin = Rupture.declarer(RuptureId.generer(), PRODUIT, ACTEUR_A, Periode.aPartirDu(DEBUT),
                Motif.DEMANDE, EnumSet.of(Region.HAUTE_PAPERASSE), List.of());
        repo.avec(sansFin);

        RuptureVue vue = service.mettreAJour(ACTEUR_A, commande(sansFin.id(), LocalDate.of(2026, 11, 15)));

        assertThat(vue.periode().finEstimee()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(vue.periode().debut()).isEqualTo(DEBUT);
    }

    @Test
    void audite_une_modification_apres_la_sauvegarde_avec_l_instant_de_l_horloge() {
        service.mettreAJour(ACTEUR_A, commande(existante.id(), null));

        assertThat(journal.appels).containsExactly("save", "audit");
        assertThat(audit.evenements).hasSize(1);
        EvenementAudit e = audit.evenements.get(0);
        assertThat(e.type()).isEqualTo(TypeEvenementAudit.MODIFICATION);
        assertThat(e.ruptureId()).isEqualTo(existante.id());
        assertThat(e.acteur()).isEqualTo(ACTEUR_A);
        assertThat(e.instant()).isEqualTo(MAINTENANT);
        assertThat(e.resume()).doesNotContain("après");
    }

    @Test
    void introuvable() {
        RuptureId inconnue = RuptureId.generer();

        assertThatThrownBy(() -> service.mettreAJour(ACTEUR_A, commande(inconnue, null)))
                .isInstanceOf(RuptureIntrouvableException.class);

        assertThat(journal.appels).isEmpty();
    }

    @Test
    void autre_declarant_refuse_sans_audit_ni_sauvegarde() {
        assertThatThrownBy(() -> service.mettreAJour(ACTEUR_B, commande(existante.id(), null)))
                .isInstanceOf(AccesRefuseException.class);

        assertThat(journal.appels).isEmpty();
        assertThat(repo.findById(existante.id()).orElseThrow()).isSameAs(existante);
    }

    @Test
    void pharmacien_refuse_sans_audit() {
        assertThatThrownBy(() -> service.mettreAJour(PHARMACIEN, commande(existante.id(), null)))
                .isInstanceOf(AccesRefuseException.class);

        assertThat(journal.appels).isEmpty();
    }

    @Test
    void rupture_cloturee_refusee_sans_audit() {
        Rupture cloturee = existante.cloturer(ACTEUR_A, DEBUT.plusDays(1));
        repo.avec(cloturee);

        assertThatThrownBy(() -> service.mettreAJour(ACTEUR_A, commande(existante.id(), null)))
                .isInstanceOf(RuptureDejaClotureeException.class);

        assertThat(journal.appels).isEmpty();
    }

    @Test
    void fin_estimee_avant_le_debut_enregistre_refusee_sans_audit() {
        assertThatThrownBy(() -> service.mettreAJour(ACTEUR_A, commande(existante.id(), DEBUT.minusDays(1))))
                .isInstanceOf(ValidationMetierException.class);

        assertThat(journal.appels).isEmpty();
    }

    @Test
    void echec_du_save_n_audite_pas() {
        repo.echecSave = new IllegalStateException("base indisponible");

        assertThatThrownBy(() -> service.mettreAJour(ACTEUR_A, commande(existante.id(), null)))
                .isSameAs(repo.echecSave);

        assertThat(journal.appels).containsExactly("save");
    }

    @Test
    void echec_de_l_audit_se_propage() {
        audit.echec = new IllegalStateException("audit indisponible");

        assertThatThrownBy(() -> service.mettreAJour(ACTEUR_A, commande(existante.id(), null)))
                .isSameAs(audit.echec);

        assertThat(journal.appels).containsExactly("save", "audit");
    }
}
