package vd.affsn.sentinelle.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Function;
import java.util.stream.Stream;

import com.nimbusds.jose.JWSAlgorithm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.IdentifiantDistributeur;
import vd.affsn.sentinelle.domain.model.Role;

/**
 * Vérification JWT et traduction en {@link Acteur} (ADR-002), sans Spring.
 * Horloge figée : les cas de dates sont déterministes.
 */
class VerificateurJwtTest {

    private static final Instant T0 = Instant.parse("2026-10-21T08:00:00Z");
    private static final JwtTestFactory JETONS = JwtTestFactory.a(T0);

    private final VerificateurJwt verificateur = verificateur(Duration.ofSeconds(30));

    private static VerificateurJwt verificateur(Duration tolerance) {
        return new VerificateurJwt(FabriqueDecodeurJwt.creer(
                JwtTestFactory.clePubliqueTest(),
                JwtTestFactory.EMETTEUR,
                JwtTestFactory.AUDIENCE,
                tolerance,
                Clock.fixed(T0, ZoneOffset.UTC)));
    }

    /** Tout refus : même exception, même message générique, pas de cause, pas d'écho du jeton. */
    private void assertRefuse(String jeton) {
        Throwable refus = catchThrowable(() -> verificateur.verifier(jeton));
        assertThat(refus).isInstanceOf(JetonInvalideException.class)
                .hasMessage(JetonInvalideException.MESSAGE_GENERIQUE)
                .hasNoCause();
        if (jeton != null && !jeton.isBlank()) {
            assertThat(refus.getMessage()).doesNotContain(jeton);
        }
    }

    @Nested
    @DisplayName("Jetons valides : l'Acteur vient uniquement du jeton")
    class Valides {

        @Test
        void pharmacien() {
            assertThat(verificateur.verifier(JETONS.pharmacien()))
                    .isEqualTo(Acteur.pharmacien(JwtTestFactory.SUJET_PHARMACIEN));
        }

        @Test
        void prescripteur() {
            assertThat(verificateur.verifier(JETONS.prescripteur()))
                    .isEqualTo(Acteur.prescripteur(JwtTestFactory.SUJET_PRESCRIPTEUR));
        }

        @Test
        void distributeurRattacheAuDistributeurDuJeton() {
            Acteur acteur = verificateur.verifier(JETONS.distributeur111());

            assertThat(acteur).isEqualTo(Acteur.distributeur(JwtTestFactory.SUJET_DISTRIBUTEUR_111,
                    IdentifiantDistributeur.de("PRO-111111")));
            assertThat(acteur.role()).isEqualTo(Role.DISTRIBUTEUR);
        }

        @Test
        void audienceMultipleContenantSentinelle() {
            assertThat(verificateur.verifier(JETONS.audienceMultipleContenantSentinelle()).role())
                    .isEqualTo(Role.PHARMACIEN);
        }

        @Test
        void lesAutresClaimsNeComptentPasPourLIdentite() {
            String jeton = JETONS.signer(JETONS.claimsValides(JwtTestFactory.SUJET_DISTRIBUTEUR_222,
                            "DISTRIBUTEUR", JwtTestFactory.DISTRIBUTEUR_222)
                    .claim("distributeurId", "PRO-111111")
                    .claim("declarant", "PRO-111111")
                    .claim("roles", java.util.List.of("PHARMACIEN"))
                    .build());

            Acteur acteur = verificateur.verifier(jeton);

            assertThat(acteur.distributeurRattache()).isEqualTo(IdentifiantDistributeur.de("PRO-222222"));
            assertThat(acteur.role()).isEqualTo(Role.DISTRIBUTEUR);
        }

        @Test
        void expireDepuisMoinsQueLaToleranceEstAccepte() {
            assertThat(verificateur.verifier(JETONS.expireDepuis(Duration.ofSeconds(20))).role())
                    .isEqualTo(Role.PHARMACIEN);
        }

        @Test
        void nbfDansMoinsQueLaToleranceEstAccepte() {
            assertThat(verificateur.verifier(JETONS.valideDans(Duration.ofSeconds(20))).role())
                    .isEqualTo(Role.PHARMACIEN);
        }
    }

    @Nested
    @DisplayName("Dates et tolérance d'horloge")
    class Dates {

        @Test
        void expire() {
            assertRefuse(JETONS.expire());
        }

        @Test
        void expireDepuisPlusQueLaTolerance() {
            assertRefuse(JETONS.expireDepuis(Duration.ofSeconds(31)));
        }

        @Test
        void pasEncoreValide() {
            assertRefuse(JETONS.pasEncoreValide());
        }

        @Test
        void nbfAuDelaDeLaTolerance() {
            assertRefuse(JETONS.valideDans(Duration.ofSeconds(31)));
        }

        @Test
        void sansExpirationEstRefuse() {
            assertRefuse(JETONS.sansExpiration());
        }

