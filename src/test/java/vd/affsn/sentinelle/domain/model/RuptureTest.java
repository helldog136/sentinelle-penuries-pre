package vd.affsn.sentinelle.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vd.affsn.sentinelle.domain.Fixtures.ACTEUR_A;
import static vd.affsn.sentinelle.domain.Fixtures.ACTEUR_B;
import static vd.affsn.sentinelle.domain.Fixtures.DATE_REF;
import static vd.affsn.sentinelle.domain.Fixtures.DISTRIBUTEUR_A;
import static vd.affsn.sentinelle.domain.Fixtures.PHARMACIEN;
import static vd.affsn.sentinelle.domain.Fixtures.PRESCRIPTEUR;
import static vd.affsn.sentinelle.domain.Fixtures.PRODUIT;
import static vd.affsn.sentinelle.domain.Fixtures.PRODUIT_ALT_1;
import static vd.affsn.sentinelle.domain.Fixtures.PRODUIT_ALT_2;
import static vd.affsn.sentinelle.domain.Fixtures.regions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import vd.affsn.sentinelle.domain.exception.AccesRefuseException;
import vd.affsn.sentinelle.domain.exception.RuptureDejaClotureeException;
import vd.affsn.sentinelle.domain.exception.ValidationMetierException;

@DisplayName("Rupture (agrégat)")
class RuptureTest {

    private static final RuptureId ID = new RuptureId(java.util.UUID.fromString("00000000-0000-4000-8000-000000000001"));
    private static final LocalDate DEBUT = LocalDate.of(2026, 10, 1);
    private static final Periode PERIODE = Periode.entre(DEBUT, DEBUT.plusDays(60));
    private static final Alternative ALT_1 = new Alternative(PRODUIT_ALT_1, "Autre conditionnement");
    private static final Alternative ALT_2 = Alternative.sansCommentaire(PRODUIT_ALT_2);

    private static Rupture declareeParA() {
        return Rupture.declarer(ID, PRODUIT, ACTEUR_A, PERIODE, Motif.PRODUCTION,
                regions(Region.HAUTE_PAPERASSE, Region.COTE_DU_TIMEOUT), List.of(ALT_1));
    }

    static Stream<Acteur> acteursSansDroitDEcriture() {
        return Stream.of(PHARMACIEN, PRESCRIPTEUR);
    }

    @Nested
    @DisplayName("Déclaration")
    class Declaration {

        @Test
        @DisplayName("un distributeur déclare une rupture EN_COURS dont il est le déclarant")
        void casNominal() {
            Rupture rupture = declareeParA();

            assertThat(rupture.id()).isEqualTo(ID);
            assertThat(rupture.produit()).isEqualTo(PRODUIT);
            assertThat(rupture.declarant()).isEqualTo(DISTRIBUTEUR_A);
            assertThat(rupture.periode()).isEqualTo(PERIODE);
            assertThat(rupture.motif()).isEqualTo(Motif.PRODUCTION);
            assertThat(rupture.regions()).containsExactlyInAnyOrder(Region.HAUTE_PAPERASSE, Region.COTE_DU_TIMEOUT);
            assertThat(rupture.alternatives()).containsExactly(ALT_1);
            assertThat(rupture.statut()).isEqualTo(StatutRupture.EN_COURS);
            assertThat(rupture.finEffective()).isEmpty();
        }

        @Test
        @DisplayName("sans alternative : accepté")
        void sansAlternative() {
            Rupture rupture = Rupture.declarer(ID, PRODUIT, ACTEUR_A, PERIODE, Motif.DEMANDE,
                    regions(Region.BASSE_VALIDATION), List.of());

            assertThat(rupture.alternatives()).isEmpty();
        }

        @Test
        @DisplayName("les six régions à la fois : accepté")
        void toutesLesRegions() {
            Rupture rupture = Rupture.declarer(ID, PRODUIT, ACTEUR_A, PERIODE, Motif.LOGISTIQUE,
                    EnumSet.allOf(Region.class), List.of());

            assertThat(rupture.regions()).hasSize(6);
        }

