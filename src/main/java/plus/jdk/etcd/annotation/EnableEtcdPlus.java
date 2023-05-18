package plus.jdk.etcd.annotation;

import org.springframework.context.annotation.Import;
import plus.jdk.etcd.selector.EtcdKVSelector;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Import(EtcdKVSelector.class)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnableEtcdPlus {
}
