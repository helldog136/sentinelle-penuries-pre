package vd.affsn.sentinelle.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Fabrique de jetons de TEST, réutilisable par tous les lots (REST, gRPC, intégration).
 *
 * <p>Signe avec la clé privée {@code test-keys/TEST-ONLY-jwt-private.pem} (TEST ONLY, ne protège
 * rien). Les clés « attaquantes » sont générées à l'exécution. Tout est fictif : sujets opaques,
 * identifiants distributeur {@code PRO-NNNNNN} imaginaires, domaines {@code .invalid}.
 *
 * <p>Indépendante du code testé : elle relit elle-même les PEM, sans passer par
 * {@link LecteurClePublique}.
 */
public final class JwtTestFactory {

    public static final String EMETTEUR = "https://emetteur-de-test.affsn.invalid";
    public static final String AUDIENCE = "https://sentinelle-de-test.affsn.invalid";

    public static final String CHEMIN_CLE_PUBLIQUE = "test-keys/TEST-ONLY-jwt-public.pem";
    public static final String CHEMIN_CLE_PRIVEE = "test-keys/TEST-ONLY-jwt-private.pem";

    public static final String SUJET_DISTRIBUTEUR_111 = "distributeur-de-test-111";
    public static final String SUJET_DISTRIBUTEUR_222 = "distributeur-de-test-222";
    public static final String SUJET_PHARMACIEN = "pharmacien-de-test-01";
    public static final String SUJET_PRESCRIPTEUR = "prescripteur-de-test-01";
    public static final String DISTRIBUTEUR_111 = "PRO-111111";
    public static final String DISTRIBUTEUR_222 = "PRO-222222";

    /** Durée de vie des jetons valides fabriqués. */
    public static final Duration DUREE_DE_VIE = Duration.ofMinutes(5);

    private static final RSAPrivateKey CLE_PRIVEE_TEST = lirePrivee(CHEMIN_CLE_PRIVEE);
    private static final RSAPublicKey CLE_PUBLIQUE_TEST = lirePublique(CHEMIN_CLE_PUBLIQUE);
    private static final KeyPair PAIRE_ATTAQUANTE = genererPaireRsa();

    private final Clock horloge;

    private JwtTestFactory(Clock horloge) {
        this.horloge = horloge;
    }

    /** Fabrique alignée sur l'horloge système (pour les tests sur contexte Spring). */
    public static JwtTestFactory systeme() {
        return new JwtTestFactory(Clock.systemUTC());
    }

    /** Fabrique dont « maintenant » est figé (tests déterministes de dates). */
    public static JwtTestFactory a(Instant maintenant) {
        return new JwtTestFactory(Clock.fixed(maintenant, java.time.ZoneOffset.UTC));
    }

    public static RSAPublicKey clePubliqueTest() {
        return CLE_PUBLIQUE_TEST;
    }

    public static Resource ressourceClePubliqueTest() {
        return new ClassPathResource(CHEMIN_CLE_PUBLIQUE);
    }

    public static Resource ressourceClePriveeTest() {
        return new ClassPathResource(CHEMIN_CLE_PRIVEE);
    }

    // ------------------------------------------------------------------ briques

    public Instant maintenant() {
        return horloge.instant();
    }

    /** Claims valides : iss, aud, sub, iat, nbf = maintenant, exp = maintenant + 5 min, jti. */
    public JWTClaimsSet.Builder claimsValides(String sujet, String role, String distributeurId) {
        Instant t = maintenant();
        JWTClaimsSet.Builder b = new JWTClaimsSet.Builder()
                .issuer(EMETTEUR)
                .audience(AUDIENCE)
                .subject(sujet)
                .issueTime(Date.from(t))
                .notBeforeTime(Date.from(t))
                .expirationTime(Date.from(t.plus(DUREE_DE_VIE)))
                .jwtID(UUID.randomUUID().toString());
        if (role != null) {
            b.claim("role", role);
        }
        if (distributeurId != null) {
            b.claim("distributeur_id", distributeurId);
        }
        return b;
    }

    /** Signe en RS256 avec la clé de test. */
    public String signer(JWTClaimsSet claims) {
        return signer(claims, JWSAlgorithm.RS256, CLE_PRIVEE_TEST);
    }