        @Test
        @DisplayName("aucune région : refusé")
        void regionsVides() {
            assertThatThrownBy(() -> Rupture.declarer(ID, PRODUIT, ACTEUR_A, PERIODE, Motif.PRODUCTION,
                    Set.of(), List.of()))
                    .isInstanceOf(ValidationMetierException.class);
        }

        @Test
        @DisplayName("alternative identique au produit en rupture : refusé")
        void alternativeIdentiqueAuProduit() {
            Alternative memeProduit = new Alternative(new CodeProduit(PRODUIT.valeur()), "le même");

            assertThatThrownBy(() -> Rupture.declarer(ID, PRODUIT, ACTEUR_A, PERIODE, Motif.PRODUCTION,
                    regions(Region.HAUTE_PAPERASSE), List.of(ALT_1, memeProduit)))
                    .isInstanceOf(ValidationMetierException.class);
        }

        @Test
        @DisplayName("deux alternatives pour le même produit : refusé")
        void alternativesEnDoublon() {
            Alternative doublon = new Alternative(PRODUIT_ALT_1, "commentaire différent");

            assertThatThrownBy(() -> Rupture.declarer(ID, PRODUIT, ACTEUR_A, PERIODE, Motif.PRODUCTION,
                    regions(Region.HAUTE_PAPERASSE), List.of(ALT_1, doublon)))
                    .isInstanceOf(ValidationMetierException.class);
        }

        @ParameterizedTest(name = "{0} ne peut pas déclarer")
        @MethodSource("vd.affsn.sentinelle.domain.model.RuptureTest#acteursSansDroitDEcriture")
        void roleNonDistributeurRefuse(Acteur acteur) {
            assertThatThrownBy(() -> Rupture.declarer(ID, PRODUIT, acteur, PERIODE, Motif.PRODUCTION,
                    regions(Region.HAUTE_PAPERASSE), List.of()))
                    .isInstanceOf(AccesRefuseException.class);
        }

        @Test
        @DisplayName("champs obligatoires null : refusés")
        void champsObligatoiresNull() {
            Set<Region> r = regions(Region.HAUTE_PAPERASSE);
            List<Alternative> a = List.of();

            assertThatThrownBy(() -> Rupture.declarer(null, PRODUIT, ACTEUR_A, PERIODE, Motif.PRODUCTION, r, a))
                    .isInstanceOf(ValidationMetierException.class);
            assertThatThrownBy(() -> Rupture.declarer(ID, null, ACTEUR_A, PERIODE, Motif.PRODUCTION, r, a))
                    .isInstanceOf(ValidationMetierException.class);
            assertThatThrownBy(() -> Rupture.declarer(ID, PRODUIT, ACTEUR_A, null, Motif.PRODUCTION, r, a))
                    .isInstanceOf(ValidationMetierException.class);
            assertThatThrownBy(() -> Rupture.declarer(ID, PRODUIT, ACTEUR_A, PERIODE, null, r, a))
                    .isInstanceOf(ValidationMetierException.class);
            assertThatThrownBy(() -> Rupture.declarer(ID, PRODUIT, ACTEUR_A, PERIODE, Motif.PRODUCTION, null, a))
                    .isInstanceOf(ValidationMetierException.class);
            assertThatThrownBy(() -> Rupture.declarer(ID, PRODUIT, ACTEUR_A, PERIODE, Motif.PRODUCTION, r, null))
                    .isInstanceOf(ValidationMetierException.class);
        }

        @Test
        @DisplayName("acteur null : accès refusé")
        void acteurNull() {
            assertThatThrownBy(() -> Rupture.declarer(ID, PRODUIT, null, PERIODE, Motif.PRODUCTION,
                    regions(Region.HAUTE_PAPERASSE), List.of()))
                    .isInstanceOf(AccesRefuseException.class);
        }

        @Test
        @DisplayName("copie défensive : modifier les collections fournies n'altère pas la rupture")
        void copieDefensive() {
            Set<Region> regionsMutables = EnumSet.of(Region.HAUTE_PAPERASSE);
            List<Alternative> alternativesMutables = new ArrayList<>(List.of(ALT_1));

            Rupture rupture = Rupture.declarer(ID, PRODUIT, ACTEUR_A, PERIODE, Motif.PRODUCTION,
                    regionsMutables, alternativesMutables);
            regionsMutables.add(Region.MARAIS_DE_LA_RECETTE);
            alternativesMutables.add(ALT_2);

            assertThat(rupture.regions()).containsExactly(Region.HAUTE_PAPERASSE);
            assertThat(rupture.alternatives()).containsExactly(ALT_1);
        }

