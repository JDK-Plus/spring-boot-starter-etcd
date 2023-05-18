package plus.jdk.etcd.common;

import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
import io.etcd.jetcd.watch.WatchResponse;
import plus.jdk.etcd.model.KeyValuePair;

public interface IEventProcessor<T> {
    void process(String watchKey, WatchEvent event, KeyValuePair<T> keyValuePair, WatchOption option, WatchResponse watchResponse);
}
