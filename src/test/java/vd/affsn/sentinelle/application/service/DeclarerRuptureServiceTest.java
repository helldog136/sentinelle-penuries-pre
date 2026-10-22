package vd.affsn.sentinelle.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vd.affsn.sentinelle.domain.Fixtures.ACTEUR_A;
import static vd.affsn.sentinelle.domain.Fixtures.PHARMACIEN;
import static vd.affsn.sentinelle.domain.Fixtures.PRODUIT;
import static vd.affsn.sentinelle.domain.Fixtures.PRODUIT_ALT_1;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vd.affsn.sentinelle.application.port.in.DeclarerRuptureUseCase.Commande;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.application.port.out.EvenementAudit;
import vd.affsn.sentinelle.application.port.out.TypeEvenementAudit;
import vd.affsn.sentinelle.application.service.Fakes.AuditEnregistreur;
import vd.affsn.sentinelle.application.service.Fakes.HorlogeFixe;
import vd.affsn.sentinelle.application.service.Fakes.Journal;
import vd.affsn.sentinelle.application.service.Fakes.RepositoryMemoire;
import vd.affsn.sentinelle.domain.exception.AccesRefuseException;
import vd.affsn.sentinelle.domain.exception.ValidationMetierException;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.StatutRupture;

class DeclarerRuptureServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-21T08:30:00Z");

    private Journal journal;
    private RepositoryMemoire repo;
    private AuditEnregistreur audit;
    private DeclarerRuptureService service;

    @BeforeEach
    void init() {
        journal = new Journal();
        repo = new RepositoryMemoire(journal);
        audit = new AuditEnregistreur(journal);
        service = new DeclarerRuptureService(repo, audit,
                new HorlogeFixe(MAINTENANT, LocalDate.of(2026, 10, 21)));
    }

    private Commande commande() {
        return new Commande(PRODUIT, Periode.aPartirDu(LocalDate.of(2026, 10, 20)), Motif.PRODUCTION,
                EnumSet.of(Region.HAUTE_PAPERASSE, Region.COTE_DU_TIMEOUT),
                List.of(new Alternative(PRODUIT_ALT_1, "Autre dosage")));
    }

    @Test
    void declare_sauvegarde_et_retourne_la_vue() {
        RuptureVue vue = service.declarer(ACTEUR_A, commande());

        assertThat(vue.id()).isNotNull();
        assertThat(vue.produit()).isEqualTo(PRODUIT);
        assertThat(vue.declarant()).isEqualTo(ACTEUR_A.distributeurRattache());
        assertThat(vue.statut()).isEqualTo(StatutRupture.EN_COURS);
        assertThat(vue.finEffective()).isEmpty();
        assertThat(vue.motif()).isEqualTo(Motif.PRODUCTION);
        assertThat(vue.regions()).containsExactlyInAnyOrder(Region.HAUTE_PAPERASSE, Region.COTE_DU_TIMEOUT);
        assertThat(vue.alternatives()).containsExactly(new Alternative(PRODUIT_ALT_1, "Autre dosage"));
        assertThat(repo.findById(vue.id())).isPresent();
    }

    @Test
    void chaque_declaration_recoit_un_nouvel_identifiant() {
        RuptureVue a = service.declarer(ACTEUR_A, commande());
        RuptureVue b = service.declarer(ACTEUR_A, commande());

        assertThat(a.id()).isNotEqualTo(b.id());
        assertThat(repo.donnees).hasSize(2);
    }

    @Test
    void audite_une_declaration_apres_la_sauvegarde() {
        RuptureVue vue = service.declarer(ACTEUR_A, commande());

        assertThat(journal.appels).containsExactly("save", "audit");
        assertThat(audit.evenements).hasSize(1);
        EvenementAudit e = audit.evenements.get(0);
        assertThat(e.type()).isEqualTo(TypeEvenementAudit.DECLARATION);
        assertThat(e.ruptureId()).isEqualTo(vue.id());
        assertThat(e.acteur()).isEqualTo(ACTEUR_A);
    }

    @Test
    void instant_de_l_audit_vient_de_l_horloge() {
        service.declarer(ACTEUR_A, commande());

        assertThat(audit.evenements.get(0).instant()).isEqualTo(MAINTENANT);
    }

    @Test
    void resume_court_sans_valeur_saisie_libre() {
        service.declarer(ACTEUR_A, commande());

        String resume = audit.evenements.get(0).resume();
        assertThat(resume).contains(PRODUIT.valeur()).contains("2");
        assertThat(resume).doesNotContain("Autre dosage");
        assertThat(resume.length()).isLessThanOrEqualTo(EvenementAudit.LONGUEUR_MAX_RESUME);
    }

    @Test
    void pharmacien_refuse_sans_sauvegarde_ni_audit() {
        assertThatThrownBy(() -> service.declarer(PHARMACIEN, commande()))
                .isInstanceOf(AccesRefuseException.class);

        assertThat(journal.appels).isEmpty();
        assertThat(repo.donnees).isEmpty();
        assertThat(audit.evenements).isEmpty();
    }

    @Test
    void invariant_viole_refuse_sans_sauvegarde_ni_audit() {
        Commande sansRegion = new Commande(PRODUIT, Periode.aPartirDu(LocalDate.of(2026, 10, 20)),
                Motif.PRODUCTION, EnumSet.noneOf(Region.class), List.of());

        assertThatThrownBy(() -> service.declarer(ACTEUR_A, sansRegion))
                .isInstanceOf(ValidationMetierException.class);

        assertThat(journal.appels).isEmpty();
    }

    @Test
    void echec_du_save_n_audite_pas() {
        repo.echecSave = new IllegalStateException("base indisponible");

        assertThatThrownBy(() -> service.declarer(ACTEUR_A, commande()))
                .isSameAs(repo.echecSave);

        assertThat(journal.appels).containsExactly("save");
        assertThat(audit.evenements).isEmpty();
    }

    @Test
    void echec_de_l_audit_se_propage() {
        audit.echec = new IllegalStateException("audit indisponible");

        assertThatThrownBy(() -> service.declarer(ACTEUR_A, commande()))
                .isSameAs(audit.echec);

        assertThat(journal.appels).containsExactly("save", "audit");
    }
}