        @Test
        @DisplayName("les collections exposées sont non modifiables")
        void collectionsNonModifiables() {
            Rupture rupture = declareeParA();

            assertThatThrownBy(() -> rupture.regions().add(Region.MARAIS_DE_LA_RECETTE))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> rupture.alternatives().add(ALT_2))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("égalité d'entité par identifiant")
        void egaliteParIdentifiant() {
            Rupture r1 = declareeParA();
            Rupture r2 = Rupture.declarer(ID, PRODUIT_ALT_2, ACTEUR_A, Periode.aPartirDu(DEBUT), Motif.DEMANDE,
                    regions(Region.PROVINCE_DU_TICKET_OUVERT), List.of());

            assertThat(r1).isEqualTo(r2).hasSameHashCodeAs(r2);
        }
    }

    @Nested
    @DisplayName("Reconstitution depuis la persistance")
    class Reconstitution {

        @Test
        @DisplayName("une rupture clôturée est reconstituée avec sa fin effective")
        void clotureeReconstituee() {
            Rupture rupture = Rupture.reconstituer(ID, PRODUIT, DISTRIBUTEUR_A, PERIODE, Motif.REGLEMENTAIRE,
                    regions(Region.PLATEAU_DU_LEGACY), List.of(ALT_1), StatutRupture.CLOTUREE, DEBUT.plusDays(5));

            assertThat(rupture.statut()).isEqualTo(StatutRupture.CLOTUREE);
            assertThat(rupture.finEffective()).contains(DEBUT.plusDays(5));
            assertThat(rupture.declarant()).isEqualTo(DISTRIBUTEUR_A);
        }

        @Test
        @DisplayName("les invariants s'appliquent aussi : aucune région refusée")
        void invariantsAppliques() {
            assertThatThrownBy(() -> Rupture.reconstituer(ID, PRODUIT, DISTRIBUTEUR_A, PERIODE, Motif.PRODUCTION,
                    Set.of(), List.of(), StatutRupture.EN_COURS, null))
                    .isInstanceOf(ValidationMetierException.class);
        }

        @Test
        @DisplayName("CLOTUREE sans fin effective : refusé")
        void clotureeSansFin() {
            assertThatThrownBy(() -> Rupture.reconstituer(ID, PRODUIT, DISTRIBUTEUR_A, PERIODE, Motif.PRODUCTION,
                    regions(Region.HAUTE_PAPERASSE), List.of(), StatutRupture.CLOTUREE, null))
                    .isInstanceOf(ValidationMetierException.class);
        }

        @Test
        @DisplayName("EN_COURS avec fin effective : refusé")
        void enCoursAvecFin() {
            assertThatThrownBy(() -> Rupture.reconstituer(ID, PRODUIT, DISTRIBUTEUR_A, PERIODE, Motif.PRODUCTION,
                    regions(Region.HAUTE_PAPERASSE), List.of(), StatutRupture.EN_COURS, DEBUT.plusDays(1)))
                    .isInstanceOf(ValidationMetierException.class);
        }

        @Test
        @DisplayName("fin effective avant le début : refusé")
        void finEffectiveAvantDebut() {
            assertThatThrownBy(() -> Rupture.reconstituer(ID, PRODUIT, DISTRIBUTEUR_A, PERIODE, Motif.PRODUCTION,
                    regions(Region.HAUTE_PAPERASSE), List.of(), StatutRupture.CLOTUREE, DEBUT.minusDays(1)))
                    .isInstanceOf(ValidationMetierException.class);
        }
    }

    @Nested
    @DisplayName("Mise à jour")
    class MiseAJour {

