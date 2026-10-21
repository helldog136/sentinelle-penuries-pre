package vd.affsn.sentinelle.config;

import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import vd.affsn.sentinelle.application.service.CloturerRuptureService;
import vd.affsn.sentinelle.application.service.ConsulterAlternativesService;
import vd.affsn.sentinelle.application.service.ConsulterRuptureService;
import vd.affsn.sentinelle.application.service.ConsulterRupturesService;
import vd.affsn.sentinelle.application.service.DeclarerRuptureService;
import vd.affsn.sentinelle.application.service.MettreAJourRuptureService;

/**
 * Câblage des use cases : seuls les décorateurs transactionnels sont des beans ; les services purs
 * sont instanciés ici et jamais exposés (une seule implémentation par interface).
 */
@Configuration(proxyBeanMethods = false)
public class ServicesConfig {

    /** Horloge par défaut, remplaçable : voir {@link HorlogeParDefautRegistrar}. */
    @Bean
    static BeanDefinitionRegistryPostProcessor horlogeParDefaut() {
        return new HorlogeParDefautRegistrar();
    }

    @Bean
    DeclarerRuptureUseCase declarerRuptureUseCase(RuptureRepository r, AuditPort a, HorlogePort h,
            PlatformTransactionManager tm) {
        return new DeclarerRuptureTransactionnel(new DeclarerRuptureService(r, a, h), tm);
    }

    @Bean
    MettreAJourRuptureUseCase mettreAJourRuptureUseCase(RuptureRepository r, AuditPort a, HorlogePort h,
            PlatformTransactionManager tm) {
        return new MettreAJourRuptureTransactionnel(new MettreAJourRuptureService(r, a, h), tm);
    }

    @Bean
    CloturerRuptureUseCase cloturerRuptureUseCase(RuptureRepository r, AuditPort a, HorlogePort h,
            PlatformTransactionManager tm) {
        return new CloturerRuptureTransactionnel(new CloturerRuptureService(r, a, h), tm);
    }

    @Bean
    ConsulterRupturesUseCase consulterRupturesUseCase(RuptureRepository r, PlatformTransactionManager tm) {
        return new ConsulterRupturesTransactionnel(new ConsulterRupturesService(r), tm);
    }

    @Bean
    ConsulterRuptureUseCase consulterRuptureUseCase(RuptureRepository r, PlatformTransactionManager tm) {
        return new ConsulterRuptureTransactionnel(new ConsulterRuptureService(r), tm);
    }

    @Bean
    ConsulterAlternativesUseCase consulterAlternativesUseCase(RuptureRepository r, PlatformTransactionManager tm) {
        return new ConsulterAlternativesTransactionnel(new ConsulterAlternativesService(r), tm);
    }
}
