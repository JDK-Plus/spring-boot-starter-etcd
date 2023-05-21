package plus.jdk.etcd.annotation;

import plus.jdk.etcd.common.DefaultEtcdNodePostProcessor;
import plus.jdk.etcd.common.IEtcdNodePostProcessor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface EtcdNode {

    /**
     * 配置路径
     */
    String path();

    /**
     * 初始化和数据变化时触发的回调
     */
    Class<? extends IEtcdNodePostProcessor> processor() default DefaultEtcdNodePostProcessor.class;
}
