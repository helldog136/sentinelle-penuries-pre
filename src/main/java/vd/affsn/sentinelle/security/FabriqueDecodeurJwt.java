package vd.affsn.sentinelle.security;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;

import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Construit le décodeur Nimbus de Sentinelle : RS256 ÉPINGLÉ (tout autre {@code alg}, dont
 * {@code none} et HS256, est refusé), signature, {@code exp} obligatoire, {@code nbf},
 * {@code iss}, {@code aud}, avec une tolérance d'horloge bornée.
 *
 * <p>STUB (phase A).
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
        return jeton -> {
            throw new UnsupportedOperationException("FabriqueDecodeurJwt : phase B");
        };
    }
}
