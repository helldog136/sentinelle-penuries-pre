package vd.affsn.sentinelle.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import vd.affsn.sentinelle.application.port.out.HorlogePort;

/**
 * Enregistre {@link HorlogeSysteme} seulement si aucun autre {@link HorlogePort} n'est défini, une fois TOUTES
 * les définitions de beans chargées (équivalent fiable de {@code @ConditionalOnMissingBean}, dont l'évaluation
 * dépend de l'ordre de traitement des classes de configuration hors auto-configuration).
 */
class HorlogeParDefautRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

    static final String NOM_BEAN = "horlogePort";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (registry instanceof ListableBeanFactory lbf
                && lbf.getBeanNamesForType(HorlogePort.class, true, false).length > 0) {
            return;
        }
        RootBeanDefinition definition = new RootBeanDefinition(HorlogeSysteme.class,
                () -> HorlogeSysteme.pourFuseau(environment.getProperty("sentinelle.horloge.fuseau", "UTC")));
        registry.registerBeanDefinition(NOM_BEAN, definition);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // rien à faire
    }
}
