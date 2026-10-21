package vd.affsn.sentinelle.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

class LecteurClePubliqueTest {

    @Test
    void litLaClePubliqueDeTestMalgreLaLigneDAvertissement() {
        RSAPublicKey cle = LecteurClePublique.lire(JwtTestFactory.ressourceClePubliqueTest());

        assertThat(cle).isNotNull();
        assertThat(cle.getModulus()).isEqualTo(JwtTestFactory.clePubliqueTest().getModulus());
        assertThat(cle.getPublicExponent()).isEqualTo(JwtTestFactory.clePubliqueTest().getPublicExponent());
    }

    @Test
    void refuseUneClePrivee() {
        assertRefusSansFuite(JwtTestFactory.ressourceClePriveeTest(), "MIIEv");
    }

    @Test
    void refuseUneRessourceAbsente() {
        assertRefusSansFuite(new ClassPathResource("test-keys/absente.pem"), null);
    }

    @Test
    void refuseUnContenuNonPem() {
        assertRefusSansFuite(texte("pas une clé du tout"), "pas une clé");
    }

    @Test
    void refuseUnBlocPemCorrompu() {
        assertRefusSansFuite(texte("-----BEGIN PUBLIC KEY-----\nAAAA%%%%\n-----END PUBLIC KEY-----\n"), "AAAA");
    }

    @Test
    void refuseUneCleNonRsa() throws Exception {
        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(256);
        assertRefusSansFuite(pem(ec.generateKeyPair().getPublic().getEncoded()), null);
    }

    @Test
    void refuseUneCleRsaDeMoinsDe2048Bits() throws Exception {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(1024);
        assertRefusSansFuite(pem(rsa.generateKeyPair().getPublic().getEncoded()), null);
    }

    @Test
    void refuseUneRessourceNulle() {
        assertThat(catchThrowable(() -> LecteurClePublique.lire(null))).isInstanceOf(IllegalStateException.class);
    }

    private static void assertRefusSansFuite(Resource ressource, String extraitInterdit) {
        Throwable refus = catchThrowable(() -> LecteurClePublique.lire(ressource));
        assertThat(refus).isInstanceOf(IllegalStateException.class);
        if (extraitInterdit != null) {
            assertThat(refus.getMessage()).doesNotContain(extraitInterdit);
        }
    }

    private static Resource texte(String contenu) {
        return new ByteArrayResource(contenu.getBytes(StandardCharsets.US_ASCII), "clé de test en mémoire");
    }

    private static Resource pem(byte[] der) {
        return texte("-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END PUBLIC KEY-----\n");
    }
}
