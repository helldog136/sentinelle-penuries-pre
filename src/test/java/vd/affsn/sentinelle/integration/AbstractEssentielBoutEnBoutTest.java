package vd.affsn.sentinelle.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import vd.affsn.sentinelle.grpc.v1.Alternative;
import vd.affsn.sentinelle.grpc.v1.DeclarerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.MettreAJourRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.MotifRupture;
import vd.affsn.sentinelle.grpc.v1.Region;

/**
 * Socle des tests de bout en bout d'ADR-004 (produits essentiels, alternative obligatoire) : même
 * application complète que {@link AbstractBoutEnBoutTest} (gRPC in-process, REST réel, batch), plus
 * une horloge métier RÉGLABLE ({@link HorlogeReglableE2e}, {@code @Primary}) et un référentiel
 * {@code produits_essentiels} alimenté directement en base (aucune API d'écriture n'existe, ADR-004 § 1).
 *
 * <p>Conventions des cas d'acceptation : {@code VMP-100001} et {@code VMP-300003} essentiels,
 * {@code VMP-200002} et {@code VMP-400004} absents ; aujourd'hui = 2026-11-01 ; propriétaire
 * PRO-000201 ; région {@code HAUTE_PAPERASSE}. Horloge et référentiel sont remis à ces conventions
 * avant CHAQUE test.
 *
 * <p>Le message attendu est recopié de l'ADR (oracle indépendant du code de production).
 */
@Import(AbstractEssentielBoutEnBoutTest.ConfigurationHorlogeReglable.class)
abstract class AbstractEssentielBoutEnBoutTest extends AbstractBoutEnBoutTest {

    /** ADR-004 § 3 : description EXACTE du refus. */
    static final String MSG =
            "alternatives: au moins une alternative requise (produit essentiel, rupture de plus de 30 jours)";

    static final String ESSENTIEL_1 = "VMP-100001";
    static final String NON_ESSENTIEL_2 = "VMP-200002";
    static final String ESSENTIEL_3 = "VMP-300003";
    static final String NON_ESSENTIEL_4 = "VMP-400004";
    static final String INCONNU = "VMP-999999";

    static final LocalDate AUJOURDHUI = HorlogeReglableE2e.AUJOURDHUI_ADR_004;
    static final String COMMENTAIRE_ALT = "Même principe actif, boîte de 30";

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfigurationHorlogeReglable {
        @Bean
        @Primary
        HorlogeReglableE2e horlogeReglableE2e() {
            return new HorlogeReglableE2e();
        }
    }

    @Autowired
    protected HorlogeReglableE2e horloge;

    @BeforeEach
    void conventionsAdr004() {
        horloge.regler(AUJOURDHUI);
        referentiel(ESSENTIEL_1, ESSENTIEL_3);
    }

    // ------------------------------------------------------------------ référentiel (lecture seule côté service)

    /** Remplace TOUT le contenu du référentiel par les codes donnés (alimentation « AFFSN » simulée). */
    void referentiel(String... codesEssentiels) {
        jdbc.update("DELETE FROM produits_essentiels");
        for (String code : codesEssentiels) {
            ajouterAuReferentiel(code);
        }
    }

    void ajouterAuReferentiel(String code) {
        jdbc.update("INSERT INTO produits_essentiels (code_produit, depuis) VALUES (?, DATE '2026-01-01')", code);
    }

    void retirerDuReferentiel(String code) {
        jdbc.update("DELETE FROM produits_essentiels WHERE code_produit = ?", code);
    }

    // ------------------------------------------------------------------ requêtes gRPC

    static DeclarerRuptureRequest declaration(String produit, LocalDate debut, LocalDate finEstimee,
                                              List<Region> regions, Alternative... alternatives) {
        DeclarerRuptureRequest.Builder b = DeclarerRuptureRequest.newBuilder()
                .setCodeProduit(produit)
                .setDateDebut(date(debut))
                .setMotif(MotifRupture.MOTIF_RUPTURE_PRODUCTION)
                .addAllRegions(regions)
                .addAllAlternatives(List.of(alternatives));
        if (finEstimee != null) {
            b.setDateFinEstimee(date(finEstimee));
        }
        return b.build();
    }

