package vd.affsn.sentinelle.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

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
 * <p>Le binder de Spring Boot laisse un {@code ${VARIABLE}} non résolu sous forme littérale au lieu
 * d'échouer : les valeurs contenant {@code $}, <code>{</code>, <code>}</code> ou un blanc sont donc
 * refusées, pour que le démarrage échoue réellement quand l'environnement ne fournit pas la valeur.
 */
@Validated
@ConfigurationProperties(prefix = "sentinelle.security.jwt")
public record ProprietesJwt(
        @NotNull Resource publicKey,
        @NotBlank @Pattern(regexp = VALEUR_RESOLUE, message = "valeur absente ou placeholder non résolu") String issuer,
        @NotBlank @Pattern(regexp = VALEUR_RESOLUE, message = "valeur absente ou placeholder non résolu") String audience,
        @DefaultValue("30") @Min(0) @Max(60) int leewaySecondes) {

    /** Valeur non vide, sans blanc ni caractère de placeholder, bornée. */
    static final String VALEUR_RESOLUE = "[^\\s${}]{1,512}";

    /** Refuse un emplacement de clé issu d'un placeholder non résolu. */
    @AssertTrue(message = "emplacement de clé publique absent ou placeholder non résolu")
    public boolean isEmplacementClePubliqueResolu() {
        if (publicKey == null) {
            return true; // déjà signalé par @NotNull
        }
        String description = publicKey.getDescription();
        return description == null || !(description.contains("${") || description.contains("}"));
    }
}