        @Test
        @DisplayName("le déclarant remplace période, motif, régions et alternatives ; l'original est inchangé")
        void casNominal() {
            Rupture originale = declareeParA();
            Periode nouvellePeriode = Periode.entre(DEBUT.minusDays(2), DEBUT.plusDays(90));

            Rupture modifiee = originale.mettreAJour(ACTEUR_A, nouvellePeriode, Motif.LOGISTIQUE,
                    regions(Region.MARAIS_DE_LA_RECETTE), List.of(ALT_2));

            assertThat(modifiee.id()).isEqualTo(ID);
            assertThat(modifiee.produit()).isEqualTo(PRODUIT);
            assertThat(modifiee.declarant()).isEqualTo(DISTRIBUTEUR_A);
            assertThat(modifiee.periode()).isEqualTo(nouvellePeriode);
            assertThat(modifiee.motif()).isEqualTo(Motif.LOGISTIQUE);
            assertThat(modifiee.regions()).containsExactly(Region.MARAIS_DE_LA_RECETTE);
            assertThat(modifiee.alternatives()).containsExactly(ALT_2);
            assertThat(modifiee.statut()).isEqualTo(StatutRupture.EN_COURS);

            assertThat(originale.motif()).isEqualTo(Motif.PRODUCTION);
            assertThat(originale.regions()).containsExactlyInAnyOrder(Region.HAUTE_PAPERASSE, Region.COTE_DU_TIMEOUT);
        }

        @Test
        @DisplayName("un autre distributeur : accès refusé")
        void autreDistributeur() {
            Rupture rupture = declareeParA();

            assertThatThrownBy(() -> rupture.mettreAJour(ACTEUR_B, PERIODE, Motif.DEMANDE,
                    regions(Region.HAUTE_PAPERASSE), List.of()))
                    .isInstanceOf(AccesRefuseException.class);
        }

        @ParameterizedTest(name = "{0} ne peut pas modifier")
        @MethodSource("vd.affsn.sentinelle.domain.model.RuptureTest#acteursSansDroitDEcriture")
        void roleNonDistributeur(Acteur acteur) {
            Rupture rupture = declareeParA();

            assertThatThrownBy(() -> rupture.mettreAJour(acteur, PERIODE, Motif.DEMANDE,
                    regions(Region.HAUTE_PAPERASSE), List.of()))
                    .isInstanceOf(AccesRefuseException.class);
        }

        @Test
        @DisplayName("acteur null : accès refusé")
        void acteurNull() {
            Rupture rupture = declareeParA();

            assertThatThrownBy(() -> rupture.mettreAJour(null, PERIODE, Motif.DEMANDE,
                    regions(Region.HAUTE_PAPERASSE), List.of()))
                    .isInstanceOf(AccesRefuseException.class);
        }

        @Test
        @DisplayName("mise à jour vers aucune région : refusé")
        void regionsVides() {
            Rupture rupture = declareeParA();

            assertThatThrownBy(() -> rupture.mettreAJour(ACTEUR_A, PERIODE, Motif.DEMANDE, Set.of(), List.of()))
                    .isInstanceOf(ValidationMetierException.class);
        }

        @Test
        @DisplayName("mise à jour avec alternative identique au produit : refusé")
        void alternativeIdentique() {
            Rupture rupture = declareeParA();

            assertThatThrownBy(() -> rupture.mettreAJour(ACTEUR_A, PERIODE, Motif.DEMANDE,
                    regions(Region.HAUTE_PAPERASSE), List.of(Alternative.sansCommentaire(PRODUIT))))
                    .isInstanceOf(ValidationMetierException.class);
        }

        @Test
        @DisplayName("rupture clôturée, par le déclarant : déjà clôturée")
        void ruptureCloturee() {
            Rupture cloturee = declareeParA().cloturer(ACTEUR_A, DEBUT.plusDays(10));

            assertThatThrownBy(() -> cloturee.mettreAJour(ACTEUR_A, PERIODE, Motif.DEMANDE,
                    regions(Region.HAUTE_PAPERASSE), List.of()))
                    .isInstanceOf(RuptureDejaClotureeException.class);
        }

        @Test
        @DisplayName("rupture clôturée, par un tiers : l'accès est vérifié avant l'état")
        void ruptureClotureeParUnTiers() {
            Rupture cloturee = declareeParA().cloturer(ACTEUR_A, DEBUT.plusDays(10));

            assertThatThrownBy(() -> cloturee.mettreAJour(ACTEUR_B, PERIODE, Motif.DEMANDE,
                    regions(Region.HAUTE_PAPERASSE), List.of()))
                    .isInstanceOf(AccesRefuseException.class);
        }
    }

