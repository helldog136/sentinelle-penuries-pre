package vd.affsn.sentinelle.config;

import vd.affsn.sentinelle.application.port.in.RuptureVue;
import org.springframework.transaction.PlatformTransactionManager;
import vd.affsn.sentinelle.application.port.in.MettreAJourRuptureUseCase;
import vd.affsn.sentinelle.domain.model.Acteur;

/** Décorateur transactionnel (ecriture) de {@link MettreAJourRuptureUseCase}. */
public class MettreAJourRuptureTransactionnel implements MettreAJourRuptureUseCase {

    private final MettreAJourRuptureUseCase delegue;
    private final TransactionnelSupport tx;

    public MettreAJourRuptureTransactionnel(MettreAJourRuptureUseCase delegue, PlatformTransactionManager tm) {
        this.delegue = delegue;
        this.tx = TransactionnelSupport.ecriture(tm);
    }

    @Override
    public RuptureVue mettreAJour(Acteur acteur, Commande commande) {
        return tx.executer(() -> delegue.mettreAJour(acteur, commande));
    }
}
