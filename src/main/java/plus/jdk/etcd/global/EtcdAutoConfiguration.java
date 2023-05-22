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
@EnableConfigurationProperties(EtcdPlusProperties.class)
public class EtcdAutoConfiguration {

}
