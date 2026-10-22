package vd.affsn.sentinelle.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import vd.affsn.sentinelle.SentinelleApplication;

/**
 * DÉMARRAGE et propriété {@code sentinelle.regles.alternative-obligatoire.entree-en-vigueur}
 * (ADR-004 § 5, AC-19, AC-20) : la VRAIE application (profil test, gRPC in-process activé, base H2
 * dédiée par démarrage) est lancée pour chaque valeur.
 * <ul>
 *   <li>absente : démarre (règle immédiate, le comportement est vérifié de bout en bout ailleurs) ;</li>
 *   <li>date valide AAAA-MM-JJ : démarre ;</li>
 *   <li>invalide (mois 13, texte, date inexistante, autre format) : le démarrage ÉCHOUE avec une
 *       erreur de configuration qui NOMME la propriété.</li>
 * </ul>
 */
class ConfigurationEntreeEnVigueurDemarrageTest {

    static final String PROPRIETE = "sentinelle.regles.alternative-obligatoire.entree-en-vigueur";

    @ParameterizedTest(name = "entree-en-vigueur = \"{0}\" : démarrage refusé")
    @ValueSource(strings = {"2026-13-01", "demain", "2026-02-30", "01.12.2026", "2026-12-1"})
    void ac19_uneValeurInvalideEmpecheLeDemarrageEnNommantLaPropriete(String valeur) {
        Throwable echec = catchThrowable(() -> {
            try (ConfigurableApplicationContext contexte = demarrer(PROPRIETE + "=" + valeur)) {
                assertThat(contexte.isActive()).isTrue();
            }
        });

        assertThat(echec).as("le démarrage aurait dû échouer pour %s=%s", PROPRIETE, valeur).isNotNull();
        assertThat(echec).as("échec de démarrage, pas d'assertion").isNotInstanceOf(AssertionError.class);
        assertThat(messages(echec)).as("l'erreur de configuration nomme la propriété")
                .anySatisfy(m -> assertThat(m).contains(PROPRIETE));
    }

    @Test
    void ac19_uneDateValideDemarre() {
        try (ConfigurableApplicationContext contexte = demarrer(PROPRIETE + "=2026-12-01")) {
            assertThat(contexte.isActive()).isTrue();
        }
    }

    @Test
    void ac20_laProprieteEstOptionnelle() {
        try (ConfigurableApplicationContext contexte = demarrer()) {
            assertThat(contexte.isActive()).isTrue();
            assertThat(contexte.getEnvironment().getProperty(PROPRIETE)).isNull();
        }
    }

    private static ConfigurableApplicationContext demarrer(String... proprietes) {
        String nom = "sentinelle-demarrage-" + UUID.randomUUID();
        List<String> toutes = new ArrayList<>(List.of(
                "spring.datasource.url=jdbc:h2:mem:" + nom + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                        + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                "sentinelle.grpc.enabled=true",
                "grpc.server.port=-1",
                "grpc.server.in-process-name=" + nom,
                "spring.main.banner-mode=off"));
        toutes.addAll(List.of(proprietes));
        return new SpringApplicationBuilder(SentinelleApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .properties(toutes.toArray(new String[0]))
                .run();
    }

    private static List<String> messages(Throwable t) {
        List<String> messages = new ArrayList<>();
        for (Throwable c = t; c != null && messages.size() < 50; c = c.getCause()) {
            messages.add(String.valueOf(c.getMessage()));
        }
        return messages;
    }
}
