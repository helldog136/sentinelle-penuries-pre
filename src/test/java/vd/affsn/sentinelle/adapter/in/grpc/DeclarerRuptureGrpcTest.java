package vd.affsn.sentinelle.adapter.in.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import vd.affsn.sentinelle.application.port.in.DeclarerRuptureUseCase;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.StatutRupture;
import vd.affsn.sentinelle.grpc.v1.DeclarerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.DeclarerRuptureResponse;
import vd.affsn.sentinelle.grpc.v1.MotifRupture;

/** DeclarerRupture : cas passants et règles de validation du proto. */
class DeclarerRuptureGrpcTest extends AbstractSignalementGrpcTest {

    private static final LocalDate DEBUT = AUJOURDHUI.minusDays(3);

    private static DeclarerRuptureRequest.Builder requeteValide() {
        return DeclarerRuptureRequest.newBuilder()
                .setCodeProduit("VMP-123456")
                .setDateDebut(DEBUT.toString())
                .setMotif(MotifRupture.MOTIF_RUPTURE_PRODUCTION)
                .addRegions(PROTO_HAUTE_PAPERASSE);
    }

    private void declarer(DeclarerRuptureRequest.Builder requete) {
        client.declarerRupture(requete.build());
    }

    private void assertAccepte(DeclarerRuptureRequest.Builder requete) {
        when(declarer.declarer(any(), any())).thenReturn(vue(StatutRupture.EN_COURS, null));
        DeclarerRuptureResponse reponse = client.declarerRupture(requete.build());
        assertThat(reponse.getRupture().getId()).isEqualTo(UUID_VALIDE);
        verify(declarer).declarer(any(), any());
    }

    // --------------------------------------------------------------- cas passants

    @Test
    void declarationCompleteAppelleLeCasDUsageAvecLActeurDuContexteEtLaCommandeMappee() {
        when(declarer.declarer(any(), any())).thenReturn(vue(StatutRupture.EN_COURS, null));

        client.declarerRupture(requeteValide()
                .setDateFinEstimee(AUJOURDHUI.plusDays(20).toString())
                .setMotif(MotifRupture.MOTIF_RUPTURE_REGLEMENTAIRE)
                .addRegions(PROTO_PROVINCE)
                .addAlternatives(alternativeProto("VMP-654321", "  Boîte de 30  "))
                .addAlternatives(alternativeProto("VMP-111111"))
                .build());

        ArgumentCaptor<DeclarerRuptureUseCase.Commande> commande =
                ArgumentCaptor.forClass(DeclarerRuptureUseCase.Commande.class);
        verify(declarer).declarer(eq(distributeurCourant()), commande.capture());
        assertThat(commande.getValue().produit()).isEqualTo(CodeProduit.de("VMP-123456"));
        assertThat(commande.getValue().periode())
                .isEqualTo(Periode.entre(DEBUT, AUJOURDHUI.plusDays(20)));
        assertThat(commande.getValue().motif()).isEqualTo(Motif.REGLEMENTAIRE);
        assertThat(commande.getValue().regions())
                .isEqualTo(Set.of(Region.HAUTE_PAPERASSE, Region.PROVINCE_DU_TICKET_OUVERT));
        assertThat(commande.getValue().alternatives()).containsExactly(
                new Alternative(CodeProduit.de("VMP-654321"), "Boîte de 30"),
                Alternative.sansCommentaire(CodeProduit.de("VMP-111111")));
    }

    @Test
    void declarationSansFinEstimeeNiAlternativesDonneUnePeriodeOuverteEtUneListeVide() {
        when(declarer.declarer(any(), any())).thenReturn(vue(StatutRupture.EN_COURS, null));

        declarer(requeteValide());

        ArgumentCaptor<DeclarerRuptureUseCase.Commande> commande =
                ArgumentCaptor.forClass(DeclarerRuptureUseCase.Commande.class);
        verify(declarer).declarer(any(), commande.capture());
        assertThat(commande.getValue().periode()).isEqualTo(Periode.aPartirDu(DEBUT));
        assertThat(commande.getValue().alternatives()).isEmpty();
    }

