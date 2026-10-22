package vd.affsn.sentinelle.adapter.in.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Status;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import vd.affsn.sentinelle.application.port.in.MettreAJourRuptureUseCase;
import vd.affsn.sentinelle.domain.exception.AccesRefuseException;
import vd.affsn.sentinelle.domain.exception.RuptureDejaClotureeException;
import vd.affsn.sentinelle.domain.exception.RuptureIntrouvableException;
import vd.affsn.sentinelle.domain.exception.ValidationMetierException;
import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.domain.model.StatutRupture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vd.affsn.sentinelle.grpc.v1.MettreAJourRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.MotifRupture;

/**
 * MettreAJourRupture : cas passants, validation (arrêt AVANT le cas d'usage), authentification,
 * rôle et mapping des exceptions du cas d'usage. Le plafond de 730 jours et la comparaison au
 * début ne s'appliquent pas ici : « fin >= début » relève du domaine via le cas d'usage.
 */
class MettreAJourRuptureValidationGrpcTest extends AbstractSignalementGrpcTest {

    private static MettreAJourRuptureRequest.Builder requeteValide() {
        return MettreAJourRuptureRequest.newBuilder()
                .setId(UUID_VALIDE)
                .setMotif(MotifRupture.MOTIF_RUPTURE_DEMANDE)
                .addRegions(PROTO_HAUTE_PAPERASSE);
    }

