package vd.affsn.sentinelle.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vd.affsn.sentinelle.application.service.FakesEssentiel.MSG;
import static vd.affsn.sentinelle.application.service.FakesEssentiel.VMP_100001;
import static vd.affsn.sentinelle.application.service.FakesEssentiel.VMP_200002;
import static vd.affsn.sentinelle.application.service.FakesEssentiel.VMP_300003;
import static vd.affsn.sentinelle.application.service.FakesEssentiel.VMP_400004;
import static vd.affsn.sentinelle.domain.Fixtures.ACTEUR_A;
import static vd.affsn.sentinelle.domain.Fixtures.ACTEUR_B;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import vd.affsn.sentinelle.application.port.in.MettreAJourRuptureUseCase.Commande;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.application.port.out.EvenementAudit;
import vd.affsn.sentinelle.application.port.out.TypeEvenementAudit;
import vd.affsn.sentinelle.application.service.Fakes.AuditEnregistreur;
import vd.affsn.sentinelle.application.service.Fakes.Journal;
import vd.affsn.sentinelle.application.service.Fakes.RepositoryMemoire;
import vd.affsn.sentinelle.application.service.FakesEssentiel.HorlogeReglable;
import vd.affsn.sentinelle.application.service.FakesEssentiel.ReferentielMemoire;
import vd.affsn.sentinelle.domain.exception.AccesRefuseException;
import vd.affsn.sentinelle.domain.exception.RuptureDejaClotureeException;
import vd.affsn.sentinelle.domain.exception.RuptureIntrouvableException;
import vd.affsn.sentinelle.domain.exception.ValidationMetierException;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.domain.model.StatutRupture;

/**
 * ADR-004, règle « alternative obligatoire » appliquée à l'état résultant d'une mise à jour.
 * Rupture préexistante {@code R} : VMP-100001 (essentiel), début 2026-10-01, EN_COURS, déclarée
 * par le distributeur A. Horloge 2026-11-01, entrée en vigueur absente sauf mention contraire.
 */
class MettreAJourRuptureEssentielTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 1);
    private static final LocalDate DEBUT_R = LocalDate.of(2026, 10, 1);
    private static final LocalDate DEBUT_R_PLUS_30 = LocalDate.of(2026, 10, 31);
    private static final LocalDate DEBUT_R_PLUS_31 = LocalDate.of(2026, 11, 1);

    private Journal journal;
    private RepositoryMemoire repo;
    private AuditEnregistreur audit;
    private ReferentielMemoire referentiel;
    private HorlogeReglable horloge;
    private MettreAJourRuptureService service;

    @BeforeEach
    void init() {
        journal = new Journal();
        repo = new RepositoryMemoire(journal);
        audit = new AuditEnregistreur(journal);
        referentiel = ReferentielMemoire.parDefaut();
        horloge = new HorlogeReglable(AUJOURDHUI);
        service = serviceEnVigueurLe(null);
    }

    private MettreAJourRuptureService serviceEnVigueurLe(LocalDate entreeEnVigueur) {
        return new MettreAJourRuptureService(repo, audit, horloge, referentiel, entreeEnVigueur);
    }

    private static List<Alternative> uneAlternative(CodeProduit produit) {
        return List.of(new Alternative(produit, "Même principe actif"));
    }

    /** Pré-charge une rupture en cours de A (sans passer par save : n'apparaît pas au journal). */
    private Rupture existante(CodeProduit produit, LocalDate finEstimee, Set<Region> regions,
                              List<Alternative> alternatives) {
        Rupture r = Rupture.declarer(RuptureId.generer(), produit, ACTEUR_A, new Periode(DEBUT_R, finEstimee),
                Motif.PRODUCTION, regions, alternatives);
        repo.avec(r);
        return r;
    }

    private Rupture existante(LocalDate finEstimee, List<Alternative> alternatives) {
        return existante(VMP_100001, finEstimee, EnumSet.of(Region.HAUTE_PAPERASSE), alternatives);
    }

    /** {@code R} comme AC-25 : sans fin estimée ni alternative (non conforme, tolérée). */
    private Rupture nonConforme() {
        return existante(null, List.of());
    }

    private static Commande commande(Rupture r, LocalDate finEstimee, List<Alternative> alternatives) {
        return new Commande(r.id(), finEstimee, Motif.PRODUCTION, EnumSet.of(Region.HAUTE_PAPERASSE), alternatives);
    }

    /** Mise à jour renvoyant exactement l'état enregistré. */
    private static Commande identique(Rupture r) {
        return new Commande(r.id(), r.periode().finEstimee(), r.motif(), r.regions(), r.alternatives());
    }

    /** « Refusé » : MSG exact, rupture enregistrée inchangée, aucun save, aucun audit. */
    private void assertRefuse(Rupture avant, Executable appel) {
        int evenementsAvant = audit.evenements.size();
        int sauvegardesAvant = repo.sauvegardes.size();

        assertThatThrownBy(appel::execute)
                .isInstanceOf(ValidationMetierException.class)
                .hasMessage(MSG);

        assertThat(repo.sauvegardes).hasSize(sauvegardesAvant);
        assertThat(audit.evenements).hasSize(evenementsAvant);
        assertThat(repo.findById(avant.id()).orElseThrow()).isSameAs(avant);
    }

    private void assertAcceptee(Rupture avant, RuptureVue vue, boolean essentiel) {
        assertThat(vue.id()).isEqualTo(avant.id());
        assertThat(vue.statut()).isEqualTo(StatutRupture.EN_COURS);
        assertThat(vue.essentiel()).isEqualTo(essentiel);
        assertThat(repo.findById(avant.id()).orElseThrow()).isNotSameAs(avant);
        assertThat(journal.appels).containsSubsequence("save", "audit");
    }

    // ---------------------------------------------------------------- État résultant

    @Test
    void ac21_retrait_des_alternatives_d_une_rupture_longue_refuse_R_garde_son_alternative() {
        Rupture r = existante(null, uneAlternative(VMP_400004));

        assertRefuse(r, () -> service.mettreAJour(ACTEUR_A, commande(r, null, List.of())));

        assertThat(repo.findById(r.id()).orElseThrow().alternatives())
                .extracting(Alternative::produit).containsExactly(VMP_400004);
        assertThat(journal.appels).isEmpty();
    }

    @Test
    void ac22_allongement_de_30_a_31_jours_sans_alternative_refuse() {
        Rupture r = existante(DEBUT_R_PLUS_30, List.of());

        assertRefuse(r, () -> service.mettreAJour(ACTEUR_A, commande(r, DEBUT_R_PLUS_31, List.of())));
    }

    @Test
    void ac23_effacement_de_la_fin_estimee_sans_alternative_refuse() {
        Rupture r = existante(DEBUT_R_PLUS_30, List.of());

        assertRefuse(r, () -> service.mettreAJour(ACTEUR_A, commande(r, null, List.of())));
    }

    @Test
    void ac24_raccourcissement_a_30_jours_et_retrait_des_alternatives_accepte() {
        Rupture r = existante(null, uneAlternative(VMP_400004));

        RuptureVue vue = service.mettreAJour(ACTEUR_A, commande(r, DEBUT_R_PLUS_30, List.of()));

        assertAcceptee(r, vue, true);
        assertThat(vue.periode()).isEqualTo(Periode.entre(DEBUT_R, DEBUT_R_PLUS_30));
        assertThat(vue.alternatives()).isEmpty();
    }

    @Test
    void ac26_mise_a_jour_identique_d_une_rupture_existante_non_conforme_refusee() {
        Rupture r = nonConforme();

        assertRefuse(r, () -> service.mettreAJour(ACTEUR_A, identique(r)));
        assertThat(journal.appels).isEmpty();
    }

    @Test
    void ac27_mise_en_conformite_avec_une_alternative_acceptee_et_auditee() {
        Rupture r = nonConforme();

        RuptureVue vue = service.mettreAJour(ACTEUR_A, commande(r, null, uneAlternative(VMP_400004)));

        assertAcceptee(r, vue, true);
        assertThat(audit.evenements).hasSize(1);
        EvenementAudit e = audit.evenements.get(0);
        assertThat(e.type()).isEqualTo(TypeEvenementAudit.MODIFICATION);
        assertThat(e.ruptureId()).isEqualTo(r.id());
        assertThat(e.resume()).isEqualTo("produit VMP-100001, 1 régions");
    }

    @Test
    void ac33_changement_de_regions_sans_alternative_refuse_une_alternative_suffit_quel_que_soit_le_nombre() {
        Rupture r = existante(null, uneAlternative(VMP_400004));

        assertRefuse(r, () -> service.mettreAJour(ACTEUR_A, new Commande(r.id(), null, Motif.PRODUCTION,
                EnumSet.of(Region.HAUTE_PAPERASSE, Region.COTE_DU_TIMEOUT), List.of())));

        RuptureVue vue = service.mettreAJour(ACTEUR_A, new Commande(r.id(), null, Motif.PRODUCTION,
                EnumSet.allOf(Region.class), uneAlternative(VMP_400004)));
        assertAcceptee(r, vue, true);
    }

    @Test
    void ac34_produit_retire_du_referentiel_la_regle_ne_s_applique_plus() {
        Rupture r = nonConforme();
        referentiel.retirer(VMP_100001);

        RuptureVue vue = service.mettreAJour(ACTEUR_A, commande(r, null, List.of()));

        assertAcceptee(r, vue, false);
    }

    @Test
    void ac35_produit_reclasse_essentiel_apres_declaration_identique_refusee_puis_conforme_acceptee() {
        Rupture s = existante(VMP_200002, null, EnumSet.of(Region.HAUTE_PAPERASSE), List.of());
        referentiel.ajouter(VMP_200002);

        assertRefuse(s, () -> service.mettreAJour(ACTEUR_A, identique(s)));

        RuptureVue vue = service.mettreAJour(ACTEUR_A, commande(s, null, uneAlternative(VMP_400004)));
        assertAcceptee(s, vue, true);
    }

    @Test
    void ac36_alternative_remplacee_par_une_alternative_elle_meme_essentielle_acceptee() {
        Rupture r = existante(null, uneAlternative(VMP_400004));

        RuptureVue vue = service.mettreAJour(ACTEUR_A, commande(r, null, uneAlternative(VMP_300003)));

        assertAcceptee(r, vue, true);
        assertThat(vue.alternatives()).extracting(Alternative::produit).containsExactly(VMP_300003);
    }

    @Test
    void le_debut_retenu_est_le_debut_enregistre_pas_la_date_du_jour() {
        // Début 2026-10-01, fin 2026-11-15 : 45 jours depuis le début enregistré (14 depuis aujourd'hui).
        Rupture r = existante(null, uneAlternative(VMP_400004));

        assertRefuse(r, () -> service.mettreAJour(ACTEUR_A,
                commande(r, LocalDate.of(2026, 11, 15), List.of())));
    }

    @Test
    void produit_non_essentiel_longue_sans_alternative_acceptee_essentiel_faux() {
        Rupture s = existante(VMP_200002, null, EnumSet.of(Region.HAUTE_PAPERASSE), uneAlternative(VMP_400004));

        RuptureVue vue = service.mettreAJour(ACTEUR_A, commande(s, null, List.of()));

        assertAcceptee(s, vue, false);
    }

    // ---------------------------------------------------------------- Ordre d'évaluation

    @Nested
    class OrdreEvaluation {

        @Test
        void ac30_tiers_acces_refuse_avant_la_regle() {
            Rupture r = nonConforme();

            assertThatThrownBy(() -> service.mettreAJour(ACTEUR_B, commande(r, null, List.of())))
                    .isInstanceOf(AccesRefuseException.class);

            assertThat(journal.appels).isEmpty();
            assertThat(repo.findById(r.id()).orElseThrow()).isSameAs(r);
        }

        @Test
        void ac31_rupture_cloturee_deja_cloturee_avant_la_regle() {
            Rupture cloturee = nonConforme().cloturer(ACTEUR_A, LocalDate.of(2026, 10, 20));
            repo.avec(cloturee);

            assertThatThrownBy(() -> service.mettreAJour(ACTEUR_A, commande(cloturee, null, List.of())))
                    .isInstanceOf(RuptureDejaClotureeException.class);

            assertThat(journal.appels).isEmpty();
        }

        @Test
        void ac32_rupture_inconnue_introuvable_avant_la_regle() {
            RuptureId inconnue = RuptureId.generer();

            assertThatThrownBy(() -> service.mettreAJour(ACTEUR_A, new Commande(inconnue, null, Motif.PRODUCTION,
                    EnumSet.of(Region.HAUTE_PAPERASSE), List.of())))
                    .isInstanceOf(RuptureIntrouvableException.class);

            assertThat(journal.appels).isEmpty();
        }

        @Test
        void fin_estimee_avant_le_debut_enregistre_levee_avant_la_regle() {
            Rupture r = nonConforme();

            assertThatThrownBy(() -> service.mettreAJour(ACTEUR_A,
                    commande(r, DEBUT_R.minusDays(1), List.of())))
                    .isInstanceOf(ValidationMetierException.class)
                    .hasMessage("Période : la fin estimée ne peut pas précéder le début");

            assertThat(journal.appels).isEmpty();
        }

        @Test
        void referentiel_en_panne_exception_propagee_rien_ecrit_rien_audite() {
            Rupture r = existante(null, uneAlternative(VMP_400004));
            referentiel.panne = new IllegalStateException("référentiel indisponible");

            assertThatThrownBy(() -> service.mettreAJour(ACTEUR_A, commande(r, null, uneAlternative(VMP_400004))))
                    .isSameAs(referentiel.panne);

            assertThat(journal.appels).isEmpty();
            assertThat(repo.findById(r.id()).orElseThrow()).isSameAs(r);
        }
    }

    // ---------------------------------------------------------------- Entrée en vigueur

    @Nested
    class EntreeEnVigueur {

        @Test
        void ac18_identique_acceptee_la_veille_puis_refusee_le_jour_de_l_entree_en_vigueur() {
            Rupture r = nonConforme();
            horloge.jour = LocalDate.of(2026, 11, 30);
            MettreAJourRuptureService avecDate = serviceEnVigueurLe(LocalDate.of(2026, 12, 1));

            RuptureVue veille = avecDate.mettreAJour(ACTEUR_A, identique(r));
            assertAcceptee(r, veille, true);
            Rupture apresVeille = repo.findById(r.id()).orElseThrow();

            horloge.jour = LocalDate.of(2026, 12, 1);
            assertRefuse(apresVeille, () -> avecDate.mettreAJour(ACTEUR_A, identique(apresVeille)));

            assertThat(audit.evenements).hasSize(1);
            assertThat(repo.sauvegardes).hasSize(1);
        }

        @Test
        void date_absente_regle_immediate_sur_la_mise_a_jour() {
            Rupture r = nonConforme();

            assertRefuse(r, () -> serviceEnVigueurLe(null).mettreAJour(ACTEUR_A, identique(r)));
        }
    }

    // ---------------------------------------------------------------- Non-régression

    @Nested
    class NonRegression {

        @Test
        void ancien_constructeur_identique_non_conforme_acceptee_sans_regle() {
            Rupture r = nonConforme();
            MettreAJourRuptureService ancien = new MettreAJourRuptureService(repo, audit, horloge);

            RuptureVue vue = ancien.mettreAJour(ACTEUR_A, identique(r));

            assertAcceptee(r, vue, false);
            assertThat(journal.appels).containsExactly("save", "audit");
        }

        @Test
        void ancien_constructeur_retrait_des_alternatives_accepte() {
            Rupture r = existante(null, uneAlternative(VMP_400004));
            MettreAJourRuptureService ancien = new MettreAJourRuptureService(repo, audit, horloge);

            RuptureVue vue = ancien.mettreAJour(ACTEUR_A, commande(r, null, List.of()));

            assertAcceptee(r, vue, false);
            assertThat(vue.alternatives()).isEmpty();
        }
    }
}
