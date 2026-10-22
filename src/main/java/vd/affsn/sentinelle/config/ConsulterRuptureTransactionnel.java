package vd.affsn.sentinelle.config;

import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.domain.model.RuptureId;
import org.springframework.transaction.PlatformTransactionManager;
import vd.affsn.sentinelle.application.port.in.ConsulterRuptureUseCase;
import vd.affsn.sentinelle.domain.model.Acteur;

/** Décorateur transactionnel (lecture) de {@link ConsulterRuptureUseCase}. */
public class ConsulterRuptureTransactionnel implements ConsulterRuptureUseCase {

    private final ConsulterRuptureUseCase delegue;
    private final TransactionnelSupport tx;

    public ConsulterRuptureTransactionnel(ConsulterRuptureUseCase delegue, PlatformTransactionManager tm) {
        this.delegue = delegue;
        this.tx = TransactionnelSupport.lecture(tm);
    }

    @Override
    public RuptureVue consulter(Acteur acteur, RuptureId ruptureId) {
        return tx.executer(() -> delegue.consulter(acteur, ruptureId));
    }
}
