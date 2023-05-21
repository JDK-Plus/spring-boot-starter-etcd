package plus.jdk.etcd.global;

import io.etcd.jetcd.Watch;
import io.etcd.jetcd.watch.WatchEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import plus.jdk.etcd.annotation.EtcdNode;
import plus.jdk.etcd.common.IEtcdNodePostProcessor;
import plus.jdk.etcd.config.EtcdPlusProperties;
import plus.jdk.etcd.model.EtcdWatcherModel;
import plus.jdk.etcd.model.KeyValuePair;

import java.lang.reflect.Field;
import java.util.concurrent.*;

@Slf4j
public class EtcdNodeDelegateService implements BeanPostProcessor {

    private final ConfigurableBeanFactory configurableBeanFactory;

    private final ConfigurableApplicationContext configurableApplicationContext;

    private final EtcdPlusService etcdPlusService;

    private final EtcdPlusProperties properties;

    private final ThreadPoolExecutor threadPoolExecutor;

    private Boolean started = false;

    public EtcdNodeDelegateService(BeanFactory beanFactory, ApplicationContext context,
                                   EtcdPlusService etcdPlusService, EtcdPlusProperties properties) {
        this.configurableApplicationContext = (ConfigurableApplicationContext) context;
        this.configurableBeanFactory = this.configurableApplicationContext.getBeanFactory();
        this.etcdPlusService = etcdPlusService;
        this.properties = properties;
        this.threadPoolExecutor = new ThreadPoolExecutor(properties.getWatcherCoreThreadPollSize(),
                properties.getWatcherThreadPollMaxSize(), properties.getWatcherThreadKeepAliveTime(), TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(properties.getWatcherThreadPoolCapacity()));
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        processBeanFields(bean, beanName);
        return bean;
    }

    protected <T> void processBeanFields(Object bean, String beanName) {
        for (Field field : bean.getClass().getDeclaredFields()) {
            EtcdNode etcdNode = field.getAnnotation(EtcdNode.class);
            if (etcdNode == null) {
                continue;
            }
            EtcdWatcherModel<T> etcdWatcher = new EtcdWatcherModel<T>(etcdNode, bean, field, (Class<T>) field.getType());
            synchronizeDataFromEtcd(etcdWatcher);
        }
    }


    protected <T> void synchronizeDataFromEtcd(EtcdWatcherModel<T> watcherModel) {
        try {
            EtcdNode etcdNode = watcherModel.getEtcdNode();
            KeyValuePair<T> keyValuePair = etcdPlusService.getFirstKV(etcdNode.path(), watcherModel.getClazz()).get();
            watcherModel.setFieldValue(keyValuePair.getValue());
            IEtcdNodePostProcessor processor = configurableApplicationContext.getBean(etcdNode.processor());
            Watch.Watcher watcher = etcdPlusService.watch(etcdNode.path(), (watchKey, event, keyValue, option, watchResponse) -> {
                watcherModel.setFieldValue(keyValue.getValue());
                log.info("type={}, key={}, value={}", event.getEventType().toString(), keyValue.getKey(), keyValue.getValue());
                try{
                    processor.postProcessOnChange(etcdNode, keyValue, event, watchResponse);
                }catch (Exception | Error e) {
                    e.printStackTrace();
                }
            }, watcherModel.getClazz());
            processor.postProcessOnInitialization(etcdNode, keyValuePair);
        } catch (Exception | Error e) {
            e.printStackTrace();
            log.error("distributeZKNodeDataForBeanField, msg:{}", e.getMessage());
        }
    }
}
