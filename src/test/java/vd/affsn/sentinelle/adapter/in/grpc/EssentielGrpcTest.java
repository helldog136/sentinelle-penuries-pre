package vd.affsn.sentinelle.adapter.in.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.UnknownFieldSet;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import org.junit.jupiter.api.Test;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.domain.exception.ValidationMetierException;
import vd.affsn.sentinelle.domain.model.StatutRupture;
import vd.affsn.sentinelle.grpc.v1.CloturerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.CloturerRuptureResponse;
import vd.affsn.sentinelle.grpc.v1.DeclarerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.DeclarerRuptureResponse;
import vd.affsn.sentinelle.grpc.v1.MettreAJourRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.MettreAJourRuptureResponse;
import vd.affsn.sentinelle.grpc.v1.MotifRupture;

/** ADR-004 AC-44 et AC-45 : champ {@code essentiel} (13) des réponses et erreur de la règle. */
class EssentielGrpcTest extends AbstractSignalementGrpcTest {

    private static final String MESSAGE_REGLE =
            "alternatives: au moins une alternative requise (produit essentiel, rupture de plus de 30 jours)";

    /** Vue du socle de test avec le caractère essentiel imposé. */
    private static RuptureVue vue(StatutRupture statut, boolean essentiel) {
        RuptureVue base = vue(statut, statut == StatutRupture.CLOTUREE ? AUJOURDHUI.minusDays(1) : null);
        return new RuptureVue(base.id(), base.produit(), base.declarant(), base.periode(), base.finEffective(),
                base.motif(), base.regions(), base.alternatives(), base.statut(), essentiel);
    }

    private static DeclarerRuptureRequest declaration() {
        return DeclarerRuptureRequest.newBuilder()
                .setCodeProduit("VMP-100001")
                .setDateDebut(AUJOURDHUI.minusDays(3).toString())
                .setMotif(MotifRupture.MOTIF_RUPTURE_PRODUCTION)
                .addRegions(PROTO_HAUTE_PAPERASSE)
                .build();
    }

    private static MettreAJourRuptureRequest miseAJour() {
        return MettreAJourRuptureRequest.newBuilder()
                .setId(UUID_VALIDE)
                .setMotif(MotifRupture.MOTIF_RUPTURE_PRODUCTION)
                .addRegions(PROTO_HAUTE_PAPERASSE)
                .build();
    }

    private static CloturerRuptureRequest cloture() {
        return CloturerRuptureRequest.newBuilder()
                .setId(UUID_VALIDE)
                .setDateFinEffective(AUJOURDHUI.minusDays(1).toString())
                .build();
    }

    // ------------------------------------------------------------------ AC-45 : trois RPC

    @Test
    void ac45_declarerRupture_produitNonEssentiel_essentielFalse() {
        when(declarer.declarer(any(), any())).thenReturn(vue(StatutRupture.EN_COURS, false));

        DeclarerRuptureResponse reponse = client.declarerRupture(declaration());

        assertThat(reponse.getRupture().getEssentiel()).isFalse();
    }

    @Test
    void ac45_mettreAJourRupture_produitNonEssentiel_essentielFalse() {
        when(mettreAJour.mettreAJour(any(), any())).thenReturn(vue(StatutRupture.EN_COURS, false));

        MettreAJourRuptureResponse reponse = client.mettreAJourRupture(miseAJour());

        assertThat(reponse.getRupture().getEssentiel()).isFalse();
    }

    @Test
    void ac45_cloturerRupture_produitNonEssentiel_essentielFalse() {
        when(cloturer.cloturer(any(), any())).thenReturn(vue(StatutRupture.CLOTUREE, false));

        CloturerRuptureResponse reponse = client.cloturerRupture(cloture());

        assertThat(reponse.getRupture().getEssentiel()).isFalse();
    }

    @Test
    void declarerRupture_produitEssentiel_essentielTrue() {
        when(declarer.declarer(any(), any())).thenReturn(vue(StatutRupture.EN_COURS, true));

        assertThat(client.declarerRupture(declaration()).getRupture().getEssentiel()).isTrue();
    }

    @Test
    void mettreAJourRupture_produitEssentiel_essentielTrue() {
        when(mettreAJour.mettreAJour(any(), any())).thenReturn(vue(StatutRupture.EN_COURS, true));

        assertThat(client.mettreAJourRupture(miseAJour()).getRupture().getEssentiel()).isTrue();
    }

    @Test
    void cloturerRupture_produitEssentiel_essentielTrue_etRuptureCloturee() {
        when(cloturer.cloturer(any(), any())).thenReturn(vue(StatutRupture.CLOTUREE, true));

        vd.affsn.sentinelle.grpc.v1.Rupture rupture = client.cloturerRupture(cloture()).getRupture();

        assertThat(rupture.getEssentiel()).isTrue();
        assertThat(rupture.getStatut())
                .isEqualTo(vd.affsn.sentinelle.grpc.v1.StatutRupture.STATUT_RUPTURE_CLOTUREE);
    }

