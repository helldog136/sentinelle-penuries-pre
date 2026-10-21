package vd.affsn.sentinelle.adapter.in.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import vd.affsn.sentinelle.application.port.in.CloturerRuptureUseCase;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.domain.model.StatutRupture;
import vd.affsn.sentinelle.grpc.v1.CloturerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.CloturerRuptureResponse;

/** CloturerRupture : cas passants et règles de validation du proto. */
class CloturerRuptureGrpcTest extends AbstractSignalementGrpcTest {

    private static CloturerRuptureRequest.Builder requeteValide() {
        return CloturerRuptureRequest.newBuilder()
                .setId(UUID_VALIDE)
                .setDateFinEffective(AUJOURDHUI.minusDays(1).toString());
    }

    private void cloturer(CloturerRuptureRequest.Builder requete) {
        client.cloturerRupture(requete.build());
    }

    private void assertAccepte(CloturerRuptureRequest.Builder requete) {
        when(cloturer.cloturer(any(), any())).thenReturn(vue(StatutRupture.CLOTUREE, AUJOURDHUI));
        client.cloturerRupture(requete.build());
        verify(cloturer).cloturer(any(), any());
    }

    // --------------------------------------------------------------- cas passants

    @Test
    void clotureAppelleLeCasDUsageAvecLActeurDuContexteEtLaCommandeMappee() {
        LocalDate fin = AUJOURDHUI.minusDays(1);
        when(cloturer.cloturer(any(), any())).thenReturn(vue(StatutRupture.CLOTUREE, fin));

        client.cloturerRupture(requeteValide().setDateFinEffective(fin.toString()).build());

        ArgumentCaptor<CloturerRuptureUseCase.Commande> commande =
                ArgumentCaptor.forClass(CloturerRuptureUseCase.Commande.class);
        verify(cloturer).cloturer(eq(distributeurCourant()), commande.capture());
        assertThat(commande.getValue())
                .isEqualTo(new CloturerRuptureUseCase.Commande(RuptureId.depuis(UUID_VALIDE), fin));
    }

    @Test
    void laReponseDeClotureExposeLaFinEffectiveEtLeStatutCloture() {
        LocalDate fin = AUJOURDHUI.minusDays(1);
        when(cloturer.cloturer(any(), any())).thenReturn(vue(StatutRupture.CLOTUREE, fin));

        CloturerRuptureResponse reponse = client.cloturerRupture(requeteValide().build());

        vd.affsn.sentinelle.grpc.v1.Rupture rupture = reponse.getRupture();
        assertThat(rupture.getId()).isEqualTo(UUID_VALIDE);
        assertThat(rupture.getStatut())
                .isEqualTo(vd.affsn.sentinelle.grpc.v1.StatutRupture.STATUT_RUPTURE_CLOTUREE);
        assertThat(rupture.hasDateFinEffective()).isTrue();
        assertThat(rupture.getDateFinEffective()).isEqualTo(fin.toString());
        assertThat(rupture.getDistributeurId()).isEqualTo("PRO-000042");
    }

    @Test
    void dateFinEffectiveAujourdhuiEstAcceptee() {
        assertAccepte(requeteValide().setDateFinEffective(AUJOURDHUI.toString()));
    }

    @Test
    void dateFinEffectiveDemainEstRefusee() {
        assertInvalide(() -> cloturer(requeteValide().setDateFinEffective(AUJOURDHUI.plusDays(1).toString())),
                "date_fin_effective");
    }

    // ------------------------------------------------------------------ identifiant

    @ParameterizedTest
    @ValueSource(strings = {
            "", "pas-un-uuid", "123e4567e89b42d3a456426614174000",
            "123E4567-E89B-42D3-A456-426614174000", " 123e4567-e89b-42d3-a456-426614174000",
            "123e4567-e89b-42d3-a456-426614174000 ", "123e4567-e89b-42d3-a456-42661417400",
            "123e4567-e89b-42d3-a456-4266141740000", "1-1-1-1-1",
            "urn:uuid:123e4567-e89b-42d3-a456-426614174000",
            "{123e4567-e89b-42d3-a456-426614174000}", "123e4567-e89b-42d3-a456-42661417400g"})
    void identifiantQuiNEstPasUnUuidCanoniqueEnMinusculesEstRefuse(String id) {
        assertInvalide(() -> cloturer(requeteValide().setId(id)), "id");
    }

    @Test
    void identifiantAbsentEstRefuse() {
        assertInvalide(() -> cloturer(requeteValide().clearId()), "id");
    }

    // ----------------------------------------------------------- date_fin_effective

    @ParameterizedTest
    @ValueSource(strings = {
            "", "2026-6-10", "2026/06/10", "20260610", "2026-06-10T00:00:00", " 2026-06-10",
            "2026-06-10 ", "10-06-2026", "2026-02-30", "2026-13-01"})
    void dateFinEffectiveHorsFormatStrictEstRefusee(String date) {
        assertInvalide(() -> cloturer(requeteValide().setDateFinEffective(date)), "date_fin_effective");
    }

    @Test
    void dateFinEffectiveAbsenteEstRefusee() {
        assertInvalide(() -> cloturer(requeteValide().clearDateFinEffective()), "date_fin_effective");
    }

    @Test
    void identifiantEtDateInvalidesSontTousDeuxSignales() {
        var erreur = erreurGrpc(() -> cloturer(requeteValide().setId("x").setDateFinEffective("y")));
        assertThat(erreur.getStatus().getDescription()).contains("id: ", "date_fin_effective: ", "; ");
    }
}