    @Test
    void laReponseReprendLaRuptureDuDomaineConformementAuProto() {
        when(declarer.declarer(any(), any())).thenReturn(vue(StatutRupture.EN_COURS, null));

        DeclarerRuptureResponse reponse = client.declarerRupture(requeteValide().build());

        vd.affsn.sentinelle.grpc.v1.Rupture rupture = reponse.getRupture();
        assertThat(rupture.getId()).isEqualTo(UUID_VALIDE);
        assertThat(rupture.getCodeProduit()).isEqualTo("VMP-123456");
        assertThat(rupture.getDateDebut()).isEqualTo(DEBUT.toString());
        assertThat(rupture.hasDateFinEstimee()).isTrue();
        assertThat(rupture.getDateFinEstimee()).isEqualTo(AUJOURDHUI.plusDays(30).toString());
        assertThat(rupture.hasDateFinEffective()).isFalse();
        assertThat(rupture.getMotif()).isEqualTo(MotifRupture.MOTIF_RUPTURE_LOGISTIQUE);
        // ordre croissant des numéros d'enum, quel que soit l'ordre du Set du domaine
        assertThat(rupture.getRegionsList()).containsExactly(PROTO_HAUTE_PAPERASSE, PROTO_PROVINCE);
        assertThat(rupture.getAlternativesList()).containsExactly(
                alternativeProto("VMP-654321", "Boîte de 30"),
                alternativeProto("VMP-111111"));
        assertThat(rupture.getStatut())
                .isEqualTo(vd.affsn.sentinelle.grpc.v1.StatutRupture.STATUT_RUPTURE_EN_COURS);
        assertThat(rupture.getDistributeurId()).isEqualTo("PRO-000042");
    }

    // -------------------------------------------------------------- code produit

    @ParameterizedTest
    @ValueSource(strings = {
            "", "vmp-123456", "VMP-12345", "VMP-1234567", " VMP-123456", "VMP-123456 ",
            "VMP-12345a", "XYZ-123456", "VMP123456", "VMP-123456\n", "VMP-１２３４５６"})
    void codeProduitHorsFormatEstRefuse(String code) {
        assertInvalide(() -> declarer(requeteValide().setCodeProduit(code)), "code_produit");
    }

    // --------------------------------------------------------------------- motif

    @Test
    void motifNonSpecifieEstRefuse() {
        assertInvalide(() -> declarer(requeteValide().setMotif(MotifRupture.MOTIF_RUPTURE_UNSPECIFIED)),
                "motif");
    }

    @Test
    void motifAbsentEstRefuse() {
        assertInvalide(() -> declarer(requeteValide().clearMotif()), "motif");
    }

    @Test
    void motifInconnuEstTraiteCommeNonSpecifie() {
        assertInvalide(() -> declarer(requeteValide().setMotifValue(99)), "motif");
    }

    // ------------------------------------------------------------------- régions

    @Test
    void aucuneRegionEstRefusee() {
        assertInvalide(() -> declarer(requeteValide().clearRegions()), "regions");
    }

    @Test
    void regionNonSpecifieeEstRefusee() {
        assertInvalide(() -> declarer(requeteValide()
                .addRegions(vd.affsn.sentinelle.grpc.v1.Region.REGION_UNSPECIFIED)), "regions");
    }

    @Test
    void regionInconnueEstTraiteeCommeNonSpecifiee() {
        assertInvalide(() -> declarer(requeteValide().addRegionsValue(42)), "regions");
    }

    @Test
    void regionEnDoublonEstRefusee() {
        assertInvalide(() -> declarer(requeteValide().addRegions(PROTO_HAUTE_PAPERASSE)), "regions");
    }

