package vd.affsn.sentinelle.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** Gestionnaire de transactions de test : enregistre begin/commit/rollback et le drapeau readOnly. */
class TransactionManagerEnregistreur extends AbstractPlatformTransactionManager {

    final List<String> evenements = new ArrayList<>();
    final List<Boolean> readOnly = new ArrayList<>();
    final List<Integer> propagations = new ArrayList<>();

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        evenements.add("begin");
        readOnly.add(definition.isReadOnly());
        propagations.add(definition.getPropagationBehavior());
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        evenements.add("commit");
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        evenements.add("rollback");
    }
}
