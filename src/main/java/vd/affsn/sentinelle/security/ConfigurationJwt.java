package vd.affsn.sentinelle.security;

import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Câblage de la vérification JWT, commun aux surfaces REST et gRPC. Ne dépend que de
 * l'infrastructure (clé publique) : pas de garde {@code @ConditionalOnProperty}.
 *
 * <p>Exposer un {@link JwtDecoder} désactive aussi l'utilisateur en mémoire par défaut de Spring
 * Boot (pas de mot de passe généré).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProprietesJwt.class)
public class ConfigurationJwt {

    @Bean
    JwtDecoder decodeurJwt(ProprietesJwt proprietes) {
        return FabriqueDecodeurJwt.creer(
                LecteurClePublique.lire(proprietes.publicKey()),
                proprietes.issuer(),
                proprietes.audience(),
                Duration.ofSeconds(proprietes.leewaySecondes()),
                Clock.systemUTC());
    }

    @Bean
    VerificateurJwt verificateurJwt(JwtDecoder decodeurJwt) {
        return new VerificateurJwt(decodeurJwt);
    }
}
