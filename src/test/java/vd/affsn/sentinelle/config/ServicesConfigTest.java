package vd.affsn.sentinelle.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Proxy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import vd.affsn.sentinelle.application.port.in.CloturerRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.ConsulterAlternativesUseCase;
import vd.affsn.sentinelle.application.port.in.ConsulterRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.ConsulterRupturesUseCase;
import vd.affsn.sentinelle.application.port.in.DeclarerRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.MettreAJourRuptureUseCase;
import vd.affsn.sentinelle.application.port.out.AuditPort;
import vd.affsn.sentinelle.application.port.out.HorlogePort;
import vd.affsn.sentinelle.application.port.out.RuptureRepository;

class ServicesConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ServicesConfig.class)
            .withBean(RuptureRepository.class, () -> stub(RuptureRepository.class))
            .withBean(AuditPort.class, () -> stub(AuditPort.class))
            .withBean(PlatformTransactionManager.class, TransactionManagerEnregistreur::new);

    private static <T> T stub(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, m, a) -> switch (m.getName()) {
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p == a[0];
            case "toString" -> "stub";
            default -> null;
        }));
    }

    @Test
    void unSeulBeanParUseCaseEtCEstUnDecorateur() {
        runner.run(ctx -> {
            assertThat(ctx.getBeansOfType(DeclarerRuptureUseCase.class).values())
                    .singleElement().isInstanceOf(DeclarerRuptureTransactionnel.class);
            assertThat(ctx.getBeansOfType(MettreAJourRuptureUseCase.class).values())
                    .singleElement().isInstanceOf(MettreAJourRuptureTransactionnel.class);
            assertThat(ctx.getBeansOfType(CloturerRuptureUseCase.class).values())
                    .singleElement().isInstanceOf(CloturerRuptureTransactionnel.class);
            assertThat(ctx.getBeansOfType(ConsulterRupturesUseCase.class).values())
                    .singleElement().isInstanceOf(ConsulterRupturesTransactionnel.class);
            assertThat(ctx.getBeansOfType(ConsulterRuptureUseCase.class).values())
                    .singleElement().isInstanceOf(ConsulterRuptureTransactionnel.class);
            assertThat(ctx.getBeansOfType(ConsulterAlternativesUseCase.class).values())
                    .singleElement().isInstanceOf(ConsulterAlternativesTransactionnel.class);
        });
    }

    @Test
    void horlogeParDefautEstHorlogeSysteme() {
        runner.run(ctx -> assertThat(ctx.getBean(HorlogePort.class)).isInstanceOf(HorlogeSysteme.class));
    }

    @Test
    void fuseauInvalideFaitEchouerLeDemarrage() {
        runner.withPropertyValues("sentinelle.horloge.fuseau=Nulle/Part")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void horlogePeutEtreRemplacee() {
        HorlogePort fixe = new HorlogePort() {
            public Instant maintenant() { return Instant.EPOCH; }
            public LocalDate aujourdhui() { return LocalDate.of(2000, 1, 1); }
        };
        runner.withBean("horlogeDeTest", HorlogePort.class, () -> fixe).run(ctx -> {
            assertThat(ctx.getBeansOfType(HorlogePort.class)).hasSize(1);
            assertThat(ctx.getBean(HorlogePort.class)).isSameAs(fixe);
        });
    }
}
