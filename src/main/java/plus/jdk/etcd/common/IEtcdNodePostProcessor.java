package plus.jdk.etcd.common;

import io.etcd.jetcd.watch.WatchEvent;
import io.etcd.jetcd.watch.WatchResponse;
import plus.jdk.etcd.annotation.EtcdNode;
import plus.jdk.etcd.model.KeyValuePair;

public interface IEtcdNodePostProcessor {

    default <T> void postProcessOnInitialization(EtcdNode etcdNode, KeyValuePair<T> keyValuePair) {

    }

    default <T> void postProcessOnChange(EtcdNode etcdNode, KeyValuePair<T> keyValuePair, WatchEvent event, WatchResponse response) {

    }
}
