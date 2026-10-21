package vd.affsn.sentinelle.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import java.lang.reflect.Proxy;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import vd.affsn.sentinelle.application.port.in.CloturerRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.ConsulterAlternativesUseCase;
import vd.affsn.sentinelle.application.port.in.ConsulterRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.ConsulterRupturesUseCase;
import vd.affsn.sentinelle.application.port.in.DeclarerRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.MettreAJourRuptureUseCase;
import vd.affsn.sentinelle.domain.model.Acteur;

class DecorateursTransactionnelsTest {

    private static final Acteur ACTEUR = Acteur.pharmacien("sujet-test");

    /** Un cas : appel du décorateur (délégué mocké), attendu readOnly. */
    private record Cas(String nom, boolean lectureSeule, Function<TransactionManagerEnregistreur, Appel> fabrique) {
    }

    private interface Appel {
        Object appeler(Throwable aLever);
    }

    /** Délégué de test sans Mockito : renvoie null ou lève l'exception demandée. */
    @SuppressWarnings("unchecked")
    private static <T> T delegue(Class<T> type, Throwable lever) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, m, a) -> {
            if (lever != null) {
                throw lever;
            }
            return null;
        });
    }

    private List<Cas> cas() {
        return List.of(
            new Cas("declarer", false, t -> l ->
                new DeclarerRuptureTransactionnel(delegue(DeclarerRuptureUseCase.class, l), t).declarer(ACTEUR, null)),
            new Cas("mettreAJour", false, t -> l ->
                new MettreAJourRuptureTransactionnel(delegue(MettreAJourRuptureUseCase.class, l), t)
                        .mettreAJour(ACTEUR, null)),
            new Cas("cloturer", false, t -> l ->
                new CloturerRuptureTransactionnel(delegue(CloturerRuptureUseCase.class, l), t).cloturer(ACTEUR, null)),
            new Cas("consulterRupture", true, t -> l ->
                new ConsulterRuptureTransactionnel(delegue(ConsulterRuptureUseCase.class, l), t)
                        .consulter(ACTEUR, null)),
            new Cas("consulterRuptures", true, t -> l ->
                new ConsulterRupturesTransactionnel(delegue(ConsulterRupturesUseCase.class, l), t)
                        .consulter(ACTEUR, null)),
            new Cas("consulterAlternatives", true, t -> l ->
                new ConsulterAlternativesTransactionnel(delegue(ConsulterAlternativesUseCase.class, l), t)
                        .consulter(ACTEUR, null)));
    }

    @Test
    void succesCommitteDansUneTransactionRequiseAvecLeBonModeLecture() {
        for (Cas c : cas()) {
            TransactionManagerEnregistreur t = new TransactionManagerEnregistreur();
            assertThat(c.fabrique().apply(t).appeler(null)).as(c.nom()).isNull();
            assertThat(t.evenements).as(c.nom()).containsExactly("begin", "commit");
            assertThat(t.readOnly).as(c.nom()).containsExactly(c.lectureSeule());
            assertThat(t.propagations).as(c.nom()).containsExactly(0);
        }
    }

    @Test
    void exceptionDuServiceRollbackEtEstRelanceeIntacte() {
        for (Cas c : cas()) {
            for (RuntimeException e : List.of(new IllegalStateException("métier"), new RuntimeException("x"))) {
                TransactionManagerEnregistreur t = new TransactionManagerEnregistreur();
                Throwable lancee = catchThrowable(() -> c.fabrique().apply(t).appeler(e));
                assertThat(lancee).as(c.nom()).isSameAs(e);
                assertThat(t.evenements).as(c.nom()).containsExactly("begin", "rollback");
            }
        }
    }
}
