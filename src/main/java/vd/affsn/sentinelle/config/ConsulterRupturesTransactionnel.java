package vd.affsn.sentinelle.config;

import vd.affsn.sentinelle.application.port.Page;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import org.springframework.transaction.PlatformTransactionManager;
import vd.affsn.sentinelle.application.port.in.ConsulterRupturesUseCase;
import vd.affsn.sentinelle.domain.model.Acteur;

/** Décorateur transactionnel (lecture) de {@link ConsulterRupturesUseCase}. */
public class ConsulterRupturesTransactionnel implements ConsulterRupturesUseCase {

    private final ConsulterRupturesUseCase delegue;
    private final TransactionnelSupport tx;

    public ConsulterRupturesTransactionnel(ConsulterRupturesUseCase delegue, PlatformTransactionManager tm) {
        this.delegue = delegue;
        this.tx = TransactionnelSupport.lecture(tm);
    }

    @Override
    public Page<RuptureVue> consulter(Acteur acteur, Critere critere) {
        return tx.executer(() -> delegue.consulter(acteur, critere));
    }
}
