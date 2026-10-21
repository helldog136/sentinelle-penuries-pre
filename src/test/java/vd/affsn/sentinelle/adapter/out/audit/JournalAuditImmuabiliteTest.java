package vd.affsn.sentinelle.adapter.out.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import vd.affsn.sentinelle.adapter.out.audit.ResultatVerificationIntegrite.NatureRupture;
import vd.affsn.sentinelle.application.port.out.AuditPort;

/**
 * Immuabilité : absence d'opération de modification dans le code, et détection par le
 * vérificateur des altérations faites DIRECTEMENT en base.
 *
 * <p><b>Limite documentée (ADR-003)</b> : la base n'interdit pas un {@code UPDATE} ni un
 * {@code DELETE} sur {@code journal_audit} (pas de trigger, pas de droits restreints dans le
 * schéma) : les altérations ci-dessous RÉUSSISSENT ; seul le vérificateur les détecte.
 */
class JournalAuditImmuabiliteTest extends AuditTestSupport {

    private static final Pattern SQL_MUTATION = Pattern.compile(
            "(?i)\\b(update\\s+journal_audit|delete\\s+from\\s+journal_audit|delete\\s+journal_audit"
                    + "|truncate\\s+(table\\s+)?journal_audit|merge\\s+into\\s+journal_audit"
                    + "|drop\\s+table\\s+(if\\s+exists\\s+)?journal_audit|alter\\s+table\\s+journal_audit)\\b");

    // ------------------------------------------------------------------ absence d'API de mutation

    @Test
    void lePortNExposeQuUnAjout() {
        assertThat(Arrays.stream(AuditPort.class.getMethods()).map(Method::getName)).containsExactly("enregistrer");
        assertThat(AuditPort.class.getMethods()[0].getReturnType()).isEqualTo(void.class);
    }

    @Test
    void lAdaptateurNExposeAucuneAutreMethodePublique() {
        List<String> publiques = Arrays.stream(JournalAuditJdbcAdapter.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic())
                .map(Method::getName).toList();
        assertThat(publiques).containsExactly("enregistrer");
        List<String> verificateurPubliques = Arrays.stream(VerificateurIntegriteJournalAudit.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic())
                .map(Method::getName).toList();
        assertThat(verificateurPubliques).containsExactly("verifier");
    }

