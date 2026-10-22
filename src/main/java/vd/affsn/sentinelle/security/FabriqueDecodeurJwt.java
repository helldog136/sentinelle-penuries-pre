package vd.affsn.sentinelle.security;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Construit le décodeur Nimbus de Sentinelle : RS256 ÉPINGLÉ (tout autre {@code alg}, dont
 * {@code none} et HS256, est refusé, quel que soit l'en-tête du jeton), signature avec la seule clé
 * configurée (une clé embarquée {@code jwk} est ignorée), {@code exp} obligatoire, {@code nbf},
 * {@code iss}, {@code aud}, avec une tolérance d'horloge bornée.
 */
public final class FabriqueDecodeurJwt {

    /** Tolérance d'horloge maximale acceptée. */
    public static final Duration TOLERANCE_MAX = Duration.ofSeconds(60);

    private FabriqueDecodeurJwt() {
    }

    public static JwtDecoder creer(RSAPublicKey cle,
                                   String emetteur,
                                   String audience,
                                   Duration tolerance,
                                   Clock horloge) {
        Objects.requireNonNull(cle, "clé publique");
        Objects.requireNonNull(horloge, "horloge");
        if (emetteur == null || emetteur.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("Émetteur et audience obligatoires");
        }
        if (tolerance == null || tolerance.isNegative() || tolerance.compareTo(TOLERANCE_MAX) > 0) {
            throw new IllegalArgumentException("Tolérance d'horloge hors de [0 ; 60] secondes");
        }

        NimbusJwtDecoder decodeur = NimbusJwtDecoder.withPublicKey(cle)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        JwtTimestampValidator dates = new JwtTimestampValidator(tolerance);
        dates.setClock(horloge);
        JwtClaimValidator<Instant> expirationObligatoire = new JwtClaimValidator<>(JwtClaimNames.EXP, Objects::nonNull);
        JwtIssuerValidator emetteurAttendu = new JwtIssuerValidator(emetteur);
        JwtClaimValidator<List<String>> audienceAttendue =
                new JwtClaimValidator<>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience));

        decodeur.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                expirationObligatoire, dates, emetteurAttendu, audienceAttendue));
        return decodeur;
    }
}