    @Nested
    @DisplayName("Clôture")
    class Cloture {

        @Test
        @DisplayName("le déclarant clôture : statut CLOTUREE, fin effective fixée, original inchangé")
        void casNominal() {
            Rupture originale = declareeParA();

            Rupture cloturee = originale.cloturer(ACTEUR_A, DEBUT.plusDays(10));

            assertThat(cloturee.statut()).isEqualTo(StatutRupture.CLOTUREE);
            assertThat(cloturee.finEffective()).contains(DEBUT.plusDays(10));
            assertThat(cloturee.periode()).isEqualTo(PERIODE);
            assertThat(cloturee.id()).isEqualTo(ID);
            assertThat(originale.statut()).isEqualTo(StatutRupture.EN_COURS);
            assertThat(originale.finEffective()).isEmpty();
        }

        @Test
        @DisplayName("clôture le jour même du début : acceptée (cas limite)")
        void clotureLeJourDuDebut() {
            assertThat(declareeParA().cloturer(ACTEUR_A, DEBUT).finEffective()).contains(DEBUT);
        }

        @Test
        @DisplayName("fin effective avant le début : refusée")
        void finAvantDebut() {
            Rupture rupture = declareeParA();

            assertThatThrownBy(() -> rupture.cloturer(ACTEUR_A, DEBUT.minusDays(1)))
                    .isInstanceOf(ValidationMetierException.class);
        }

        @Test
        @DisplayName("fin effective null : refusée")
        void finNull() {
            Rupture rupture = declareeParA();

            assertThatThrownBy(() -> rupture.cloturer(ACTEUR_A, null))
                    .isInstanceOf(ValidationMetierException.class);
        }

        @Test
        @DisplayName("double clôture : déjà clôturée")
        void doubleCloture() {
            Rupture cloturee = declareeParA().cloturer(ACTEUR_A, DEBUT.plusDays(10));

            assertThatThrownBy(() -> cloturee.cloturer(ACTEUR_A, DEBUT.plusDays(11)))
                    .isInstanceOf(RuptureDejaClotureeException.class);
        }

        @Test
        @DisplayName("un autre distributeur : accès refusé")
        void autreDistributeur() {
            Rupture rupture = declareeParA();

            assertThatThrownBy(() -> rupture.cloturer(ACTEUR_B, DEBUT.plusDays(10)))
                    .isInstanceOf(AccesRefuseException.class);
        }

        @ParameterizedTest(name = "{0} ne peut pas clôturer")
        @MethodSource("vd.affsn.sentinelle.domain.model.RuptureTest#acteursSansDroitDEcriture")
        void roleNonDistributeur(Acteur acteur) {
            Rupture rupture = declareeParA();

            assertThatThrownBy(() -> rupture.cloturer(acteur, DEBUT.plusDays(10)))
                    .isInstanceOf(AccesRefuseException.class);
        }
    }

    @Nested
    @DisplayName("Activité à une date")
    class Activite {

        @Test
        @DisplayName("en cours : inactive la veille du début, active le jour du début et après")
        void enCours() {
            Rupture rupture = declareeParA();

            assertThat(rupture.estActiveLe(DEBUT.minusDays(1))).isFalse();
            assertThat(rupture.estActiveLe(DEBUT)).isTrue();
            assertThat(rupture.estActiveLe(DATE_REF)).isTrue();
        }

        @Test
        @DisplayName("fin estimée dépassée mais toujours EN_COURS : reste active")
        void finEstimeeDepassee() {
            Rupture rupture = declareeParA();

            assertThat(rupture.estActiveLe(PERIODE.debut().plusDays(365))).isTrue();
        }

        @Test
        @DisplayName("clôturée : jamais active")
        void cloturee() {
            Rupture cloturee = declareeParA().cloturer(ACTEUR_A, DEBUT.plusDays(10));

            assertThat(cloturee.estActiveLe(DEBUT.plusDays(5))).isFalse();
            assertThat(cloturee.estActiveLe(DATE_REF)).isFalse();
        }
    }
}
