package plus.jdk.etcd.selector;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNullApi;
import org.springframework.web.context.support.WebApplicationObjectSupport;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import plus.jdk.etcd.common.DefaultConfigAdaptor;
import plus.jdk.etcd.common.DefaultEtcdNodePostProcessor;
import plus.jdk.etcd.config.EtcdPlusProperties;
import plus.jdk.etcd.global.EtcdNodeDelegateService;
import plus.jdk.etcd.global.EtcdPlusService;

@Configuration
public class EtcdKVSelector extends WebApplicationObjectSupport implements BeanFactoryAware, WebMvcConfigurer {

    private BeanFactory beanFactory;

    @Bean
    DefaultConfigAdaptor getIConfigAdaptor() {
        return new DefaultConfigAdaptor();
    }

    @Bean
    DefaultEtcdNodePostProcessor getDefaultEtcdNodePostProcessor() {
        return new DefaultEtcdNodePostProcessor();
    }

    @Bean
    EtcdPlusService getEtcdPlusService(DefaultConfigAdaptor adaptor, EtcdPlusProperties properties){
        return new EtcdPlusService(adaptor, properties);
    }

    @Bean
    EtcdNodeDelegateService getEtcdNodeDelegateService(EtcdPlusService etcdPlusService, EtcdPlusProperties properties) {
        return new EtcdNodeDelegateService(beanFactory, getApplicationContext(), etcdPlusService, properties);
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
}
