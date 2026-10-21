package vd.affsn.sentinelle.adapter.out.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import vd.affsn.sentinelle.application.port.out.AuditPort;

/** Transaction appelante, verrou de chaîne, concurrence et garde de séquence. */
class JournalAuditTransactionConcurrenceTest extends AuditTestSupport {

    private static final long ATTENTE_S = 30;

    private long compterDepuisUnAutreThread() throws Exception {
        return CompletableFuture.supplyAsync(this::compter).get(ATTENTE_S, TimeUnit.SECONDS);
    }

    private void insererRupture(UUID id) {
        jdbc.update("INSERT INTO ruptures (id, code_produit, declarant, date_debut, motif, statut, cree_le, modifie_le)"
                + " VALUES (?, 'VMP-000001', 'PRO-000001', DATE '2026-05-01', 'PRODUCTION', 'EN_COURS',"
                + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", id);
    }

    private long compterRupture(UUID id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ruptures WHERE id = ?", Long.class, id);
    }

    // ------------------------------------------------------------------ transaction appelante

    @Test
    void leBeanDuContexteEstTransactionnel() {
        assertThat(AopUtils.isAopProxy(auditPort)).isTrue();
    }

    @Test
    void lEcritureRejointLaTransactionAppelante() throws Exception {
        tx().executeWithoutResult(s -> {
            auditPort.enregistrer(evenement("dans la transaction métier"));
            // Visible dans la transaction appelante (même connexion)...
            assertThat(compter()).isEqualTo(1);
            // ... invisible des autres tant qu'elle n'est pas committée (READ COMMITTED).
            try {
                assertThat(compterDepuisUnAutreThread()).isZero();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            assertThat(verrouDe(auditPort).isHeldByCurrentThread()).isTrue();
        });
        assertThat(compter()).isEqualTo(1);
        assertThat(verrouDe(auditPort).isLocked()).isFalse();
    }

    @Test
    void entreeEcriteDansUneTransactionAnnuleeNExistePasEtNeCassePasLaChaine() {
        ecrire(auditPort, 2);
        UUID rupture = UUID.randomUUID();
        tx().executeWithoutResult(s -> {
            insererRupture(rupture);
            auditPort.enregistrer(evenement("sera annulée"));
            auditPort.enregistrer(evenement("sera annulée aussi"));
            s.setRollbackOnly();
        });
        assertThat(compter()).isEqualTo(2);
        assertThat(compterRupture(rupture)).isZero();
        assertThat(verrouDe(auditPort).isLocked()).isFalse();

        ecrire(auditPort, 2);
        List<Ligne> l = lignes();
        assertThat(l).hasSize(4);
        assertThat(l.get(2).hashPrecedent()).isEqualTo(l.get(1).hashCourant());
        assertThat(verificateur.verifier().integre()).isTrue();
    }

    @Test
    void exceptionMetierApresAuditAnnuleLAuditEtRendLeVerrou() {
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> {
            auditPort.enregistrer(evenement("x"));
            throw new IllegalStateException("échec métier");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(compter()).isZero();
        assertThat(verrouDe(auditPort).isLocked()).isFalse();
    }

    @Test
    void deuxAuditsDansLaMemeTransactionSontChainesEntreEux() {
        tx().executeWithoutResult(s -> {
            auditPort.enregistrer(evenement("1"));
            auditPort.enregistrer(evenement("2"));
            auditPort.enregistrer(evenement("3"));
            assertThat(verrouDe(auditPort).getHoldCount()).isEqualTo(1);
        });
        List<Ligne> l = lignes();
        assertThat(l.get(1).hashPrecedent()).isEqualTo(l.get(0).hashCourant());
        assertThat(l.get(2).hashPrecedent()).isEqualTo(l.get(1).hashCourant());
        assertThat(verificateur.verifier().integre()).isTrue();
        assertThat(verrouDe(auditPort).isLocked()).isFalse();
    }

    @Test
    void verrouTenuJusquALaFinDeLaTransactionPuisReleveApresCommit() throws Exception {
        AuditPort port = nouvelAdaptateur(props(null, Duration.ofMillis(200), null));
        CountDownLatch audite = new CountDownLatch(1);
        CountDownLatch terminer = new CountDownLatch(1);
        CompletableFuture<Void> a = CompletableFuture.runAsync(() -> tx().executeWithoutResult(s -> {
            port.enregistrer(evenement("A"));
            audite.countDown();
            attendre(terminer);
        }));
        assertThat(audite.await(ATTENTE_S, TimeUnit.SECONDS)).isTrue();
        assertThat(verrouDe(port).isLocked()).isTrue();

        terminer.countDown();
        a.get(ATTENTE_S, TimeUnit.SECONDS);
        assertThat(verrouDe(port).isLocked()).isFalse();
        // Un autre thread écrit immédiatement (délai de 200 ms suffisant puisque le verrou est libre).
        CompletableFuture.runAsync(() -> port.enregistrer(evenement("B"))).get(ATTENTE_S, TimeUnit.SECONDS);
        assertThat(compter()).isEqualTo(2);
        assertThat(verificateur.verifier().integre()).isTrue();
    }

    @Test
    void verrouReleveApresRollback() throws Exception {
        AuditPort port = nouvelAdaptateur(props(null, Duration.ofMillis(200), null));
        CompletableFuture.runAsync(() -> tx().executeWithoutResult(s -> {
            port.enregistrer(evenement("annulée"));
            s.setRollbackOnly();
        })).get(ATTENTE_S, TimeUnit.SECONDS);
        assertThat(verrouDe(port).isLocked()).isFalse();
        CompletableFuture.runAsync(() -> port.enregistrer(evenement("B"))).get(ATTENTE_S, TimeUnit.SECONDS);
        assertThat(lignes()).extracting(Ligne::resume).containsExactly("B");
        assertThat(lignes().get(0).hashPrecedent()).isEqualTo(GENESE_DEFAUT);
    }

    @Test
    void delaiDAttenteDuVerrouDepasseExceptionPuisRollbackDeLOperationMetier() throws Exception {
        AuditPort port = nouvelAdaptateur(props(null, Duration.ofMillis(150), null));
        CountDownLatch audite = new CountDownLatch(1);
        CountDownLatch terminer = new CountDownLatch(1);
        CompletableFuture<Void> a = CompletableFuture.runAsync(() -> tx().executeWithoutResult(s -> {
            port.enregistrer(evenement("A tient le verrou"));
            audite.countDown();
            attendre(terminer);
        }));
        try {
            assertThat(audite.await(ATTENTE_S, TimeUnit.SECONDS)).isTrue();
            UUID rupture = UUID.randomUUID();
            assertThatThrownBy(() -> tx().executeWithoutResult(s -> {
                insererRupture(rupture);
                port.enregistrer(evenement("B attend"));
            })).isInstanceOf(EcritureAuditImpossibleException.class)
                    .hasMessageContaining("délai");
            assertThat(compterRupture(rupture)).isZero();
            assertThat(verrouDe(port).isHeldByCurrentThread()).isFalse();
        } finally {
            terminer.countDown();
        }
        a.get(ATTENTE_S, TimeUnit.SECONDS);
        assertThat(lignes()).extracting(Ligne::resume).containsExactly("A tient le verrou");
        assertThat(verificateur.verifier().integre()).isTrue();
    }

    @Test
    void requiresNewSansAuditEnglobantAutorise() {
        tx().executeWithoutResult(s -> txRequiresNew().executeWithoutResult(s2 ->
                auditPort.enregistrer(evenement("interne"))));
        assertThat(compter()).isEqualTo(1);
        assertThat(verificateur.verifier().integre()).isTrue();
    }

    @Test
    void auditApresUneTransactionRequiresNewAuditeeDansLaTransactionEnglobante() {
        tx().executeWithoutResult(s -> {
            txRequiresNew().executeWithoutResult(s2 -> auditPort.enregistrer(evenement("interne committée")));
            auditPort.enregistrer(evenement("englobante"));
        });
        assertThat(lignes()).extracting(Ligne::resume).containsExactly("interne committée", "englobante");
        assertThat(verificateur.verifier().integre()).isTrue();
    }

    // ------------------------------------------------------------------ concurrence

    @Test
    void huitThreadsDixEcrituresSansFourche() throws Exception {
        int threads = 8;
        int parThread = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch prets = new CountDownLatch(threads);
        CountDownLatch depart = new CountDownLatch(1);
        try {
            List<Future<?>> taches = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int numero = t;
                taches.add(pool.submit(() -> {
                    prets.countDown();
                    attendre(depart);
                    for (int i = 0; i < parThread; i++) {
                        if (i % 2 == 0) {
                            auditPort.enregistrer(evenement("t" + numero + "-" + i));
                        } else {
                            // Une écriture sur deux rejoint une transaction appelante.
                            int k = i;
                            tx().executeWithoutResult(s -> auditPort.enregistrer(evenement("t" + numero + "-" + k)));
                        }
                    }
                    return null;
                }));
            }
            assertThat(prets.await(ATTENTE_S, TimeUnit.SECONDS)).isTrue();
            depart.countDown();
            for (Future<?> f : taches) {
                f.get(ATTENTE_S, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        List<Ligne> l = lignes();
        assertThat(l).hasSize(threads * parThread);
        assertThat(l.stream().map(Ligne::hashPrecedent).distinct()).hasSize(threads * parThread);
        assertThat(l.get(l.size() - 1).sequence() - l.get(0).sequence() + 1).isEqualTo(threads * parThread);
        for (int i = 1; i < l.size(); i++) {
            assertThat(l.get(i).hashPrecedent()).isEqualTo(l.get(i - 1).hashCourant());
        }
        ResultatVerificationIntegrite r = verificateur.verifier();
        assertThat(r.integre()).isTrue();
        assertThat(r.entreesVerifiees()).isEqualTo(threads * parThread);
        assertThat(verrouDe(auditPort).isLocked()).isFalse();
    }

    // ------------------------------------------------------------------ garde de séquence

    /**
     * Lecture en retard simulée par une isolation SERIALIZABLE chez l'appelant : son instantané
     * est figé avant le commit d'un autre écrivain ; l'écriture doit être refusée plutôt que forker.
     */
    @Test
    void instantanePerimeEcritureRefuseePlutotQueFourche() throws Exception {
        ecrire(auditPort, 1);
        TransactionTemplate serialisable = tx();
        serialisable.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        CountDownLatch instantanePris = new CountDownLatch(1);
        CountDownLatch autreCommitte = new CountDownLatch(1);
        CompletableFuture<Void> lecteurEnRetard = CompletableFuture.runAsync(() -> serialisable.executeWithoutResult(s -> {
            assertThat(compter()).isEqualTo(1); // fige l'instantané
            instantanePris.countDown();
            attendre(autreCommitte);
            assertThat(compter()).as("instantané figé par l'isolation").isEqualTo(1);
            auditPort.enregistrer(evenement("depuis un instantané périmé"));
        }));
        assertThat(instantanePris.await(ATTENTE_S, TimeUnit.SECONDS)).isTrue();
        auditPort.enregistrer(evenement("committée entre-temps"));
        autreCommitte.countDown();

        assertThatThrownBy(() -> lecteurEnRetard.get(ATTENTE_S, TimeUnit.SECONDS))
                .hasCauseInstanceOf(EcritureAuditImpossibleException.class)
                .cause().hasMessageContaining("instantané périmé");
        assertThat(compter()).isEqualTo(2);
        assertThat(verificateur.verifier().integre()).isTrue();
        assertThat(verrouDe(auditPort).isLocked()).isFalse();
    }

    @Test
    void attenteDuVerrouInterrompueRefuseeEtDrapeauRestaure() {
        try {
            assertThatThrownBy(() -> tx().executeWithoutResult(s -> {
                Thread.currentThread().interrupt();
                auditPort.enregistrer(evenement("interrompue"));
            })).isInstanceOf(EcritureAuditImpossibleException.class).hasMessageContaining("interrompue");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        assertThat(compter()).isZero();
        assertThat(verrouDe(auditPort).isLocked()).isFalse();
    }

    /**
     * Interblocage verrou applicatif / verrou de ligne : A tient la ligne d'une rupture et attend le
     * verrou d'audit ; B tient le verrou d'audit et veut la ligne. Le délai d'attente borné fait
     * échouer A (rollback, ligne libérée) et B se termine.
     */
    @Test
    void interblocageVerrouDAuditEtVerrouDeLigneResoluParLeDelai() throws Exception {
        AuditPort port = nouvelAdaptateur(props(null, Duration.ofMillis(200), null));
        UUID rupture = UUID.randomUUID();
        insererRupture(rupture);
        CountDownLatch bAudite = new CountDownLatch(1);
        CompletableFuture<Void> b = CompletableFuture.runAsync(() -> tx().executeWithoutResult(s -> {
            port.enregistrer(evenement("B"));
            bAudite.countDown();
            jdbc.update("UPDATE ruptures SET motif = 'DEMANDE' WHERE id = ?", rupture);
        }));
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> {
            jdbc.update("UPDATE ruptures SET motif = 'LOGISTIQUE' WHERE id = ?", rupture);
            attendre(bAudite);
            port.enregistrer(evenement("A"));
        })).isInstanceOf(EcritureAuditImpossibleException.class);
        b.get(ATTENTE_S, TimeUnit.SECONDS);
        assertThat(jdbc.queryForObject("SELECT motif FROM ruptures WHERE id = ?", String.class, rupture))
                .isEqualTo("DEMANDE");
        assertThat(lignes()).extracting(Ligne::resume).containsExactly("B");
        assertThat(verificateur.verifier().integre()).isTrue();
    }

    /**
     * LIMITE documentée (Javadoc de l'adaptateur, DELEGATION.md) : le verrou est local à une
     * instance. Deux instances (simulées par deux adaptateurs distincts) peuvent forker la chaîne ;
     * la fourche est alors détectée, pas empêchée (aucune contrainte UNIQUE sur hash_precedent).
     */
    @Test
    void deuxInstancesPeuventForker_detecteeNonEmpechee() throws Exception {
        AuditPort instance1 = nouvelAdaptateur(proprietes);
        AuditPort instance2 = nouvelAdaptateur(proprietes);
        instance1.enregistrer(evenement("socle"));
        CountDownLatch i1Ecrit = new CountDownLatch(1);
        CountDownLatch i2Committe = new CountDownLatch(1);
        CompletableFuture<Void> t1 = CompletableFuture.runAsync(() -> tx().executeWithoutResult(s -> {
            instance1.enregistrer(evenement("instance 1, non committée"));
            i1Ecrit.countDown();
            attendre(i2Committe);
        }));
        assertThat(i1Ecrit.await(ATTENTE_S, TimeUnit.SECONDS)).isTrue();
        instance2.enregistrer(evenement("instance 2"));
        i2Committe.countDown();
        t1.get(ATTENTE_S, TimeUnit.SECONDS);

        List<Ligne> l = lignes();
        assertThat(l.get(1).hashPrecedent()).isEqualTo(l.get(2).hashPrecedent());
        ResultatVerificationIntegrite r = verificateur.verifier();
        assertThat(r.natureRupture()).contains(ResultatVerificationIntegrite.NatureRupture.FOURCHE);
        assertThat(r.premiereRupture()).contains(l.get(2).sequence());
    }

    /**
     * Le gestionnaire de transactions du contexte (JPA) refuse NESTED : le scénario « audit puis
     * point de sauvegarde annulé » (qui laisserait une séquence annulée dans l'état de la
     * transaction) n'est donc pas atteignable avec la configuration actuelle.
     */
    @Test
    void nestedRefuseParLeGestionnaireDuContexte() {
        TransactionTemplate imbriquee = tx();
        imbriquee.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        assertThatThrownBy(() -> tx().executeWithoutResult(s -> {
            auditPort.enregistrer(evenement("englobante"));
            imbriquee.executeWithoutResult(s2 -> auditPort.enregistrer(evenement("imbriquée")));
        })).isInstanceOf(org.springframework.transaction.NestedTransactionNotSupportedException.class);
        assertThat(compter()).isZero();
        assertThat(verrouDe(auditPort).isLocked()).isFalse();
    }

    @Test
    void synchronisationInactiveRefusee() {
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        JournalAuditJdbcAdapter brut = new JournalAuditJdbcAdapter(namedJdbc, proprietes);
        assertThatThrownBy(() -> brut.enregistrer(evenement("x"))).isInstanceOf(EcritureAuditImpossibleException.class);
        assertThat(verrouDe(proxyTransactionnel(brut)).isLocked()).isFalse();
    }

    static void attendre(CountDownLatch latch) {
        try {
            if (!latch.await(ATTENTE_S, TimeUnit.SECONDS)) {
                throw new IllegalStateException("attente expirée");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ défauts constatés

    /** Tests volontairement rouges : défauts du code de production, non corrigés ici. */
    @Nested
    class BugsDecouverts {

        /**
         * La garde « REQUIRES_NEW alors que la transaction englobante a déjà audité » est inopérante :
         * la ressource transactionnelle de l'adaptateur (clé {@code cleRessource}) n'est PAS suspendue
         * par Spring avec la transaction englobante ; la transaction interne la retrouve, croit le
         * verrou « déjà tenu pour cette transaction » et écrit en lisant la dernière entrée committée,
         * sans voir l'entrée non committée de l'englobante : FOURCHE avec une seule instance.
         */
        @Test
        void requiresNewApresAuditEnglobantRefuseEtSansFourche() {
            Throwable[] interne = new Throwable[1];
            tx().executeWithoutResult(s -> {
                auditPort.enregistrer(evenement("englobante"));
                try {
                    txRequiresNew().executeWithoutResult(s2 -> auditPort.enregistrer(evenement("interne")));
                } catch (RuntimeException e) {
                    interne[0] = e;
                }
            });
            ResultatVerificationIntegrite r = verificateur.verifier();
            assertThat(r.integre()).as("chaîne après commit : %s", r).isTrue();
            assertThat(interne[0]).isInstanceOf(EcritureAuditImpossibleException.class);
        }

        /**
         * Même cause, autre effet : la transaction REQUIRES_NEW annulée réécrit la séquence de l'état
         * PARTAGÉ avec l'englobante ; au commit de l'englobante, cette séquence annulée (supérieure à
         * toute séquence committée) devient la « dernière committée connue » et la garde de séquence
         * refuse ensuite TOUTE écriture de l'instance (déni de service jusqu'au redémarrage).
         */
        @Test
        void requiresNewAnnuleeNeBloquePasLesEcrituresSuivantes() {
            tx().executeWithoutResult(s -> {
                auditPort.enregistrer(evenement("englobante"));
                try {
                    txRequiresNew().executeWithoutResult(s2 -> {
                        auditPort.enregistrer(evenement("interne annulée"));
                        s2.setRollbackOnly();
                    });
                } catch (EcritureAuditImpossibleException attendu) {
                    // refus attendu : la transaction englobante continue
                }
            });
            assertThat(lignes()).extracting(Ligne::resume).containsExactly("englobante");

            auditPort.enregistrer(evenement("suivante"));
            assertThat(compter()).isEqualTo(2);
        }
    }
}
