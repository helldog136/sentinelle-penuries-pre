package vd.affsn.sentinelle.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import vd.affsn.sentinelle.domain.model.Role;

/**
 * Configuration JWT : {@code config/security.yml}, {@link ProprietesJwt} validé et démarrage
 * fail-fast sans clé hors profil test (ADR-002).
 */
class ConfigurationJwtTest {

    private static final String CLE_TEST = "classpath:" + JwtTestFactory.CHEMIN_CLE_PUBLIQUE;
    private static final String PREFIXE = "sentinelle.security.jwt.";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConfigurationJwt.class);

    private static List<PropertySource<?>> documentsSecurityYml() {
        try {
            return new YamlPropertySourceLoader().load("config/security.yml", new ClassPathResource("config/security.yml"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String valeur(PropertySource<?> document, String cle) {
        Object v = document.getProperty(cle);
        return v == null ? null : v.toString();
    }

    /** Ajoute les documents du YAML : le premier ajouté en dernier = priorité au profil test. */
    private ApplicationContextRunner avecSecurityYml(boolean profilTest) {
        List<PropertySource<?>> docs = documentsSecurityYml();
        return runner.withInitializer(contexte -> {
            var sources = contexte.getEnvironment().getPropertySources();
            sources.addLast(docs.get(0));
            if (profilTest) {
                sources.addBefore(docs.get(0).getName(), docs.get(1));
            }
        });
    }

    private ApplicationContextRunner avecProprietesValides() {
        return runner.withPropertyValues(
                PREFIXE + "public-key=" + CLE_TEST,
                PREFIXE + "issuer=" + JwtTestFactory.EMETTEUR,
                PREFIXE + "audience=" + JwtTestFactory.AUDIENCE);
    }

    // ------------------------------------------------------------------ contenu du fichier

    @Test
    void securityYmlNeContientAucuneValeurParDefautHorsProfilTest() {
        PropertySource<?> defaut = documentsSecurityYml().get(0);

        assertThat(valeur(defaut, PREFIXE + "public-key")).isEqualTo("${SENTINELLE_JWT_PUBLIC_KEY_LOCATION}");
        assertThat(valeur(defaut, PREFIXE + "issuer")).isEqualTo("${SENTINELLE_JWT_ISSUER}");
        assertThat(valeur(defaut, PREFIXE + "audience")).isEqualTo("${SENTINELLE_JWT_AUDIENCE}");
        assertThat(valeur(defaut, PREFIXE + "leeway-secondes")).isEqualTo("30");
        assertThat(valeur(defaut, "spring.config.activate.on-profile")).isNull();
    }

    @Test
    void securityYmlReserveLaCleDeTestAuProfilTest() {
        List<PropertySource<?>> docs = documentsSecurityYml();
        assertThat(docs).hasSize(2);
        PropertySource<?> test = docs.get(1);

        assertThat(valeur(test, "spring.config.activate.on-profile")).isEqualTo("test");
        assertThat(valeur(test, PREFIXE + "public-key")).isEqualTo(CLE_TEST);
        assertThat(valeur(test, PREFIXE + "issuer")).isEqualTo(JwtTestFactory.EMETTEUR).endsWith(".invalid");
        assertThat(valeur(test, PREFIXE + "audience")).isEqualTo(JwtTestFactory.AUDIENCE).endsWith(".invalid");
    }

    // ------------------------------------------------------------------ démarrage

    @Test
    void profilTestDemarreEtVerifieLesJetonsDeTest() {
        avecSecurityYml(true).run(contexte -> {
            assertThat(contexte).hasNotFailed();
            ProprietesJwt proprietes = contexte.getBean(ProprietesJwt.class);
            assertThat(proprietes.leewaySecondes()).isEqualTo(30);
            assertThat(contexte.getBean(VerificateurJwt.class).verifier(JwtTestFactory.systeme().pharmacien()).role())
                    .isEqualTo(Role.PHARMACIEN);
        });
    }

    @Test
    void horsProfilTestSansVariablesDEnvironnementLeDemarrageEchoue() {
        avecSecurityYml(false).run(contexte -> assertThat(contexte).hasFailed());
    }

    @Test
    void horsProfilTestAvecLaCleMaisSansEmetteurNiAudienceLeDemarrageEchoue() {
        avecSecurityYml(false)
                .withPropertyValues("SENTINELLE_JWT_PUBLIC_KEY_LOCATION=" + CLE_TEST)
                .run(contexte -> assertThat(contexte).hasFailed());
    }

    @Test
    void horsProfilTestAvecLesTroisVariablesLeDemarrageReussit() {
        avecSecurityYml(false)
                .withPropertyValues(
                        "SENTINELLE_JWT_PUBLIC_KEY_LOCATION=" + CLE_TEST,
                        "SENTINELLE_JWT_ISSUER=" + JwtTestFactory.EMETTEUR,
                        "SENTINELLE_JWT_AUDIENCE=" + JwtTestFactory.AUDIENCE)
                .run(contexte -> {
                    assertThat(contexte).hasNotFailed();
                    assertThat(contexte.getBean(VerificateurJwt.class)
                            .verifier(JwtTestFactory.systeme().prescripteur()).role()).isEqualTo(Role.PRESCRIPTEUR);
                });
    }

    // ------------------------------------------------------------------ validation des propriétés

    @Test
    void toleranceParDefautDe30Secondes() {
        avecProprietesValides().run(contexte -> {
            assertThat(contexte).hasNotFailed();
            assertThat(contexte.getBean(ProprietesJwt.class).leewaySecondes()).isEqualTo(30);
        });
    }

    @Test
    void toleranceDe60SecondesAcceptee() {
        avecProprietesValides().withPropertyValues(PREFIXE + "leeway-secondes=60")
                .run(contexte -> assertThat(contexte).hasNotFailed());
    }

    @Test
    void toleranceSuperieureA60SecondesRefusee() {
        avecProprietesValides().withPropertyValues(PREFIXE + "leeway-secondes=61")
                .run(contexte -> assertThat(contexte).hasFailed());
    }

    @Test
    void toleranceNegativeRefusee() {
        avecProprietesValides().withPropertyValues(PREFIXE + "leeway-secondes=-1")
                .run(contexte -> assertThat(contexte).hasFailed());
    }

    @Test
    void clePubliqueAbsenteRefusee() {
        runner.withPropertyValues(PREFIXE + "issuer=" + JwtTestFactory.EMETTEUR, PREFIXE + "audience=" + JwtTestFactory.AUDIENCE)
                .run(contexte -> assertThat(contexte).hasFailed());
    }

    @Test
    void clePubliqueIntrouvableRefusee() {
        avecProprietesValides().withPropertyValues(PREFIXE + "public-key=classpath:test-keys/absente.pem")
                .run(contexte -> assertThat(contexte).hasFailed());
    }

    @Test
    void clePriveeAuLieuDeLaClePubliqueRefusee() {
        avecProprietesValides().withPropertyValues(PREFIXE + "public-key=classpath:" + JwtTestFactory.CHEMIN_CLE_PRIVEE)
                .run(contexte -> assertThat(contexte).hasFailed());
    }

    @Test
    void emetteurAbsentRefuse() {
        runner.withPropertyValues(PREFIXE + "public-key=" + CLE_TEST, PREFIXE + "audience=" + JwtTestFactory.AUDIENCE)
                .run(contexte -> assertThat(contexte).hasFailed());
    }

    @Test
    void audienceVideRefusee() {
        avecProprietesValides().withPropertyValues(PREFIXE + "audience= ")
                .run(contexte -> assertThat(contexte).hasFailed());
    }
}