    public String signer(JWTClaimsSet claims, JWSAlgorithm algorithme, RSAPrivateKey cle) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(algorithme).build(), claims);
            jwt.sign(new RSASSASigner(cle));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Jeton pharmacien valide dont on ajuste les claims. */
    public String avecClaims(Consumer<JWTClaimsSet.Builder> ajustement) {
        JWTClaimsSet.Builder b = claimsValides(SUJET_PHARMACIEN, "PHARMACIEN", null);
        ajustement.accept(b);
        return signer(b.build());
    }

    // ------------------------------------------------------------------ jetons valides

    public String pharmacien() {
        return signer(claimsValides(SUJET_PHARMACIEN, "PHARMACIEN", null).build());
    }

    public String prescripteur() {
        return signer(claimsValides(SUJET_PRESCRIPTEUR, "PRESCRIPTEUR", null).build());
    }

    public String distributeur111() {
        return signer(claimsValides(SUJET_DISTRIBUTEUR_111, "DISTRIBUTEUR", DISTRIBUTEUR_111).build());
    }

    public String distributeur222() {
        return signer(claimsValides(SUJET_DISTRIBUTEUR_222, "DISTRIBUTEUR", DISTRIBUTEUR_222).build());
    }

    public String parRole(String role) {
        return switch (role) {
            case "PHARMACIEN" -> pharmacien();
            case "PRESCRIPTEUR" -> prescripteur();
            case "DISTRIBUTEUR" -> distributeur111();
            default -> throw new IllegalArgumentException(role);
        };
    }

    // ------------------------------------------------------------------ dates

    /** Expiré depuis 10 minutes (bien au-delà de toute tolérance). */
    public String expire() {
        return expireDepuis(Duration.ofMinutes(10));
    }

    public String expireDepuis(Duration duree) {
        Instant t = maintenant();
        return avecClaims(b -> b.issueTime(Date.from(t.minus(DUREE_DE_VIE).minus(duree)))
                .notBeforeTime(Date.from(t.minus(DUREE_DE_VIE).minus(duree)))
                .expirationTime(Date.from(t.minus(duree))));
    }

    /** nbf dans 10 minutes. */
    public String pasEncoreValide() {
        return valideDans(Duration.ofMinutes(10));
    }

    public String valideDans(Duration duree) {
        Instant t = maintenant();
        return avecClaims(b -> b.notBeforeTime(Date.from(t.plus(duree)))
                .expirationTime(Date.from(t.plus(duree).plus(DUREE_DE_VIE))));
    }

    public String sansExpiration() {
        return avecClaims(b -> b.expirationTime(null));
    }

    // ------------------------------------------------------------------ signature et algorithme

    /** Signé RS256 par une AUTRE clé, générée à l'exécution. */
    public String signeParUneAutreCle() {
        return signer(claimsValides(SUJET_PHARMACIEN, "PHARMACIEN", null).build(),
                JWSAlgorithm.RS256, (RSAPrivateKey) PAIRE_ATTAQUANTE.getPrivate());
    }

    /** Charge utile modifiée après signature (rôle PHARMACIEN → PRESCRIPTEUR, sujet changé). */
    public String signatureAlteree() {
        String[] parties = pharmacien().split("\\.");
        JWTClaimsSet falsifie = claimsValides("intrus-de-test", "PRESCRIPTEUR", null).build();
        String charge = Base64URL.encode(falsifie.toString().getBytes(StandardCharsets.UTF_8)).toString();
        return parties[0] + "." + charge + "." + parties[2];
    }

    /** {@code alg: none}, sans signature. */
    public String algNone() {
        return new PlainJWT(claimsValides(SUJET_PHARMACIEN, "PHARMACIEN", null).build()).serialize();
    }

    /** {@code alg: none} suivi d'une signature RS256 valide recopiée (variante). */
    public String algNoneAvecSignatureRecopiee() {
        String[] valide = pharmacien().split("\\.");
        String entete = Base64URL.encode("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)).toString();
        return entete + "." + valide[1] + "." + valide[2];
    }

    /** Confusion HS256 : HMAC avec, pour secret, le texte PEM de la clé PUBLIQUE de test. */
    public String confusionHs256() {
        return signerHmac(lireTexte(CHEMIN_CLE_PUBLIQUE).getBytes(StandardCharsets.US_ASCII));
    }

    /** Confusion HS256 : HMAC avec, pour secret, l'encodage DER de la clé publique de test. */
    public String confusionHs256Der() {
        return signerHmac(CLE_PUBLIQUE_TEST.getEncoded());
    }

    /** Signé avec la BONNE clé mais un algorithme non épinglé (RS384, RS512, PS256...). */
    public String algorithmeNonEpingle(JWSAlgorithm algorithme) {
        return signer(claimsValides(SUJET_PHARMACIEN, "PHARMACIEN", null).build(), algorithme, CLE_PRIVEE_TEST);
    }

    /** Clé attaquante embarquée dans l'en-tête ({@code jwk}) et signature avec cette clé. */
    public String jwkEmbarque() {
        try {
            RSAKey jwk = new RSAKey.Builder((RSAPublicKey) PAIRE_ATTAQUANTE.getPublic()).build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).jwk(jwk).build(),
                    claimsValides(SUJET_PHARMACIEN, "PHARMACIEN", null).build());
            jwt.sign(new RSASSASigner(PAIRE_ATTAQUANTE.getPrivate()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ émetteur et audience

    public String mauvaisEmetteur() {
        return avecClaims(b -> b.issuer("https://emetteur-pirate.affsn.invalid"));
    }

    public String sansEmetteur() {
        return avecClaims(b -> b.issuer(null));
    }

    public String mauvaiseAudience() {
        return avecClaims(b -> b.audience("https://autre-service.affsn.invalid"));
    }

    public String sansAudience() {
        return avecClaims(b -> b.audience((String) null));
    }

    /** Audience multiple contenant la nôtre : accepté. */
    public String audienceMultipleContenantSentinelle() {
        return avecClaims(b -> b.audience(List.of("https://autre-service.affsn.invalid", AUDIENCE)));
    }

    // ------------------------------------------------------------------ claims d'identité

    public String roleInconnu() {
        return avecClaims(b -> b.claim("role", "ADMINISTRATEUR"));
    }

    public String sansRole() {
        return avecClaims(b -> b.claim("role", null));
    }

    public String roleEnMinuscules() {
        return avecClaims(b -> b.claim("role", "pharmacien"));
    }

    public String roleEnTableau() {
        return avecClaims(b -> b.claim("role", List.of("PHARMACIEN")));
    }

    public String distributeurSansIdentifiant() {
        return signer(claimsValides(SUJET_DISTRIBUTEUR_111, "DISTRIBUTEUR", null).build());
    }

    public String pharmacienAvecIdentifiantDistributeur() {
        return signer(claimsValides(SUJET_PHARMACIEN, "PHARMACIEN", DISTRIBUTEUR_111).build());
    }

    public String prescripteurAvecIdentifiantDistributeur() {
        return signer(claimsValides(SUJET_PRESCRIPTEUR, "PRESCRIPTEUR", DISTRIBUTEUR_111).build());
    }

    public String distributeurIdentifiantMalForme() {
        return signer(claimsValides(SUJET_DISTRIBUTEUR_111, "DISTRIBUTEUR", "PRO-11111").build());
    }

    public String distributeurIdentifiantNonTexte() {
        JWTClaimsSet.Builder b = claimsValides(SUJET_DISTRIBUTEUR_111, "DISTRIBUTEUR", null)
                .claim("distributeur_id", 111111);
        return signer(b.build());
    }

    public String sansSujet() {
        return avecClaims(b -> b.subject(null));
    }

    public String sujetTropLong() {
        return avecClaims(b -> b.subject("s".repeat(129)));
    }

    // ------------------------------------------------------------------ forme et taille

    /** Jeton correctement signé mais de plus de 8 KiB. */
    public String surdimensionne() {
        return avecClaims(b -> b.claim("bourrage", "x".repeat(9 * 1024)));
    }

    public static String malForme() {
        return "ceci-n-est-pas-un-jwt";
    }

    public static String troisSegmentsIllisibles() {
        return "abc.def.ghi";
    }

    // ------------------------------------------------------------------ outillage interne

    private String signerHmac(byte[] secret) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).build(),
                    claimsValides(SUJET_PHARMACIEN, "PHARMACIEN", null).build());
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String lireTexte(String chemin) {
        try (InputStream in = new ClassPathResource(chemin).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalStateException("Ressource de test illisible : " + chemin, e);
        }
    }

    private static byte[] derDuBlocPem(String chemin, String type) {
        String texte = lireTexte(chemin);
        String debut = "-----BEGIN " + type + "-----";
        String fin = "-----END " + type + "-----";
        int i = texte.indexOf(debut);
        int j = texte.indexOf(fin);
        if (i < 0 || j < i) {
            throw new IllegalStateException("Bloc PEM " + type + " absent de " + chemin);
        }
        String base64 = texte.substring(i + debut.length(), j).replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private static RSAPrivateKey lirePrivee(String chemin) {
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(derDuBlocPem(chemin, "PRIVATE KEY")));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAPublicKey lirePublique(String chemin) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(derDuBlocPem(chemin, "PUBLIC KEY")));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair genererPaireRsa() {
        try {
            KeyPairGenerator generateur = KeyPairGenerator.getInstance("RSA");
            generateur.initialize(2048);
            return generateur.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
