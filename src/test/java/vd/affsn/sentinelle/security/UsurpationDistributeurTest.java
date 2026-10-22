package vd.affsn.sentinelle.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vd.affsn.sentinelle.domain.Fixtures;
import vd.affsn.sentinelle.domain.exception.AccesRefuseException;
import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.IdentifiantDistributeur;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.domain.model.StatutRupture;

/**
 * Attaque : le distributeur PRO-222222 tente de modifier la déclaration de PRO-111111.
 *
 * <p>Chaîne complète jeton → {@link VerificateurJwt} → {@link Acteur} → agrégat {@link Rupture} :
 * la règle de propriété est dans le domaine (ADR-002) et l'Acteur ne peut venir que du jeton.
 */
class UsurpationDistributeurTest {

    private static final Instant T0 = Instant.parse("2026-10-21T08:00:00Z");
    private static final JwtTestFactory JETONS = JwtTestFactory.a(T0);
    private static final IdentifiantDistributeur PRO_111111 = IdentifiantDistributeur.de("PRO-111111");

    private final VerificateurJwt verificateur = new VerificateurJwt(FabriqueDecodeurJwt.creer(
            JwtTestFactory.clePubliqueTest(), JwtTestFactory.EMETTEUR, JwtTestFactory.AUDIENCE,
            Duration.ofSeconds(30), Clock.fixed(T0, ZoneOffset.UTC)));

    private Acteur acteur111;
    private Acteur acteur222;
    private Rupture ruptureDe111;

    @BeforeEach
    void declarerUneRuptureDe111() {
        acteur111 = verificateur.verifier(JETONS.distributeur111());
        acteur222 = verificateur.verifier(JETONS.distributeur222());
        ruptureDe111 = Rupture.declarer(RuptureId.generer(), Fixtures.PRODUIT, acteur111,
                Periode.aPartirDu(LocalDate.of(2026, 10, 1)), Motif.PRODUCTION,
                Fixtures.regions(Region.HAUTE_PAPERASSE), List.of());
    }

    @Test
    void leDeclarantEstLeDistributeurDuJeton() {
        assertThat(ruptureDe111.declarant()).isEqualTo(PRO_111111);
    }

    @Test
    void leDistributeur222NePeutPasMettreAJourLaDeclarationDe111() {
        assertThatThrownBy(() -> ruptureDe111.mettreAJour(acteur222, ruptureDe111.periode(), Motif.LOGISTIQUE,
                ruptureDe111.regions(), List.of()))
                .isInstanceOf(AccesRefuseException.class);
        assertThat(ruptureDe111.motif()).isEqualTo(Motif.PRODUCTION);
    }

    @Test
    void leDistributeur222NePeutPasCloturerLaDeclarationDe111() {
        assertThatThrownBy(() -> ruptureDe111.cloturer(acteur222, LocalDate.of(2026, 10, 20)))
                .isInstanceOf(AccesRefuseException.class);
        assertThat(ruptureDe111.statut()).isEqualTo(StatutRupture.EN_COURS);
    }

    @Test
    void desClaimsDecoratifsNeTransformentPas222En111() {
        String jeton = JETONS.signer(JETONS.claimsValides(JwtTestFactory.SUJET_DISTRIBUTEUR_222, "DISTRIBUTEUR",
                        JwtTestFactory.DISTRIBUTEUR_222)
                .claim("distributeurId", "PRO-111111")
                .claim("declarant", "PRO-111111")
                .claim("on_behalf_of", "PRO-111111")
                .build());

        Acteur acteur = verificateur.verifier(jeton);

        assertThat(acteur.agitPour(PRO_111111)).isFalse();
        assertThatThrownBy(() -> ruptureDe111.cloturer(acteur, LocalDate.of(2026, 10, 20)))
                .isInstanceOf(AccesRefuseException.class);
    }

    @Test
    void unJetonDe111AltereEn222OuInversementEstRefuseAvantLeDomaine() {
        String[] parties222 = JETONS.distributeur222().split("\\.");
        String[] parties111 = JETONS.distributeur111().split("\\.");
        String greffe = parties111[0] + "." + parties111[1] + "." + parties222[2];

        assertThatThrownBy(() -> verificateur.verifier(greffe)).isInstanceOf(JetonInvalideException.class);
    }

    @Test
    void leProprietaireLegitimePeutModifierEtCloturer() {
        Rupture modifiee = ruptureDe111.mettreAJour(acteur111, ruptureDe111.periode(), Motif.LOGISTIQUE,
                ruptureDe111.regions(), List.of());
        Rupture cloturee = modifiee.cloturer(acteur111, LocalDate.of(2026, 10, 20));

        assertThat(modifiee.motif()).isEqualTo(Motif.LOGISTIQUE);
        assertThat(cloturee.statut()).isEqualTo(StatutRupture.CLOTUREE);
    }

    @Test
    void unPharmacienIssuDuJetonNePeutNiDeclarerNiModifier() {
        Acteur pharmacien = verificateur.verifier(JETONS.pharmacien());

        assertThatThrownBy(() -> Rupture.declarer(RuptureId.generer(), Fixtures.PRODUIT, pharmacien,
                Periode.aPartirDu(LocalDate.of(2026, 10, 1)), Motif.PRODUCTION,
                Fixtures.regions(Region.HAUTE_PAPERASSE), List.of()))
                .isInstanceOf(AccesRefuseException.class);
        assertThatThrownBy(() -> ruptureDe111.cloturer(pharmacien, LocalDate.of(2026, 10, 20)))
                .isInstanceOf(AccesRefuseException.class);
    }
}
