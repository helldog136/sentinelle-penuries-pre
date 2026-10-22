package vd.affsn.sentinelle.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

/**
 * Garde-fou de l'outillage : la fabrique produit bien ce qu'elle annonce, pour que les tests
 * d'attaque échouent (ou passent) pour la bonne raison. Indépendant du code de production.
 */
class JwtTestFactoryTest {

    private static final Instant T0 = Instant.parse("2026-10-21T08:00:00Z");
    private final JwtTestFactory jetons = JwtTestFactory.a(T0);

    @Test
    void leJetonValideEstSigneRs256ParLaCleDeTestEtPorteLesClaimsAttendus() throws Exception {
        SignedJWT jwt = SignedJWT.parse(jetons.distributeur111());

        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jwt.verify(new RSASSAVerifier(JwtTestFactory.clePubliqueTest()))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo(JwtTestFactory.EMETTEUR).endsWith(".invalid");
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly(JwtTestFactory.AUDIENCE);
        assertThat(jwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("DISTRIBUTEUR");
        assertThat(jwt.getJWTClaimsSet().getStringClaim("distributeur_id")).isEqualTo("PRO-111111");
        assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(T0.plus(JwtTestFactory.DUREE_DE_VIE));
    }

    @Test
    void leJetonSigneParUneAutreCleNeSeVerifiePasAvecLaCleDeTest() throws Exception {
        SignedJWT jwt = SignedJWT.parse(jetons.signeParUneAutreCle());
        assertThat(jwt.verify(new RSASSAVerifier(JwtTestFactory.clePubliqueTest()))).isFalse();
    }

    @Test
    void leJetonAltereNeSeVerifiePlus() throws Exception {
        SignedJWT jwt = SignedJWT.parse(jetons.signatureAlteree());
        assertThat(jwt.verify(new RSASSAVerifier(JwtTestFactory.clePubliqueTest()))).isFalse();
        assertThat(jwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("PRESCRIPTEUR");
    }

    @Test
    void lesJetonsDAttaqueOntLEnTeteAnnonce() throws Exception {
        assertThat(JWTParser.parse(jetons.algNone())).isInstanceOf(PlainJWT.class);
        assertThat(SignedJWT.parse(jetons.confusionHs256()).getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
        assertThat(SignedJWT.parse(jetons.confusionHs256Der()).getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
        assertThat(SignedJWT.parse(jetons.algorithmeNonEpingle(JWSAlgorithm.RS512)).getHeader().getAlgorithm())
                .isEqualTo(JWSAlgorithm.RS512);
        assertThat(SignedJWT.parse(jetons.jwkEmbarque()).getHeader().getJWK()).isNotNull();
    }

    @Test
    void leJetonSurdimensionneDepasse8KiBEtLesJetonsNormauxNon() {
        assertThat(jetons.surdimensionne().length()).isGreaterThan(8 * 1024);
        assertThat(jetons.pharmacien().length()).isLessThan(2 * 1024);
    }

    @Test
    void lesClaimsRetiresSontAbsents() throws Exception {
        assertThat(SignedJWT.parse(jetons.sansRole()).getJWTClaimsSet().getClaim("role")).isNull();
        assertThat(SignedJWT.parse(jetons.sansExpiration()).getJWTClaimsSet().getExpirationTime()).isNull();
        assertThat(SignedJWT.parse(jetons.distributeurSansIdentifiant()).getJWTClaimsSet()
                .getClaim("distributeur_id")).isNull();
    }
}
