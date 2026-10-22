package vd.affsn.sentinelle.config;

import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Frontière transactionnelle commune des décorateurs. Écriture : REQUIRED, lecture-écriture ;
 * lecture : REQUIRED en lecture seule. Rollback sur toute RuntimeException/Error, relancée intacte.
 */
final class TransactionnelSupport {

    private final TransactionTemplate template;

    private TransactionnelSupport(PlatformTransactionManager tm, boolean lectureSeule) {
        this.template = new TransactionTemplate(tm);
        this.template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.template.setReadOnly(lectureSeule);
    }

    static TransactionnelSupport ecriture(PlatformTransactionManager tm) {
        return new TransactionnelSupport(tm, false);
    }

    static TransactionnelSupport lecture(PlatformTransactionManager tm) {
        return new TransactionnelSupport(tm, true);
    }

    <T> T executer(Supplier<T> action) {
        return template.execute(status -> action.get());
    }
}
