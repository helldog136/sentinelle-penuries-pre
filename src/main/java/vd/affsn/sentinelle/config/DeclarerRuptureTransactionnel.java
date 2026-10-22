package vd.affsn.sentinelle.config;

import vd.affsn.sentinelle.application.port.in.RuptureVue;
import org.springframework.transaction.PlatformTransactionManager;
import vd.affsn.sentinelle.application.port.in.DeclarerRuptureUseCase;
import vd.affsn.sentinelle.domain.model.Acteur;

/** Décorateur transactionnel (ecriture) de {@link DeclarerRuptureUseCase}. */
public class DeclarerRuptureTransactionnel implements DeclarerRuptureUseCase {

    private final DeclarerRuptureUseCase delegue;
    private final TransactionnelSupport tx;

    public DeclarerRuptureTransactionnel(DeclarerRuptureUseCase delegue, PlatformTransactionManager tm) {
        this.delegue = delegue;
        this.tx = TransactionnelSupport.ecriture(tm);
    }

    @Override
    public RuptureVue declarer(Acteur acteur, Commande commande) {
        return tx.executer(() -> delegue.declarer(acteur, commande));
    }
}
