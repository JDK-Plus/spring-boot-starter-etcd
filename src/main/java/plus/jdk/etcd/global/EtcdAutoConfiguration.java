package plus.jdk.etcd.global;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import plus.jdk.etcd.annotation.EnableEtcdPlus;
import plus.jdk.etcd.config.EtcdPlusProperties;

@Slf4j
@Configuration
@EnableEtcdPlus
@ConditionalOnProperty(prefix = "plus.jdk.etcd", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(EtcdPlusProperties.class)
public class EtcdAutoConfiguration {

    public EtcdAutoConfiguration(EtcdPlusProperties etcdPlusProperties) {
        log.info("{}", etcdPlusProperties);
    }
}
