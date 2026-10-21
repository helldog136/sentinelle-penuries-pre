package vd.affsn.sentinelle.config;

import vd.affsn.sentinelle.application.port.in.RuptureVue;
import org.springframework.transaction.PlatformTransactionManager;
import vd.affsn.sentinelle.application.port.in.CloturerRuptureUseCase;
import vd.affsn.sentinelle.domain.model.Acteur;

/** Décorateur transactionnel (ecriture) de {@link CloturerRuptureUseCase}. */
public class CloturerRuptureTransactionnel implements CloturerRuptureUseCase {

    private final CloturerRuptureUseCase delegue;
    private final TransactionnelSupport tx;

    public CloturerRuptureTransactionnel(CloturerRuptureUseCase delegue, PlatformTransactionManager tm) {
        this.delegue = delegue;
        this.tx = TransactionnelSupport.ecriture(tm);
    }

    @Override
    public RuptureVue cloturer(Acteur acteur, Commande commande) {
        return tx.executer(() -> delegue.cloturer(acteur, commande));
    }
}
