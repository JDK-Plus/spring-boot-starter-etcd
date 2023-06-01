package plus.jdk.etcd.global;

import io.etcd.jetcd.*;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.kv.PutResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.shaded.com.google.common.base.Charsets;
import io.etcd.jetcd.shaded.com.google.common.base.Function;
import io.etcd.jetcd.watch.WatchEvent;
import lombok.extern.slf4j.Slf4j;
import plus.jdk.etcd.common.IEventProcessor;
import plus.jdk.etcd.config.EtcdPlusProperties;
import plus.jdk.etcd.common.IConfigAdaptor;
import plus.jdk.etcd.model.KeyValuePair;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class EtcdClient {

    private final IConfigAdaptor configAdaptor;

    private final EtcdPlusProperties properties;

    private final Client client;

    public EtcdClient(IConfigAdaptor configAdaptor, EtcdPlusProperties properties) {
        this.properties = properties;
        this.configAdaptor = configAdaptor;
        this.client = Client.builder()
                .endpoints(properties.getEndpoints())
                .user(ByteSequence.from(properties.getUserName().getBytes()))
                .password(ByteSequence.from(properties.getPassword().getBytes()))
                .build();
    }

    public <T> Watch.Watcher watch(String key, IEventProcessor<T> processor, Class<T> clazz) {
        return this.watch(key, processor, clazz, WatchOption.DEFAULT);
    }

    public <T> Watch.Watcher watch(String key, IEventProcessor<T> processor, Class<T> clazz, WatchOption watchOption) {
        ByteSequence watchKey = ByteSequence.from(key, Charsets.UTF_8);
        return client.getWatchClient().watch(watchKey, watchOption, response -> {
            for (WatchEvent event : response.getEvents()) {
                KeyValue keyValue = event.getKeyValue();
                String valueStr = event.getKeyValue().getValue().toString(StandardCharsets.UTF_8);
                T data = configAdaptor.deserialize(valueStr, clazz);
                KeyValuePair<T> keyValuePair = new KeyValuePair<T>(key, data, keyValue);
                try{
                    processor.process(key, event,  keyValuePair,  watchOption, response);
                }catch (Exception | Error e) {
                    log.error("event process failed, event:{}, keyValues:{}", event, keyValuePair);
                }
            }
        });
    }

    public <T> CompletableFuture<Boolean> put(String key, T value, PutOption putOption) {
        KV kvClient = client.getKVClient();
        ByteSequence keyBytes = ByteSequence.from(key.getBytes());
        ByteSequence valueBytes = ByteSequence.from(configAdaptor.serialize(value).getBytes());
        return kvClient.put(keyBytes, valueBytes, putOption).thenApply((Function<PutResponse, Boolean>) putResponse -> {
            if(putResponse == null) {
                return false;
            }
            return putResponse.getHeader().getRevision() > 0;
        });
    }

    public <T> CompletableFuture<KeyValuePair<T>> getFirstKV(String key, Class<T> clazz) {
        GetOption getOption = GetOption.newBuilder()
                .withSortField(GetOption.SortTarget.VERSION)
                .withSortOrder(GetOption.SortOrder.DESCEND)
                .withLimit(1)
                .build();
        return this.get(key, clazz, getOption).thenApply((Function<List<KeyValuePair<T>>, KeyValuePair<T>>) keyValueDescs -> {
            if(keyValueDescs == null || keyValueDescs.isEmpty()) {
                return null;
            }
            return keyValueDescs.get(0);
        });
    }

    public <T> CompletableFuture<List<KeyValuePair<T>>> scanByPrefix(String prefix, Class<T> clazz) {
        GetOption getOption = GetOption.newBuilder()
                .isPrefix(true)
                .build();
        return this.get(prefix, clazz, getOption);
    }

    public <T> CompletableFuture<List<KeyValuePair<T>>> get(String key, Class<T> clazz, GetOption getOption) {
        KV kvClient = client.getKVClient();
        ByteSequence keyBytes = ByteSequence.from(key.getBytes());
        return kvClient.get(keyBytes, getOption).thenApply((Function<GetResponse, List<KeyValuePair<T>>>) getResponse -> {
            List<KeyValuePair<T>> keyValues = new ArrayList<>();
            if(getResponse == null) {
                 return keyValues;
             }
             List<KeyValue> keyValueList = getResponse.getKvs();
             if(keyValueList == null) {
                 return keyValues;
             }
             for(KeyValue keyValue:keyValueList) {
                 String keyStr = keyValue.getKey().toString(StandardCharsets.UTF_8);
                 String valueStr = keyValue.getValue().toString(StandardCharsets.UTF_8);
                 T value = null;
                 try{
                     value = configAdaptor.deserialize(valueStr, clazz);
                 }catch (Exception e) {
                     log.error("deserialize data failed, key:{}, value:{}, clazz:{}", keyStr, valueStr, clazz.getName());
                 }
                 keyValues.add(new KeyValuePair<>(keyStr, value, keyValue));
             }
             return keyValues;
         });
    }
}
