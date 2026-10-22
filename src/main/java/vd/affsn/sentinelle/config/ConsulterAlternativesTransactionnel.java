package vd.affsn.sentinelle.config;

import vd.affsn.sentinelle.domain.model.CodeProduit;
import org.springframework.transaction.PlatformTransactionManager;
import vd.affsn.sentinelle.application.port.in.ConsulterAlternativesUseCase;
import vd.affsn.sentinelle.domain.model.Acteur;

/** Décorateur transactionnel (lecture) de {@link ConsulterAlternativesUseCase}. */
public class ConsulterAlternativesTransactionnel implements ConsulterAlternativesUseCase {

    private final ConsulterAlternativesUseCase delegue;
    private final TransactionnelSupport tx;

    public ConsulterAlternativesTransactionnel(ConsulterAlternativesUseCase delegue, PlatformTransactionManager tm) {
        this.delegue = delegue;
        this.tx = TransactionnelSupport.lecture(tm);
    }

    @Override
    public Resultat consulter(Acteur acteur, CodeProduit produit) {
        return tx.executer(() -> delegue.consulter(acteur, produit));
    }
}