        @Test
        void laToleranceConfigureeEstAppliquee() {
            VerificateurJwt strict = verificateur(Duration.ZERO);
            assertThatThrownBy(() -> strict.verifier(JETONS.expireDepuis(Duration.ofSeconds(5))))
                    .isInstanceOf(JetonInvalideException.class);
        }

        @Test
        void uneToleranceSuperieureA60SecondesEstRefuseeALaConstruction() {
            assertThatThrownBy(() -> verificateur(Duration.ofSeconds(61)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void uneToleranceNegativeEstRefuseeALaConstruction() {
            assertThatThrownBy(() -> verificateur(Duration.ofSeconds(-1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    static Stream<Arguments> attaques() {
        return Stream.of(
                cas("signature d'une autre clé", JwtTestFactory::signeParUneAutreCle),
                cas("charge utile altérée après signature", JwtTestFactory::signatureAlteree),
                cas("alg none", JwtTestFactory::algNone),
                cas("alg none avec signature recopiée", JwtTestFactory::algNoneAvecSignatureRecopiee),
                cas("confusion HS256 (PEM de la clé publique comme secret)", JwtTestFactory::confusionHs256),
                cas("confusion HS256 (DER de la clé publique comme secret)", JwtTestFactory::confusionHs256Der),
                cas("RS384 avec la bonne clé", j -> j.algorithmeNonEpingle(JWSAlgorithm.RS384)),
                cas("RS512 avec la bonne clé", j -> j.algorithmeNonEpingle(JWSAlgorithm.RS512)),
                cas("PS256 avec la bonne clé", j -> j.algorithmeNonEpingle(JWSAlgorithm.PS256)),
                cas("clé attaquante embarquée (jwk)", JwtTestFactory::jwkEmbarque),
                cas("mauvais émetteur", JwtTestFactory::mauvaisEmetteur),
                cas("sans émetteur", JwtTestFactory::sansEmetteur),
                cas("mauvaise audience", JwtTestFactory::mauvaiseAudience),
                cas("sans audience", JwtTestFactory::sansAudience),
                cas("rôle inconnu", JwtTestFactory::roleInconnu),
                cas("sans rôle", JwtTestFactory::sansRole),
                cas("rôle en minuscules", JwtTestFactory::roleEnMinuscules),
                cas("rôle en tableau", JwtTestFactory::roleEnTableau),
                cas("DISTRIBUTEUR sans distributeur_id", JwtTestFactory::distributeurSansIdentifiant),
                cas("PHARMACIEN avec distributeur_id", JwtTestFactory::pharmacienAvecIdentifiantDistributeur),
                cas("PRESCRIPTEUR avec distributeur_id", JwtTestFactory::prescripteurAvecIdentifiantDistributeur),
                cas("distributeur_id mal formé", JwtTestFactory::distributeurIdentifiantMalForme),
                cas("distributeur_id non textuel", JwtTestFactory::distributeurIdentifiantNonTexte),
                cas("sans sujet", JwtTestFactory::sansSujet),
                cas("sujet de plus de 128 caractères", JwtTestFactory::sujetTropLong),
                cas("jeton surdimensionné (> 8 KiB)", JwtTestFactory::surdimensionne),
                cas("jeton mal formé", j -> JwtTestFactory.malForme()),
                cas("trois segments illisibles", j -> JwtTestFactory.troisSegmentsIllisibles()));
    }

    private static Arguments cas(String nom, Function<JwtTestFactory, String> fabrique) {
        return Arguments.of(nom, fabrique);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("attaques")
    void jetonRefuseAvecLeMessageGenerique(String nom, Function<JwtTestFactory, String> fabrique) {
        assertRefuse(fabrique.apply(JETONS));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "Bearer", "."})
    void jetonAbsentOuVideRefuse(String jeton) {
        assertRefuse(jeton);
    }

    @Test
    void leRefusEstIdentiqueQuelleQueSoitLaRaison() {
        Throwable expire = catchThrowable(() -> verificateur.verifier(JETONS.expire()));
        Throwable signature = catchThrowable(() -> verificateur.verifier(JETONS.signeParUneAutreCle()));
        Throwable role = catchThrowable(() -> verificateur.verifier(JETONS.roleInconnu()));

        assertThat(expire).isInstanceOf(JetonInvalideException.class);
        assertThat(signature).isInstanceOf(JetonInvalideException.class);
        assertThat(role).isInstanceOf(JetonInvalideException.class);
        assertThat(expire.getMessage()).isEqualTo(signature.getMessage()).isEqualTo(role.getMessage());
    }

    @Test
    void unJetonJusteSousLaLimiteDeTailleResteTraite() {
        // Jeton valide d'environ 7 KiB : la borne ne doit pas refuser les jetons légitimes « gras ».
        String jeton = JETONS.avecClaims(b -> b.claim("bourrage", "x".repeat(5 * 1024)));
        assertThat(jeton.length()).isLessThan(VerificateurJwt.TAILLE_MAX_JETON);

        assertThat(verificateur.verifier(jeton).role()).isEqualTo(Role.PHARMACIEN);
    }
}