    @Test
    void plusDeSixRegionsSontRefusees() {
        DeclarerRuptureRequest.Builder requete = requeteValide().clearRegions().addAllRegions(PROTO_SIX_REGIONS);
        requete.addRegions(PROTO_HAUTE_PAPERASSE);
        assertInvalide(() -> declarer(requete), "regions");
    }

    @Test
    void sixRegionsDistinctesSontAcceptees() {
        assertAccepte(requeteValide().clearRegions().addAllRegions(PROTO_SIX_REGIONS));
    }

    // --------------------------------------------------------------- date_debut

    @ParameterizedTest
    @ValueSource(strings = {
            "", "2026-6-15", "2026-06-5", "2026/06/12", "20260612", "2026-06-12T00:00:00",
            "2026-06-12T00:00:00Z", "12-06-2026", " 2026-06-12", "2026-06-12 ", "2026-06-12\n",
            "+2026-06-12", "26-06-12", "2026-02-30", "2026-13-01", "2026-00-10", "2026-06-00",
            "2026-W24-5", "2026-163", "٢٠٢٦-٠٦-١٢"})
    void dateDebutHorsFormatStrictEstRefusee(String date) {
        assertInvalide(() -> declarer(requeteValide().setDateDebut(date)), "date_debut");
    }

    @Test
    void dateDebutAbsenteEstRefusee() {
        assertInvalide(() -> declarer(requeteValide().clearDateDebut()), "date_debut");
    }

    @Test
    void dateDebutAJMoins365EstAcceptee() {
        assertAccepte(requeteValide().setDateDebut(AUJOURDHUI.minusDays(365).toString()));
    }

    @Test
    void dateDebutAJMoins366EstRefusee() {
        assertInvalide(() -> declarer(requeteValide().setDateDebut(AUJOURDHUI.minusDays(366).toString())),
                "date_debut");
    }

    @Test
    void dateDebutAJPlus90EstAcceptee() {
        assertAccepte(requeteValide().setDateDebut(AUJOURDHUI.plusDays(90).toString()));
    }

    @Test
    void dateDebutAJPlus91EstRefusee() {
        assertInvalide(() -> declarer(requeteValide().setDateDebut(AUJOURDHUI.plusDays(91).toString())),
                "date_debut");
    }

    @Test
    void dateDebutAujourdhuiEstAcceptee() {
        assertAccepte(requeteValide().setDateDebut(AUJOURDHUI.toString()));
    }

    // --------------------------------------------------------- date_fin_estimee

    @ParameterizedTest
    @ValueSource(strings = {
            "", "2026-6-20", "2026/06/20", "20260620", "2026-06-20T00:00:00", " 2026-06-20",
            "2026-02-30", "2026-13-01"})
    void dateFinEstimeeHorsFormatStrictEstRefusee(String date) {
        assertInvalide(() -> declarer(requeteValide().setDateFinEstimee(date)), "date_fin_estimee");
    }

    @Test
    void dateFinEstimeeAvantLeDebutEstRefusee() {
        assertInvalide(() -> declarer(requeteValide().setDateFinEstimee(DEBUT.minusDays(1).toString())),
                "date_fin_estimee");
    }

    @Test
    void dateFinEstimeeEgaleAuDebutEstAcceptee() {
        assertAccepte(requeteValide().setDateFinEstimee(DEBUT.toString()));
    }

    @Test
    void dateFinEstimeeADebutPlus730EstAcceptee() {
        assertAccepte(requeteValide().setDateFinEstimee(DEBUT.plusDays(730).toString()));
    }

    @Test
    void dateFinEstimeeADebutPlus731EstRefusee() {
        assertInvalide(() -> declarer(requeteValide().setDateFinEstimee(DEBUT.plusDays(731).toString())),
                "date_fin_estimee");
    }

    // -------------------------------------------------------------- alternatives