    private void mettreAJour(MettreAJourRuptureRequest.Builder requete) {
        client.mettreAJourRupture(requete.build());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "pas-un-uuid", "123E4567-E89B-42D3-A456-426614174000", " 123e4567-e89b-42d3-a456-426614174000",
            "123e4567e89b42d3a456426614174000"})
    void identifiantQuiNEstPasUnUuidCanoniqueEnMinusculesEstRefuse(String id) {
        assertInvalide(() -> mettreAJour(requeteValide().setId(id)), "id");
    }

    @Test
    void motifNonSpecifieEstRefuse() {
        assertInvalide(() -> mettreAJour(requeteValide().setMotif(MotifRupture.MOTIF_RUPTURE_UNSPECIFIED)),
                "motif");
    }

    @Test
    void aucuneRegionEstRefusee() {
        assertInvalide(() -> mettreAJour(requeteValide().clearRegions()), "regions");
    }

    @Test
    void regionEnDoublonEstRefusee() {
        assertInvalide(() -> mettreAJour(requeteValide().addRegions(PROTO_HAUTE_PAPERASSE)), "regions");
    }

    @Test
    void regionNonSpecifieeEstRefusee() {
        assertInvalide(() -> mettreAJour(requeteValide()
                .addRegions(vd.affsn.sentinelle.grpc.v1.Region.REGION_UNSPECIFIED)), "regions");
    }

    @Test
    void vingtEtUneAlternativesSontRefusees() {
        MettreAJourRuptureRequest.Builder requete = requeteValide();
        IntStream.range(0, 21)
                .forEach(i -> requete.addAlternatives(alternativeProto("VMP-%06d".formatted(200000 + i))));
        assertInvalide(() -> mettreAJour(requete), "alternatives");
    }

    @Test
    void alternativeEnDoublonEstRefusee() {
        assertInvalide(() -> mettreAJour(requeteValide()
                .addAlternatives(alternativeProto("VMP-654321"))
                .addAlternatives(alternativeProto("VMP-654321"))), "alternatives[1].code_produit");
    }

    @Test
    void commentaireDe281CaracteresEstRefuse() {
        assertInvalide(() -> mettreAJour(requeteValide()
                .addAlternatives(alternativeProto("VMP-654321", "a".repeat(281)))),
                "alternatives[0].commentaire");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "2026-6-20", "2026/06/20", "20260620", "2026-06-20T00:00:00", "2026-02-30"})
    void dateFinEstimeeHorsFormatStrictEstRefusee(String date) {
        assertInvalide(() -> mettreAJour(requeteValide().setDateFinEstimee(date)), "date_fin_estimee");
    }

    @Test
    void laValidationPrecedeLExistenceDeLaRupture() {
        // Message invalide ET rupture éventuellement inconnue : INVALID_ARGUMENT, pas NOT_FOUND.
        assertCode(() -> mettreAJour(requeteValide().clearRegions()), Status.Code.INVALID_ARGUMENT);
        assertAucunCasDUsageAppele();
    }

    @Test
    void commentaireAvecSautDeLigneEstRefuse() {
        assertInvalide(() -> mettreAJour(requeteValide()
                .addAlternatives(alternativeProto("VMP-654321", "ligne1\nligne2"))),
                "alternatives[0].commentaire");
    }

    // --------------------------------------------------------------- cas passants

    @Test
    void miseAJourAvecFinEstimeeMappeLaCommandeAvecLActeurDuContexte() {
        LocalDate fin = AUJOURDHUI.plusDays(40);
        when(mettreAJour.mettreAJour(any(), any())).thenReturn(vue(StatutRupture.EN_COURS, null));

        client.mettreAJourRupture(requeteValide()
                .setDateFinEstimee(fin.toString())
                .setMotif(MotifRupture.MOTIF_RUPTURE_REGLEMENTAIRE)
                .addRegions(PROTO_PROVINCE)
                .addAlternatives(alternativeProto("VMP-654321", "  Boîte de 30  "))
                .addAlternatives(alternativeProto("VMP-111111"))
                .build());

        ArgumentCaptor<MettreAJourRuptureUseCase.Commande> commande =
                ArgumentCaptor.forClass(MettreAJourRuptureUseCase.Commande.class);
        verify(mettreAJour).mettreAJour(eq(distributeurCourant()), commande.capture());
        assertThat(commande.getValue()).isEqualTo(new MettreAJourRuptureUseCase.Commande(
                RuptureId.depuis(UUID_VALIDE),
                fin,
                Motif.REGLEMENTAIRE,
                Set.of(Region.HAUTE_PAPERASSE, Region.PROVINCE_DU_TICKET_OUVERT),
                java.util.List.of(
                        new Alternative(CodeProduit.de("VMP-654321"), "Boîte de 30"),
                        Alternative.sansCommentaire(CodeProduit.de("VMP-111111")))));
    }

    @Test
    void miseAJourSansFinEstimeeTransmetNullEtListeVide() {
        when(mettreAJour.mettreAJour(any(), any())).thenReturn(vue(StatutRupture.EN_COURS, null));

        client.mettreAJourRupture(requeteValide().build());

        ArgumentCaptor<MettreAJourRuptureUseCase.Commande> commande =
                ArgumentCaptor.forClass(MettreAJourRuptureUseCase.Commande.class);
        verify(mettreAJour).mettreAJour(any(), commande.capture());
        assertThat(commande.getValue().finEstimee()).isNull();
        assertThat(commande.getValue().alternatives()).isEmpty();
        assertThat(commande.getValue().motif()).isEqualTo(Motif.DEMANDE);
    }

    @Test
    void laReponseReprendLaRuptureDuDomaineConformementAuProto() {
        when(mettreAJour.mettreAJour(any(), any())).thenReturn(vue(StatutRupture.EN_COURS, null));

        vd.affsn.sentinelle.grpc.v1.Rupture rupture =
                client.mettreAJourRupture(requeteValide().build()).getRupture();

        assertThat(rupture.getId()).isEqualTo(UUID_VALIDE);
        assertThat(rupture.getCodeProduit()).isEqualTo("VMP-123456");
        assertThat(rupture.getDateDebut()).isEqualTo(AUJOURDHUI.minusDays(3).toString());
        assertThat(rupture.getDateFinEstimee()).isEqualTo(AUJOURDHUI.plusDays(30).toString());
        assertThat(rupture.hasDateFinEffective()).isFalse();
        assertThat(rupture.getMotif()).isEqualTo(MotifRupture.MOTIF_RUPTURE_LOGISTIQUE);
        assertThat(rupture.getRegionsList()).containsExactly(PROTO_HAUTE_PAPERASSE, PROTO_PROVINCE);
        assertThat(rupture.getAlternativesList()).containsExactly(
                alternativeProto("VMP-654321", "Boîte de 30"), alternativeProto("VMP-111111"));
        assertThat(rupture.getStatut())
                .isEqualTo(vd.affsn.sentinelle.grpc.v1.StatutRupture.STATUT_RUPTURE_EN_COURS);
        assertThat(rupture.getDistributeurId()).isEqualTo("PRO-000042");
    }

    // ------------------------------------------- authentification, rôle, exceptions

    @Test
    void sansActeurDansLeContexteEstNonAuthentifie() {
        acteurAuthentifie.set(null);
        assertCode(() -> mettreAJour(requeteValide()), Status.Code.UNAUTHENTICATED);
        assertAucunCasDUsageAppele();
    }

    @Test
    void unPharmacienQuiEcritEstRefuse() {
        acteurAuthentifie.set(Acteur.pharmacien("pharmacien-fictif-01"));
        when(mettreAJour.mettreAJour(any(), any())).thenThrow(new AccesRefuseException("rôle"));
        assertCode(() -> mettreAJour(requeteValide()), Status.Code.PERMISSION_DENIED);
    }

    static Stream<Arguments> exceptionsDuDomaine() {
        return Stream.of(
                Arguments.of(new ValidationMetierException("Période : la fin estimée ne peut pas précéder le début"),
                        Status.Code.INVALID_ARGUMENT),
                Arguments.of(new AccesRefuseException("Seul le distributeur déclarant peut modifier cette rupture"),
                        Status.Code.PERMISSION_DENIED),
                Arguments.of(new RuptureIntrouvableException(RuptureId.depuis(UUID_VALIDE)), Status.Code.NOT_FOUND),
                Arguments.of(new RuptureDejaClotureeException(RuptureId.depuis(UUID_VALIDE)),
                        Status.Code.FAILED_PRECONDITION));
    }

    @ParameterizedTest
    @MethodSource("exceptionsDuDomaine")
    void traduitLesExceptionsDuCasDUsage(RuntimeException exception, Status.Code attendu) {
        when(mettreAJour.mettreAJour(any(), any())).thenThrow(exception);
        var erreur = erreurGrpc(() -> mettreAJour(requeteValide()));
        assertThat(erreur.getStatus().getCode()).isEqualTo(attendu);
        assertThat(erreur.getStatus().getDescription()).isNotBlank();
    }

    @Test
    void uneErreurInattendueDonneInternalSansFuite() {
        when(mettreAJour.mettreAJour(any(), any()))
                .thenThrow(new IllegalStateException("jdbc:postgresql://db-interne password=hunter2"));
        var erreur = erreurGrpc(() -> mettreAJour(requeteValide()));
        assertThat(erreur.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(String.valueOf(erreur.getStatus().getDescription())).doesNotContain("hunter2", "jdbc");
    }
}
