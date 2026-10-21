package vd.affsn.sentinelle.security;

import java.security.interfaces.RSAPublicKey;

import org.springframework.core.io.Resource;

/**
 * Lit une clé PUBLIQUE RSA au format PEM ({@code -----BEGIN PUBLIC KEY-----}, SubjectPublicKeyInfo).
 *
 * <p>Refuse (exception {@link IllegalStateException}, message sans contenu de fichier) : ressource
 * absente ou illisible, contenu non PEM, clé privée, clé non RSA, clé RSA de moins de 2048 bits.
 *
 * <p>STUB (phase A).
 */
public final class LecteurClePublique {

    private LecteurClePublique() {
    }

    public static RSAPublicKey lire(Resource emplacement) {
        return null;
    }
}
