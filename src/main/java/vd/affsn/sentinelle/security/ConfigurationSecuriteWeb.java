package vd.affsn.sentinelle.security;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Chaîne de sécurité REST (ADR-002) : sans session, CSRF désactivé (API bearer, pas de cookie),
 * {@code /actuator/health} permis, {@code /api/**} réservé à PHARMACIEN et PRESCRIPTEUR, tout le
 * reste refusé ; 401 {@code WWW-Authenticate: Bearer} et 403 en {@code application/problem+json}.
 *
 * <p>Les dispatchs d'erreur internes ({@code /error}, atteint uniquement par renvoi du conteneur)
 * sont permis pour que le statut d'origine ne soit pas masqué ; un accès direct à {@code /error}
 * reste refusé.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ConfigurationSecuriteWeb {

    @Bean
    SecurityFilterChain chaineSecuriteRest(HttpSecurity http, VerificateurJwt verificateurJwt) throws Exception {
        PointEntreeBearer pointEntree = new PointEntreeBearer();
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .exceptionHandling(erreurs -> erreurs
                        .authenticationEntryPoint(pointEntree)
                        .accessDeniedHandler(new GestionnaireAccesRefuse()))
                .addFilterBefore(new FiltreAuthentificationJwt(verificateurJwt, pointEntree),
                        AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(autorisations -> autorisations
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/**").hasAnyRole("PHARMACIEN", "PRESCRIPTEUR")
                        .anyRequest().denyAll());
        return http.build();
    }
}
