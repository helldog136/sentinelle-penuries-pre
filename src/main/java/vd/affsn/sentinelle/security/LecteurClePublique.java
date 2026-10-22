package vd.affsn.sentinelle.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.core.io.Resource;

/**
 * Lit une clé PUBLIQUE RSA au format PEM ({@code -----BEGIN PUBLIC KEY-----}, SubjectPublicKeyInfo).
 * Le texte hors du bloc PEM (ligne d'avertissement par exemple) est ignoré.
 *
 * <p>Refuse (exception {@link IllegalStateException}, message sans contenu de fichier) : ressource
 * absente ou illisible, contenu non PEM, clé privée, clé non RSA, clé RSA de moins de 2048 bits.
 */
public final class LecteurClePublique {

    /** Taille RSA minimale acceptée. */
    public static final int BITS_MIN = 2048;

    private static final int TAILLE_MAX_FICHIER = 64 * 1024;
    private static final String DEBUT = "-----BEGIN PUBLIC KEY-----";
    private static final String FIN = "-----END PUBLIC KEY-----";

    private LecteurClePublique() {
    }

    public static RSAPublicKey lire(Resource emplacement) {
        if (emplacement == null) {
            throw refus("emplacement non configuré");
        }
        if (!emplacement.exists()) {
            throw refus("ressource introuvable (" + emplacement.getDescription() + ")");
        }
        String texte = lireTexte(emplacement);
        if (texte.contains("PRIVATE KEY")) {
            throw refus("le fichier contient une clé PRIVÉE ; seule une clé publique est acceptée");
        }
        int debut = texte.indexOf(DEBUT);
        int fin = texte.indexOf(FIN);
        if (debut < 0 || fin < debut) {
            throw refus("bloc PEM « PUBLIC KEY » absent");
        }
        byte[] der;
        try {
            der = Base64.getDecoder().decode(texte.substring(debut + DEBUT.length(), fin).replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            throw refus("bloc PEM corrompu");
        }
        PublicKey cle;
        try {
            cle = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | RuntimeException e) {
            throw refus("le bloc PEM n'est pas une clé publique RSA");
        }
        if (!(cle instanceof RSAPublicKey rsa)) {
            throw refus("le bloc PEM n'est pas une clé publique RSA");
        }
        if (rsa.getModulus().bitLength() < BITS_MIN) {
            throw refus("clé RSA de moins de " + BITS_MIN + " bits");
        }
        return rsa;
    }

    private static String lireTexte(Resource emplacement) {
        try (InputStream flux = emplacement.getInputStream()) {
            byte[] octets = flux.readNBytes(TAILLE_MAX_FICHIER + 1);
            if (octets.length > TAILLE_MAX_FICHIER) {
                throw refus("fichier trop volumineux");
            }
            return new String(octets, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw refus("ressource illisible");
        }
    }

    private static IllegalStateException refus(String raison) {
        return new IllegalStateException("Clé publique JWT refusée : " + raison);
    }
}
