package vd.affsn.sentinelle.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Propriétés {@code sentinelle.security.jwt.*} (voir {@code config/security.yml}).
 *
 * <ul>
 *   <li>{@code public-key} : emplacement ({@code classpath:} ou {@code file:}) d'un PEM de clé
 *       PUBLIQUE RSA, obligatoire ;</li>
 *   <li>{@code issuer} : émetteur attendu (claim {@code iss}), obligatoire ;</li>
 *   <li>{@code audience} : audience attendue (claim {@code aud}), obligatoire ;</li>
 *   <li>{@code leeway-secondes} : tolérance d'horloge sur {@code exp}/{@code nbf}, 30 par défaut,
 *       entre 0 et 60.</li>
 * </ul>
 *
 * <p>STUB (phase A) : validation à écrire en phase B.
 */
@ConfigurationProperties(prefix = "sentinelle.security.jwt")
public record ProprietesJwt(Resource publicKey, String issuer, String audience, int leewaySecondes) {
}
