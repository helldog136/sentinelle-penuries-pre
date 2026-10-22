package vd.affsn.sentinelle.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.UnknownFieldSet;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import vd.affsn.sentinelle.grpc.v1.CloturerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.DeclarerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.MettreAJourRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.Rupture;
import vd.affsn.sentinelle.grpc.v1.StatutRupture;

/**
 * RÉTROCOMPATIBILITÉ « ANCIEN CLIENT » d'ADR-004, de bout en bout (TDD, phase rouge) :
 * <ul>
 *   <li>une rupture enregistrée AVANT la règle et non conforme (insérée directement en base) reste
 *       lisible en REST, se clôture normalement, ne se met à jour qu'avec une alternative ;</li>
 *   <li>les requêtes d'un ancien client hors du champ de la règle (produit non essentiel, ou rupture
 *       de 30 jours au plus) sont acceptées comme avant ;</li>
 *   <li>un décodeur généré depuis le proto v1 SANS le champ 13 lit les réponses sans erreur ;</li>
 *   <li>le caractère essentiel suit le référentiel COURANT (reclassement, retrait) sans redémarrage.</li>
 * </ul>
 *
 * <p>Chaque test crée ses propres ruptures (aucun ordre) ; horloge (2026-11-01) et référentiel
 * conventionnel sont rétablis avant chaque test par le socle.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sentinelle-e2e-essentiel-retrocompat;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "grpc.server.in-process-name=sentinelle-e2e-essentiel-retrocompat"})
class RetrocompatibiliteEssentielBoutEnBoutTest extends AbstractEssentielBoutEnBoutTest {

    /** Début de la rupture préexistante R des cas d'acceptation « Mise à jour ». */
    private static final LocalDate DEBUT_R = LocalDate.of(2026, 10, 1);

    @TempDir
    static Path repertoireNotifications;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("sentinelle.batch.notifications.repertoire", () -> repertoireNotifications.toString());
    }

    /** R : VMP-100001, début 2026-10-01, sans fin estimée, SANS alternative, PRO-000201 (non conforme). */
    private String ruptureNonConformeAnterieure() {
        return insererRuptureAnterieure(ESSENTIEL_1, PRO_201, DEBUT_R, null).toString();
    }

    // ====================================================================== rupture existante non conforme

    @Test
    void ac25_uneRuptureAnterieureNonConformeResteLisibleEnRestTelleQuelle() {
        String idR = ruptureNonConformeAnterieure();

        JsonNode detail = getOk("/api/v1/ruptures/" + idR, jetons.pharmacien());

        assertThat(detail.get("id").asText()).isEqualTo(idR);
        assertThat(detail.get("codeProduit").asText()).isEqualTo(ESSENTIEL_1);
        assertThat(detail.get("dateDebut").asText()).isEqualTo("2026-10-01");
        assertThat(detail.get("dateFinEstimee").isNull()).isTrue();
        assertThat(detail.get("alternatives")).isEmpty();
        assertThat(detail.get("statut").asText()).isEqualTo("EN_COURS");
        assertThat(detail.has("essentiel")).as("propriété essentiel présente").isTrue();
        assertThat(essentielDe(detail)).isTrue();
        // ... et dans la liste filtrée (tolérée, jamais revérifiée d'office).
        assertThat(ids(getOk("/api/v1/ruptures?essentiel=true&produit=VMP-100001&size=100", jetons.pharmacien())))
                .contains(idR);
    }

    @Test
    void ac26_ac27_laPremiereMiseAJourDoitEtreConformeMemeSiElleRenvoieLEtatEnregistre() {
        String idR = ruptureNonConformeAnterieure();
        Map<String, Object> avant = etatEnBase(idR);
        long journalAvant = compterJournal();

        // AC-26 : l'ancien client renvoie exactement l'état qu'il a toujours stocké.
        assertRefuseParLaRegle(() -> grpc(jeton201()).mettreAJourRupture(miseAJour(idR, null)));
        assertThat(etatEnBase(idR)).isEqualTo(avant);
        assertThat(compterJournal()).isEqualTo(journalAvant);

        // AC-27 : avec une alternative, acceptée, une entrée MODIFICATION.
        Rupture r = grpc(jeton201()).mettreAJourRupture(miseAJour(idR, null, alt(NON_ESSENTIEL_4))).getRupture();
        assertThat(r.getAlternativesList()).extracting(a -> a.getCodeProduit()).containsExactly(NON_ESSENTIEL_4);
        assertThat(r.getEssentiel()).isTrue();
        assertThat(typesAudit(idR)).containsExactly("MODIFICATION");
        assertThat(compterJournal()).isEqualTo(journalAvant + 1);
    }

    @Test
    void ac28_laClotureDUneRuptureNonConformeNEstJamaisBloquee() {
        String idR = ruptureNonConformeAnterieure();

        Rupture r = grpc(jeton201()).cloturerRupture(CloturerRuptureRequest.newBuilder()
                .setId(idR).setDateFinEffective("2026-11-01").build()).getRupture();

        assertThat(r.getStatut()).isEqualTo(StatutRupture.STATUT_RUPTURE_CLOTUREE);
        assertThat(r.getDateFinEffective()).isEqualTo("2026-11-01");
        assertThat(r.getAlternativesList()).isEmpty();
        assertThat(r.getEssentiel()).as("essentiel dans la réponse de CloturerRupture").isTrue();
        assertThat(typesAudit(idR)).containsExactly("CLOTURE");
        // AC-42 : détail REST d'une rupture clôturée de produit essentiel.
        JsonNode detail = getOk("/api/v1/ruptures/" + idR, jetons.pharmacien());
        assertThat(detail.get("statut").asText()).isEqualTo("CLOTUREE");
        assertThat(essentielDe(detail)).isTrue();
    }

    @Test
    void ac30_ac31_ac32_lesControlesDExistenceProprieteEtEtatPassentAvantLaRegle() {
        String idR = ruptureNonConformeAnterieure();

        StatusRuntimeException tiers = erreurGrpc(() -> grpc(jeton202()).mettreAJourRupture(miseAJour(idR, null)));
        assertThat(tiers.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(tiers.getStatus().getDescription()).isEqualTo("Accès refusé");

        StatusRuntimeException inconnue = erreurGrpc(() -> grpc(jeton201()).mettreAJourRupture(
                miseAJour(UUID.randomUUID().toString(), null)));
        assertThat(inconnue.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(inconnue.getStatus().getDescription()).isEqualTo("Rupture introuvable");

        grpc(jeton201()).cloturerRupture(CloturerRuptureRequest.newBuilder()
                .setId(idR).setDateFinEffective("2026-11-01").build());
        StatusRuntimeException cloturee = erreurGrpc(() -> grpc(jeton201()).mettreAJourRupture(miseAJour(idR, null)));
        assertThat(cloturee.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
        assertThat(cloturee.getStatus().getDescription()).isEqualTo("Rupture déjà clôturée");
    }

    // ====================================================================== référentiel courant

    @Test
    void ac35_unProduitReclasseEssentielApresLaDeclarationEstToleréJusquALaProchaineMiseAJour() {
        String idS = insererRuptureAnterieure(NON_ESSENTIEL_2, PRO_201, DEBUT_R, null).toString();
        ajouterAuReferentiel(NON_ESSENTIEL_2);

        assertThat(essentielDe(getOk("/api/v1/ruptures/" + idS, jetons.pharmacien()))).isTrue();
        assertRefuseParLaRegle(() -> grpc(jeton201()).mettreAJourRupture(miseAJour(idS, null)));
        Rupture r = grpc(jeton201()).mettreAJourRupture(miseAJour(idS, null, alt(NON_ESSENTIEL_4))).getRupture();
        assertThat(r.getEssentiel()).isTrue();
    }

    @Test
    void ac34_ac43_unProduitRetireDuReferentielNEstPlusEssentielSansRedemarrage() {
        String idR = ruptureNonConformeAnterieure();
        assertThat(essentielDe(getOk("/api/v1/ruptures/" + idR, jetons.pharmacien()))).isTrue();

        retirerDuReferentiel(ESSENTIEL_1);

        // AC-43 : sans redémarrage ni modification de la rupture.
        JsonNode detail = getOk("/api/v1/ruptures/" + idR, jetons.pharmacien());
        assertThat(detail.has("essentiel")).isTrue();
        assertThat(essentielDe(detail)).isFalse();
        // AC-34 : la règle ne s'applique plus.
        Rupture r = grpc(jeton201()).mettreAJourRupture(miseAJour(idR, null)).getRupture();
        assertThat(r.getEssentiel()).isFalse();
        assertThat(r.getAlternativesList()).isEmpty();
    }

    // ====================================================================== ancien client, hors champ de la règle

    @Test
    void ac07_ac45_unAncienClientSurUnProduitNonEssentielEstServiCommeAvantDansLesTroisRpc() {
        // Requête strictement v1 (aucun champ nouveau n'existe côté requête), longue et sans alternative.
        DeclarerRuptureRequest requete = declaration(NON_ESSENTIEL_2, AUJOURDHUI, null);
        Rupture declaree = grpc(jeton201()).declarerRupture(requete).getRupture();
        assertThat(declaree.getCodeProduit()).isEqualTo(NON_ESSENTIEL_2);
        assertThat(declaree.hasDateFinEstimee()).isFalse();
        assertThat(declaree.getStatut()).isEqualTo(StatutRupture.STATUT_RUPTURE_EN_COURS);
        assertThat(declaree.getEssentiel()).isFalse();

        // Puis allongée à début + 31 j, toujours sans alternative : acceptée (AC-07, second volet).
        MettreAJourRuptureRequest maj = miseAJour(declaree.getId(), AUJOURDHUI.plusDays(31));
        Rupture modifiee = grpc(jeton201()).mettreAJourRupture(maj).getRupture();
        assertThat(modifiee.getDateFinEstimee()).isEqualTo("2026-12-02");
        assertThat(modifiee.getEssentiel()).isFalse();

        Rupture cloturee = grpc(jeton201()).cloturerRupture(CloturerRuptureRequest.newBuilder()
                .setId(declaree.getId()).setDateFinEffective(date(AUJOURDHUI)).build()).getRupture();
        assertThat(cloturee.getEssentiel()).isFalse();
        assertThat(typesAudit(declaree.getId())).containsExactly("DECLARATION", "MODIFICATION", "CLOTURE");
    }

    @Test
    void ac14_unProduitInconnuDuReferentielEstNonEssentiel() {
        Rupture r = grpc(jeton201()).declarerRupture(declaration(INCONNU, AUJOURDHUI, null)).getRupture();
        assertThat(r.getEssentiel()).isFalse();
        assertThat(essentielDe(getOk("/api/v1/ruptures/" + r.getId(), jetons.pharmacien())))
                .isFalse();
    }

    @Test
    void ac01_ac06_unAncienClientSurUnProduitEssentielDe30JoursAuPlusEstAccepteSansAlternative() {
        Rupture trenteJours = grpc(jeton201()).declarerRupture(
                declaration(ESSENTIEL_1, AUJOURDHUI, LocalDate.of(2026, 12, 1))).getRupture();
        assertThat(trenteJours.getStatut()).isEqualTo(StatutRupture.STATUT_RUPTURE_EN_COURS);
        assertThat(trenteJours.getAlternativesList()).isEmpty();
        assertThat(trenteJours.getEssentiel()).isTrue();

        Rupture dureeNulle = grpc(jeton201()).declarerRupture(
                declaration(ESSENTIEL_1, AUJOURDHUI, AUJOURDHUI)).getRupture();
        assertThat(dureeNulle.getEssentiel()).isTrue();
    }

    // ====================================================================== ancien décodeur (proto v1 sans champ 13)

    @Test
    void ac44_unDecodeurGenereSansLeChamp13LitLaReponseSansErreur() throws Exception {
        Rupture reponse = grpc(jeton201()).declarerRupture(
                declaration(ESSENTIEL_1, AUJOURDHUI, null, alt(NON_ESSENTIEL_4))).getRupture();
        ByteString octets = reponse.toByteString();

        Descriptor ancien = descripteurRuptureSansChamp13();
        assertThat(ancien.findFieldByNumber(13)).isNull();
        DynamicMessage decode = DynamicMessage.parseFrom(ancien, octets);

        // Champs connus identiques à avant.
        for (FieldDescriptor champ : ancien.getFields()) {
            FieldDescriptor nouveau = Rupture.getDescriptor().findFieldByNumber(champ.getNumber());
            if (champ.isRepeated()) {
                assertThat(decode.getRepeatedFieldCount(champ)).as(champ.getName())
                        .isEqualTo(reponse.getRepeatedFieldCount(nouveau));
            } else if (champ.hasPresence()) {
                assertThat(decode.hasField(champ)).as(champ.getName()).isEqualTo(reponse.hasField(nouveau));
            }
        }
        assertThat(decode.getField(ancien.findFieldByName("id"))).isEqualTo(reponse.getId());
        assertThat(decode.getField(ancien.findFieldByName("code_produit"))).isEqualTo(ESSENTIEL_1);
        assertThat(decode.getField(ancien.findFieldByName("distributeur_id"))).isEqualTo(PRO_201);
        // Le champ 13 est simplement ignoré (inconnu) par l'ancien client : il a bien été émis (true).
        UnknownFieldSet inconnus = decode.getUnknownFields();
        assertThat(inconnus.hasField(13)).as("champ 13 émis par le serveur, inconnu de l'ancien client").isTrue();
        assertThat(inconnus.getField(13).getVarintList()).containsExactly(1L);
        // Réencodé par l'ancien client, il reste lisible par un client à jour.
        assertThat(Rupture.parseFrom(decode.toByteString())).isEqualTo(reponse);
    }

    /** Descripteur de {@code Rupture} tel qu'un client compilé AVANT l'ajout du champ 13 le connaît. */
    private static Descriptor descripteurRuptureSansChamp13() throws Exception {
        FileDescriptor actuel = Rupture.getDescriptor().getFile();
        FileDescriptorProto.Builder fichier = actuel.toProto().toBuilder();
        for (int i = 0; i < fichier.getMessageTypeCount(); i++) {
            DescriptorProto message = fichier.getMessageType(i);
            if (message.getName().equals("Rupture")) {
                DescriptorProto.Builder ancien = message.toBuilder().clearField();
                message.getFieldList().stream().filter(f -> f.getNumber() != 13).forEach(ancien::addField);
                fichier.setMessageType(i, ancien);
            }
        }
        FileDescriptor v1 = FileDescriptor.buildFrom(fichier.build(),
                actuel.getDependencies().toArray(new FileDescriptor[0]));
        return v1.findMessageTypeByName("Rupture");
    }

    @Test
    void lesReponsesNonEssentiellesSontOctetPourOctetCellesDAvant() throws Exception {
        // proto3 : false n'est pas sérialisé ; un ancien client ne voit AUCUNE différence pour un non essentiel.
        Rupture reponse = grpc(jeton201()).declarerRupture(declaration(NON_ESSENTIEL_2, AUJOURDHUI, null))
                .getRupture();
        DynamicMessage decode = DynamicMessage.parseFrom(descripteurRuptureSansChamp13(), reponse.toByteString());
        assertThat(decode.getUnknownFields().asMap()).isEmpty();
        assertThat(decode.toByteString()).isEqualTo(reponse.toByteString());
        assertThat(List.of(reponse.getEssentiel())).containsExactly(false);
    }
}