    /** Déclaration dans la seule région {@code HAUTE_PAPERASSE} (convention des cas d'acceptation). */
    static DeclarerRuptureRequest declaration(String produit, LocalDate debut, LocalDate finEstimee,
                                              Alternative... alternatives) {
        return declaration(produit, debut, finEstimee, List.of(Region.REGION_HAUTE_PAPERASSE), alternatives);
    }

    static MettreAJourRuptureRequest miseAJour(String id, LocalDate finEstimee, List<Region> regions,
                                              Alternative... alternatives) {
        MettreAJourRuptureRequest.Builder b = MettreAJourRuptureRequest.newBuilder()
                .setId(id)
                .setMotif(MotifRupture.MOTIF_RUPTURE_PRODUCTION)
                .addAllRegions(regions)
                .addAllAlternatives(List.of(alternatives));
        if (finEstimee != null) {
            b.setDateFinEstimee(date(finEstimee));
        }
        return b.build();
    }

    static MettreAJourRuptureRequest miseAJour(String id, LocalDate finEstimee, Alternative... alternatives) {
        return miseAJour(id, finEstimee, List.of(Region.REGION_HAUTE_PAPERASSE), alternatives);
    }

    static Alternative alt(String code) {
        return alternative(code, COMMENTAIRE_ALT);
    }

    /** « Refusé » au sens d'ADR-004 : INVALID_ARGUMENT, description EXACTEMENT {@link #MSG}. */
    static StatusRuntimeException assertRefuseParLaRegle(ThrowingCallable appel) {
        StatusRuntimeException e = erreurGrpc(appel);
        assertThat(e.getStatus().getCode()).as("code gRPC").isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(e.getStatus().getDescription()).as("description").isEqualTo(MSG);
        return e;
    }

    /** Propriété REST additive {@code essentiel} : toujours présente, booléen JSON (ADR-004 § 7). */
    static boolean essentielDe(JsonNode rupture) {
        JsonNode essentiel = rupture.get("essentiel");
        assertThat(essentiel).as("propriété REST « essentiel » présente dans %s", rupture).isNotNull();
        assertThat(essentiel.isBoolean()).as("« essentiel » est un booléen JSON").isTrue();
        return essentiel.asBoolean();
    }

    // ------------------------------------------------------------------ base de données

    /**
     * Insère DIRECTEMENT en base une rupture EN_COURS, comme si elle avait été enregistrée avant la
     * règle (aucun contrôle applicatif, aucune entrée d'audit), motif PRODUCTION, région HAUTE_PAPERASSE.
     */
    UUID insererRuptureAnterieure(String produit, String declarant, LocalDate debut, LocalDate finEstimee,
                                  String... alternatives) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO ruptures (id, code_produit, declarant, date_debut, date_fin_estimee,"
                        + " motif, statut, version, cree_le, modifie_le)"
                        + " VALUES (?, ?, ?, ?, ?, 'PRODUCTION', 'EN_COURS', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, produit, declarant, debut, finEstimee);
        jdbc.update("INSERT INTO rupture_regions (rupture_id, region) VALUES (?, 'HAUTE_PAPERASSE')", id);
        for (String code : alternatives) {
            jdbc.update("INSERT INTO rupture_alternatives (rupture_id, produit_alternatif, commentaire)"
                    + " VALUES (?, ?, ?)", id, code, COMMENTAIRE_ALT);
        }
        return id;
    }

    /** Instantané complet d'une rupture en base (ligne, régions, alternatives), pour « inchangée ». */
    Map<String, Object> etatEnBase(String id) {
        UUID uuid = UUID.fromString(id);
        Map<String, Object> ligne = new java.util.TreeMap<>(jdbc.queryForMap(
                "SELECT code_produit, declarant, date_debut, date_fin_estimee, date_fin_effective, motif, statut,"
                        + " version, modifie_le FROM ruptures WHERE id = ?", uuid));
        ligne.put("regions", jdbc.queryForList(
                "SELECT region FROM rupture_regions WHERE rupture_id = ? ORDER BY region", String.class, uuid));
        ligne.put("alternatives", jdbc.queryForList(
                "SELECT produit_alternatif || '|' || commentaire FROM rupture_alternatives WHERE rupture_id = ?"
                        + " ORDER BY produit_alternatif", String.class, uuid));
        return ligne;
    }

    List<String> typesAudit(String id) {
        return jdbc.queryForList("SELECT type_evenement FROM journal_audit WHERE rupture_id = ? ORDER BY sequence",
                String.class, UUID.fromString(id));
    }
}
