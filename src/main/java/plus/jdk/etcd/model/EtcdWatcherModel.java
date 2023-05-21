package plus.jdk.etcd.model;

import io.etcd.jetcd.Watch;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.util.ReflectionUtils;
import plus.jdk.etcd.annotation.EtcdNode;

import java.lang.reflect.Field;

@Data
@AllArgsConstructor
public class EtcdWatcherModel<T> {

    /**
     * 字段注解
     */
    private EtcdNode etcdNode;

    /**
     * 对应的bean实例
     */
    private Object beanInstance;

    /**
     * 需要刷新的字段
     */
    private Field field;

    /**
     * 字段类型
     */
    private Class<T> clazz;

    public void setFieldValue(Object value) {
        ReflectionUtils.makeAccessible(field);
        ReflectionUtils.setField(field, beanInstance, value);
    }
}
