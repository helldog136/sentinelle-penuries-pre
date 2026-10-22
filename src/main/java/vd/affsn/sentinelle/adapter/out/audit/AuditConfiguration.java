package vd.affsn.sentinelle.adapter.out.audit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Active les propriétés {@code sentinelle.audit.*} de l'adaptateur d'audit. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuditProperties.class)
class AuditConfiguration {
}