    private static DeclarerRuptureRequest.Builder avecAlternatives(int nombre) {
        DeclarerRuptureRequest.Builder requete = requeteValide();
        IntStream.range(0, nombre)
                .forEach(i -> requete.addAlternatives(alternativeProto("VMP-%06d".formatted(200000 + i))));
        return requete;
    }

    @Test
    void vingtAlternativesDistinctesSontAcceptees() {
        assertAccepte(avecAlternatives(20));
    }

    @Test
    void vingtEtUneAlternativesSontRefusees() {
        assertInvalide(() -> declarer(avecAlternatives(21)), "alternatives");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "vmp-654321", "VMP-65432", " VMP-654321", "ABC-654321"})
    void codeAlternatifHorsFormatEstRefuse(String code) {
        assertInvalide(() -> declarer(requeteValide().addAlternatives(alternativeProto(code))),
                "alternatives[0].code_produit");
    }

    @Test
    void alternativeEgaleAuProduitEnRuptureEstRefusee() {
        assertInvalide(() -> declarer(requeteValide().addAlternatives(alternativeProto("VMP-123456"))),
                "alternatives[0].code_produit");
    }

    @Test
    void alternativeEnDoublonEstRefusee() {
        assertInvalide(() -> declarer(requeteValide()
                .addAlternatives(alternativeProto("VMP-654321"))
                .addAlternatives(alternativeProto("VMP-654321", "autre commentaire"))),
                "alternatives[1].code_produit");
    }

    @Test
    void commentaireDe280CaracteresEstAccepte() {
        assertAccepte(requeteValide().addAlternatives(alternativeProto("VMP-654321", "a".repeat(280))));
    }

    @Test
    void commentaireDe281CaracteresEstRefuse() {
        assertInvalide(() -> declarer(requeteValide()
                .addAlternatives(alternativeProto("VMP-654321", "a".repeat(281)))),
                "alternatives[0].commentaire");
    }

    @Test
    void laLimiteDe280CaracteresSAppliqueApresRetraitDesEspacesDeBord() {
        String commentaire = "   " + "é".repeat(280) + "  ";
        assertAccepte(requeteValide().addAlternatives(alternativeProto("VMP-654321", commentaire)));

        ArgumentCaptor<DeclarerRuptureUseCase.Commande> commande =
                ArgumentCaptor.forClass(DeclarerRuptureUseCase.Commande.class);
        verify(declarer).declarer(any(), commande.capture());
        assertThat(commande.getValue().alternatives().get(0).commentaire()).isEqualTo("é".repeat(280));
    }

    @Test
    void commentaireAvecCaractereDeControleEstRefuse() {
        assertInvalide(() -> declarer(requeteValide()
                .addAlternatives(alternativeProto("VMP-654321", "texte\u0007piégé"))),
                "alternatives[0].commentaire");
    }

    @Test
    void commentaireAvecSautDeLigneEstRefuse() {
        assertInvalide(() -> declarer(requeteValide()
                .addAlternatives(alternativeProto("VMP-654321", "ligne1\nligne2"))),
                "alternatives[0].commentaire");
    }

    // ---------------------------------------------------- accumulation, format

    @Test
    void lesViolationsSontAccumuleesAuFormatChampRaisonSepareesParPointVirgule() {
        DeclarerRuptureRequest.Builder requete = requeteValide()
                .setCodeProduit("n'importe quoi")
                .setDateDebut("hier")
                .setMotif(MotifRupture.MOTIF_RUPTURE_UNSPECIFIED)
                .clearRegions();

        StatusRuntimeException erreur = erreurGrpc(() -> declarer(requete));

        assertThat(erreur.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        String description = erreur.getStatus().getDescription();
        assertThat(description).contains("code_produit: ", "date_debut: ", "motif: ", "regions: ");
        List<String> violations = List.of(description.split("; "));
        assertThat(violations).hasSize(4).allSatisfy(v -> assertThat(v).matches("[a-z_0-9\\[\\]]+: .+"));
        assertAucunCasDUsageAppele();
    }
}