    @Test
    void lAjoutDeEssentielNeModifiePasLesAutresChampsDeLaReponse() {
        when(declarer.declarer(any(), any())).thenReturn(vue(StatutRupture.EN_COURS, true));

        vd.affsn.sentinelle.grpc.v1.Rupture rupture = client.declarerRupture(declaration()).getRupture();

        assertThat(rupture.getId()).isEqualTo(UUID_VALIDE);
        assertThat(rupture.getCodeProduit()).isEqualTo("VMP-123456");
        assertThat(rupture.getDistributeurId()).isEqualTo("PRO-000042");
        assertThat(rupture.getRegionsList()).containsExactly(PROTO_HAUTE_PAPERASSE, PROTO_PROVINCE);
        assertThat(rupture.getAlternativesCount()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ erreur de la règle

    @Test
    void declarerRupture_regleRefusee_invalidArgument_descriptionExacte() {
        when(declarer.declarer(any(), any())).thenThrow(new ValidationMetierException(MESSAGE_REGLE));

        StatusRuntimeException erreur = erreurGrpc(() -> client.declarerRupture(declaration()));

        assertThat(erreur.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(erreur.getStatus().getDescription()).isEqualTo(MESSAGE_REGLE);
    }

    @Test
    void mettreAJourRupture_regleRefusee_invalidArgument_descriptionExacte() {
        when(mettreAJour.mettreAJour(any(), any())).thenThrow(new ValidationMetierException(MESSAGE_REGLE));

        StatusRuntimeException erreur = erreurGrpc(() -> client.mettreAJourRupture(miseAJour()));

        assertThat(erreur.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(erreur.getStatus().getDescription()).isEqualTo(MESSAGE_REGLE);
    }

    @Test
    void regleRefusee_neProduitAucuneReponseRupture() {
        when(declarer.declarer(any(), any())).thenThrow(new ValidationMetierException(MESSAGE_REGLE));

        assertThat(erreurGrpc(() -> client.declarerRupture(declaration())).getStatus().isOk()).isFalse();
    }

    // ------------------------------------------------------------------ AC-44 : ancien client

    @Test
    void ac44_unClientSansLeChamp13DecodeLaReponseSansErreur_etLesChampsConnusSontIdentiques()
            throws Exception {
        when(declarer.declarer(any(), any())).thenReturn(vue(StatutRupture.EN_COURS, true));
        DeclarerRuptureResponse reponse = client.declarerRupture(declaration());
        byte[] octets = reponse.toByteArray();

        Descriptor ancienne = descripteurSansLeChamp13(DeclarerRuptureResponse.getDescriptor());
        DynamicMessage decodee = DynamicMessage.parseFrom(ancienne, octets);

        Descriptor ancienneRupture = ancienne.findFieldByName("rupture").getMessageType();
        assertThat(ancienneRupture.findFieldByNumber(13)).isNull();
        DynamicMessage rupture = (DynamicMessage) decodee.getField(ancienne.findFieldByName("rupture"));
        assertThat(rupture.getField(ancienneRupture.findFieldByName("id"))).isEqualTo(UUID_VALIDE);
        assertThat(rupture.getField(ancienneRupture.findFieldByName("code_produit"))).isEqualTo("VMP-123456");
        assertThat(rupture.getField(ancienneRupture.findFieldByName("distributeur_id"))).isEqualTo("PRO-000042");
        assertThat(rupture.getField(ancienneRupture.findFieldByName("date_debut")))
                .isEqualTo(AUJOURDHUI.minusDays(3).toString());
        // Le serveur a bien émis le champ 13, que l'ancien client range simplement parmi les champs inconnus.
        UnknownFieldSet inconnus = rupture.getUnknownFields();
        assertThat(inconnus.hasField(13)).isTrue();
        assertThat(inconnus.getField(13).getVarintList()).containsExactly(1L);
    }

    /** Reconstruit le descripteur du proto v1 tel qu'il était avant l'ajout du champ 13 de {@code Rupture}. */
    private static Descriptor descripteurSansLeChamp13(Descriptor reponse) throws Exception {
        FileDescriptorProto fichier = reponse.getFile().toProto();
        FileDescriptorProto.Builder sansChamp = fichier.toBuilder();
        for (int i = 0; i < sansChamp.getMessageTypeCount(); i++) {
            if (sansChamp.getMessageType(i).getName().equals("Rupture")) {
                DescriptorProto.Builder rupture = sansChamp.getMessageType(i).toBuilder();
                for (int j = rupture.getFieldCount() - 1; j >= 0; j--) {
                    if (rupture.getField(j).getNumber() == 13) {
                        rupture.removeField(j);
                    }
                }
                sansChamp.setMessageType(i, rupture);
            }
        }
        FileDescriptor ancien = FileDescriptor.buildFrom(sansChamp.build(), new FileDescriptor[0]);
        return ancien.findMessageTypeByName(reponse.getName());
    }

    @Test
    void ac44_leProtoCourantDeclareBienLeChamp13Essentiel() {
        var champ = vd.affsn.sentinelle.grpc.v1.Rupture.getDescriptor().findFieldByNumber(13);

        assertThat(champ).isNotNull();
        assertThat(champ.getName()).isEqualTo("essentiel");
        assertThat(List.of(champ.getType().name())).containsExactly("BOOL");
    }
}
