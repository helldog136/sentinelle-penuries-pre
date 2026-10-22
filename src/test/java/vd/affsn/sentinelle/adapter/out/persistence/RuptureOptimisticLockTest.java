package vd.affsn.sentinelle.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.StatutRupture;

/** Transactions réelles (pas de rollback de test) : deux écritures concurrentes de la même rupture. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RuptureRepositoryAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RuptureOptimisticLockTest {

    @Autowired
    private RuptureRepositoryAdapter repository;
    @Autowired
    private PlatformTransactionManager tm;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void nettoyer() {
        jdbc.update("DELETE FROM ruptures");
    }

    private static Rupture variante(Rupture base, Motif motif) {
        return Rupture.reconstituer(base.id(), base.produit(), base.declarant(), Periode.aPartirDu(LocalDate.of(2026, 5, 1)),
                motif, EnumSet.of(Region.HAUTE_PAPERASSE), List.of(), StatutRupture.EN_COURS, null);
    }

    @Test
    void deuxMisesAJourConcurrentesLaSecondeEchoue() throws Exception {
        Rupture base = RuptureFixtures.enCours("VMP-000060", LocalDate.of(2026, 5, 1), Region.HAUTE_PAPERASSE);
        repository.save(base);
        TransactionTemplate tx = new TransactionTemplate(tm);

        CountDownLatch aSauvegarde = new CountDownLatch(1);
        CountDownLatch bTermine = new CountDownLatch(1);
        // A lit et modifie, puis attend que B ait validé avant de valider à son tour.
        CompletableFuture<Void> a = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
            repository.save(variante(base, Motif.LOGISTIQUE));
            aSauvegarde.countDown();
            try {
                assertThat(bTermine.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(aSauvegarde.await(10, TimeUnit.SECONDS)).isTrue();
        tx.executeWithoutResult(s -> repository.save(variante(base, Motif.DEMANDE)));
        bTermine.countDown();

        assertThatThrownBy(() -> a.get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(OptimisticLockingFailureException.class);
        // La première validée l'emporte, la perdante n'a rien écrasé.
        assertThat(repository.findById(base.id()).orElseThrow().motif()).isEqualTo(Motif.DEMANDE);
        assertThat(jdbc.queryForObject("SELECT version FROM ruptures WHERE id = ?", Long.class, base.id().valeur()))
                .isGreaterThanOrEqualTo(1L);
    }
}