    @Test
    void constantesSqlSansMutation() throws IllegalAccessException {
        for (Class<?> c : List.of(JournalAuditJdbcAdapter.class, VerificateurIntegriteJournalAudit.class)) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() == String.class && Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    String sql = (String) f.get(null);
                    assertThat(sql).as("%s.%s", c.getSimpleName(), f.getName())
                            .doesNotContainPattern("(?i)\\b(update|delete|truncate|merge|drop|alter)\\b");
                }
            }
        }
    }

    @Test
    void aucunSqlDeMutationSurJournalAuditDansLeCodeDeProduction() throws IOException {
        Path racine = Path.of("src/main");
        assertThat(racine).isDirectory();
        try (Stream<Path> fichiers = Files.walk(racine)) {
            List<Path> fautifs = fichiers.filter(Files::isRegularFile)
                    .filter(p -> p.toString().matches(".*\\.(java|sql|xml|yml|yaml|properties)$"))
                    .filter(p -> {
                        try {
                            return SQL_MUTATION.matcher(Files.readString(p)).find();
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    }).toList();
            assertThat(fautifs).isEmpty();
        }
    }

    @Test
    void lePortNEstImplementeQueParLAdaptateurJdbc() {
        assertThat(AopProxyCible.classeCible(auditPort)).isEqualTo(JournalAuditJdbcAdapter.class);
    }

    // ------------------------------------------------------------------ altérations directes en base

    @Test
    void laBaseAccepteUnUpdateEtUnDeleteDirects_limiteDocumentee() {
        ecrire(auditPort, 3);
        assertThat(jdbc.update("UPDATE journal_audit SET resume = 'falsifié'")).isEqualTo(3);
        assertThat(jdbc.update("DELETE FROM journal_audit")).isEqualTo(3);
    }

    @Test
    void resumeModifieDetecte() {
        ecrire(auditPort, 5);
        long cible = lignes().get(2).sequence();
        jdbc.update("UPDATE journal_audit SET resume = 'falsifié' WHERE sequence = ?", cible);

        ResultatVerificationIntegrite r = verificateur.verifier();
        assertRupture(r, cible, NatureRupture.HASH_RECALCULE_DIFFERENT, 2);
        assertThat(r.derniereSequenceVerifiee()).isEqualTo(lignes().get(1).sequence());
        assertThat(r.dernierHashVerifie()).isEqualTo(lignes().get(1).hashCourant());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "type_evenement = 'CLOTURE'",
            "rupture_id = RANDOM_UUID()",
            "evenement_id = RANDOM_UUID()",
            "acteur_sujet = 'autre'",
            "acteur_role = 'PRESCRIPTEUR'",
            "instant = instant + INTERVAL '0.000001' SECOND",
            "hash_precedent = '" + "1111111111111111111111111111111111111111111111111111111111111111" + "'"})
    void toutChampModifieDetecte(String affectation) {
        ecrire(auditPort, 4);
        long cible = lignes().get(1).sequence();
        assertThat(jdbc.update("UPDATE journal_audit SET " + affectation + " WHERE sequence = ?", cible)).isEqualTo(1);

        assertRupture(verificateur.verifier(), cible, NatureRupture.HASH_RECALCULE_DIFFERENT, 1);
    }

    @Test
    void hashCourantModifieDetecte() {
        ecrire(auditPort, 4);
        long cible = lignes().get(1).sequence();
        jdbc.update("UPDATE journal_audit SET hash_courant = ? WHERE sequence = ?", "f".repeat(64), cible);

        assertRupture(verificateur.verifier(), cible, NatureRupture.HASH_RECALCULE_DIFFERENT, 1);
    }

    @Test
    void falsificationAvecHashRecalculeDetecteeParLeChainageSuivant() {
        ecrire(auditPort, 4);
        Ligne l = lignes().get(1);
        Ligne modifiee = new Ligne(l.sequence(), l.evenementId(), l.type(), l.ruptureId(), l.sujet(), l.role(),
                l.instant(), "falsifié", l.hashPrecedent(), null);
        jdbc.update("UPDATE journal_audit SET resume = ?, hash_courant = ? WHERE sequence = ?",
                "falsifié", hashIndependant(modifiee), l.sequence());

        assertRupture(verificateur.verifier(), lignes().get(2).sequence(), NatureRupture.CHAINAGE_ROMPU, 2);
    }

    @Test
    void entreeDuMilieuSupprimeeDetectee() {
        ecrire(auditPort, 5);
        List<Ligne> avant = lignes();
        jdbc.update("DELETE FROM journal_audit WHERE sequence = ?", avant.get(2).sequence());

        assertRupture(verificateur.verifier(), avant.get(3).sequence(), NatureRupture.CHAINAGE_ROMPU, 2);
    }

    @Test
    void premiereEntreeSupprimeeDetectee() {
        ecrire(auditPort, 3);
        List<Ligne> avant = lignes();
        jdbc.update("DELETE FROM journal_audit WHERE sequence = ?", avant.get(0).sequence());

        assertRupture(verificateur.verifier(), avant.get(1).sequence(), NatureRupture.CHAINAGE_ROMPU, 0);
    }

    @Test
    void deuxEntreesEchangeesDetectees() {
        ecrire(auditPort, 5);
        List<Ligne> avant = lignes();
        long s2 = avant.get(2).sequence();
        long s3 = avant.get(3).sequence();
        jdbc.update("UPDATE journal_audit SET sequence = -1 WHERE sequence = ?", s2);
        jdbc.update("UPDATE journal_audit SET sequence = ? WHERE sequence = ?", s2, s3);
        jdbc.update("UPDATE journal_audit SET sequence = ? WHERE sequence = -1", s3);

        assertRupture(verificateur.verifier(), s2, NatureRupture.CHAINAGE_ROMPU, 2);
    }

    @Test
    void fourcheDeuxLignesDeMemeHashPrecedentDetectee() {
        ecrire(auditPort, 4);
        List<Ligne> avant = lignes();
        insererLigneForgee(avant.get(1).hashCourant(), "branche concurrente");
        long fourche = lignes().get(4).sequence();

        assertRupture(verificateur.verifier(), fourche, NatureRupture.FOURCHE, 4);
    }

    @Test
    void fourcheSurLaGeneseDetectee() {
        ecrire(auditPort, 2);
        insererLigneForgee(GENESE_DEFAUT, "seconde tête");

        assertRupture(verificateur.verifier(), lignes().get(2).sequence(), NatureRupture.FOURCHE, 2);
    }

    @Test
    void ligneForgeeChaineeCorrectementEnQueueNonDetectee_limite() {
        // Un écrivain qui a accès à la base et connaît le format peut prolonger la chaîne : seul un
        // contrôle d'accès (hors périmètre de ce code) l'en empêche.
        ecrire(auditPort, 2);
        insererLigneForgee(lignes().get(1).hashCourant(), "ajout légitime en apparence");

        ResultatVerificationIntegrite r = verificateur.verifier();
        assertThat(r.integre()).isTrue();
        assertThat(r.entreesVerifiees()).isEqualTo(3);
    }

    @Test
    void geneseChangeeDetecteeEnTete() {
        ecrire(auditPort, 3);
        VerificateurIntegriteJournalAudit autreGenese = verificateur(props("1".repeat(64), null, null));

        ResultatVerificationIntegrite r = autreGenese.verifier();
        assertRupture(r, lignes().get(0).sequence(), NatureRupture.CHAINAGE_ROMPU, 0);
        assertThat(r.derniereSequenceVerifiee()).isNull();
        assertThat(r.dernierHashVerifie()).isNull();
    }

    @Test
    void ruptureEnTeteHashAltereSansDerniereEntreeValide() {
        ecrire(auditPort, 2);
        jdbc.update("UPDATE journal_audit SET resume = 'x' WHERE sequence = ?", lignes().get(0).sequence());

        ResultatVerificationIntegrite r = verificateur.verifier();
        assertRupture(r, lignes().get(0).sequence(), NatureRupture.HASH_RECALCULE_DIFFERENT, 0);
        assertThat(r.derniereSequenceVerifiee()).isNull();
    }

    /**
     * LIMITE : la troncature de la queue (suppression des dernières entrées) n'est PAS détectable
     * par le vérificateur seul ; le journal tronqué est déclaré intègre. Seule la comparaison avec un
     * dernier hash ancré HORS base la révèle.
     */
    @Test
    void troncatureDeQueueNonDetecteeParLeVerificateur_limiteSignalee() {
        ecrire(auditPort, 5);
        String ancrageExterne = verificateur.verifier().dernierHashVerifie();
        List<Ligne> avant = lignes();
        jdbc.update("DELETE FROM journal_audit WHERE sequence >= ?", avant.get(3).sequence());

        ResultatVerificationIntegrite r = verificateur.verifier();
        assertThat(r.integre()).isTrue();
        assertThat(r.entreesVerifiees()).isEqualTo(3);
        assertThat(r.dernierHashVerifie()).isEqualTo(avant.get(2).hashCourant()).isNotEqualTo(ancrageExterne);
    }

    /**
     * En revanche, l'instance qui a écrit la queue supprimée la détecte (garde de séquence) et
     * refuse toute nouvelle écriture tant qu'elle vit : fermeture sûre, mais blocage de toutes les
     * opérations auditées jusqu'au redémarrage (l'état est en mémoire seulement).
     */
    @Test
    void troncatureDeQueueDetecteeParLInstanceEcrivaineQuiRefuseEnsuiteDEcrire() {
        ecrire(auditPort, 3);
        jdbc.update("DELETE FROM journal_audit WHERE sequence = ?", lignes().get(2).sequence());

        assertThatThrownBy(() -> auditPort.enregistrer(evenement("après troncature")))
                .isInstanceOf(EcritureAuditImpossibleException.class)
                .hasMessageContaining("tronqué");
        assertThat(compter()).isEqualTo(2);
        assertThat(verrouDe(auditPort).isLocked()).isFalse();
        // Un adaptateur neuf (redémarrage) ne voit plus rien et prolonge la chaîne tronquée.
        nouvelAdaptateur(proprietes).enregistrer(evenement("après redémarrage"));
        assertThat(verificateur.verifier().integre()).isTrue();
    }

    @Test
    void trouDeSequenceNEstPasUneRupture() {
        ecrire(auditPort, 2);
        tx().executeWithoutResult(s -> {
            auditPort.enregistrer(evenement("annulée"));
            s.setRollbackOnly();
        });
        ecrire(auditPort, 1);
        List<Ligne> l = lignes();
        assertThat(l.get(2).sequence() - l.get(1).sequence()).isGreaterThan(1);
        assertThat(verificateur.verifier().integre()).isTrue();
    }

    private static void assertRupture(ResultatVerificationIntegrite r, long sequence, NatureRupture nature,
                                      long verifiees) {
        assertThat(r.integre()).isFalse();
        assertThat(r.premiereRupture()).contains(sequence);
        assertThat(r.natureRupture()).contains(nature);
        assertThat(r.entreesVerifiees()).isEqualTo(verifiees);
    }

    private static final class AopProxyCible {
        static Class<?> classeCible(Object o) {
            return org.springframework.aop.support.AopUtils.getTargetClass(o);
        }
    }
}
