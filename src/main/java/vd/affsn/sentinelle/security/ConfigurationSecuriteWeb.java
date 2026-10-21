package vd.affsn.sentinelle.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Chaîne de sécurité REST (ADR-002) : sans session, CSRF désactivé (API bearer),
 * {@code /actuator/health} permis, {@code /api/**} réservé à PHARMACIEN et PRESCRIPTEUR, tout le
 * reste refusé ; 401 {@code WWW-Authenticate: Bearer} et 403 en {@code application/problem+json}.
 *
 * <p>STUB (phase A) : chaîne fermée (tout refusé), filtre JWT non implémenté.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ConfigurationSecuriteWeb {

    @Bean
    SecurityFilterChain chaineSecuriteRest(HttpSecurity http, VerificateurJwt verificateurJwt) throws Exception {
        http.addFilterBefore(new FiltreAuthentificationJwt(verificateurJwt, new PointEntreeBearer()),
                        AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(autorisations -> autorisations.anyRequest().denyAll());
        return http.build();
    }
}
