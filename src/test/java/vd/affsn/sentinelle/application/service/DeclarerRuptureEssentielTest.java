package vd.affsn.sentinelle.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vd.affsn.sentinelle.application.service.FakesEssentiel.MSG;
import static vd.affsn.sentinelle.application.service.FakesEssentiel.VMP_100001;
import static vd.affsn.sentinelle.application.service.FakesEssentiel.VMP_200002;
import static vd.affsn.sentinelle.application.service.FakesEssentiel.VMP_300003;
import static vd.affsn.sentinelle.application.service.FakesEssentiel.VMP_400004;
import static vd.affsn.sentinelle.application.service.FakesEssentiel.VMP_999999;
import static vd.affsn.sentinelle.domain.Fixtures.ACTEUR_A;
import static vd.affsn.sentinelle.domain.Fixtures.PHARMACIEN;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import vd.affsn.sentinelle.application.port.in.DeclarerRuptureUseCase.Commande;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.application.port.out.TypeEvenementAudit;
import vd.affsn.sentinelle.application.service.Fakes.AuditEnregistreur;
import vd.affsn.sentinelle.application.service.Fakes.Journal;
import vd.affsn.sentinelle.application.service.Fakes.RepositoryMemoire;
import vd.affsn.sentinelle.application.service.FakesEssentiel.HorlogeReglable;
import vd.affsn.sentinelle.application.service.FakesEssentiel.ReferentielMemoire;
import vd.affsn.sentinelle.domain.exception.AccesRefuseException;
import vd.affsn.sentinelle.domain.exception.ValidationMetierException;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.StatutRupture;

/**
 * ADR-004, règle « alternative obligatoire » côté déclaration (cas d'acceptation au niveau du
 * service d'application). Conventions : référentiel VMP-100001 et VMP-300003 essentiels, horloge
 * 2026-11-01, entrée en vigueur absente sauf mention contraire.
 */
class DeclarerRuptureEssentielTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 1);
    private static final LocalDate DEBUT = LocalDate.of(2026, 11, 1);

    private Journal journal;
    private RepositoryMemoire repo;
    private AuditEnregistreur audit;
    private ReferentielMemoire referentiel;
    private HorlogeReglable horloge;
    private DeclarerRuptureService service;

    @BeforeEach
    void init() {
        journal = new Journal();
        repo = new RepositoryMemoire(journal);
        audit = new AuditEnregistreur(journal);
        referentiel = ReferentielMemoire.parDefaut();
        horloge = new HorlogeReglable(AUJOURDHUI);
        service = serviceEnVigueurLe(null);
    }

    private DeclarerRuptureService serviceEnVigueurLe(LocalDate entreeEnVigueur) {
        return new DeclarerRuptureService(repo, audit, horloge, referentiel, entreeEnVigueur);
    }

    private static Commande commande(CodeProduit produit, Periode periode, List<Alternative> alternatives) {
        return commande(produit, periode, EnumSet.of(Region.HAUTE_PAPERASSE), alternatives);
    }

    private static Commande commande(CodeProduit produit, Periode periode, Set<Region> regions,
                                     List<Alternative> alternatives) {
        return new Commande(produit, periode, Motif.PRODUCTION, regions, alternatives);
    }

    private static List<Alternative> uneAlternative(CodeProduit produit) {
        return List.of(new Alternative(produit, "Même principe actif"));
    }

    /** « Refusé » au sens d'ADR-004 : MSG exact, aucune écriture, aucun audit. */
    private void assertRefuse(Executable appel) {
        assertThatThrownBy(appel::execute)
                .isInstanceOf(ValidationMetierException.class)
                .hasMessage(MSG);
        assertThat(repo.sauvegardes).isEmpty();
        assertThat(repo.donnees).isEmpty();
        assertThat(audit.evenements).isEmpty();
        assertThat(journal.appels).doesNotContain("save", "audit");
    }

    private void assertAcceptee(RuptureVue vue, boolean essentiel) {
        assertThat(vue.statut()).isEqualTo(StatutRupture.EN_COURS);
        assertThat(vue.essentiel()).isEqualTo(essentiel);
        assertThat(repo.findById(vue.id())).isPresent();
    }

    // ---------------------------------------------------------------- Durée « plus de 30 jours »

    @Test
    void ac01_essentiel_30_jours_sans_alternative_accepte_et_essentiel_dans_la_vue() {
        RuptureVue vue = service.declarer(ACTEUR_A,
                commande(VMP_100001, Periode.entre(DEBUT, LocalDate.of(2026, 12, 1)), List.of()));

        assertAcceptee(vue, true);
        assertThat(audit.evenements).hasSize(1);
        assertThat(audit.evenements.get(0).type()).isEqualTo(TypeEvenementAudit.DECLARATION);
    }

    @Test
    void ac02_essentiel_31_jours_sans_alternative_refuse() {
        assertRefuse(() -> service.declarer(ACTEUR_A,
                commande(VMP_100001, Periode.entre(DEBUT, LocalDate.of(2026, 12, 2)), List.of())));
    }

    @Test
    void ac03_essentiel_31_jours_avec_une_alternative_accepte() {
        RuptureVue vue = service.declarer(ACTEUR_A,
                commande(VMP_100001, Periode.entre(DEBUT, LocalDate.of(2026, 12, 2)), uneAlternative(VMP_400004)));

        assertAcceptee(vue, true);
        assertThat(vue.alternatives()).extracting(Alternative::produit).containsExactly(VMP_400004);
    }

    @Test
    void ac04_essentiel_sans_fin_estimee_sans_alternative_refuse() {
        assertRefuse(() -> service.declarer(ACTEUR_A,
                commande(VMP_100001, Periode.aPartirDu(DEBUT), List.of())));
    }

    @Test
    void ac05_essentiel_sans_fin_estimee_avec_une_alternative_accepte() {
        RuptureVue vue = service.declarer(ACTEUR_A,
                commande(VMP_100001, Periode.aPartirDu(DEBUT), uneAlternative(VMP_400004)));

        assertAcceptee(vue, true);
    }

    @Test
    void ac06_essentiel_duree_nulle_sans_alternative_accepte() {
        RuptureVue vue = service.declarer(ACTEUR_A,
                commande(VMP_100001, Periode.entre(DEBUT, DEBUT), List.of()));

        assertAcceptee(vue, true);
    }

    @Test
    void ac07_non_essentiel_sans_fin_puis_31_jours_sans_alternative_acceptes_essentiel_faux() {
        RuptureVue sansFin = service.declarer(ACTEUR_A,
                commande(VMP_200002, Periode.aPartirDu(DEBUT), List.of()));
        RuptureVue longue = service.declarer(ACTEUR_A,
                commande(VMP_200002, Periode.entre(DEBUT, LocalDate.of(2026, 12, 2)), List.of()));

        assertAcceptee(sansFin, false);
        assertAcceptee(longue, false);
        assertThat(audit.evenements).hasSize(2);
    }

    @Test
    void ac10_debut_passe_sans_fin_estimee_refuse_la_duree_ne_depend_pas_du_jour() {
        assertRefuse(() -> service.declarer(ACTEUR_A,
                commande(VMP_100001, Periode.aPartirDu(LocalDate.of(2026, 9, 1)), List.of())));
    }

    @Test
    void ac12_six_regions_une_seule_violation_puis_une_alternative_suffit() {
        Set<Region> six = EnumSet.allOf(Region.class);
        assertThat(six).hasSize(6);

        assertRefuse(() -> service.declarer(ACTEUR_A,
                commande(VMP_100001, Periode.aPartirDu(DEBUT), six, List.of())));

        RuptureVue vue = service.declarer(ACTEUR_A,
                commande(VMP_100001, Periode.aPartirDu(DEBUT), six, uneAlternative(VMP_400004)));
        assertAcceptee(vue, true);
        assertThat(vue.regions()).hasSize(6);
    }

    @Test
    void ac14_produit_inconnu_du_referentiel_non_essentiel_accepte() {
        RuptureVue vue = service.declarer(ACTEUR_A,
                commande(VMP_999999, Periode.aPartirDu(DEBUT), List.of()));

        assertAcceptee(vue, false);
    }

    @Test
    void alternative_elle_meme_essentielle_satisfait_la_regle() {
        // Analogue de AC-11 / AC-36 côté déclaration.
        RuptureVue vue = service.declarer(ACTEUR_A,
                commande(VMP_100001, Periode.entre(DEBUT, LocalDate.of(2026, 12, 2)), uneAlternative(VMP_300003)));

        assertAcceptee(vue, true);
    }

    @Test
    void le_statut_est_lu_dans_le_referentiel_pour_le_produit_declare() {
        service.declarer(ACTEUR_A, commande(VMP_200002, Periode.aPartirDu(DEBUT), List.of()));

        assertThat(referentiel.interrogations).contains(VMP_200002);
    }

    @Test
    void ac47_resume_d_audit_inchange_sans_mention_du_caractere_essentiel() {
        service.declarer(ACTEUR_A, commande(VMP_100001,
                Periode.entre(DEBUT, LocalDate.of(2026, 12, 2)), uneAlternative(VMP_400004)));

        assertThat(audit.evenements).hasSize(1);
        assertThat(audit.evenements.get(0).type()).isEqualTo(TypeEvenementAudit.DECLARATION);
        assertThat(audit.evenements.get(0).resume()).isEqualTo("produit VMP-100001, 1 régions");
    }

    // ---------------------------------------------------------------- Ordre d'évaluation

    @Test
    void violation_de_domaine_levee_avant_la_regle_et_sans_concatenation() {
        // Pendant service de AC-13 : la règle n'est pas évaluée quand une autre violation existe.
        assertThatThrownBy(() -> service.declarer(ACTEUR_A,
                new Commande(VMP_100001, Periode.aPartirDu(DEBUT), Motif.PRODUCTION, Set.of(), List.of())))
                .isInstanceOf(ValidationMetierException.class)
                .hasMessage("Rupture : au moins une région est requise");
        assertThat(journal.appels).isEmpty();
    }

    @Test
    void motif_absent_leve_la_violation_de_motif_et_pas_la_regle() {
        assertThatThrownBy(() -> service.declarer(ACTEUR_A,
                new Commande(VMP_100001, Periode.aPartirDu(DEBUT), null, EnumSet.of(Region.HAUTE_PAPERASSE),
                        List.of())))
                .isInstanceOf(ValidationMetierException.class)
                .hasMessage("Rupture : motif obligatoire");
        assertThat(journal.appels).isEmpty();
    }

    @Test
    void non_distributeur_refuse_par_l_acces_avant_la_regle() {
        assertThatThrownBy(() -> service.declarer(PHARMACIEN,
                commande(VMP_100001, Periode.aPartirDu(DEBUT), List.of())))
                .isInstanceOf(AccesRefuseException.class);
        assertThat(journal.appels).isEmpty();
    }

    // ---------------------------------------------------------------- Référentiel indisponible

    @Test
    void ac15_referentiel_en_panne_rupture_longue_exception_propagee_rien_ecrit_rien_audite() {
        referentiel.panne = new IllegalStateException("référentiel indisponible");

        assertThatThrownBy(() -> service.declarer(ACTEUR_A,
                commande(VMP_100001, Periode.aPartirDu(DEBUT), List.of())))
                .isSameAs(referentiel.panne);

        assertThat(journal.appels).isEmpty();
        assertThat(repo.donnees).isEmpty();
        assertThat(audit.evenements).isEmpty();
    }

    @Test
    void ac15_referentiel_en_panne_quelle_que_soit_la_duree_meme_courte_avec_alternative() {
        referentiel.panne = new IllegalStateException("référentiel indisponible");

        assertThatThrownBy(() -> service.declarer(ACTEUR_A,
                commande(VMP_100001, Periode.entre(DEBUT, DEBUT.plusDays(3)), uneAlternative(VMP_400004))))
                .isSameAs(referentiel.panne);

        assertThat(journal.appels).isEmpty();
        assertThat(repo.donnees).isEmpty();
        assertThat(audit.evenements).isEmpty();
    }

    // ---------------------------------------------------------------- Entrée en vigueur

    @Nested
    class EntreeEnVigueur {

        @Test
        void ac16_la_veille_de_l_entree_en_vigueur_la_regle_ne_s_applique_pas() {
            horloge.jour = LocalDate.of(2026, 11, 30);
            DeclarerRuptureService avant = serviceEnVigueurLe(LocalDate.of(2026, 12, 1));

            RuptureVue vue = avant.declarer(ACTEUR_A,
                    commande(VMP_100001, Periode.aPartirDu(DEBUT), List.of()));

            assertAcceptee(vue, true);
        }

        @Test
        void ac17_le_jour_meme_de_l_entree_en_vigueur_la_regle_s_applique_borne_incluse() {
            horloge.jour = LocalDate.of(2026, 11, 1);
            DeclarerRuptureService jourJ = serviceEnVigueurLe(LocalDate.of(2026, 11, 1));

            assertRefuse(() -> jourJ.declarer(ACTEUR_A,
                    commande(VMP_100001, Periode.aPartirDu(DEBUT), List.of())));
        }

        @Test
        void ac20_date_absente_la_regle_est_en_vigueur_immediatement() {
            horloge.jour = LocalDate.of(2026, 11, 1);
            DeclarerRuptureService immediat = serviceEnVigueurLe(null);

            assertRefuse(() -> immediat.declarer(ACTEUR_A,
                    commande(VMP_100001, Periode.aPartirDu(DEBUT), List.of())));
        }

        @Test
        void c_est_la_date_de_l_acte_qui_compte_pas_la_date_de_debut() {
            // Début avant l'entrée en vigueur, acte après : la règle s'applique.
            horloge.jour = LocalDate.of(2026, 12, 1);
            DeclarerRuptureService apres = serviceEnVigueurLe(LocalDate.of(2026, 12, 1));

            assertRefuse(() -> apres.declarer(ACTEUR_A,
                    commande(VMP_100001, Periode.aPartirDu(LocalDate.of(2026, 11, 15)), List.of())));
        }
    }

    // ---------------------------------------------------------------- Non-régression

    @Nested
    class NonRegression {

        @Test
        void ancien_constructeur_aucun_produit_essentiel_rupture_longue_sans_alternative_acceptee() {
            DeclarerRuptureService ancien = new DeclarerRuptureService(repo, audit, horloge);

            RuptureVue vue = ancien.declarer(ACTEUR_A,
                    commande(VMP_100001, Periode.aPartirDu(DEBUT), List.of()));

            assertAcceptee(vue, false);
            assertThat(journal.appels).containsExactly("save", "audit");
        }

        @Test
        void ancien_constructeur_resume_d_audit_inchange() {
            DeclarerRuptureService ancien = new DeclarerRuptureService(repo, audit, horloge);

            ancien.declarer(ACTEUR_A, commande(VMP_100001, Periode.aPartirDu(DEBUT), uneAlternative(VMP_400004)));

            assertThat(audit.evenements.get(0).resume()).isEqualTo("produit VMP-100001, 1 régions");
        }
    }
}
